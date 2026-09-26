#!/usr/bin/env python3
"""Apply the client-only source delta to a restored rev199 tree; preflight before every write."""
import argparse
import hashlib
import json
from pathlib import Path
import os

BASE_REVISION = '2026-09-26.198-generic-block-names-and-bootstrap-arguments'
PACKAGE = 'src/main/java/dev/yinghuang/legacyforgebridge/'
HERE = Path(__file__).resolve().parent

def safe(root, name):
    path = Path(name)
    if path.is_absolute() or '..' in path.parts or '\\' in name:
        raise ValueError('Unsafe source path: ' + name)
    target = (root / name).resolve()
    if root.resolve() not in target.parents:
        raise ValueError('Source path escapes root: ' + name)
    return target

def payload():
    manifest = json.loads((HERE / 'source-manifest.json').read_text(encoding='utf-8'))
    for name, expected in manifest.items():
        actual = hashlib.sha256(safe(HERE, name).read_bytes()).hexdigest()
        if actual != expected:
            raise ValueError('Checkpoint checksum mismatch: ' + name)
    return {name: safe(HERE, name).read_bytes() for name in manifest if name.startswith('src/')}

def replace_once(data, old, new, path):
    text = data.decode('utf-8')
    if text.count(old) != 1:
        raise ValueError('Expected one reviewed source anchor in ' + path)
    return text.replace(old, new, 1).encode('utf-8')

def plan(root):
    root = root.resolve()
    if not root.is_dir():
        raise ValueError('Restored source directory does not exist')
    build = safe(root, PACKAGE + 'BuildInfo.java').read_text(encoding='utf-8')
    if BASE_REVISION not in build:
        raise ValueError('Restore rev199 first; the old root src is NOT the rev198/rev199 baseline')
    additions = payload()
    for name in additions:
        if safe(root, name).exists():
            raise FileExistsError('Refusing to overwrite a new source: ' + name)
    mixin = PACKAGE + 'mixin/client/LegacyLivingBehaviorMixin.java'
    client = PACKAGE + 'behavior/LegacyBehaviorClient.java'
    config = 'src/main/resources/legacyforgebridge.client.mixins.json'
    changes = {
        mixin: replace_once(safe(root, mixin).read_bytes(),
            'LegacyBehaviorRuntime.jump((LivingEntity)(Object)this);',
            'dev.yinghuang.legacyforgebridge.behavior.LegacyClientJumpMotion.sourceJump((LivingEntity)(Object)this);', mixin),
        client: replace_once(safe(root, client).read_bytes(),
            'LegacyBehaviorRuntime.setLocalPlayer(e->e==Minecraft.getInstance().player);',
            'LegacyBehaviorRuntime.setLocalPlayer(e->e==Minecraft.getInstance().player);\n        LegacyClientJumpMotion.initialize();', client),
    }
    data = json.loads(safe(root, config).read_text(encoding='utf-8'))
    if data.get('package') != 'dev.yinghuang.legacyforgebridge.mixin.client' or not isinstance(data.get('client'), list):
        raise ValueError('Unexpected client Mixin configuration')
    if data['client'].count('LegacyLivingBehaviorMixin') != 1 or 'LegacyJumpMotionPacketMixin' in data['client']:
        raise ValueError('Missing baseline Mixin or already-applied new Mixin')
    data['client'].append('LegacyJumpMotionPacketMixin')
    changes[config] = (json.dumps(data, indent=2, ensure_ascii=False) + '\n').encode('utf-8')
    return {**changes, **additions}

def apply(root, check_only=False):
    root = root.resolve(); changes = plan(root)
    if check_only:
        return list(changes)
    originals = {name: safe(root, name).read_bytes() if safe(root, name).is_file() else None for name in changes}
    written = []
    try:
        for name, content in changes.items():
            target = safe(root, name); target.parent.mkdir(parents=True, exist_ok=True)
            temporary = target.with_name(target.name + '.rev200-tmp')
            # Never silently replace another operation's scratch file.
            with temporary.open('xb') as out:
                out.write(content)
            try:
                os.replace(temporary, target)
                written.append(name)
            finally:
                if temporary.exists(): temporary.unlink()
    except BaseException:
        for name in reversed(written):
            target = safe(root, name)
            if originals[name] is None: target.unlink(missing_ok=True)
            else: target.write_bytes(originals[name])
        raise
    return list(changes)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-root', type=Path, required=True)
    p.add_argument('--check-only', action='store_true')
    args = p.parse_args()
    for name in apply(args.source_root, args.check_only): print(name)
    print('Preflight only' if args.check_only else 'Applied source only; production compilation and Mixin/game validation are still required.')
if __name__ == '__main__': main()
