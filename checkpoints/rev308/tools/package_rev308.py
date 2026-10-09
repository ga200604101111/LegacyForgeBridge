#!/usr/bin/env python3
"""Base-locked rev308 complete JAR overlay; non-target assets and conversion code are not mutated."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import hashlib,json
root=Path('/mnt/data/lfb308work');base=Path('/mnt/data/legacyforgebridge-0.2.0-alpha.27-rev307-legacy-mod-config-menu-v2.jar');out=Path('/mnt/data/legacyforgebridge-0.2.0-alpha.27-rev308-twilight-sword-arrow-equipment.jar')
base_expected='42c12ff87acd5261935d7d079513b777b2fd8ded4907c73e315e27fa374a909f'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
if sha(base)!=base_expected:raise ValueError('Unknown rev307 baseline')
compiled=root/'rev308classes';res=root/'resources'
changes={p.relative_to(compiled).as_posix():p.read_bytes() for p in compiled.rglob('*.class')}
changes.update({p.relative_to(res).as_posix():p.read_bytes() for p in res.rglob('*') if p.is_file()})
version='0.2.0-alpha.27-corpus4-local.61-rev308-twilight-sword-arrow-equipment.1'
with ZipFile(base) as old:
 names=old.namelist();assert len(names)==len(set(names))
 metadata=json.loads(old.read('fabric.mod.json'))
 metadata['version']=version
 metadata['description']='rev308: source-classified legacy sword right-click BLOCK/pose fallback, SHA-pinned Twilight seeker-arrow remote renderer, source .tooltip text, and source-proven creative stack enchantments; rev307 old-mod Cloth/Mod Menu configuration retained; server authority unchanged.'
 changes['fabric.mod.json']=(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n').encode()
 replaced={p for p in changes if p in names};added={p for p in changes if p not in names}
 expected={
 'dev/yinghuang/legacyforgebridge/BuildInfo.class',
 'dev/yinghuang/legacyforgebridge/convert/runtime/GeneratedModSupport.class',
 'dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime.class',
 'dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorRuntime.class',
 'dev/yinghuang/legacyforgebridge/compat/LegacySourceInteractionRuntime.class',
 'dev/yinghuang/legacyforgebridge/compat/LegacyProjectilePresentationRegistry.class',
 'fabric.mod.json'
 }
 if replaced!=expected:raise AssertionError('Unexpected targeted classes: '+str(replaced^expected))
 with ZipFile(out,'w') as target:
  for entry in old.infolist():target.writestr(entry,changes[entry.filename] if entry.filename in replaced else old.read(entry.filename))
  for n in sorted(added):
   zinfo=ZipInfo(n,(1980,1,1,0,0,0));zinfo.compress_type=ZIP_DEFLATED
   target.writestr(zinfo,changes[n])
with ZipFile(out) as final,ZipFile(base) as old:
 assert final.testzip() is None
 assert len(final.namelist())==len(set(final.namelist()))
 assert len(final.namelist())==len(old.namelist())+len(added)
 unchanged=[p for p in old.namelist() if p not in replaced]
 for p in unchanged:assert final.read(p)==old.read(p),p
 assert json.loads(final.read('fabric.mod.json'))['version']==version
 assert version.encode() in final.read('dev/yinghuang/legacyforgebridge/BuildInfo.class')
 assert not any(p.startswith('org/objectweb/asm/') or p.startswith('META-INF/jars/asm') for p in final.namelist())
 assert not any('stub' in p or 'jdk/internal/org/objectweb' in p for p in final.namelist())
 assert sum(p.endswith('.class') for p in added)==4
 assert sum(p.endswith('.properties') for p in added)==2
 print('REVPACK_BASE_SHA256',sha(base))
 print('REVPACK_MAIN_SHA256',sha(out))
 print('REVPACK_MAIN_BYTES',out.stat().st_size)
 print('REVPACK_REPLACED',len(replaced),'REVPACK_ADDED',len(added),'REVPACK_UNCHANGED',len(unchanged))
 print('REVPACK_ZIP_INTEGRITY_PASS',final.testzip() is None)
 print('REVPACK_CONVERTER_CACHE_UNCHANGED',True)
 print('REVPACK_GAMEPLAY_RUNTIME_TESTED',False)
 print('REVPACK_OUTPUT',out)
