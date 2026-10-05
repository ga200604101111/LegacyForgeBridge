"""Offline JDK21 overlay. New code compiles against optional actual intermediary libraries or explicit ABI declarations. Nothing is downloaded or published."""
from pathlib import Path
import argparse, subprocess, zipfile, hashlib, json, os, shutil, sys
R=Path(__file__).resolve().parent
BASE='11928e03f5e7c5801ebd311a49f887931ffb0a2bc4195f228f06c2f1bcfbbd28'
BAMBOO='bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402'
VERSION='0.2.0-alpha.27-corpus4-local.39-rev256-sapling-proof-repair.1'
TARGET='dev/yinghuang/legacyforgebridge/convert/pass/LegacyClientOnlySourceStripPass.class'
def sha(b):return hashlib.sha256(b).hexdigest()
def run(*args):subprocess.run([str(x) for x in args],check=True)
def js(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
def main():
 p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--bamboo',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--classpath');a=p.parse_args()
 if sha(a.base.read_bytes())!=BASE or sha(a.bamboo.read_bytes())!=BAMBOO:raise ValueError('Unexpected input hash')
 for d in ['classes','patchclasses']:shutil.rmtree(R/d,ignore_errors=True);(R/d).mkdir()
 if a.classpath:cp=a.classpath+os.pathsep+str(a.base)
 else:
  run(sys.executable,R/'prepare_stubs.py');shutil.rmtree(R/'stubclasses',ignore_errors=True);(R/'stubclasses').mkdir()
  run('javac','--release','21','-encoding','UTF-8','-d',R/'stubclasses',*sorted((R/'stubs').rglob('*.java')))
  cp=str(R/'stubclasses')+os.pathsep+str(a.base)
 run('javac','--release','21','-proc:none','-encoding','UTF-8','-cp',cp,'-d',R/'classes',*sorted((R/'src').rglob('*.java')))
 run('java','-Xverify:all','-cp',R/'classes','dev.yinghuang.legacyforgebridge.rev256.SaplingProof',a.bamboo,'ruby/bamboo/block/BlockSakura','bamboomod:sakura',BAMBOO,R/'bamboo-proof.properties')
 with zipfile.ZipFile(a.base) as z:old={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 data=old.copy()
 exports=[]
 for s in ['jdk.internal.org.objectweb.asm','jdk.internal.org.objectweb.asm.tree','jdk.internal.org.objectweb.asm.tree.analysis']:exports+=['--add-exports','java.base/'+s+'=ALL-UNNAMED']
 run('javac',*exports,'-d',R/'patchclasses',R/'Patch256.java')
 (R/'strip-in.class').write_bytes(data[TARGET]);run('java',*exports,'-cp',R/'patchclasses','Patch256',R/'strip-in.class',R/'strip-out.class');data[TARGET]=(R/'strip-out.class').read_bytes()
 for n in list(data):
  if n.startswith('dev/yinghuang/legacyforgebridge/rev254/LegacySaplingSupport$'):del data[n]
 for f in sorted((R/'classes').rglob('*.class')):
  key=f.relative_to(R/'classes').as_posix();assert key.startswith('dev/yinghuang/legacyforgebridge/');data[key]=f.read_bytes()
 data['legacyforgebridge/sapling-evidence/'+BAMBOO+'.properties']=(R/'bamboo-proof.properties').read_bytes()
 # Replace the previous ordinary-mod override by an activated built-in pack.
 prior='assets/bamboomod/blockstates/sakura.json';data.pop(prior,None)
 pack='resourcepacks/verified_sakura/'
 data[pack+'pack.mcmeta']=js({'pack':{'description':'LFB verified Bamboo Sakura sapling repair','min_format':[75,0],'max_format':[75,0]}})
 with zipfile.ZipFile(a.bamboo) as z:png=z.read('assets/bamboo/textures/blocks/sakura.png')
 worldtex='legacyforgebridge:block/rev256/sakura';itemtex='legacyforgebridge:item/rev256/sakura'
 for kind in ['block','item']:data[pack+f'assets/legacyforgebridge/textures/{kind}/rev256/sakura.png']=png
 world={'parent':'minecraft:block/cross','textures':{'cross':worldtex,'particle':worldtex}}
 item={'parent':'minecraft:item/generated','textures':{'layer0':itemtex}}
 data[pack+'assets/legacyforgebridge/models/block/rev256/sakura.json']=js(world)
 data[pack+'assets/legacyforgebridge/models/item/rev256/sakura.json']=js(item)
 data[pack+'assets/bamboomod/models/block/sakura.json']=js(world)
 data[pack+'assets/bamboomod/models/item/sakura.json']=js(item)
 data[pack+prior]=js({'variants':{'legacy_meta='+str(m):{'model':'legacyforgebridge:block/rev256/sakura'} for m in range(16)}})
 definition={'model':{'type':'minecraft:model','model':'legacyforgebridge:item/rev256/sakura'}}
 data[pack+'assets/bamboomod/items/sakura.json']=js(definition)
 for m in range(16):
  for prefix in ['lfb_meta','lfb_geometry','lfb_saplings']:data[pack+f'assets/bamboomod/items/{prefix}/sakura/{m}.json']=js(definition)
  data[pack+f'assets/bamboomod/models/block/sakura_lfb_meta_{m}.json']=js(world)
  data[pack+f'assets/bamboomod/models/item/sakura_lfb_meta_{m}.json']=js(item)
 # Explicit distinct atlas entries; sprites never share an ID across atlases.
 for kind,atlas,sprite in [('block','blocks',worldtex),('item','items',itemtex)]:data[pack+f'assets/minecraft/atlases/{atlas}.json']=js({'sources':[{'type':'minecraft:single','resource':sprite}]})
 meta=json.loads(data['fabric.mod.json']);meta['version']=VERSION
 meta['entrypoints']['client'].append('dev.yinghuang.legacyforgebridge.rev256.SaplingClient');data['fabric.mod.json']=js(meta)
 prov={'version':VERSION,'baseSha256':BASE,'bambooSha256':BAMBOO,'newRuleSource':'compiled before source stripping; persisted properties','runtimeSourceClassesRequired':False,'compileClasspath':'actual libraries' if a.classpath else 'explicit compile-only ABI declarations (not packaged)','liveMinecraftTested':False,'actualSpongeTested':False,'gpuTested':False,'sourceFiles':{str(f.relative_to(R)):sha(f.read_bytes()) for f in sorted((R/'src').rglob('*.java'))}}
 prov['changed']=sorted(n for n in old if n in data and old[n]!=data[n]);prov['removed']=sorted(set(old)-set(data));prov['added']=sorted(set(data)-set(old));prov['unchangedCount']=sum(old[n]==data[n] for n in old if n in data)
 data['legacyforgebridge/rev256-build.json']=js(prov)
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for n,b in sorted(data.items()):
   info=zipfile.ZipInfo(n,(2026,10,6,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,b)
 with zipfile.ZipFile(a.output) as z:assert z.testzip() is None
 (R/'provenance.json').write_bytes(js(prov));print('BUILT',a.output,a.output.stat().st_size,sha(a.output.read_bytes()))
if __name__=='__main__':main()
