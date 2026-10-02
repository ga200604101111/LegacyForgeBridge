#!/usr/bin/env python3
"""Offline JDK21 incremental full main-JAR build; no Gradle, Actions or dependency fetch.
Usage: python build_local.py EXACT_REV242.jar OUTPUT_REV243.jar
"""
from pathlib import Path
import hashlib,json,os,subprocess,sys,tempfile,zipfile
ROOT=Path(__file__).resolve().parent
BASE_SHA='dadc5024b8713781684caa57ca93d55f54506c2abbf205366510a3f932ab8f44'
VERSION='0.2.0-alpha.27-corpus4-local.27-rev243-diagnostic.1'
REVISION='2026-10-02.243-observation-only-motion-diagnostics'
PARENT='2acf7d9481bb6f77d97a230ec9104d313a7360d4'
PKG='dev/yinghuang/legacyforgebridge/'
EXPORTS=['--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports','java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
def sha(data):return hashlib.sha256(data).hexdigest()
def run(args,logs):
    p=subprocess.run(list(map(str,args)),check=True,capture_output=True,text=True)
    text=p.stdout+p.stderr;logs.append(text)
    if text: print(text,end='')
    return text

def main():
    if len(sys.argv)!=3:raise SystemExit(__doc__)
    base,output=[Path(a).resolve() for a in sys.argv[1:]]
    if base==output:raise SystemExit('Refusing to overwrite baseline')
    if sha(base.read_bytes())!=BASE_SHA:raise SystemExit('Baseline SHA256 mismatch')
    logs=[]
    with tempfile.TemporaryDirectory(prefix='lfb-rev243-') as td:
        work=Path(td);classes=work/'classes';tools=work/'tools';testclasses=work/'testclasses'
        sources=sorted((ROOT/'source').rglob('*.java'))
        run(['javac','--release','21','-cp',base,'-d',classes,*sources],logs)
        run(['javac',*EXPORTS,'-d',tools,ROOT/'PatchRev243.java',ROOT/'tests/AuditReadOnly.java'],logs)
        run(['java',*EXPORTS,'-cp',tools,'PatchRev243',base,classes],logs)
        with zipfile.ZipFile(base) as z:
            if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise RuntimeError('Invalid base ZIP')
            old={n:z.read(n) for n in z.namelist()}
        patches={f.relative_to(classes).as_posix():f.read_bytes() for f in sorted(classes.rglob('*.class'))}
        if any(not n.startswith(PKG) for n in patches):raise RuntimeError('Foreign/stub class in production output')
        mod=json.loads(old['fabric.mod.json']);mod['version']=VERSION;mod['mixins'].append('legacyforgebridge.diagnostic.mixins.json')
        patches['fabric.mod.json']=(json.dumps(mod,indent=2)+'\n').encode()
        patches['legacyforgebridge.diagnostic.mixins.json']=(json.dumps({'required':False,'package':PKG.replace('/','.').rstrip('.')+'.mixin.diagnostic','compatibilityLevel':'JAVA_21','client':['Rev243CameraTraceMixin','Rev243MoveTraceMixin'],'injectors':{'defaultRequire':0}},indent=2)+'\n').encode()
        provenance={'schema':1,'checkpoint':'rev243-observation-only-motion-diagnostics','sourceParentCommit':PARENT,
            'baseSha256':BASE_SHA,'version':VERSION,'converterRevision':REVISION,
            'cacheCompatibilityVersion':'0.2.0-alpha.27-corpus4-local.17-rev233-cache.1',
            'buildMethod':'JDK21 javac --release 21 helpers + guarded JDK ASM checkpoint edits over cumulative rev242 full main JAR',
            'fullGradleLoomBuild':False,'networkDependencyDownloads':False,'githubActionsBuild':False,
            'heuristicReconciliation':'hard-disabled; old JVM enable values cannot reactivate it',
            'motionHooks':'six legacy helper hooks replaced by pure return; tick keeps independent rev242 landing adapter',
            'logging':{'autoStartOnLegacySession':True,'commands':['lfbtrace start','lfbtrace stop','lfbtrace mark','lfbtrace status'],
                'directory':'logs/lfb-motion','partMiB':16,'captureMiB':128,'queueCapacity':8192,'flushMilliseconds':500,
                'queueLossAndTruncationDisclosed':True,'stopAtDiskLimit':True,'oldCapturesDeleted':False},
            'observerCoverage':['existing raw legacy/Via motion stages and application stages retained',
                'tick start/end and jump/sneak key snapshots','local requested/actual move and bounding box',
                'Camera.update return positions and interpolation progress','source event before/after/exception',
                'source particle queue acceptance, not GPU visibility'],
            'liveMinecraftLaunch':False,'minecraftTestDoubles':True,'testDoubleClassesPackaged':False,
            'limitations':['Not a root-cause jump repair; original jerk/truncation may reappear without heuristics.',
                'No original 1.7.10 paired live-client or server-side causal tracing.',
                'Camera/move optional mixin application requires a real launch; missing observations reported.',
                'Camera sample is before final bobbing/shader matrices; not a full rendered-frame capture.',
                'Not all packet types or all direct field writes are observed.',
                'No per-game overhead measurement; additional capture work can affect timing.',
                'A full queue, truncated detail, disk cap, writer failure or missing end marks an incomplete capture.']}
        patches['legacyforgebridge/rev243-build.json']=(json.dumps(provenance,indent=2)+'\n').encode()
        new={**old,**patches};output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,data in sorted(new.items()):
                info=zipfile.ZipInfo(name,date_time=(2026,10,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                info.external_attr=(0o40755 if name.endswith('/') else 0o100644)<<16;z.writestr(info,data,compresslevel=9)
        with zipfile.ZipFile(output) as z:
            assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
            assert all(z.read(n)==data for n,data in new.items())
        changed=sorted(n for n in old if old[n]!=new[n]);added=sorted(set(new)-set(old))
        allow={PKG+'BuildInfo.class',PKG+'LegacyFileLogger.class',PKG+'network/FmlConnectionTrace.class',
               PKG+'behavior/LegacyClientJumpMotion.class',PKG+'behavior/LegacyMotionTraceLog.class',
               PKG+'behavior/Rev241JumpMotionBridge.class',PKG+'behavior/LegacyBehaviorApi$World.class','fabric.mod.json'}
        if set(changed)!=allow:raise RuntimeError('Unexpected existing class changes: '+str(changed))
        protected=[PKG+'behavior/Rev242LandingBridge.class',PKG+'behavior/Rev239VanillaLiquidBridge.class',
            PKG+'behavior/LegacyBehaviorRuntime.class',PKG+'behavior/LegacyBehaviorRuntime$Snapshot.class',
            PKG+'behavior/LegacyViaMotionTrace.class',PKG+'convert/runtime/ConvertedLegacyBlock.class',
            'legacyforgebridge.client.mixins.json','META-INF/MANIFEST.MF','META-INF/jars/energy-4.2.0.jar','META-INF/lfb/desktop-helper.jar']
        for n in protected:assert old[n]==new[n],n
        for n in patches:
            if n.endswith('.class'):
                text=subprocess.run(['javap','-p','-v','-cp',str(output),n[:-6].replace('/','.')],capture_output=True,text=True,check=True).stdout
                # Keep disassembly out of the human test log.
                assert 'major version: 65' in text,n
        audit=run(['java',*EXPORTS,'-cp',tools,'AuditReadOnly',output],logs)
        sys.path.insert(0,str(ROOT/'tests'))
        from write_stubs import write
        testsrc=write(work/'testsrc')
        run(['javac','--release','21','-cp',output,'-d',testclasses,*testsrc,ROOT/'tests/DiagnosticTest.java'],logs)
        test=run(['java','-Xverify:all','-cp',os.pathsep.join(map(str,[testclasses,output])),
                  'dev.yinghuang.legacyforgebridge.behavior.DiagnosticTest',work/'testlogs'],logs)
        evidence={**provenance,'artifact':output.name,'bytes':output.stat().st_size,'sha256':sha(output.read_bytes()),
            'baselineEntries':len(old),'outputEntries':len(new),'changedEntries':changed,'addedEntries':added,'removedEntries':[],
            'allOtherEntriesContentPreserved':True,'protectedEntries':protected,'zipIntegrity':'pass','classVersion':65,
            'javapParse':'pass','readOnlyAudit':audit.strip(),'packagedRegressionResults':test.strip(),
            'sourceSha256':{f.relative_to(ROOT).as_posix():sha(f.read_bytes()) for f in sorted(ROOT.rglob('*'))
                if f.is_file() and f.suffix in {'.java','.py'}},
            'jdk':subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip()}
        output.with_suffix('.evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
        output.with_suffix('.sha256').write_text(evidence['sha256']+'  '+output.name+'\n')
        output.with_suffix('.tests.log').write_text(''.join(logs))
        print(json.dumps({k:evidence[k] for k in ['artifact','bytes','sha256','changedEntries','addedEntries']},indent=2))
if __name__=='__main__':main()
