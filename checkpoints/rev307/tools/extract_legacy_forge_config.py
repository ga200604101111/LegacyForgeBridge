#!/usr/bin/env python3
"""Source-bytecode-derived Forge 1.7.10 Configuration profiles.

Does not execute mod code, does not modify originals, and does not use mod
names/IDs to select conversion behavior. Fails closed for non-literal options.
Profiles are bound to the complete input JAR SHA-256.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile

INSTRUCTION = re.compile(r'^\s*(\d+):\s+(\w+)(?:\s+(.*?))?\s*$')
CONST_STRING = re.compile(r'// (?:String|double|float|int) (.*)')
METHOD = re.compile(r'\s*(?:(?:public|private|protected|static|final|synchronized|native|abstract|strictfp)\s+)+[^=;]+\([^;]*\);$')
DESC = re.compile(r'Configuration\.(get|getBoolean|getInt|getFloat|getString):\(([^)]*)\)')
TYPE = {'Z':'B','I':'I','D':'D','F':'D','Ljava/lang/String;':'S'}

def parse_instruction(line):
    m = INSTRUCTION.match(line)
    if not m: return None
    _,op,arg=m.groups()
    arg=arg or ''
    return (op,arg)

def constant(inst):
    op,arg=inst
    if op.startswith('ldc'):
        m=CONST_STRING.search(arg)
        if m:
            s=m.group(1)
            if '// double ' in arg or '// float ' in arg:
                try: return float(s.rstrip('dDfF'))
                except ValueError: return None
            if '// int ' in arg:
                try: return int(s)
                except ValueError: return None
            return s
    if op.startswith('iconst_'):
        tail=op[7:]
        return -1 if tail == 'm1' else int(tail)
    if op in ('bipush','sipush'):
        try:return int(arg.split()[0])
        except ValueError:return None
    if op.startswith('dconst_'):return float(op[-1])
    if op.startswith('fconst_'):return float(op[-1])
    return None

def descriptor_argtypes(desc):
    res=[]
    j=0
    while j<len(desc):
        code=desc[j]
        if code in 'ZBCSIFJD':res.append(code);j+=1
        elif code=='L':
            k=desc.find(';',j)
            if k<0:return []
            res.append(desc[j:k+1]);j=k+1
        elif code=='[':
            k=j
            while k<len(desc) and desc[k]=='[':k+=1
            if k<len(desc) and desc[k]=='L':k=desc.index(';',k)+1
            else:k+=1
            res.append(desc[j:k]);j=k
        else:return []
    return res

def get_args(lines,idx,count):
    """Only accept straight-line adjacent literal args, not computed state."""
    args=[]
    i=idx-1
    while i>=0 and len(args)<count:
        inst=lines[i]
        if inst is None:
            i-=1;continue
        op,arg=inst
        value=constant(inst)
        if value is not None:
            args.append(value)
        elif op in ('aload','aload_0','aload_1','aload_2','aload_3','getstatic','checkcast','dup'):
            # receiver or other non-value, but a gap in args is *not* safe
            if args:return None
        else:
            return None
        i-=1
    if len(args)!=count:return None
    return args[::-1]

def parse_method(modclass, lines, modid):
    result=[]
    current_method=''
    filename=None
    scope='instance-config'
    for i,line in enumerate(lines):
        if METHOD.match(line):
            current_method=line.strip()
            filename=None
            scope='instance-config'
        inst=parse_instruction(line)
        if inst is None:continue
        op,arg=inst
        if 'Method net/minecraftforge/common/DimensionManager.getCurrentSaveRootDirectory:' in arg:
            scope='world-save'
        if op.startswith('ldc'):
            val=constant(inst)
            if isinstance(val,str) and val.lower().endswith('.cfg') and '/' not in val and '\\' not in val:
                filename=val
        if 'Method net/minecraftforge/common/config/Configuration.' not in arg:continue
        dm=DESC.search(arg)
        if not dm:continue
        name,desc=dm.groups()
        types=descriptor_argtypes(desc)
        if name=='get' and len(types)>=3 and types[0]=='Ljava/lang/String;' and types[1]=='Ljava/lang/String;' and types[2] in TYPE:
            # simple .get(category, key, default) form, not list or exotic overload
            use=3
            args=get_args([parse_instruction(q) for q in lines],i,use)
            if not args:continue
            cat,key,default=args
            typ=TYPE[types[2]]
            comment=''
            for nextline in lines[i+1:i+10]:
                if 'Method net/minecraftforge/common/config/Configuration.' in nextline:break
                if 'Field net/minecraftforge/common/config/Property.comment:' in nextline:
                    for cand in reversed(lines[i+1:lines.index(nextline,i+1)]):
                        v=constant(parse_instruction(cand) or ('',''))
                        if isinstance(v,str):comment=v;break
                    break
        elif name.startswith('get') and len(types)>=4 and types[0]=='Ljava/lang/String;' and types[1]=='Ljava/lang/String;' and types[2] in TYPE and types[3]=='Ljava/lang/String;':
            # property name, category, default, comment (e.g. getBoolean)
            args=get_args([parse_instruction(q) for q in lines],i,len(types))
            if not args:continue
            key,cat,default=args[:3]
            comment=args[-1]
            typ=TYPE[types[2]]
        else:continue
        if not isinstance(cat,str) or not isinstance(key,str) or not cat.strip() or not key.strip():continue
        if typ=='B' and default not in (0,1,False,True):continue
        if typ=='I' and not isinstance(default,int):continue
        if typ=='D' and not isinstance(default,(int,float)):continue
        if typ=='S' and not isinstance(default,str):continue
        if len(key)>128 or len(cat)>128:continue
        cfg=filename or modid+'.cfg'
        result.append(dict(file=cfg,category=cat,key=key,type=typ,default=str(bool(default)).lower() if typ=='B' else str(default),comment=comment if isinstance(comment,str) else '',scope=scope,className=modclass.replace('/','.'),method=current_method))
    return result

def extract(path):
    with open(path,'rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
    with zipfile.ZipFile(path) as jar:
        mcmod=json.loads(jar.read('mcmod.info').decode('utf-8-sig'))
        if isinstance(mcmod,dict):mcmod=mcmod.get('modList',[mcmod])
        mods=[m for m in mcmod if isinstance(m,dict) and m.get('modid')]
        if len(mods)!=1:raise ValueError('Profile association requires one unambiguous logical mod per JAR')
        mod=mods[0]
        classes=[n.removesuffix('.class') for n in jar.namelist() if n.endswith('.class')]
    raw=[]
    for cls in classes:
        # Only classes actually referencing Forge Configuration are candidates
        with zipfile.ZipFile(path) as jar:
            if b'net/minecraftforge/common/config/Configuration' not in jar.read(cls+'.class'):continue
        result=subprocess.run(['javap','-p','-c','-classpath',path,cls.replace('/','.')],capture_output=True,text=True)
        if result.returncode:continue
        raw+=parse_method(cls,result.stdout.splitlines(),mod['modid'])
    # first get() is the execution default; later duplicate property writes may only assign comments
    found={}
    for item in raw:
        k=(item['file'].lower(),item['category'].lower(),item['key'].lower())
        if k not in found: found[k]=item
        elif not found[k]['comment'] and item['comment']: found[k]['comment']=item['comment']
        elif found[k]['default']!=item['default']:
            found[k]['ambiguousRepeatedDefault']=item['default']
    options=[]
    for v in found.values():
        v.pop('method',None);v.pop('className',None)
        options.append(v)
    options.sort(key=lambda v:(v['file'].lower(),v['category'].lower(),v['key'].lower()))
    return {'schemaVersion':1,'sourceSha256':sha,'sourceFileName':os.path.basename(path),'modId':mod['modid'],'modName':mod.get('name',mod['modid']),'version':mod.get('version',''),'extraction':'literal Forge Configuration invocation bytecode (nonliteral omitted)','propertyCount':len(options),'properties':options}

if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit('Usage: extract_legacy_forge_config.py <source.jar> <output-dir>')
    out=extract(sys.argv[1]);os.makedirs(sys.argv[2],exist_ok=True)
    filename=os.path.join(sys.argv[2],out['sourceSha256']+'.json')
    with open(filename,'w',encoding='utf8') as f:json.dump(out,f,ensure_ascii=False,indent=2);f.write('\n')
    print(f"{out['modId']}: {out['propertyCount']} literal Forge config options -> {filename}")
    print('file groups:',sorted({x['file'] for x in out['properties']}))
