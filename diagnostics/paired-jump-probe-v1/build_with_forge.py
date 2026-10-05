"""Build against actual Forge 1.7.10, LaunchWrapper and ASM 5 libraries supplied locally.
Example (Windows): python build_with_forge.py --classpath "forge.jar;launchwrapper.jar;asm-all-5.0.3.jar"
No downloads, GitHub API calls, Actions dispatches, or source modifications.
"""
from __future__ import annotations
import argparse, hashlib, json, subprocess, tempfile, zipfile
from pathlib import Path

def main():
 p=argparse.ArgumentParser();p.add_argument('--classpath',required=True);p.add_argument('--output',type=Path,default=Path('lfb-paired-jump-probe-1.7.10-rebuilt.jar'));a=p.parse_args()
 root=Path(__file__).resolve().parent
 with tempfile.TemporaryDirectory(prefix='lfb-probe-build-') as work:
  classes=Path(work)/'classes';classes.mkdir()
  subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-classpath',a.classpath,'-d',str(classes),*[str(x) for x in sorted((root/'src').rglob('*.java'))]],check=True)
  manifest='Manifest-Version: 1.0\r\nFMLCorePlugin: dev.yinghuang.lfb.jumpprobe.JumpProbePlugin\r\nImplementation-Title: LFB Paired Jump Probe\r\nImplementation-Version: 1.0.0\r\n\r\n'
  with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
   z.writestr('META-INF/MANIFEST.MF',manifest)
   z.writestr('META-INF/lfb-jump-probe-build.json',json.dumps({'version':'1.0.0','observationOnly':True,'builtAgainst':'caller supplied actual libraries','liveMinecraftTested':False}))
   for f in sorted(classes.rglob('*.class')):z.write(f,f.relative_to(classes).as_posix())
  with zipfile.ZipFile(a.output) as z:assert z.testzip() is None
 print(a.output,hashlib.sha256(a.output.read_bytes()).hexdigest())
if __name__=='__main__':main()
