#!/usr/bin/env python3
"""Rebuild rev312 complete main from the exact supplied rev311 main, with Java 21.

No game signatures are stubbed: new code references only the JDK and existing LFB API.
Provide actual ASM jars through --asm-classpath or use the local JDK's genuine ASM
for offline building. No dependency binary or original Forge mod is redistributed.
"""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from prepare_offline_asm import prepare
BASE_SHA='a21281ef417669490546e4176e38f8670bdc3dd3e28c973ae828b90ce59c4913'
VERSION='0.2.0-alpha.27-corpus4-local.66-rev312-shared-source-registry.1'
BUILD='dev/yinghuang/legacyforgebridge/BuildInfo.class'

def sha(p: Path) -> str:
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--work',type=Path);parser.add_argument('--asm-classpath')
    args=parser.parse_args();root=Path(__file__).resolve().parents[1];work=(args.work or root/'build').resolve()
    if sha(args.base)!=BASE_SHA:raise SystemExit('Wrong rev311 base SHA-256; refusing to overwrite newer or unknown behavior')
    classes=work/'classes';toolclasses=work/'tool-classes';evidence=work/'evidence'
    if classes.exists():shutil.rmtree(classes)
    for p in (classes,toolclasses,evidence):p.mkdir(parents=True,exist_ok=True)
    asm=args.asm_classpath or str(prepare(root/'tools',work))
    # The new runtime helpers compile against the real supplied LFB binary, no API stubs.
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',str(args.base),'-d',str(classes),*[str(p) for p in sorted((root/'src').rglob('*.java'))]],check=True)
    subprocess.run(['javac','--release','21','-cp',asm,'-d',str(toolclasses),str(root/'tools/TransformRev312.java'),str(root/'tools/VerifyRev312.java')],check=True)
    toolcp=str(toolclasses)+os.pathsep+asm
    with (evidence/'transform-sites.txt').open('w') as log:
        subprocess.run(['java','-cp',toolcp,'TransformRev312',str(args.base),str(classes)],stdout=log,stderr=subprocess.STDOUT,check=True)
    with (evidence/'bytecode-validation.txt').open('w') as log:
        subprocess.run(['java','-cp',toolcp,'VerifyRev312',str(args.base),str(classes)],stdout=log,stderr=subprocess.STDOUT,check=True)
    replacements={p.relative_to(classes).as_posix():p.read_bytes() for p in sorted(classes.rglob('*.class'))}
    with zipfile.ZipFile(args.base) as base:
        names=base.namelist()
        if len(names)!=len(set(names)):raise RuntimeError('Duplicate entries in base')
        metadata=json.loads(base.read('fabric.mod.json'));metadata['version']=VERSION
        metadata['description']='rev312: bounded source-scoped byte cache and shared registry analysis with preserved classification/creative visibility. Sequential output passes, rev311 animated models and all earlier runtime fixes retained. Source-analysis tests are not live Minecraft acceptance.'
        replacements['fabric.mod.json']=(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n').encode()
        changed=set(replacements)&set(names);added=set(replacements)-set(names)
        expected_special={BUILD,'fabric.mod.json'}
        if any(n not in expected_special and not n.startswith('dev/yinghuang/legacyforgebridge/convert/') for n in changed):raise RuntimeError('Unexpected runtime/render change')
        if len(changed)!=138 or len(added)!=10:raise RuntimeError(f'Unexpected overlay shape {len(changed)}/{len(added)}')
        args.out.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(args.out,'w') as target:
            for entry in base.infolist():target.writestr(entry,replacements.get(entry.filename,base.read(entry.filename)))
            for n in sorted(added):
                entry=zipfile.ZipInfo(n,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;target.writestr(entry,replacements[n])
    with zipfile.ZipFile(args.base) as base,zipfile.ZipFile(args.out) as target:
        if target.testzip() is not None or len(target.namelist())!=len(set(target.namelist())):raise RuntimeError('Invalid output ZIP')
        unchanged=[n for n in names if n not in changed]
        for n in unchanged:
            if base.read(n)!=target.read(n):raise RuntimeError('Non-target entry changed '+n)
        if any(n.startswith('org/objectweb/asm/') or n.startswith('jdk/') or n.startswith('META-INF/jars/asm-') for n in target.namelist()):raise RuntimeError('Bundled ASM forbidden')
        if VERSION.encode() not in target.read(BUILD):raise RuntimeError('BuildInfo version mismatch')
        # Latest models, particles, arrow/sword/glint, config screens and auxiliary helper stay identical.
        protected=[n for n in names if n.startswith(('dev/yinghuang/legacyforgebridge/rev','dev/yinghuang/legacyforgebridge/render/','dev/yinghuang/legacyforgebridge/config/','dev/yinghuang/legacyforgebridge/compat/','dev/yinghuang/legacyforgebridge/behavior/','dev/yinghuang/legacyforgebridge/convert/runtime/','assets/')) or n=='META-INF/lfb/desktop-helper.jar']
        for n in protected:
            if base.read(n)!=target.read(n):raise RuntimeError('Protected runtime/resource changed '+n)
    info={'baseSha256':BASE_SHA,'artifact':args.out.name,'sha256':sha(args.out),'bytes':args.out.stat().st_size,'changedExistingEntries':len(changed),'newClasses':len(added),'unchangedEntryPayloads':len(unchanged),'protectedRuntimeResourceEntries':len(protected),'zipIntegrity':True,'bundledAsm':False,'version':VERSION,'conversionSemanticRevisionUnchanged':True,'minecraftRuntimeTested':False}
    (evidence/'packaging.json').write_text(json.dumps(info,indent=2)+'\n');print(json.dumps(info,indent=2))
if __name__=='__main__':main()
