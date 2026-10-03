#!/usr/bin/env python3
"""Pinned offline JDK21 full-main build. No downloads or CI. Usage: build_local.py REV246.jar OUTPUT.jar"""
from pathlib import Path
import hashlib,io,json,os,subprocess,sys,tempfile,zipfile
ROOT=Path(__file__).resolve().parent
BASE_SHA='6fc64f88351db277ef438b8eb226d313baa5410bf5a8e1a007c65b85bed414c4'
VERSION='0.2.0-alpha.27-corpus4-local.30-rev247-ui-cache.1'
REVISION='2026-10-03.245-deep-motion-correlation-diagnostics'
PKG='dev/yinghuang/legacyforgebridge/'
UI=PKG+'desktop/DesktopProgressUi.class'
HELPER='META-INF/lfb/desktop-helper.jar'
EXPORTS=['--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports','java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
def sha(b):return hashlib.sha256(b).hexdigest()
def run(args):
 p=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=60)
 if p.returncode:raise RuntimeError(p.stdout+p.stderr)
 if p.stdout:print(p.stdout,end='')
 return p.stdout

def unzip(data):
 with zipfile.ZipFile(io.BytesIO(data)) as z:
  if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise ValueError('Invalid/duplicate ZIP')
  return {n:z.read(n) for n in z.namelist()}

def pack(entries):
 buffer=io.BytesIO()
 with zipfile.ZipFile(buffer,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for n,b in sorted(entries.items()):
   i=zipfile.ZipInfo(n,(2026,10,3,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=(0o40755 if n.endswith('/') else 0o100644)<<16
   z.writestr(i,b,compresslevel=9)
 return buffer.getvalue()

def main():
 if len(sys.argv)!=3:raise SystemExit(__doc__)
 base,output=[Path(x).resolve() for x in sys.argv[1:]]
 if base==output or output.exists():raise ValueError('Refusing baseline or existing output overwrite')
 data=base.read_bytes()
 if sha(data)!=BASE_SHA:raise ValueError('Baseline SHA-256 mismatch')
 old=unzip(data);oldhelper=unzip(old[HELPER]);assert oldhelper[UI]==old[UI]
 with tempfile.TemporaryDirectory(prefix='lfb-rev247-') as td:
  td=Path(td);classes=td/'classes';tools=td/'tools'
  src=sorted((ROOT/'source').rglob('*.java'))
  run(['javac','--release','21','-encoding','UTF-8','-cp',base,'-d',classes,*src])
  run(['javac',*EXPORTS,'-encoding','UTF-8','-d',tools,ROOT/'PatchRev247.java'])
  guards=run(['java',*EXPORTS,'-cp',tools,'PatchRev247',base,classes])
  compiled={f.relative_to(classes).as_posix():f.read_bytes() for f in sorted(classes.rglob('*.class'))}
  assert all(n.startswith(PKG) for n in compiled)
  new={**old,**compiled};helper=dict(oldhelper)
  helper[UI]=compiled[UI]
  for n,b in compiled.items():
   if n.startswith(PKG+'desktop/Rev247SupportWindow'):helper[n]=b
  new[HELPER]=pack(helper)
  mod=json.loads(old['fabric.mod.json']);assert mod['version']=='0.2.0-alpha.27-corpus4-local.29-rev246-ui.1'
  mod['version']=VERSION;new['fabric.mod.json']=(json.dumps(mod,indent=2,ensure_ascii=False)+'\n').encode()
  provenance={'schema':1,'checkpoint':'rev247-ui-cache-ground-evidence','sourceParent':'e34f9ed26478212beda239751c77b69338d9ef94','version':VERSION,
    'converterRevision':REVISION,'converterRevisionNote':'rev245 semantic identity retained; exact rev246 UI alias accepted only with unchanged schema/cache version and source SHA. No general stale cache acceptance.',
    'baseSha256':BASE_SHA,'buildMethod':'offline javac --release 21 + guarded named-method JDK ASM edits of pinned full rev246 main and nested helper',
    'fullGradleLoomBuild':False,'networkDependencyDownloads':False,'githubActionsBuild':False,
    'serverChanged':False,'originalModsChanged':False,'velocityReconciliationEnabled':False,
    'conversionFailureRootCauseConfirmed':False,'liveMinecraftValidated':False,
    'sourceSha256':{p.relative_to(ROOT).as_posix():sha(p.read_bytes()) for p in [ROOT/'build_local.py',ROOT/'PatchRev247.java',*src]},'patchGuards':guards.strip().splitlines()}
  new[PKG+'rev247-build.json']=(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n').encode()
  expected={UI,HELPER,'fabric.mod.json',*(PKG+x+'.class' for x in ['BuildInfo','LegacyFileLogger','network/FmlConnectionTrace','behavior/LegacyMotionTraceLog','convert/LegacyConversionManager','convert/ConversionStateStore$Snapshot','desktop/ConversionOutcomeReport'])}
  changed=sorted(n for n in old if new[n]!=old[n]);added=sorted(set(new)-set(old))
  if set(changed)!=expected:raise AssertionError('unexpected changed entries '+repr(set(changed)^expected))
  for n in ['behavior/Rev243Diagnostics.class','behavior/Rev245VelocityCorrelation.class','behavior/Rev245VelocityCorrelation$Flow.class','behavior/Rev245VelocityCorrelation$Vec.class','behavior/LegacyClientJumpMotion.class','behavior/Rev241JumpMotionBridge.class','behavior/Rev242LandingBridge.class','behavior/Rev239VanillaLiquidBridge.class','behavior/LegacyBehaviorRuntime.class','behavior/LegacyBehaviorRuntime$Snapshot.class','desktop/DesktopConversionSession.class']:
   assert new[PKG+n]==old[PKG+n],n
  for n in ['legacyforgebridge.client.mixins.json','legacyforgebridge.diagnostic.mixins.json','META-INF/jars/energy-4.2.0.jar']:
   assert new[n]==old[n]
  jar=pack(new);assert unzip(jar)==new
  for n,b in compiled.items():assert b[:4]==b'\xca\xfe\xba\xbe' and int.from_bytes(b[6:8],'big')==65,n
  output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(jar)
  for n in compiled:run(['javap','-p','-cp',output,n[:-6].replace('/','.')])
  evidence={**provenance,'artifact':output.name,'bytes':len(jar),'sha256':sha(jar),'baselineEntries':len(old),'outputEntries':len(new),
    'changedEntries':changed,'addedEntries':added,'removedEntries':[],'untouchedEntriesContentIdentical':len(old)-len(changed),
    'helperExistingChangedEntries':[n for n in oldhelper if oldhelper[n]!=helper[n]],'helperAddedEntries':sorted(set(helper)-set(oldhelper)),
    'noTestDoublesPackaged':True,'java21ClassFilesParsed':len(compiled),'jdk':subprocess.run(['java','-version'],capture_output=True,text=True).stderr.strip(),
    'verification':'Compilation, ZIP and preservation complete. See separate test-results.json for executed UI/cache/replay tests.'}
  output.with_suffix('.evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
  output.with_suffix('.sha256').write_text(sha(jar)+'  '+output.name+'\n')
  print(json.dumps({k:evidence[k] for k in ['artifact','bytes','sha256','changedEntries','addedEntries']},indent=2))
if __name__=='__main__':main()
