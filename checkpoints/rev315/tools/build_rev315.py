#!/usr/bin/env python3
"""Rebuild rev315 opcode-scanner fix from the exact rev314 install ZIP with JDK 21.

Only the standalone converter's independent classfile scanner is changed.
No original Forge mod, server or loader runtime is required for this patch.
"""
import argparse
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import zipfile

REQUIRED_PACKAGE_SHA = '3803e4126dc372da46f83dc04affc4ba48c75b7897d3267af4925cf90d556fda'
REQUIRED_CONVERTER_SHA = 'ce362e2b924fa87c54ebb360ad90bd23e1e8bad1ad8ccac82e0f546a3ffceb08'
CLASS = 'dev/yinghuang/legacyforgebridge/convert/LegacyGeneratedBytecodeLinkageAnalyzer.class'
CORE = 'lfb_install/LFB_Package/LFB-Converter.jar'
PROPS = 'lfb_install/LFB_Package/bundle.properties'
CHECKSUMS = 'lfb_install/LFB_Package/checksums.json'
VERSION = 'rev315-linkage-scanner-opcodes.1'
EXPORTS = [
    '--add-exports', 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
    '--add-exports', 'java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED'
]

def sha(blob):
    return hashlib.sha256(blob).hexdigest()

def rewrite_zip(basedata, replacement):
    import io
    src = zipfile.ZipFile(io.BytesIO(basedata), 'r')
    stream = io.BytesIO()
    with src, zipfile.ZipFile(stream, 'w') as dst:
        names = [inf.filename for inf in src.infolist()]
        if len(names) != len(set(names)) or len(names) == 0 or any(x not in names for x in replacement):
            raise ValueError('Missing/duplicate ZIP entry')
        for info in src.infolist():
            dst.writestr(info, replacement.get(info.filename, src.read(info)))
    return stream.getvalue()

def build(base, out, java='java', javac='javac'):
    raw = pathlib.Path(base).read_bytes()
    if sha(raw) != REQUIRED_PACKAGE_SHA:
        raise ValueError(f'Incorrect rev314 package SHA: {sha(raw)}')
    srcpath = pathlib.Path(__file__).with_name('PatchOpcodeLengths.java')
    if not srcpath.is_file():
        raise FileNotFoundError(srcpath)
    with zipfile.ZipFile(base) as zin:
        converter = zin.read(CORE)
        if sha(converter) != REQUIRED_CONVERTER_SHA:
            raise ValueError('Wrong base Converter.jar')
        original = zin.read('lfb_install/Install_LFB.ps1').decode('utf-8')
        description = zin.read('lfb_install/使用說明.txt').decode('utf-8')
        bundle = zin.read(PROPS).decode('utf-8')
        checksum = json.loads(zin.read(CHECKSUMS))
    with tempfile.TemporaryDirectory(prefix='lfb-rev315-') as tmp:
        tmp = pathlib.Path(tmp)
        patch_class = tmp/'LegacyGeneratedBytecodeLinkageAnalyzer.class'
        with zipfile.ZipFile(pathlib.Path(tmp/'base.jar'), 'w') as z:  # only a temporary wrapper for tool input
            z.writestr(CLASS, zipfile.ZipFile(__import__('io').BytesIO(converter)).read(CLASS))
        with zipfile.ZipFile(tmp/'base.jar') as z:
            (tmp/'source.class').write_bytes(z.read(CLASS))
        subprocess.run([javac, *EXPORTS, '-d', str(tmp), str(srcpath)], check=True)
        subprocess.run([java, *EXPORTS, '-cp', str(tmp), 'PatchOpcodeLengths',
                        str(tmp/'source.class'), str(patch_class)], check=True)
        patched_converter = rewrite_zip(converter, {CLASS: patch_class.read_bytes()})
    bundle = bundle.replace('build=rev314-uv-layout.1', f'build={VERSION}')
    bundle = bundle.replace(REQUIRED_CONVERTER_SHA, sha(patched_converter))
    if f'build={VERSION}' not in bundle:
        raise ValueError('Unexpected original bundle.properties')
    new_core = patched_converter
    updated_installer = original.replace('LFB rev314 本機修復版安裝完成', 'LFB rev315 Bytecode 檢查器修復版安裝完成')
    if updated_installer == original:
        raise ValueError('Expected installer completion message missing')
    new_description = ('【rev315 適用：Bamboo 位元組碼檢查熱修復】\n'
       '修復轉換檢查器把 CHECKCAST (192) 與 INSTANCEOF (193) 的操作數誤認成新指令，造成 Invalid/unsupported JVM instruction 235。\n'
       '正式轉換時仍保留完整 linkage audit、Forge 依賴拒絕規則、無效 class fail-closed。\n'
       '請依下文相同程序安裝；其餘 rev314 功能不變。Minecraft 實機尚待測試。\n\n'+description)
    files = {
      CORE: new_core,
      PROPS: bundle.encode('utf-8'),
      'lfb_install/Install_LFB.ps1': updated_installer.encode('utf-8'),
      'lfb_install/使用說明.txt': new_description.encode('utf-8')
    }
    for entry in checksum['files']:
        rel = 'lfb_install/LFB_Package/'+entry['name']
        if rel == CORE: entry['sha256'] = sha(new_core)
        elif rel == PROPS: entry['sha256'] = sha(files[PROPS])
    files[CHECKSUMS] = (json.dumps(checksum, indent=2, ensure_ascii=False)+'\n').encode('utf-8')
    with zipfile.ZipFile(base) as z:
        names = [info.filename for info in z.infolist()]
        package_sha = ''.join(f'{sha(files.get(n,z.read(n)))}  {n.removeprefix("lfb_install/")}\n'
                              for n in names if n.startswith('lfb_install/') and n != 'lfb_install/PACKAGE_SHA256.txt')
    files['lfb_install/PACKAGE_SHA256.txt']=package_sha.encode('utf-8')
    finished = rewrite_zip(raw, files)
    output=pathlib.Path(out)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists(): raise FileExistsError('Refusing to overwrite: '+str(output))
    output.write_bytes(finished)
    with zipfile.ZipFile(output) as z:
        assert z.testzip() is None
        j=json.loads(z.read(CHECKSUMS))
        for f in j['files']:
            assert sha(z.read('lfb_install/LFB_Package/'+f['name']))==f['sha256']
    return {'name':output.name,'sha256':sha(finished), 'bytes':len(finished),
            'converterSha256':sha(new_core), 'unchangedRuntimeSha256':sha(zipfile.ZipFile(__import__('io').BytesIO(raw)).read('lfb_install/LFB_Package/LegacyForgeBridge-rev313-bootstrap1.jar'))}

if __name__ == '__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--base-rev314',required=True)
    p.add_argument('--out',required=True)
    p.add_argument('--java',default='java')
    p.add_argument('--javac',default='javac')
    args=p.parse_args()
    print(json.dumps(build(args.base_rev314,args.out,args.java,args.javac),indent=2))
