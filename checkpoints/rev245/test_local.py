#!/usr/bin/env python3
"""Offline packaged rev245 regression runner. Uses explicit test doubles; no live game/server."""
from pathlib import Path
import hashlib, importlib.util, json, os, subprocess, sys, tempfile, zipfile
ROOT=Path(__file__).resolve().parent
EXPECTED='92d00685e9316946353bf410455e57f14a15fd4a903ce5eed749b4fafd658ae6'
CASES='inactive-start local-start nonlegacy-start offthread-start identity-start stop-start restart-counts active-start-idempotent missing-camera-move missing-tickstart pending-move-restart pending-move-same-capture failure-restart source-label source-semantics untraced-source readonly-observers local-commands reconnect registration-once'.split()
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def run(args,**kw):
    p=subprocess.run(list(map(str,args)),text=True,capture_output=True,timeout=60,**kw)
    if p.stdout:print(p.stdout,end='')
    if p.stderr:print(p.stderr,end='',file=sys.stderr)
    if p.returncode:raise SystemExit(p.returncode)
    return p

def write_stubs(out):
    p=ROOT/'tests/write_stubs.py'
    spec=importlib.util.spec_from_file_location('rev243_stubs',p);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    return m.write(out)

def main():
    if len(sys.argv)!=2:raise SystemExit('usage: test_local.py REV245.jar')
    jar=Path(sys.argv[1]).resolve()
    if sha(jar)!=EXPECTED:raise SystemExit('artifact SHA-256 mismatch')
    with zipfile.ZipFile(jar) as z:
        if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise SystemExit('invalid artifact ZIP')
    with tempfile.TemporaryDirectory(prefix='lfb-rev245-test-') as td:
        w=Path(td);doubles=w/'doubles';pkg=w/'pkg';real=w/'real';logs=w/'logs'
        stub_sources=write_stubs(doubles)
        pkg.mkdir();real.mkdir()
        pkg_sources=[*stub_sources,ROOT/'tests/LegacyBehaviorApi.java',ROOT/'tests/Rev243TraceWriter.java',ROOT/'tests/CaptureScopeTest.java',ROOT/'tests/CorrelationTest.java']
        run(['javac','--release','21','-encoding','UTF-8','-cp',jar,'-d',pkg,*pkg_sources])
        total=0
        for case in CASES:
            p=run(['java','-Xverify:all','-cp',os.pathsep.join(map(str,[pkg,jar])),'dev.yinghuang.legacyforgebridge.behavior.CaptureScopeTest',case])
            marker='checks=';total+=int(p.stdout.split(marker)[-1].split()[0])
        corr=run(['java','-Xverify:all','-cp',os.pathsep.join(map(str,[pkg,jar])),'dev.yinghuang.legacyforgebridge.behavior.CorrelationTest'])
        corr_checks=int(corr.stdout.split('checks=')[-1].split()[0])
        run(['javac','--release','21','-encoding','UTF-8','-cp',jar,'-d',real,*stub_sources,ROOT/'tests/RealWriterHarness.java'])
        writer=run(['java','-Xverify:all','-cp',os.pathsep.join(map(str,[real,jar])),'dev.yinghuang.legacyforgebridge.behavior.RealWriterHarness',logs])
        print(json.dumps({'artifact':jar.name,'sha256':EXPECTED,'namedScenarios':len(CASES),'regressionAssertions':total,'correlationAssertions':corr_checks,'realWriter':writer.stdout.strip().splitlines()[-1]},indent=2))
if __name__=='__main__':main()
