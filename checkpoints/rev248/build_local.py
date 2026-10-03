#!/usr/bin/env python3
"""Offline full main build: build_local.py EXACT_REV247.jar NEW_OUTPUT.jar (JDK21 required)."""
from pathlib import Path
import hashlib,io,json,subprocess,sys,tempfile,zipfile
ROOT=Path(__file__).resolve().parent
BASE_SHA='db7627f77ddac1d84f2eff300f6397c9304971b56f0578d507d4a573944ae88a'
VERSION='0.2.0-alpha.27-corpus4-local.31-rev248-startup.1'
REVISION='2026-10-03.245-deep-motion-correlation-diagnostics'
P='dev/yinghuang/legacyforgebridge/'
HELPER='META-INF/lfb/desktop-helper.jar'
EXPORTS=['--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports','java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
def sha(b):return hashlib.sha256(b).hexdigest()
def run(args):
 p=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=45)
 if p.returncode:raise RuntimeError(p.stdout+p.stderr)
 return p.stdout+p.stderr

def unpack(data):
 with zipfile.ZipFile(io.BytesIO(data)) as z:
  if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise ValueError('Invalid ZIP')
  return {n:z.read(n) for n in z.namelist()}
def pack(entries):
 b=io.BytesIO()
 with zipfile.ZipFile(b,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for n,v in sorted(entries.items()):
   info=zipfile.ZipInfo(n,(2026,10,3,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(0o40755 if n.endswith('/') else 0o100644)<<16
   z.writestr(info,v,compresslevel=9)
 return b.getvalue()
def main():
 if len(sys.argv)!=3:raise SystemExit(__doc__)
 base,out=[Path(a).resolve() for a in sys.argv[1:]]
 if base==out or out.exists():raise ValueError('Refusing baseline/existing output overwrite')
 data=base.read_bytes()
 if sha(data)!=BASE_SHA:raise ValueError('Baseline SHA256 mismatch')
 old=unpack(data)
 with tempfile.TemporaryDirectory(prefix='lfb248-') as td:
  td=Path(td);classes=td/'classes';tools=td/'tools';sources=sorted((ROOT/'source').rglob('*.java'))
  compilelog=run(['javac','--release','21','-encoding','UTF-8','-cp',base,'-d',classes,*sources])
  run(['javac',*EXPORTS,'-d',tools,ROOT/'PatchRev248.java'])
  guards=run(['java',*EXPORTS,'-cp',tools,'PatchRev248',base,classes]);print(guards,end='')
  compiled={p.relative_to(classes).as_posix():p.read_bytes() for p in sorted(classes.rglob('*.class'))}
  if any(not n.startswith(P) for n in compiled):raise ValueError('Foreign compiled class')
  new={**old,**compiled};oldhelper=unpack(old[HELPER]);helper=dict(oldhelper)
  ui=P+'desktop/Rev247SupportWindow.class';helper[ui]=compiled[ui];new[HELPER]=pack(helper)
  mod=json.loads(old['fabric.mod.json']);assert mod['version']=='0.2.0-alpha.27-corpus4-local.30-rev247-ui-cache.1';mod['version']=VERSION
  new['fabric.mod.json']=(json.dumps(mod,indent=2,ensure_ascii=False)+'\n').encode()
  expected={HELPER,'fabric.mod.json',*(P+n+'.class' for n in ['BuildInfo','LegacyFileLogger','network/FmlConnectionTrace','behavior/LegacyMotionTraceLog','protocol/ViaFabricPlusEntrypoint','desktop/Rev247SupportWindow'])}
  changed=sorted(n for n in old if old[n]!=new[n]);assert set(changed)==expected,(changed,expected)
  provenance={'checkpoint':'rev248-startup-protocol-default','sourceParent':'445da5d86f5141ce39026718a4ab56709fd49b4f','version':VERSION,'converterRevision':REVISION,
    'baseSha256':BASE_SHA,'buildMethod':'offline javac --release 21 + guarded JDK ASM edits over exact cumulative rev247 main',
    'vfpSourceReference':'ViaVersion/ViaFabricPlus@2f00a92851bed1bdab05e2465cadd9b21e4e6b7f (4.4.15/API6/Minecraft1.21.11)',
    'selectionPolicy':'once on POST_FILES_LOAD; verified protocol 5; revertOnDisconnect=false; no repeated forcing; no per-server settings changes',
    'motionPolicy':'rev247 ground evidence and all original motion behavior preserved; one protocol-context record added per capture',
    'fullGradleLoomBuild':False,'networkDependencyDownloads':False,'githubActionsBuild':False,'liveMinecraftValidated':False,'realVfpBinaryValidated':False,
    'originalModsChanged':False,'serverChanged':False,'cacheIdentityChanged':False,'noTestDoublesPackaged':True,
    'sourceSha256':{p.relative_to(ROOT).as_posix():sha(p.read_bytes()) for p in [ROOT/'build_local.py',ROOT/'PatchRev248.java',*sources]},'guards':guards.splitlines()}
  new[P+'rev248-build.json']=(json.dumps(provenance,indent=2,ensure_ascii=False)+'\n').encode()
  jar=pack(new);assert unpack(jar)==new
  for n,b in compiled.items():assert b[:4]==b'\xca\xfe\xba\xbe' and int.from_bytes(b[6:8],'big')==65
  out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(jar)
  for n in compiled:run(['javap','-p','-cp',out,n[:-6].replace('/','.')])
  evidence={**provenance,'artifact':out.name,'bytes':len(jar),'sha256':sha(jar),'changedEntries':changed,'addedEntries':sorted(set(new)-set(old)),
    'removedEntries':[],'untouchedOuterEntries':len(old)-len(changed),'helperOnlyChange':'Rev247SupportWindow artifact-version literal',
    'zipIntegrity':'pass','java21ClassFilesParsed':len(compiled),'compileLog':compilelog,'jdk':run(['java','-version']).strip(),
    'testEvidence':'Separate test-results.json contains actually executed tests and their limitations.'}
  out.with_suffix('.evidence.json').write_text(json.dumps(evidence,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
  out.with_suffix('.sha256').write_text(sha(jar)+'  '+out.name+'\n')
  print(json.dumps({k:evidence[k] for k in ['artifact','bytes','sha256','changedEntries','addedEntries','untouchedOuterEntries']},indent=2))
if __name__=='__main__':main()
