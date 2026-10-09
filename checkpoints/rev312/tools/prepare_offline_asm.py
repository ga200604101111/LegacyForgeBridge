#!/usr/bin/env python3
"""Create an OFFLINE TEST/BUILD dependency from this JDK's genuine embedded ASM.

No stubs are generated. Package relocation is solely for tool/test classpaths.
Nothing from this dependency is bundled into LegacyForgeBridge's output JAR.
"""
from pathlib import Path
import hashlib,json,struct,subprocess,zipfile

def relocate_class(data: bytes) -> bytes:
    out=bytearray(data[:10]); count=struct.unpack('>H',data[8:10])[0];p=10;i=1
    while i<count:
        tag=data[p];out.append(tag);p+=1
        if tag==1:
            n=int.from_bytes(data[p:p+2],'big');p+=2
            value=data[p:p+n].replace(b'jdk/internal/org/objectweb/asm',b'org/objectweb/asm');p+=n
            out.extend(len(value).to_bytes(2,'big'));out.extend(value)
        else:
            n={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag]
            out.extend(data[p:p+n]);p+=n
            if tag in (5,6):i+=1
        i+=1
    out.extend(data[p:]);return bytes(out)

def prepare(tools: Path, work: Path) -> Path:
    toolclasses=work/'tool-classes';raw=work/'dependencies/jdk-asm-raw';out=work/'dependencies/asm-jdk21-relocated.jar'
    toolclasses.mkdir(parents=True,exist_ok=True);out.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run(['javac','--release','21','-d',str(toolclasses),str(tools/'ExtractJdkAsm.java')],check=True)
    subprocess.run(['java','-cp',str(toolclasses),'ExtractJdkAsm',str(raw)],check=True)
    files=sorted(raw.rglob('*.class'))
    if len(files)<100:raise RuntimeError('JDK does not expose the expected ASM tool library')
    with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED) as jar:
        for p in files:
            entry=zipfile.ZipInfo('org/objectweb/asm/'+p.relative_to(raw).as_posix(),(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
            jar.writestr(entry,relocate_class(p.read_bytes()))
    provenance={'source':'JDK jrt:/modules/java.base/jdk/internal/org/objectweb/asm','java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr.strip(),'classCount':len(files),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'testDoubles':False,'isFabricLoaderASM9_10_1':False,'bundledInMod':False}
    (out.parent/'offline-asm-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
    return out
if __name__=='__main__':
    import sys
    print(prepare(Path(__file__).resolve().parent,Path(sys.argv[1])))
