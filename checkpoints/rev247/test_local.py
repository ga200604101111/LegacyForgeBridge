#!/usr/bin/env python3
"""Reproducible packaged tests: python test_local.py REV247.jar REPORT_DIR [USER_LOGS.zip]. No game/server launch."""
from pathlib import Path
import hashlib,importlib.util,io,json,os,re,subprocess,sys,tempfile,zipfile
ROOT=Path(__file__).resolve().parent
CASES='inactive-start local-start nonlegacy-start offthread-start identity-start stop-start restart-counts active-start-idempotent missing-camera-move missing-tickstart pending-move-restart pending-move-same-capture failure-restart source-label source-semantics untraced-source readonly-observers local-commands reconnect registration-once'.split()
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
 if len(sys.argv) not in [3,4]:raise SystemExit(__doc__)
 jar=Path(sys.argv[1]).resolve();report=Path(sys.argv[2]).resolve();report.mkdir(parents=True,exist_ok=False)
 results={'artifact':jar.name,'sha256':sha(jar),'testMethod':'java -Xverify:all; real packaged DesktopHelper in isolated temporary session; JDK Swing/Xvfb; explicit doubles only for Minecraft/Fabric/source event/writer regression suite','liveMinecraft':False,'liveServer':False,'tests':[]}
 def run(name,args,expect=None):
  p=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=60)
  (report/(name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
  if p.returncode or (expect and expect not in p.stdout):raise RuntimeError(name+'\n'+p.stdout+p.stderr)
  results['tests'].append({'name':name,'returncode':p.returncode,'result':p.stdout.strip()});print(name,p.stdout.strip()[-200:])
  return p.stdout
 with tempfile.TemporaryDirectory(prefix='lfb-rev247-tests-') as tmp:
  w=Path(tmp);tc=w/'classes';doubles=w/'doubles';helper=w/'desktop-helper.jar'
  with zipfile.ZipFile(jar) as z:
   assert z.testzip() is None;helper.write_bytes(z.read('META-INF/lfb/desktop-helper.jar'))
  run('compile-tests',['javac','--release','21','-encoding','UTF-8','-cp',jar,'-d',tc,*sorted((ROOT/'tests').glob('*.java'))])
  run('cache',['java','-Xverify:all','-cp',os.pathsep.join(map(str,[tc,jar])),'dev.yinghuang.legacyforgebridge.convert.CacheTest'],'PASS cache checks=25')
  for label,cp,scale in [('ui-outer',jar,1),('ui-helper',helper,1),('ui-helper-scale2',helper,2)]:
   run(label,['xvfb-run','-a','-s','-screen 0 2400x1800x24','java','-Dsun.java2d.uiScale='+str(scale),'-Xverify:all','-cp',os.pathsep.join(map(str,[tc,cp])),'dev.yinghuang.legacyforgebridge.desktop.UiIntegrationTest','candidate',report/label],'PASS actual-helper-ui checks=145')
  stub=ROOT/'tests/diagnostic-doubles';spec=importlib.util.spec_from_file_location('explicit_doubles',stub/'write_stubs.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
  paths=m.write(w/'stubsrc')
  run('compile-diagnostic-regressions',['javac','--release','21','-encoding','UTF-8','-cp',jar,'-d',doubles,*paths,*sorted(stub.glob('*.java'))])
  total=0
  for case in CASES:
   text=run('diagnostic-'+case,['java','-Xverify:all','-cp',os.pathsep.join(map(str,[doubles,jar])),'dev.yinghuang.legacyforgebridge.behavior.CaptureScopeTest',case],'PASS '+case)
   total+=int(re.search(r'checks=(\d+)',text).group(1))
  assert total==643;results['packagedDiagnosticAssertions']=total
  if len(sys.argv)==4:
   archive=Path(sys.argv[3]).resolve();lines=[]
   with zipfile.ZipFile(archive) as z:
    # This preserved capture's first two parts are CRC-valid; do not use the malformed third part.
    for name in sorted(z.namelist()):
     if '2026-10-03' not in name or not name.endswith(('part001.log','part002.log')):continue
     for line in z.read(name).decode('utf-8').splitlines():
      m=re.search(r'\bns=(\d+).*?\blocalJump=(\d+).*?\bstage=(\S+) (.*)',line)
      if m and m.group(3) in ['CAPTURE_START','GROUND_CONTACT','JUMP_BEFORE_SOURCE','CLIENT_APPLY_HEAD','CLIENT_APPLY_TAIL','UPWARD_REWRITE','NETTY_PACKET_DISPATCH','RAW_1710_MOTION','VIA_OUTPUT_MOTION']:lines.append('\t'.join(m.groups()))
   path=w/'existing-user-capture.tsv';path.write_text('\n'.join(lines)+'\n')
   results['replayedUserArchiveSha256']=sha(archive)
   run('ground-evidence-replay',['java','-Xverify:all','-cp',os.pathsep.join(map(str,[tc,jar])),'dev.yinghuang.legacyforgebridge.behavior.GroundReplayTest',path,report/'ground-replay-details.txt'],'PASS ground-evidence checks=11 replayPackets=89 repeated=9 postGround=5')
  results['testedSourceSha256']={p.relative_to(ROOT).as_posix():sha(p) for p in sorted(ROOT.rglob('*')) if p.is_file() and p.suffix in ['.py','.java']}
  (report/'test-results.json').write_text(json.dumps(results,indent=2,ensure_ascii=False)+'\n')
  print('PASS all packaged tests')
if __name__=='__main__':main()
