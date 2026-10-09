#!/usr/bin/env python3
"""Reproducible-ish offline main-JAR class overlay on the exact supplied rev306 base.

No Minecraft or Fabric runtime tests are implied by this packager. Verifies that
all non-target rev306 entry payloads remain identical. No stubs are included.
"""
import hashlib,json,zipfile
from pathlib import Path
BASE=Path('/mnt/data/legacyforgebridge-0.2.0-alpha.27-rev306-source-bow-presentation.jar')
ROOT=Path(__file__).resolve().parents[1]
CLASSES=ROOT/'offline-build/classes'
RESOURCES=ROOT/'resources'
OUT=Path('/mnt/data/legacyforgebridge-0.2.0-alpha.27-rev307-legacy-mod-config-menu.jar')
VERSION='0.2.0-alpha.27-corpus4-local.59-rev307-legacy-config-menu.1'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

replacements={p.relative_to(CLASSES).as_posix():p.read_bytes() for p in CLASSES.rglob('*.class')}
for path in RESOURCES.rglob('*'):
    if path.is_file():replacements[path.relative_to(RESOURCES).as_posix()]=path.read_bytes()
assert 'dev/yinghuang/legacyforgebridge/BuildInfo.class' in replacements
assert 'dev/yinghuang/legacyforgebridge/config/LegacyForgeBridgeModMenu.class' in replacements

with zipfile.ZipFile(BASE) as base:
    names=set(base.namelist());assert len(names)==len(base.namelist()),'Baseline contains duplicate ZIP entries'
    metadata=json.loads(base.read('fabric.mod.json'))
    assert 'rev306' in metadata['version'], 'Wrong base rev'
    metadata['version']=VERSION
    metadata['description']='rev307 source-SHA-pinned Forge 1.7.10 configuration editor in Mod Menu for converted Fabric wrappers; rev306 gameplay/converter semantics unchanged; Cloth Config bridge uses three supplied source JAR profiles. Client-local cfg only; server authority unaffected.'
    replacements['fabric.mod.json']=(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n').encode()
    for p in replacements:
        if p in names and p not in ['dev/yinghuang/legacyforgebridge/BuildInfo.class','dev/yinghuang/legacyforgebridge/config/LegacyForgeBridgeModMenu.class','fabric.mod.json']:
            raise AssertionError('Unexpected overwrite of previous revision payload: '+p)
    with zipfile.ZipFile(OUT,'w') as out:
        for info in base.infolist():
            out.writestr(info,replacements.pop(info.filename,base.read(info.filename)))
        for name,data in sorted(replacements.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            out.writestr(info,data)

with zipfile.ZipFile(BASE) as base, zipfile.ZipFile(OUT) as rev:
    assert rev.testzip() is None
    assert len(rev.namelist())==len(set(rev.namelist()))
    protected=[x for x in base.namelist() if x not in ('dev/yinghuang/legacyforgebridge/BuildInfo.class','dev/yinghuang/legacyforgebridge/config/LegacyForgeBridgeModMenu.class','fabric.mod.json')]
    for x in protected:
        assert rev.read(x)==base.read(x),x
    for suffix in ('.SF','.RSA','.DSA'):
        assert not any(x.startswith('META-INF/') and x.endswith(suffix) for x in rev.namelist())
    assert not any(n.startswith('org/objectweb/asm/') or n.startswith('META-INF/jars/asm-') for n in rev.namelist())
    assert len([x for x in rev.namelist() if x.startswith('legacyforgebridge/legacy-config-profiles/')])==3
    assert json.loads(rev.read('fabric.mod.json'))['version']==VERSION
    print('baseline_sha256',sha(BASE))
    print('rev307_sha256',sha(OUT))
    print('rev307_bytes',OUT.stat().st_size)
    print('protected_existing_entries_identical',len(protected))
    print('added_classes',sum(x.endswith('.class') and x not in base.namelist() for x in rev.namelist()))
    print('profile_count',3)
    print('zip_integrity','PASS')
    print('duplicate_entries','NONE')
    print('bundled_asm','NONE')
    print('artifact',str(OUT))
