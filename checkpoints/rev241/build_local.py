#!/usr/bin/env python3
"""Offline SHA-pinned cumulative main-JAR build, JDK21+Python3; no Actions/Gradle/downloads.
Usage: python3 build_local.py BASE_REV240.jar OUTPUT_REV241.jar
The test doubles are isolated in a temporary directory and NEVER packaged.
"""
import hashlib,json,os,re,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
BASE_SHA='61afe2b2a21ff4ae2f2fae2afe3f95bf2013f19e2f3d461cd2577ed5c1fa8a45'
PARENT='99df9f07ed1d86b27c6062a70a45bbecc54ee90d'
VERSION='0.2.0-alpha.27-corpus4-local.25-rev241-local-test.1'
REVISION='2026-10-02.241-source-jump-history-reconciliation'
CACHE='0.2.0-alpha.27-corpus4-local.17-rev233-cache.1'
PKG='dev/yinghuang/legacyforgebridge/'
EXPORTS=['--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
         '--add-exports','java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
def sha(b):return hashlib.sha256(b).hexdigest()
def run(cmd):
 p=subprocess.run(list(map(str,cmd)),capture_output=True,text=True)
 if p.returncode:raise RuntimeError('Command failed: '+' '.join(map(str,cmd))+'\n'+p.stdout+p.stderr)
 return p.stdout+p.stderr

def test(base,replacement,work):
 sys.path.insert(0,str(ROOT/'tests'))
 from write_stubs import write
 stubs=work/'test-source';tc=work/'test-classes';write(stubs)
 run(['javac','--release','21','-cp',os.pathsep.join(map(str,[replacement,base])),'-d',tc,
      *sorted(stubs.rglob('*.java')),ROOT/'tests/JumpLifecycleTest.java'])
 logs={}
 for mode,cp,props in [
     ('baseline',[tc,base,replacement],[]),('fixed',[tc,replacement,base],[]),
     ('off',[tc,replacement,base],['-Dlegacyforgebridge.jumpReconcile=off'])]:
  log=run(['java','-Xverify:all',*props,'-cp',os.pathsep.join(map(str,cp)),
           'JumpLifecycleTest',mode,ROOT/'tests/jump-fixture.tsv'])
  marker=next(s for s in log.splitlines() if s.startswith('RESULT '))
  logs[mode]=log;print(marker)
 return logs

def main():
 if len(sys.argv)!=3:raise SystemExit(__doc__)
 base,output=map(lambda a:Path(a).resolve(),sys.argv[1:])
 if base==output:raise SystemExit('Refusing to overwrite the baseline')
 if sha(base.read_bytes())!=BASE_SHA:raise SystemExit('Unexpected baseline SHA-256')
 with tempfile.TemporaryDirectory(prefix='lfb-rev241-') as directory:
  work=Path(directory);classes=work/'classes';tools=work/'tools'
  java_sources=sorted((ROOT/'source').rglob('*.java'))
  run(['javac','--release','21','-cp',base,'-d',classes,*java_sources])
  run(['javac',*EXPORTS,'-d',tools,ROOT/'PatchRev241.java'])
  patchlog=run(['java',*EXPORTS,'-cp',tools,'PatchRev241',base,classes]);print(patchlog.strip())
  class_files=sorted(classes.rglob('*.class'))
  expected={PKG+'behavior/Rev241JumpHistory.class',PKG+'behavior/Rev241JumpHistory$Decision.class',
   PKG+'behavior/Rev241JumpMotionBridge.class',PKG+'behavior/Rev241JumpMotionBridge$Api.class',
   PKG+'behavior/Rev241JumpMotionBridge$Source.class',PKG+'behavior/Rev241JumpMotionBridge$Pending.class',
   PKG+'behavior/LegacyClientJumpMotion.class',PKG+'BuildInfo.class'}
  patches={p.relative_to(classes).as_posix():p.read_bytes() for p in class_files}
  if set(patches)!=expected:raise RuntimeError('Unexpected class inventory')
  for name in sorted(patches):
   out=run(['javap','-p','-v','-cp',classes,name[:-6].replace('/','.')])
   if 'major version: 65' not in out:raise RuntimeError('Classfile version mismatch '+name)
  tests=test(base,classes,work)
  # No transient paths, timestamps, or output basename in the main-JAR metadata.
  prov={'schema':1,'checkpoint':'rev241-source-jump-history-reconciliation',
   'sourceParentCommit':PARENT,'baseSha256':BASE_SHA,'version':VERSION,'converterRevision':REVISION,
   'cacheCompatibilityVersion':CACHE,'buildMethod':'JDK21 javac helper classes + narrow ASM lifecycle hooks over exact cumulative rev240 main JAR',
   'fullGradleLoomBuild':False,'networkDependencyDownloads':False,'githubActionsBuild':False,
   'minecraftTestDoubles':True,'testDoubleClassesPackaged':False,'liveMinecraftLaunch':False,
   'algorithm':'bounded exact-codec matching to observed source-jump ascent history; current predicted Y retained only on a match; XZ never changed',
   'controls':{'default':'source-history','disableProperty':'-Dlegacyforgebridge.jumpReconcile=off','maxTicks':40},
   'limitations':['S12 has no jump identity or server timestamp; historical match is a compatibility heuristic, not a proven acknowledgement',
    'An unrelated identical encoded upward impulse without a barrier can be misclassified',
    'Any damage/entity-status/explosion/position/respawn barrier conservatively invalidates history, including unrelated-entity status',
    'No paired old/new real-client gameplay validation or server-side emission trace'],
   'verification':{k:next(s for s in v.splitlines() if s.startswith('RESULT ')) for k,v in tests.items()}}
  with zipfile.ZipFile(base) as z:
   if len(z.namelist())!=len(set(z.namelist())) or z.testzip():raise RuntimeError('Bad baseline ZIP')
   old={n:z.read(n) for n in z.namelist()}
  mod=json.loads(old['fabric.mod.json']);mod['version']=VERSION
  patches['fabric.mod.json']=(json.dumps(mod,ensure_ascii=False,indent=2)+'\n').encode()
  patches['legacyforgebridge/rev241-build.json']=(json.dumps(prov,indent=2)+'\n').encode()
  output.parent.mkdir(parents=True,exist_ok=True)
  with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name in sorted(set(old)|set(patches)):
    info=zipfile.ZipInfo(name,date_time=(2026,10,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
    info.external_attr=(0o40755 if name.endswith('/') else 0o100644)<<16
    z.writestr(info,patches.get(name,old.get(name)),compresslevel=9)
  with zipfile.ZipFile(output) as z:
   if len(z.namelist())!=len(set(z.namelist())) or z.testzip():raise RuntimeError('Bad output ZIP')
   new={n:z.read(n) for n in z.namelist()}
  changed=sorted(n for n in old if old[n]!=new[n]);added=sorted(set(new)-set(old))
  allowed=sorted([PKG+'behavior/LegacyClientJumpMotion.class',PKG+'BuildInfo.class','fabric.mod.json'])
  if changed!=allowed or set(old)-set(new):raise RuntimeError('Unintended cumulative changes')
  if any(n.startswith(('net/minecraft/','net/fabricmc/')) for n in added):raise RuntimeError('Test doubles leaked')
  for name in ['META-INF/MANIFEST.MF','META-INF/jars/energy-4.2.0.jar','META-INF/lfb/desktop-helper.jar',
        PKG+'behavior/Rev239VanillaLiquidBridge.class',PKG+'behavior/Rev239VanillaLiquidBridge$Api.class',
        PKG+'behavior/Rev239VanillaLiquidBridge$Binding.class',PKG+'render/ConvertedLiquidPresentationRuntime.class',
        PKG+'convert/runtime/ConvertedLegacyBlock.class',PKG+'mixin/client/LegacyJumpMotionPacketMixin.class']:
   if old[name]!=new[name]:raise RuntimeError('Protected content changed: '+name)
  packaged=test(base,output,work/'packaged')
  checksum=sha(output.read_bytes())
  source_paths=[ROOT/'PatchRev241.java',ROOT/'build_local.py',*java_sources,
                ROOT/'tests/JumpLifecycleTest.java',ROOT/'tests/write_stubs.py',ROOT/'tests/jump-fixture.tsv',ROOT/'tests/jump-fixture.json']
  ev={**prov,'artifact':output.name,'bytes':output.stat().st_size,'sha256':checksum,
      'baselineEntries':len(old),'outputEntries':len(new),'changedEntries':changed,'addedEntries':added,'removedEntries':[],
      'allOtherEntriesContentPreserved':True,'zipIntegrity':'pass','javapClassParse':'pass',
      'jvmVerification':'-Xverify:all; actual original/patched lifecycle classes with Minecraft/Fabric/source-event test doubles',
      'classfileVersion':65,'jdk':run(['java','-version']).strip(),
      'packagedVerification':{k:next(s for s in v.splitlines() if s.startswith('RESULT ')) for k,v in packaged.items()},
      'sourceSha256':{p.relative_to(ROOT).as_posix():sha(p.read_bytes()) for p in source_paths},
      'untested':['real Minecraft/Fabric/Sodium startup','actual mixin application with the installed modpack',
                   'live Forge server interaction and gameplay','water visual regression in game','external impulse with identical encoded ascent Y']}
  output.with_suffix('.evidence.json').write_text(json.dumps(ev,indent=2)+'\n')
  output.with_suffix('.sha256').write_text(checksum+'  '+output.name+'\n')
  output.with_suffix('.tests.log').write_text(patchlog+'\n'+''.join('\n=== PACKAGED '+k.upper()+' ===\n'+v for k,v in packaged.items()))
  print(json.dumps({'artifact':output.name,'bytes':ev['bytes'],'sha256':checksum,'changed':changed,'added':added},indent=2))
if __name__=='__main__':main()
