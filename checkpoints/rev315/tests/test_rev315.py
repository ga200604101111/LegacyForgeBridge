#!/usr/bin/env python3
"""Offline regression for opcode 192/193 scanner bug; uses only a JDK 21 and two LFB converter JARs."""
import argparse
import json
import pathlib
import subprocess
import tempfile

PREFIX = 'dev/yinghuang/legacyforgebridge/generated/fixture'

def run(cmd):
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout

def write_java(root, classname, body):
    target=root/(PREFIX+'/'+classname+'.java')
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text('package dev.yinghuang.legacyforgebridge.generated.fixture;\n'
                      'public class '+classname+' {\n'+body+'\n}\n',encoding='utf-8')
    return target

def launch(converter, path, helper):
    return run(['java','-Xverify:all','-cp',str(converter)+__import__('os').pathsep+str(helper),
                'AuditProbe',str(path)]).strip()

def main(args):
    base=pathlib.Path(args.base_converter).resolve()
    fixed=pathlib.Path(args.fixed_converter).resolve()
    results={}
    with tempfile.TemporaryDirectory(prefix='lfb-rev315-test-') as td:
        p=pathlib.Path(td)
        lit=','.join('"CHECK_%03d"'%i for i in range(113))
        sources=[]
        sources.append(write_java(p/'src','Cast235',
            f'public Object many(){{return new String[]{{{lit}}};}}\n'
            'public Object target(Object o) {return (java.util.Map)o;}'))
        sources.append(write_java(p/'src','Instanceof235',
            f'public Object many(){{return new String[]{{{lit}}};}}\n'
            'public boolean target(Object o) {return o instanceof java.util.Map;}'))
        sources.append(write_java(p/'src','MonitorSwitch',
            'public Object monitor(Object obj) { synchronized(obj) { return obj; } }\n'
            'public int dense(int x) { switch(x) { case 1: return 2; case 2: return 3; default:return 4;} }\n'
            'public int sparse(int x) { switch(x) { case 1: return 2; case 9999: return 3; default:return 4;} }'))
        sources.append(write_java(p/'src','BadForgeReference',
            'public void run() { net.minecraftforge.example.BadHook.go(); }'))
        stub=p/'src/net/minecraftforge/example/BadHook.java'
        stub.parent.mkdir(parents=True,exist_ok=True)
        stub.write_text('package net.minecraftforge.example; public class BadHook {public static void go() {} }')
        sources.append(stub)
        sources.append(write_java(p/'src','BadOpcode',
            'public String cast(Object x) {return (String)x;}'))
        run(['javac','-d',str(p/'classes'),*[str(s) for s in sources]])
        helper=p/'src/AuditProbe.java'
        helper.write_text('''import java.nio.file.Path;
import dev.yinghuang.legacyforgebridge.convert.LegacyGeneratedBytecodeLinkageAnalyzer;
public class AuditProbe {
  public static void main(String[] a) throws Exception {
    var v = new LegacyGeneratedBytecodeLinkageAnalyzer().analyze(Path.of(a[0]));
    System.out.println("complete="+v.complete()+";findings="+v.findings()+";diagnostics="+v.diagnostics());
  }
}
''')
        run(['javac','-cp',str(base),'-d',str(p/'classes'),str(helper)])
        for name in ['Cast235','Instanceof235','MonitorSwitch','BadForgeReference','BadOpcode']:
            cl=(p/'classes'/(PREFIX+'/'+name+'.class')).read_bytes()
            if name=='BadOpcode':
                hit=cl.index(bytes([0x2b,0xc0,0,7,0xb0]))+1
                cl=cl[:hit]+bytes([235])+cl[hit+1:]
            loc=p/name/PREFIX
            loc.mkdir(parents=True)
            (loc/(name+'.class')).write_bytes(cl)
        for name in ['Cast235','Instanceof235']:
            bytecode=run(['javap','-c','-cp',str(p/'classes'),
                          'dev.yinghuang.legacyforgebridge.generated.fixture.'+name])
            opcode='checkcast' if name=='Cast235' else 'instanceof'
            assert f'{opcode}     #235' in bytecode or f'{opcode}    #235' in bytecode,bytecode
            old=launch(base,p/name,p/'classes')
            new=launch(fixed,p/name,p/'classes')
            assert 'complete=false' in old and 'Invalid/unsupported JVM instruction 235' in old,old
            assert 'complete=true;findings=[];diagnostics=[]' in new,new
            results[name]={'baselineRejected':True,'fixedAccepted':True,'constantPoolIndex':235}
        for name in ['MonitorSwitch','BadForgeReference','BadOpcode']:
            old=launch(base,p/name,p/'classes')
            new=launch(fixed,p/name,p/'classes')
            if name=='MonitorSwitch':
                assert old==new and 'complete=true;findings=[];diagnostics=[]' in new
            elif name=='BadForgeReference':
                assert old==new and 'net/minecraftforge/example/BadHook' in new
            else:
                assert old==new and 'complete=false' in new and 'Invalid/unsupported JVM instruction 235' in new
            results[name]={'unchangedExpectedOutcome':True}
    print(json.dumps({'result':'PASS','cases':results},indent=2))

if __name__=='__main__':
    ap=argparse.ArgumentParser()
    ap.add_argument('--base-converter',required=True)
    ap.add_argument('--fixed-converter',required=True)
    main(ap.parse_args())
