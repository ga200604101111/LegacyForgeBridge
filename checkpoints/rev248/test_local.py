#!/usr/bin/env python3
"""Packaged regression suite. Needs local JDK21 and Xvfb, not Minecraft or a network connection."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,re,shutil,subprocess,tempfile,zipfile
ROOT=Path(__file__).resolve().parent
STARTUP='saved-other save-disabled already-selected manual-after disconnect duplicate-cycle callback-object off-thread queued-active null-client setter-error readback-error backend-mismatch world-active player-active network-active server-active vfp-active connecting non-connecting-screen unsupported-api register-error'.split()
DIAGNOSTIC='inactive-start local-start nonlegacy-start offthread-start identity-start stop-start restart-counts active-start-idempotent missing-camera-move missing-tickstart pending-move-restart pending-move-same-capture failure-restart source-label source-semantics untraced-source readonly-observers local-commands reconnect registration-once'.split()
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def load(path,name):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

def main():
 ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('jar',type=Path);ap.add_argument('report',type=Path);ap.add_argument('--base',type=Path);ap.add_argument('--rev247',type=Path,default=ROOT.parent/'rev247');a=ap.parse_args()
 jar=a.jar.resolve();report=a.report.resolve();prior=a.rev247.resolve();report.mkdir(parents=True,exist_ok=False)
 rows=[]
 def run(name,args,expect=None):
  p=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=40)
  (report/(name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
  if p.returncode or (expect and expect not in p.stdout):raise RuntimeError(name+'\n'+p.stdout+p.stderr)
  rows.append({'name':name,'returncode':0,'stdout':p.stdout.strip(),'stderr':p.stderr.strip()});print(name,p.stdout.strip()[-100:]);return p.stdout
 with zipfile.ZipFile(jar) as z:
  if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise ValueError('Invalid JAR')
 with tempfile.TemporaryDirectory(prefix='lfb248-tests-') as td:
  w=Path(td);st=w/'startup';ds=w/'diagnostics';ui=w/'ui';helper=w/'helper.jar'
  sources=load(ROOT/'tests/write_startup_doubles.py','startup_doubles').write(w/'startup-src')
  run('compile-startup',['javac','--release','21','-encoding','UTF-8','-cp',jar,'-d',st,*sources,ROOT/'tests/StartupTest.java'])
  if a.base:
   if sha(a.base)!='db7627f77ddac1d84f2eff300f6397c9304971b56f0578d507d4a573944ae88a':raise ValueError('Wrong regression baseline')
   for case in ['baseline-settings','baseline-disconnect']:
    run(case,['java','-Xverify:all','-cp',os.pathsep.join(map(str,[st,a.base.resolve()])),'dev.yinghuang.legacyforgebridge.protocol.StartupTest',case],'REPRODUCED '+case)
  for case in STARTUP:run(case,['java','-Xverify:all','-cp',os.pathsep.join(map(str,[st,jar])),'dev.yinghuang.legacyforgebridge.protocol.StartupTest',case],'PASS '+case)
  shutil.copytree(prior/'tests',w/'prior-tests')
  cache=w/'prior-tests/CacheTest.java';text=cache.read_text();assert text.count('VERSION.contains("rev247")')==1
  cache.write_text(text.replace('VERSION.contains("rev247")','VERSION.contains("rev248")'),encoding='utf-8')
  run('compile-cache-ui',['javac','--release','21','-encoding','UTF-8','-cp',jar,'-d',ui,cache,w/'prior-tests/UiIntegrationTest.java'])
  run('cache',['java','-Xverify:all','-cp',os.pathsep.join(map(str,[ui,jar])),'dev.yinghuang.legacyforgebridge.convert.CacheTest'],'checks=25')
  with zipfile.ZipFile(jar) as z:helper.write_bytes(z.read('META-INF/lfb/desktop-helper.jar'))
  for label,cp,scale in [('ui-main',jar,1),('ui-helper',helper,1),('ui-helper-scale2',helper,2)]:
   run(label,['xvfb-run','-a','-s','-screen 0 2400x1800x24','java','-Dsun.java2d.uiScale='+str(scale),'-Xverify:all','-cp',os.pathsep.join(map(str,[ui,cp])),'dev.yinghuang.legacyforgebridge.desktop.UiIntegrationTest','candidate',report/label],'checks=145')
  stub=w/'prior-tests/diagnostic-doubles';paths=load(stub/'write_stubs.py','diagnostic_doubles').write(w/'diagnostic-src')
  run('compile-diagnostics',['javac','--release','21','-encoding','UTF-8','-cp',jar,'-d',ds,*paths,*sorted(stub.glob('*.java')),ROOT/'tests/CaptureContextTest.java'])
  for case in DIAGNOSTIC:run('diagnostic-'+case,['java','-Xverify:all','-cp',os.pathsep.join(map(str,[ds,jar])),'dev.yinghuang.legacyforgebridge.behavior.CaptureScopeTest',case],'PASS '+case)
  run('capture-context',['java','-Xverify:all','-cp',os.pathsep.join(map(str,[ds,jar])),'dev.yinghuang.legacyforgebridge.behavior.CaptureContextTest'],'checks=4')
  def checks(names):return sum(int(re.search(r'checks=(\d+)',r['stdout']).group(1)) for r in rows if r['name'] in names)
  result={'artifact':jar.name,'sha256':sha(jar),'startupCases':len(STARTUP),'startupAssertions':checks(STARTUP),'baselineReproductions':2 if a.base else 0,
   'diagnosticCases':20,'diagnosticAssertions':checks(['diagnostic-'+x for x in DIAGNOSTIC]),'cacheAssertions':25,'uiAssertionsPerRun':145,'uiRuns':3,'captureContextAssertions':4,
   'tests':rows,'minecraftAndVfpAreTestDoubles':True,'desktopHelperIsActualPackagedSwing':True,
   'liveMinecraft':False,'liveVfpPipeline':False,'serverTested':False,'jumpRepairClaimed':False,
   'priorTestSourceSha256':{p.relative_to(prior).as_posix():sha(p) for p in sorted((prior/'tests').rglob('*')) if p.is_file() and p.suffix in ['.py','.java']},
   'testSourceSha256':{p.relative_to(ROOT).as_posix():sha(p) for p in sorted(ROOT.rglob('*')) if p.is_file() and p.suffix in ['.py','.java']}}
  (report/'test-results.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
  print(json.dumps({k:v for k,v in result.items() if k not in ['tests','priorTestSourceSha256','testSourceSha256']},indent=2))
if __name__=='__main__':main()
