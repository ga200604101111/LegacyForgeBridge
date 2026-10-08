"""Unit tests using synthetic JARs; no Minecraft/Forge runtime is executed."""
import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

spec = importlib.util.spec_from_file_location("release_guard", Path(__file__).with_name("verify_main_jar_overlay.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

MOD = {"id":"legacyforgebridge", "schemaVersion":1, "environment":"client", "version":"v1",
       "depends":{"minecraft":"1.21.11","fabricloader":">=0.19.3"},
       "entrypoints":{"client":["example.Client"]},"mixins":["legacyforgebridge.client.mixins.json"],
       "jars":[{"file":"META-INF/jars/energy-4.2.0.jar"}]}
BASE = {"fabric.mod.json":json.dumps(MOD).encode(),"a/Core.class":b"\xca\xfe\xba\xbeold",
        "META-INF/jars/energy-4.2.0.jar":b"energy", "META-INF/lfb/desktop-helper.jar":b"helper",
        "legacyforgebridge.rev258.mixins.json":b"{}",
        "legacyforgebridge.client.mixins.json":b"{}",
        "example/Client.class":b"\xca\xfe\xba\xbe\x00\x00\x00\x41stub"}
BASE["a/Core.class"] = b"\xca\xfe\xba\xbe\x00\x00\x00\x41old"


def jar(path, files):
    with ZipFile(path,"w",compression=ZIP_DEFLATED) as f:
        for name, data in files.items(): f.writestr(name,data)


class GuardTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base=Path(self.tmp.name)/"base.jar"
        self.new=Path(self.tmp.name)/"candidate.jar"
        jar(self.base,BASE)

    def new_files(self):
        files=copy.deepcopy(BASE)
        meta=copy.deepcopy(MOD);meta["version"]="v2"
        files["fabric.mod.json"]=json.dumps(meta).encode()
        return files

    def test_explicit_addition_and_version_bump_passes_structural_only(self):
        f=self.new_files();f["new/source/Proof.class"]=b"\xca\xfe\xba\xbe\x00\x00\x00\x41arg"
        jar(self.new,f)
        r=mod.validate(self.base,self.new,{"new/source/Proof.class"},{"fabric.mod.json"})
        self.assertEqual(r["verdict"],"PASS_STRUCTURAL_ONLY")
        self.assertEqual(r["changes"]["added"],["new/source/Proof.class"])

    def test_unreviewed_added_class_blocks(self):
        f=self.new_files();f["new/source/Proof.class"]=b"\xca\xfe\xba\xbe\x00\x00\x00\x41arg"
        jar(self.new,f)
        r=mod.validate(self.base,self.new,set(),{"fabric.mod.json"})
        self.assertEqual(r["verdict"],"FAIL")
        self.assertTrue(any("UNREVIEWED_ADDITION" in x for x in r["errors"]))

    def test_missing_old_class_blocks_even_with_new_version(self):
        f=self.new_files();f.pop("a/Core.class");jar(self.new,f)
        r=mod.validate(self.base,self.new,set(),{"fabric.mod.json"})
        self.assertTrue(any("REMOVED" in x for x in r["errors"]))

    def test_nested_helper_must_stay_exact_even_if_allowlisted(self):
        f=self.new_files();f["META-INF/lfb/desktop-helper.jar"]=b"different";jar(self.new,f)
        r=mod.validate(self.base,self.new,set(),{"fabric.mod.json","META-INF/lfb/desktop-helper.jar"})
        self.assertTrue(any("BUNDLED_HELPER_OR_DEPENDENCY_CHANGED" in x for x in r["errors"]))

    def test_declared_mixin_metadata_cannot_be_dropped(self):
        f=self.new_files();meta=json.loads(f["fabric.mod.json"]);meta["mixins"]=[]
        f["fabric.mod.json"]=json.dumps(meta).encode();jar(self.new,f)
        r=mod.validate(self.base,self.new,set(),{"fabric.mod.json"})
        self.assertTrue(any("FABRIC_METADATA_CHANGED: mixins" in x for x in r["errors"]))

    def test_same_version_with_modified_binary_is_blocked(self):
        f=copy.deepcopy(BASE);f["a/Core.class"]=b"\xca\xfe\xba\xbe\x00\x00\x00\x41new";jar(self.new,f)
        r=mod.validate(self.base,self.new,set(),{"a/Core.class"})
        self.assertTrue(any("VERSION_NOT_BUMPED" in x for x in r["errors"]))

    def test_test_classes_cannot_ship(self):
        f=self.new_files();f["org/junit/Test.class"]=b"\xca\xfe\xba\xbe\x00\x00\x00\x41init";jar(self.new,f)
        r=mod.validate(self.base,self.new,{"org/junit/Test.class"},{"fabric.mod.json"})
        self.assertTrue(any("TEST_CLASS_OR_RESOURCE_PACKAGED" in x for x in r["errors"]))

    def test_duplicate_zip_entry_rejected(self):
        with ZipFile(self.new,"w",compression=ZIP_DEFLATED) as z:
            for key,val in BASE.items():z.writestr(key,val)
            import warnings
            with warnings.catch_warnings():warnings.simplefilter("ignore");z.writestr("a/Core.class",BASE["a/Core.class"])
        with self.assertRaisesRegex(ValueError,"Duplicate ZIP entry"):
            mod.validate(self.base,self.new,set(),set())

    def test_wrong_base_hash_rejected(self):
        jar(self.new,self.new_files())
        with self.assertRaisesRegex(ValueError,"Baseline SHA-256"):
            mod.validate(self.base,self.new,set(),{"fabric.mod.json"},"0"*64)

    def test_source_jar_cannot_be_its_own_candidate(self):
        with self.assertRaisesRegex(ValueError,"separate files"):
            mod.validate(self.base,self.base,set(),set())


if __name__ == "__main__": unittest.main(verbosity=2)
