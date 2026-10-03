#!/usr/bin/env python3
"""Materialize a guarded SOURCE-ONLY delta over rev243. This does not build a JAR."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
PACKAGE = 'dev/yinghuang/legacyforgebridge/behavior/'
BASE_COMMIT = 'e6c4a7879b9c9407761ed133df043e97b45daefe'
BASE_JAR_SHA256 = '87d4e3cbe4939b21c4eb955b66508bbc2724aafbf6b557c87245cc345121ef63'
BASE_SOURCES = {
    'LegacyMotionTraceLog.java': '116cba4cfe8f827a505cfc2c7a8104b800bac54664b30dcd60e9784313e22b52',
    'Rev243Diagnostics.java': 'a8b2a461f02dcdca1e146fae47cf40a63bcb49ef64e98af3766fb847a92dc093',
}

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise ValueError(f'Expected exactly one edit anchor, found {count}: {old!r}')
    return text.replace(old, new, 1)

def original_sources(repo: Path = REPO) -> dict[str, bytes]:
    result = {}
    for name, expected in BASE_SOURCES.items():
        data = (repo / 'checkpoints/rev243/source' / PACKAGE / name).read_bytes()
        if sha(data) != expected:
            raise ValueError(f'Rev243 source SHA-256 mismatch: {name}; do not overwrite newer work')
        result[name] = data
    return result

def transform(name: str, data: bytes) -> bytes:
    if name not in BASE_SOURCES or sha(data) != BASE_SOURCES[name]:
        raise ValueError(f'Unrecognized source baseline: {name}')
    text = data.decode('utf-8')
    if name == 'LegacyMotionTraceLog.java':
        text = replace_once(text, '+" isAirBorne="+ent.field_70133_I',
                            '+" velocityChanged="+ent.field_70133_I')
    else:
        text = replace_once(text, '    static void captureStarted() {\n',
            '    static void captureStarted() {\n'
            '        // Observations belong to this capture, not the lifetime of the JVM.\n'
            '        CAMERAS.set(0);MOVES.set(0);STARTS.set(0);\n'
            '        WARNED.clear();MOVE.remove();\n')
        text = replace_once(text, 'reconciliation=hard_disabled traceAutoStart=true',
            'reconciliation=hard_disabled diagnosticSchema=rev244-source.1 '
            'coverageCounters=capture_local sourceFlag=velocityChanged:field_70133_I traceAutoStart=true')
        text = replace_once(text,
            '    public static void tickStart(Object client) {\n        STARTS.incrementAndGet();\n',
            '    public static void tickStart(Object client) {\n')
        text = replace_once(text,
            '            Object p=local(client);if(p==null)return;\n            tickStartNs=begin;',
            '            Object p=local(client);if(p==null)return;\n'
            '            STARTS.incrementAndGet();\n            tickStartNs=begin;')
        text = replace_once(text,
            '                if(CAMERAS.get()==0)LegacyMotionTraceLog.event(',
            '                if(STARTS.get()==0)LegacyMotionTraceLog.event("HOOK_NOT_OBSERVED",'
            '"hook=tickStart reason=not_observed_or_registration_missing");\n'
            '                if(CAMERAS.get()==0)LegacyMotionTraceLog.event(')
    return text.encode('utf-8')

def sources(repo: Path = REPO) -> dict[str, bytes]:
    return {name: transform(name, data) for name, data in original_sources(repo).items()}

def materialize(output: Path, repo: Path = REPO) -> dict:
    payload = sources(repo)  # Validate every input before creating the output directory.
    output = output.resolve()
    if output == repo.resolve() or output.is_relative_to((repo / 'src').resolve()) or output.is_relative_to((repo / 'checkpoints').resolve()):
        raise ValueError('Output must be outside root src and stored checkpoints')
    output.mkdir(parents=True, exist_ok=False)
    for name, data in payload.items():
        target = output / PACKAGE / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    manifest = {'checkpoint': 'rev244-source-only', 'baseCommit': BASE_COMMIT,
                'requiredFutureBaseJarSha256': BASE_JAR_SHA256,
                'baseSources': BASE_SOURCES,
                'generatedSources': {name: sha(data) for name, data in payload.items()},
                'buildProduced': False, 'liveMinecraftValidated': False}
    (output / 'source-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    return manifest

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--repo', type=Path, default=REPO)
    args = parser.parse_args()
    print(json.dumps(materialize(args.output, args.repo), indent=2))

if __name__ == '__main__':
    main()
