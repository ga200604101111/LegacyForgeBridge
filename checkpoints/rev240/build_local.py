#!/usr/bin/env python3
"""SHA-pinned, offline JDK 21 incremental main-JAR build. No Gradle/Actions/network.
Usage: python build_local.py BASE_REV239.jar OUTPUT_REV240.jar
Test-double classes are isolated under the temporary build directory, never packaged.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile
from test_lifecycle import run as test_lifecycle

BASE_SHA = '42f7d5e8ba647c8f599fe9d152aeeab24e4fe813a08f55cdea75500766d48a3d'
PARENT = 'b7c914369c13df6cd51f37ac6eafe9d7f6ebfbce'
VERSION = '0.2.0-alpha.27-corpus4-local.24-rev240-local-test.1'
REVISION = '2026-10-02.240-liquid-cache-refresh-visible-fallback'
CACHE = '0.2.0-alpha.27-corpus4-local.17-rev233-cache.1'
EXPORTS = ['--add-exports', 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
           '--add-exports', 'java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
ROOT = Path(__file__).resolve().parent
PKG = 'dev/yinghuang/legacyforgebridge/'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    base, output = map(lambda x: Path(x).resolve(), sys.argv[1:])
    if base == output:
        raise SystemExit('Refusing to overwrite the base JAR')
    if sha(base.read_bytes()) != BASE_SHA:
        raise SystemExit('Base SHA-256 mismatch; this checkpoint accepts only the exact uploaded rev239')
    with tempfile.TemporaryDirectory(prefix='lfb-rev240-') as td:
        work = Path(td)
        classes = work / 'classes'
        tools = work / 'tools'
        sources = sorted((ROOT / 'source').rglob('*.java'))
        subprocess.run(['javac', '--release', '21', '-cp', str(base), '-d', str(classes),
                        *map(str, sources)], check=True)
        subprocess.run(['javac', *EXPORTS, '-d', str(tools), str(ROOT / 'PatchRev240.java')], check=True)
        subprocess.run(['java', *EXPORTS, '-cp', str(tools), 'PatchRev240', str(base), str(classes)], check=True)
        tests = test_lifecycle(base, classes, work)
        patches = {str(p.relative_to(classes)).replace(os.sep, '/'): p.read_bytes()
                   for p in sorted(classes.rglob('*.class'))}
        expected_classes = {
            PKG + 'behavior/Rev239VanillaLiquidBridge.class',
            PKG + 'behavior/Rev239VanillaLiquidBridge$Api.class',
            PKG + 'behavior/Rev239VanillaLiquidBridge$Binding.class',
            PKG + 'render/ConvertedLiquidPresentationRuntime.class',
            PKG + 'BuildInfo.class',
        }
        if set(patches) != expected_classes:
            raise RuntimeError('Unexpected compiled classes: ' + str(set(patches)))
        for name in sorted(patches):
            result = subprocess.run(['javap', '-p', '-v', '-cp', str(classes),
                                     name[:-6].replace('/', '.')], check=True, capture_output=True, text=True)
            if 'major version: 65' not in result.stdout:
                raise RuntimeError('Wrong Java bytecode version: ' + name)
        provenance = {
            'schema': 1, 'checkpoint': 'rev240-liquid-cache-refresh-visible-fallback',
            'baseArtifact': base.name, 'baseSha256': BASE_SHA, 'sourceParentCommit': PARENT,
            'version': VERSION, 'converterRevision': REVISION, 'cacheCompatibilityVersion': CACHE,
            'buildMethod': 'JDK21 javac --release 21 helper + exact ASM call-site/version edits over cumulative rev239 main JAR',
            'fullGradleLoomBuild': False, 'networkDependencyDownloads': False,
            'testDoubleClassesPackaged': False, 'liveMinecraftLaunch': False,
            'semantics': ['source-proven WATER rules only', 'populate all state bindings before cache refresh',
                          'refresh existing BlockState caches at liquid registration',
                          'hide model only when renderer cached fluid equals expected real water',
                          'retain model on stale/failed cache', 'unchanged carrier state and registry identity',
                          'no world writes or server-side simulation'],
            'tests': tests,
        }
        with zipfile.ZipFile(base) as z:
            if len(z.namelist()) != len(set(z.namelist())) or z.testzip() is not None:
                raise RuntimeError('Invalid baseline ZIP')
            old = {n: z.read(n) for n in z.namelist()}
        metadata = json.loads(old['fabric.mod.json'])
        metadata['version'] = VERSION
        patches['fabric.mod.json'] = (json.dumps(metadata, ensure_ascii=False, indent=2) + '\n').encode()
        patches['legacyforgebridge/rev240-build.json'] = (json.dumps(provenance, indent=2) + '\n').encode()
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(set(old) | set(patches)):
                info = zipfile.ZipInfo(name, date_time=(2026, 10, 2, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = (0o40755 if name.endswith('/') else 0o100644) << 16
                z.writestr(info, patches.get(name, old.get(name)), compresslevel=9)
        with zipfile.ZipFile(output) as z:
            if z.testzip() is not None or len(z.namelist()) != len(set(z.namelist())):
                raise RuntimeError('Output ZIP validation failed')
            new = {n: z.read(n) for n in z.namelist()}
        changed = sorted(n for n in old if old[n] != new[n])
        added = sorted(set(new) - set(old))
        allowed_changes = sorted([PKG+'behavior/Rev239VanillaLiquidBridge.class',
                                  PKG+'render/ConvertedLiquidPresentationRuntime.class',
                                  PKG+'BuildInfo.class', 'fabric.mod.json'])
        if changed != allowed_changes:
            raise RuntimeError('Unintended baseline mutation: ' + str(changed))
        for protected in ['META-INF/MANIFEST.MF', 'META-INF/jars/energy-4.2.0.jar',
                          'META-INF/lfb/desktop-helper.jar', PKG+'convert/runtime/ConvertedLegacyBlock.class',
                          PKG+'behavior/Rev233LiquidCompat.class', PKG+'behavior/Rev237LiquidAlpha.class']:
            if old[protected] != new[protected]:
                raise RuntimeError('Protected cumulative content was modified: ' + protected)
        packaged_tests = test_lifecycle(base, output, work / 'packaged-test')
        digest = sha(output.read_bytes())
        evidence = {
            **provenance, 'artifact': output.name, 'bytes': output.stat().st_size, 'sha256': digest,
            'jdk': subprocess.run(['java', '-version'], text=True, capture_output=True, check=True).stderr.strip(),
            'baselineEntries': len(old), 'outputEntries': len(new), 'removedEntries': [],
            'changedEntries': changed, 'addedEntries': added, 'zipIntegrity': 'pass',
            'classfileVersion': 65, 'javapParse': 'pass', 'packagedHelperLifecycle': packaged_tests,
            'allOtherEntriesContentPreserved': True,
            'untested': ['real Minecraft/Fabric/Sodium startup', 'in-game transparency and side-face culling',
                         'live Forge 1.7.10 server connection', 'shader/resource-pack interactions'],
        }
        output.with_suffix('.evidence.json').write_text(json.dumps(evidence, indent=2) + '\n')
        output.with_suffix('.sha256').write_text(digest + '  ' + output.name + '\n')
        log = '\n'.join((work / name).read_text() for name in ['baseline-test.log', 'fixed-test.log'])
        output.with_suffix('.tests.log').write_text(log)
        print(json.dumps(evidence, indent=2))

if __name__ == '__main__':
    main()
