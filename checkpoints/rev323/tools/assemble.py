
from pathlib import Path
import zipfile,json,subprocess,os,hashlib
w=Path(__file__).resolve().parent;g=w.parents[2];os.chdir(w);out=w/'candidate';out.mkdir(exist_ok=True)
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest();cp=(w/'cp.txt').read_text();jdk=Path(r'D:\Program Files\Eclipse Adoptium\jdk-21.0.10\bin');prefix='dev/yinghuang/legacyforgebridge/'
base={'runtime':w/'before/mods/LegacyForgeBridge-rev321-optimizer-core.jar','converter':w/'before/lfb/LFB-Converter.jar'}
expected={'runtime':'372140809a428c860df9c7769fe498821dc6891d9bba96a0c0d0551ae8ddc035','converter':'e0042bea86f95b9975c51e33d74cbb5e6eb696d26b8f8c050fcf0120ee72ac92'}
for kind,path in base.items():
 assert sha(path)==expected[kind], 'Baseline changed'
 hooks={'source':prefix+'convert/LegacyIconTableAnalyzer.class','converter':prefix+'convert/LegacyConversionEngine.class'}
 if kind=='runtime':hooks.update({'support':prefix+'convert/runtime/GeneratedModSupport.class','block':prefix+'convert/runtime/ConvertedLegacyBlock.class','properties':prefix+'ContractRuntime320.class'})
 with zipfile.ZipFile(path) as z: files={n:z.read(n) for n in z.namelist()}
 for mode,name in hooks.items():
  src=w/f'{kind}.{mode}.before.class';dst=w/f'{kind}.{mode}.after.class';src.write_bytes(files[name]);r=subprocess.run([str(jdk/'java.exe'),'-cp',cp,'dev.yinghuang.legacyforgebridge.repair322.Patch323',mode,src.name,dst.name],capture_output=True);print(r.stdout.decode('utf-8','replace')+r.stderr.decode('utf-8','replace'));assert r.returncode==0;files[name]=dst.read_bytes()
 for p in (w/'classes/dev/yinghuang/legacyforgebridge/repair322').rglob('*.class'):
  # Build tools and source probes are harmless but not runtime entrypoints.
  files[p.relative_to(w/'classes').as_posix()]=p.read_bytes()
 for p in (w/'classes/legacyforgebridge/platform1710').glob('*.png'):files[p.relative_to(w/'classes').as_posix()]=p.read_bytes()
 if kind=='runtime':
  meta=json.loads(files['fabric.mod.json']);meta['version']='0.2.0-alpha.27-rev323-generic-contracts.1';meta.setdefault('entrypoints',{}).setdefault('client',[]).append('dev.yinghuang.legacyforgebridge.repair322.FeatureClient323');meta.setdefault('mixins',[]).append({'config':'legacyforgebridge.generic323.mixins.json','environment':'client'});files['fabric.mod.json']=json.dumps(meta,ensure_ascii=False,indent=2).encode('utf-8');files['legacyforgebridge.generic323.mixins.json']=json.dumps({'required':True,'package':'dev.yinghuang.legacyforgebridge.repair322.mixin','compatibilityLevel':'JAVA_21','client':['DependencyUpdateMixin323'],'injectors':{'defaultRequire':1}}).encode('utf-8')
 target=out/path.name
 with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for n,data in sorted(files.items()):z.writestr(zipfile.ZipInfo(n,(1980,1,1,0,0,0)),data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
 with zipfile.ZipFile(target) as z:assert z.testzip() is None
 print('CANDIDATE',kind,sha(target))
# Source-generated converted candidate, not an external resource pack.
stage=w/'stage';manifest=json.loads((stage/'legacyforgebridge/generic-contracts323.json').read_text('utf-8'))
assert len(manifest['equipment'])==28
assert sum(len(v) for v in manifest['eggs'].values())==53
for id in ['twilightforest:tile.tffireflyjar','twilightforest:tile.tftrophypedestal','twilightforest:tile.tfthorns','twilightforest:tile.hugelilypad']:assert id in manifest['meshes'],id
assert manifest['tabs']['twilightforest:twilightforest']=='twilightforest:tile.tffireflyjar'
assert manifest['shapes']['twilightforest:tile.tfmoonworm']['states']['1']['outline']==[0,0.25,0.25,0.25,0.75,0.75]
assert len(manifest['held'])>=4
p=out/'twilightforest-1.7.10-2.3.8-tw-lfb.jar'
with zipfile.ZipFile(p,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for file in sorted(stage.rglob('*')):
  if file.is_file():z.writestr(zipfile.ZipInfo(file.relative_to(stage).as_posix(),(1980,1,1,0,0,0)),file.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
proof={'version':'rev323-generic-contracts.1','artifacts':{p.name:sha(p) for p in out.glob('*.jar')},'baseline':expected,'source_sha256':manifest['sourceSha256'],'equipment':len(manifest['equipment']),'customBiped':sum('biped' in v for v in manifest['equipment'].values()),'eggs':sum(len(v) for v in manifest['eggs'].values()),'nativeRuntimeTested':False,'liveServerPlacementTested':False}
(out/'proof.json').write_text(json.dumps(proof,indent=2),encoding='utf-8');print(json.dumps(proof,indent=2),flush=True)
