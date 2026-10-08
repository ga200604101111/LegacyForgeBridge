"""Test complete main archive construction with isolated synthetic baseline JARs."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile
from test_verify_main_jar_overlay import BASE, MOD, jar
from verify_main_jar_overlay import sha256
from build_main_from_base import build

JAVA21_HEADER=b'\xca\xfe\xba\xbe\x00\x00\x00\x41'
NEW_CLASS='dev/yinghuang/legacyforgebridge/feature/BoundedCheck.class'

class PackagerTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.base=self.root/'rev260.jar'
        self.out=self.root/'new-main.jar'
        self.overlay=self.root/'overlay'
        self.overlay.mkdir()
        jar(self.base,BASE)
        self.digest=sha256(self.base.read_bytes())
        new=copy.deepcopy(MOD);new['version']='v2'
        self.staged('fabric.mod.json',json.dumps(new).encode())
        self.staged(NEW_CLASS,JAVA21_HEADER+b'new')
        self.reviewed={'fabric.mod.json',NEW_CLASS}
    def staged(self,key,data):
        path=self.overlay/key
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(data)
    def test_full_output_contains_every_baseline_byte_and_new_class(self):
        report=build(self.base,self.out,self.overlay,self.reviewed,self.digest)
        self.assertEqual(report['verdict'],'PASS_STRUCTURAL_ONLY')
        self.assertEqual(report['candidate']['name'],'new-main.jar')
        self.assertEqual(sha256(self.base.read_bytes()),self.digest)
        with ZipFile(self.base) as old,ZipFile(self.out) as new:
            for k in set(old.namelist())-{'fabric.mod.json'}:
                self.assertEqual(old.read(k),new.read(k),k)
            self.assertEqual(new.read(NEW_CLASS),JAVA21_HEADER+b'new')
    def test_build_is_deterministic(self):
        build(self.base,self.out,self.overlay,self.reviewed,self.digest)
        other=self.root/'new-main-again.jar'
        build(self.base,other,self.overlay,self.reviewed,self.digest)
        self.assertEqual(sha256(self.out.read_bytes()),sha256(other.read_bytes()))
    def test_existing_output_never_overwritten(self):
        self.out.write_bytes(b'personal file')
        with self.assertRaisesRegex(ValueError,'already exists'):
            build(self.base,self.out,self.overlay,self.reviewed,self.digest)
        self.assertEqual(self.out.read_bytes(),b'personal file')
    def test_wrong_base_hash_fails_before_output(self):
        with self.assertRaisesRegex(ValueError,'baseline SHA'):
            build(self.base,self.out,self.overlay,self.reviewed,'0'*64)
        self.assertFalse(self.out.exists())
    def test_staged_file_must_be_reviewed(self):
        self.staged('unreviewed/extra.bin',b'data')
        with self.assertRaisesRegex(ValueError,'not explicitly approved'):
            build(self.base,self.out,self.overlay,self.reviewed,self.digest)
        self.assertFalse(self.out.exists())
    def test_no_executable_class_change_rejected(self):
        (self.overlay/NEW_CLASS).unlink()
        with self.assertRaisesRegex(ValueError,'No reviewed executable'):
            build(self.base,self.out,self.overlay,{'fabric.mod.json'},self.digest)
    def test_unchanged_version_rejected_and_candidate_removed(self):
        self.staged('fabric.mod.json',BASE['fabric.mod.json'])
        with self.assertRaisesRegex(ValueError,'structural release guard'):
            build(self.base,self.out,self.overlay,self.reviewed,self.digest)
        self.assertFalse(self.out.exists())
    def test_removing_mixins_is_rejected_by_guard(self):
        altered=copy.deepcopy(MOD);altered['version']='v2';altered['mixins']=[]
        self.staged('fabric.mod.json',json.dumps(altered).encode())
        with self.assertRaisesRegex(ValueError,'structural release guard'):
            build(self.base,self.out,self.overlay,self.reviewed,self.digest)
        self.assertFalse(self.out.exists())

if __name__=='__main__':unittest.main(verbosity=2)
