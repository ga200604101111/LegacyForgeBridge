#!/usr/bin/env python3
"""Check released bytecode against the documented Minecraft 1.21.11 Property and world-clock ABIs.

This is a focused descriptor guard, not a general Minecraft linkage/integration test.
Source: Fabric Yarn 1.21.11+build.4, State.contains(Property) and WorldAccess.getTime().
"""
import argparse
import json
from pathlib import Path
import struct
import zipfile

EXPECTED = '(Lnet/minecraft/class_2769;)Z'

def references(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Not a Java class')
    count = struct.unpack_from('>H', data, 8)[0]
    pool = [None] * count
    offset, index = 10, 1
    while index < count:
        tag = data[offset]; offset += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, offset)[0]; offset += 2
            pool[index] = (tag, data[offset:offset+size].decode('utf-8', errors='replace')); offset += size
        elif tag in (3, 4):
            offset += 4
        elif tag in (5, 6):
            offset += 8; index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, struct.unpack_from('>H', data, offset)[0]); offset += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, *struct.unpack_from('>HH', data, offset)); offset += 4
        elif tag == 15:
            offset += 3
        else:
            raise ValueError('Unsupported constant-pool tag: ' + str(tag))
        index += 1
    def text(i):
        if pool[i][0] != 1: raise ValueError('Invalid UTF-8 reference')
        return pool[i][1]
    for entry in pool:
        if entry and entry[0] in (10, 11):
            owner = text(pool[entry[1]][1]); nat = pool[entry[2]]
            yield owner, text(nat[1]), text(nat[2])

def audit(jar):
    checked, invalid, clocks, bad_clocks, classes = [], [], [], [], 0
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if not name.endswith('.class'): continue
            classes += 1
            for owner, method, descriptor in references(archive.read(name)):
                if owner.startswith('net/minecraft/') and method == 'method_28498':
                    ref = {'class': name, 'owner': owner, 'name': method, 'descriptor': descriptor}
                    checked.append(ref)
                    if descriptor != EXPECTED: invalid.append(ref)
                if owner in ('net/minecraft/class_1936', 'net/minecraft/class_1937', 'net/minecraft/class_638') and method in ('method_8510', 'method_75260'):
                    ref = {'class': name, 'owner': owner, 'name': method, 'descriptor': descriptor}
                    clocks.append(ref)
                    if method != 'method_75260' or descriptor != '()J': bad_clocks.append(ref)
    if not checked: raise ValueError('No Property calls found; wrong input artifact?')
    return {'classes_scanned': classes, 'property_references_checked': len(checked),
            'invalid_property_references': invalid, 'clock_references_checked': len(clocks),
            'invalid_clock_references': bad_clocks, 'passed': not invalid and not bad_clocks}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--expect-invalid', action='store_true')
    args = parser.parse_args()
    result = audit(args.jar)
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result['passed'] != args.expect_invalid else 1)
