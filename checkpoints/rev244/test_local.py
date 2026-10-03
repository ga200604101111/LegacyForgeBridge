#!/usr/bin/env python3
"""Offline source regression tests. No main JAR, real game, disk-writer or server test."""
from __future__ import annotations
import importlib.util
import json
import re
import subprocess
import tempfile
from pathlib import Path
from prepare import ROOT, REPO, PACKAGE, original_sources, sources, sha, transform, replace_once, materialize

CASES = [
    'inactive-start','local-start','nonlegacy-start','offthread-start','identity-start',
    'stop-start','restart-counts','active-start-idempotent','missing-camera-move',
    'missing-tickstart','pending-move-restart','pending-move-same-capture','failure-restart',
    'source-label','source-semantics','untraced-source','readonly-observers','local-commands',
    'reconnect','registration-once',
]
OLD_FAILURES = set(CASES) - {
    'local-start','active-start-idempotent','pending-move-same-capture','source-semantics',
    'untraced-source','readonly-observers','local-commands','registration-once',
}
REFERENCES = {
    'checkpoints/rev242/source/'+PACKAGE+'Rev242ClientAccess.java':
        '9348ec6aea90120a4f98d0e6f5afefb5666060b53cd7ebe45f3e9c169be183c1',
    'checkpoints/rev243/tests/write_stubs.py':
        '1387861c7df8a5bd5e1fa71a0f02d7b46203a4b0cc48fd516ac7ddf8d4b266de',
}

def run(args: list, **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(list(map(str,args)), text=True, capture_output=True, timeout=30, **kwargs)

def main() -> None:
    baseline = original_sources()
    candidate = sources()
    for name, digest in REFERENCES.items():
        if sha((REPO/name).read_bytes()) != digest:
            raise ValueError('Pinned test dependency changed: '+name)
    spec=importlib.util.spec_from_file_location('rev243_test_doubles', REPO/'checkpoints/rev243/tests/write_stubs.py')
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    report={'checkpoint':'rev244-source-only','sourceCompilation':'javac --release 21',
            'execution':'java -Xverify:all; one fresh JVM per named case',
            'jdk':run(['java','-version'],check=True).stderr.strip(),
            'mainJarBuilt':False,'minecraftTestDoubles':True,'sourceEventApiTestDouble':True,
            'writerTestDouble':True,'diskWriterTested':False,'liveMinecraftTested':False,
            'originalModHandlerTested':False,'serverTested':False,'jumpRootCauseFixed':False,
            'baseSourceSha256':{n:sha(b) for n,b in baseline.items()},
            'candidateSourceSha256':{n:sha(b) for n,b in candidate.items()},
            'pinnedTestDependencies':REFERENCES,'cases':{}}
    with tempfile.TemporaryDirectory(prefix='lfb-rev244-test-') as td:
        work=Path(td)
        common=[*module.write(work/'doubles'),
                REPO/'checkpoints/rev242/source'/PACKAGE/'Rev242ClientAccess.java',
                *sorted((ROOT/'tests').glob('*.java'))]
        for label,payload in [('baseline',baseline),('candidate',candidate)]:
            java=[]
            for name,data in payload.items():
                path=work/label/'source'/PACKAGE/name
                path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);java.append(path)
            classes=work/label/'classes'
            compiled=run(['javac','--release','21','-encoding','UTF-8','-d',classes,*common,*java])
            if compiled.returncode:
                raise RuntimeError(label+' compilation failed:\n'+compiled.stdout+compiled.stderr)
            rows=[]
            for case in CASES:
                process=run(['java','-Xverify:all','-cp',classes,
                             'dev.yinghuang.legacyforgebridge.behavior.CaptureScopeTest',case])
                passed=process.returncode==0
                expected=label=='candidate' or case not in OLD_FAILURES
                if passed!=expected:
                    raise AssertionError(f'{label}/{case}: expected pass={expected}\n{process.stdout}{process.stderr}')
                rows.append({'case':case,'pass':passed,'stdout':process.stdout.strip(),'stderr':process.stderr.strip()})
                print(label,case,'PASS' if passed else 'EXPECTED_BASELINE_FAILURE')
            report['cases'][label]=rows
        guards=0
        def rejects(call, error=ValueError):
            nonlocal guards
            try:call()
            except error:guards+=1
            else:raise AssertionError('Missing guard')
        rejects(lambda:transform('LegacyMotionTraceLog.java',baseline['LegacyMotionTraceLog.java']+b' '))
        rejects(lambda:transform('unrecognized.java',b''))
        rejects(lambda:replace_once('missing','anchor','replacement'))
        rejects(lambda:replace_once('twice twice','twice','replacement'))
        rejects(lambda:materialize(REPO/'src'/'forbidden'))
        rejects(lambda:materialize(ROOT/'forbidden'))
        output=work/'materialized'
        manifest=materialize(output)
        for name,data in candidate.items():
            if (output/PACKAGE/name).read_bytes()!=data:raise AssertionError('Materialization mismatch')
        guards+=1
        if json.loads((output/'source-manifest.json').read_text())!=manifest:raise AssertionError('Manifest mismatch')
        guards+=1
        rejects(lambda:materialize(output),FileExistsError)
        report['preparationGuardChecks']=guards
    report['baselineExpectedFailures']=len(OLD_FAILURES)
    report['candidateNamedCasesPassed']=len(CASES)
    report['candidateChecks']=sum(int(re.search(r'checks=(\d+)',x['stdout']).group(1)) for x in report['cases']['candidate'])
    report['testFileSha256']={p.relative_to(ROOT).as_posix():sha(p.read_bytes())
                             for p in sorted(ROOT.rglob('*')) if p.is_file() and p.suffix in {'.java','.py'}}
    (ROOT/'test-results.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ['baselineExpectedFailures','candidateNamedCasesPassed','candidateChecks','preparationGuardChecks']},indent=2))

if __name__=='__main__':
    main()
