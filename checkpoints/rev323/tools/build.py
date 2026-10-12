
from pathlib import Path
import subprocess,sys,zipfile,os
w=Path(__file__).resolve().parent;g=w.parents[2];os.chdir(w)
p=w/'src/FeaturePass323.java';s=p.read_text('utf-8').replace('f.owner+"."+f.name());if(icon','f.owner+"."+f.name);if(icon');p.write_text(s,encoding='utf-8')
cp='classes;../../../mods/LegacyForgeBridge-rev321-optimizer-core.jar;../../../lfb/LFB-Converter.jar;../core-repair-20261012/deps322/*;../../../.fabric/remappedJars/minecraft-1.21.11-0.19.3/client-intermediary.jar'
(w/'cp.txt').write_text(cp)
jdk=Path(r'D:\Program Files\Eclipse Adoptium\jdk-21.0.10\bin')
args=['-encoding','UTF-8','--release','21','-proc:none','-cp',cp,'-d','classes']+[str(p.relative_to(w)) for p in (w/'src').glob('*.java')]
r=subprocess.run([str(jdk/'javac.exe'),*args],capture_output=True)
text=r.stdout.decode('utf-8','replace')+r.stderr.decode('utf-8','replace');(w/'compile.log').write_text(text,'utf-8');print(text);print('COMPILE',r.returncode,flush=True)
if r.returncode:sys.exit(r.returncode)
# Test the exact patched analyzer without changing the installed core.
name='dev/yinghuang/legacyforgebridge/convert/LegacyIconTableAnalyzer.class'
with zipfile.ZipFile(w/'before/mods/LegacyForgeBridge-rev321-optimizer-core.jar') as z:(w/'analyzer-before.class').write_bytes(z.read(name))
r=subprocess.run([str(jdk/'java.exe'),'-cp',cp,'dev.yinghuang.legacyforgebridge.repair322.Patch323','source','analyzer-before.class','classes/'+name],capture_output=True)
print(r.stdout.decode('utf-8','replace')+r.stderr.decode('utf-8','replace'));assert r.returncode==0
if '--test' in sys.argv:
 stage=w/'stage';stage.mkdir(exist_ok=True)
 with zipfile.ZipFile(g/'mods/twilightforest-1.7.10-2.3.8-tw-lfb.jar') as z:z.extractall(stage)
 args=['-Dfile.encoding=UTF-8','-Xmx2G','-cp',cp,'dev.yinghuang.legacyforgebridge.repair322.FeaturePass323','../../../old-mods/twilightforest-1.7.10-2.3.8-tw.jar','stage']
 with open(w/'test-pass.log','w',encoding='utf-8') as f:r=subprocess.run([str(jdk/'java.exe'),*args],stdout=f,stderr=subprocess.STDOUT)
 print((w/'test-pass.log').read_text('utf-8',errors='replace')[-18000:]);print('PASS_EXIT',r.returncode)
 sys.exit(r.returncode)
