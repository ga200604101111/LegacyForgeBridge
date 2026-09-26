#!/usr/bin/env python3
"""Standalone source-evidence tests; not a Minecraft/Gradle/integration build."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

CORPUS_SHA = '35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e'
SOURCE = 'src/main/java/dev/yinghuang/legacyforgebridge/convert/LegacyFluentTextureAnalyzer.java'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--work', type=Path, required=True, help='New, non-existing work directory')
    p.add_argument('--corpus', type=Path, help='Optional legacy JAR, inspected without classloading')
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument('--asm-classpath', help='Real ASM9 core, tree and analysis JAR classpath')
    mode.add_argument('--jdk-internal-asm', action='store_true', help='Explicit limited smoke mode: rewrite test copies to the JDK bundled ASM8 API')
    args = p.parse_args()
    root = Path(__file__).resolve().parent
    work = args.work.resolve()
    if work.exists():
        p.error('--work must not already exist')
    for executable in ('javac', 'java'):
        if not shutil.which(executable):
            p.error(executable + ' is required (JDK21 or newer)')
    corpus = args.corpus.resolve() if args.corpus else None
    if corpus and not corpus.is_file():
        p.error('--corpus is not a file')
    manifest = json.loads((root / 'source-manifest.json').read_text())
    for rel, expected in manifest.items():
        if sha(root / rel) != expected:
            raise ValueError('Source checksum mismatch: ' + rel)
    work.mkdir(parents=True)
    src, classes = work / 'src', work / 'classes'
    src.mkdir(); classes.mkdir()
    inputs = [SOURCE, 'tests/FluentTextureTest.java', 'tests/CorpusProbe.java']
    paths = []
    for rel in inputs:
        content = (root / rel).read_text()
        if args.jdk_internal_asm:
            content = content.replace('org.objectweb.asm', 'jdk.internal.org.objectweb.asm').replace('ASM9', 'ASM8')
        target = src / Path(rel).name
        target.write_text(content)
        paths.append(str(target))
    exports = []
    if args.jdk_internal_asm:
        for package in ('asm', 'asm.tree', 'asm.tree.analysis'):
            exports += ['--add-exports', 'java.base/jdk.internal.org.objectweb.' + package + '=ALL-UNNAMED']
    javac = ['javac', *exports]
    classpath = str(classes)
    if args.asm_classpath:
        import os
        javac += ['-cp', args.asm_classpath]
        classpath += os.pathsep + args.asm_classpath
    javac += ['-encoding', 'UTF-8', '-d', str(classes), *paths]
    def run(command):
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
        if result.returncode:
            raise RuntimeError('Command failed: ' + ' '.join(command) + '\n' + result.stdout)
        return result.stdout
    compile_log = run(javac)
    (work / 'compile.log').write_text(compile_log)
    java = ['java', *exports, '-Xverify:all', '-cp', classpath]
    tests = run([*java, 'FluentTextureTest'])
    (work / 'tests.log').write_text(tests)
    matched = re.search(r'^PASS assertions=(\d+)$', tests, re.M)
    if not matched or int(matched[1]) != 101:
        raise AssertionError('Expected 101 passing synthetic assertions')
    report = {
        'status': 'SOURCE_EVIDENCE_ONLY', 'installable': False,
        'pipeline_integration': False, 'default_icons_admitted': 0,
        'new_runtime_entity_rules': 0, 'synthetic_assertions_passed': int(matched[1]),
        'validation_mode': 'JDK_INTERNAL_ASM8_TEST_COPY' if args.jdk_internal_asm else 'EXTERNAL_ASM9',
        'java_version': run(['java', '-version']).strip(),
        'production_asm9_build_tested': not args.jdk_internal_asm,
        'full_gradle_build_tested': False, 'minecraft_launch_tested': False,
        'live_server_tested': False, 'cumulative_restore_tested': False,
        'actions_requested': False, 'source_sha256': manifest,
    }
    if corpus:
        output = run([*java, 'CorpusProbe', str(corpus)])
        (work / 'corpus.tsv').write_text(output)
        rows = [line.split('\t') for line in output.splitlines()]
        def count(label):
            return int(next(row[1] for row in rows if row[0] == label))
        report['corpus'] = {
            'file': corpus.name, 'bytes': corpus.stat().st_size, 'sha256': sha(corpus),
            'literal_texture_field_evidence': count('EVIDENCE'),
            'direct_registered_item_fields': count('DIRECT_REGISTERED_FIELDS'),
            'direct_registered_field_matches': count('DIRECT_REGISTERED_MATCHES'),
            'resource_missing': sum(row[5] != 'true' for row in rows if row[0] == 'FIELD'),
            'not_directly_registered_fields': [row[1] + '.' + row[2] for row in rows if row[0] == 'NOT_DIRECTLY_REGISTERED'],
            'excluded_fields': count('EXCLUSIONS'), 'log_sha256': sha(work / 'corpus.tsv'),
        }
        c = report['corpus']
        if c['sha256'] == CORPUS_SHA:
            observed = (c['literal_texture_field_evidence'], c['direct_registered_item_fields'], c['direct_registered_field_matches'], c['resource_missing'], c['excluded_fields'])
            if observed != (101, 99, 99, 0, 7):
                raise AssertionError('Known corpus regression: ' + str(observed))
    (work / 'verification.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    print(tests.strip())
    print(json.dumps(report, indent=2, ensure_ascii=False))

if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, AssertionError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
