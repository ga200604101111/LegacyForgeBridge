#!/usr/bin/env python3
"""Offline reproducible cumulative JAR build + source-backed regressions.
Usage: python3 build_local.py REV241_MAIN.jar ORIGINAL_RPGTOOL.jar OUTPUT_REV242.jar
Requires JDK 21 (javac/java/javap) and Python 3. No Gradle or Actions.
The exact old mod is read only for tests, never modified or packaged.
"""
import hashlib,json,os,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
BASE_SHA='7ebcea2bd669fb6baed5b99e5929d68d9a356660035d9b2cd67e6ef3864b9835'
SOURCE_SHA='b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d'
VERSION='0.2.0-alpha.27-corpus4-local.26-rev242-local-test.1'
REVISION='2026-10-02.242-stationary-descent-and-source-landing'
CACHE='0.2.0-alpha.27-corpus4-local.17-rev233-cache.1'
B='dev/yinghuang/legacyforgebridge/behavior/'
EXPORTS=sum((['--add-exports','java.base/jdk.internal.org.objectweb.asm'+p+'=ALL-UNNAMED'] for p in ['', '.tree', '.commons', '.util']),[])

def sha(data):return hashlib.sha256(data).hexdigest()
def run(args,**kw):return subprocess.run(args,check=True,**kw)
def main():
 if len(sys.argv)!=4:raise SystemExit(__doc__)
 base,source,output=map(lambda p:Path(p).resolve(),sys.argv[1:])
 if output in (base,source):raise SystemExit('Refusing to overwrite an input')
 if sha(base.read_bytes())!=BASE_SHA:raise SystemExit('Wrong cumulative rev241 baseline SHA-256')
 if sha(source.read_bytes())!=SOURCE_SHA:raise SystemExit('Wrong original source JAR SHA-256')
 logs=[]
 with tempfile.TemporaryDirectory(prefix='lfb-rev242-') as tmp:
  work=Path(tmp);classes=work/'classes';tools=work/'tools';test=work/'test-classes'
  run(['javac','--release','21','-cp',str(base),'-d',str(classes),*map(str,sorted((ROOT/'source').rglob('*.java')))])
  run(['javac',*EXPORTS,'-d',str(tools),str(ROOT/'PatchRev242.java'),str(ROOT/'tests/ExtractSourceFixture.java'),str(ROOT/'tests/AuditPatches.java')])
  run(['java',*EXPORTS,'-cp',str(tools),'PatchRev242',str(base),str(classes)])
  run(['java',*EXPORTS,'-cp',str(tools),'ExtractSourceFixture',str(source),str(test)])
  sys.path.insert(0,str(ROOT/'tests'));from write_stubs import write
  stubs=write(work/'test-src')
  cp=os.pathsep.join(map(str,[test,classes,base]))
  run(['javac','--release','21','-cp',cp,'-d',str(test),*stubs,str(ROOT/'tests/RegressionTest.java')])
  results={}
  for mode in ['baseline','fixed','off']:
   order=[test,base,classes] if mode=='baseline' else [test,classes,base]
   flags=['-Dlegacyforgebridge.jumpReconcile=off'] if mode=='off' else []
   r=run(['java','-Xverify:all',*flags,'-cp',os.pathsep.join(map(str,order)),'RegressionTest',mode,str(ROOT/'tests/reported-jumps.tsv')],capture_output=True,text=True)
   logs.append(r.stdout+r.stderr);print(r.stdout,end='')
   results[mode]=r.stdout.strip()
  patches={str(p.relative_to(classes)).replace(os.sep,'/'):p.read_bytes() for p in sorted(classes.rglob('*.class'))}
  for name in patches:
   r=run(['javap','-p','-v','-cp',str(classes),name[:-6].replace('/','.')],capture_output=True,text=True)
   if 'major version: 65' not in r.stdout:raise RuntimeError('Wrong bytecode version '+name)
  provenance={
   'schema':1,'checkpoint':'rev242-stationary-descent-and-source-landing',
   'sourceParentCommit':'038581a307f9eccdb93d24ca5a30b0117c228cb1',
   'baseSha256':BASE_SHA,'originalSourceSha256':SOURCE_SHA,
   'reportedLogSha256':'5cdace8c6ff139d8df4982b92b0f4b01ca74bf9b9f5e170624769eccc389d970',
   'version':VERSION,'converterRevision':REVISION,'cacheCompatibilityVersion':CACHE,
   'buildMethod':'JDK21 javac source helpers + guarded JDK ASM edits over cumulative rev241 main JAR',
   'fullGradleLoomBuild':False,'networkDependencyDownloads':False,'githubActionsBuild':False,
   'minecraftTestDoubles':True,'actualOriginalSourceMethodBodiesInTests':True,
   'testDoubleClassesPackaged':False,'liveMinecraftLaunch':False,
   'motion':{'sourceBoostModified':False,'heightLimitAdded':False,'incomingXZModified':False,
     'previousDescent':'at most one exact observed negative phase from an immediately previous completed stationary jump; current stationary ascent only',
     'maxLandingToNextJumpTicks':2,'maxLandingToPacketTicks':8,'maxCurrentPhaseForPreviousDescent':6},
   'fall':{'dispatch':'existing source-compiled fall programs, native-hook + end-client-tick fallback',
     'deduplication':'local player and tick','coordinates':'1.7.10 local posY offset 1.62F scoped only to fall snapshots',
     'modNameOrItemIdDispatch':False,'hardcodedParticles':False,'serverDamageExecuted':False},
   'verification':results,
   'limitations':['Legacy velocity packets lack causal IDs; exact matching is a heuristic, not a proven server acknowledgement.',
     'An unrelated identical velocity with no observed damage/teleport barrier can still be misclassified.',
     'Cross-jump negative guard deliberately excludes moving previous/current jumps and expires quickly.',
     'No native 1.7.10 paired live-client measurements; normal source boost remains 0.15, not an invented 3-5 block jump.',
     'Landing fallback is local-only and excludes fluids, climbing and flying; remote-player fall effects are not added.',
     'Particle renderer/shader settings and actual mixin application remain untested in game.']}
  with zipfile.ZipFile(base) as z:
   if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise RuntimeError('Bad base ZIP')
   old={n:z.read(n) for n in z.namelist()}
  mod=json.loads(old['fabric.mod.json']);mod['version']=VERSION
  patches['fabric.mod.json']=(json.dumps(mod,ensure_ascii=False,indent=2)+'\n').encode()
  patches['legacyforgebridge/rev242-build.json']=(json.dumps(provenance,indent=2)+'\n').encode()
  removed={B+'Rev241JumpMotionBridge$Api.class'} # obsolete; shared Rev242ClientAccess replaces it
  output.parent.mkdir(parents=True,exist_ok=True)
  with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name in sorted((set(old)|set(patches))-removed):
    info=zipfile.ZipInfo(name,(2026,10,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
    info.external_attr=(0o40755 if name.endswith('/') else 0o100644)<<16
    z.writestr(info,patches.get(name,old.get(name)),compresslevel=9)
  with zipfile.ZipFile(output) as z:
   if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise RuntimeError('Bad output ZIP')
   new={n:z.read(n) for n in z.namelist()}
  changed=sorted(n for n in set(old)&set(new) if old[n]!=new[n]);added=sorted(set(new)-set(old))
  allowed={B+n+'.class' for n in ['LegacyBehaviorRuntime','LegacyBehaviorRuntime$Snapshot','LegacyClientJumpMotion','Rev241JumpHistory','Rev241JumpHistory$Decision','Rev241JumpMotionBridge','Rev241JumpMotionBridge$Pending','Rev241JumpMotionBridge$Source']}
  allowed|={'dev/yinghuang/legacyforgebridge/BuildInfo.class','fabric.mod.json'}
  if not set(changed)<=allowed:raise RuntimeError('Unexpected changed entries '+str(set(changed)-allowed))
  expected_added={B+n+'.class' for n in ['Rev241JumpHistory$Completed','Rev242ClientAccess','Rev242LandingBridge']}|{'legacyforgebridge/rev242-build.json'}
  if set(added)!=expected_added or set(old)-set(new)!=removed:raise RuntimeError('Unexpected entry additions/removals')
  for name in ['META-INF/MANIFEST.MF','META-INF/jars/energy-4.2.0.jar','META-INF/lfb/desktop-helper.jar',B+'Rev239VanillaLiquidBridge.class','dev/yinghuang/legacyforgebridge/convert/runtime/ConvertedLegacyBlock.class']:
   if old[name]!=new[name]:raise RuntimeError('Protected prior fix changed '+name)
  audit=run(['java',*EXPORTS,'-cp',str(tools),'AuditPatches',str(base),str(output)],capture_output=True,text=True)
  logs.append(audit.stdout+audit.stderr);print(audit.stdout,end='')
  packaged=run(['java','-Xverify:all','-cp',os.pathsep.join(map(str,[test,output])),'RegressionTest','fixed',str(ROOT/'tests/reported-jumps.tsv')],capture_output=True,text=True)
  logs.append('PACKAGED RUN\n'+packaged.stdout+packaged.stderr);print(packaged.stdout,end='')
  digest=sha(output.read_bytes())
  evidence={**provenance,'artifact':output.name,'bytes':output.stat().st_size,'sha256':digest,
    'baselineEntries':len(old),'outputEntries':len(new),'changedEntries':changed,'addedEntries':added,'removedEntries':sorted(removed),
    'allOtherEntriesContentPreserved':True,'zipIntegrity':'pass','javapClassParse':'pass',
    'coreBytecodeAudit':audit.stdout.strip(),'packagedVerification':packaged.stdout.strip(),
    'jvmVerification':'-Xverify:all on actual helper classes with Minecraft/logger/snapshot doubles and original source event method bodies',
    'jdk':run(['java','-version'],capture_output=True,text=True).stderr.strip(),
    'sourceSha256':{str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in sorted(ROOT.rglob('*')) if p.is_file() and p.suffix in ('.java','.py','.tsv')}}
  output.with_suffix('.evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
  output.with_suffix('.sha256').write_text(digest+'  '+output.name+'\n')
  output.with_suffix('.tests.log').write_text('\n'.join(logs))
  print('ARTIFACT',output.name,output.stat().st_size,digest)
if __name__=='__main__':main()
