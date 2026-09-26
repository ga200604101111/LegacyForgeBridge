"""Minimal source fixtures test preflight and preservation, NOT the cumulative Git restoration chain."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('client_delta', ROOT/'apply.py')
delta=importlib.util.module_from_spec(spec);spec.loader.exec_module(delta)
P=delta.PACKAGE

class ApplyTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)/'restored';self.root.mkdir()
        self.files={
            P+'BuildInfo.java': 'class BuildInfo { String revision="'+delta.BASE_REVISION+'"; }\n',
            P+'mixin/client/LegacyLivingBehaviorMixin.java': 'void callback(){LegacyBehaviorRuntime.jump((LivingEntity)(Object)this);}\n// FALL_UNCHANGED\n',
            P+'behavior/LegacyBehaviorClient.java': 'void init(){LegacyBehaviorRuntime.setLocalPlayer(e->e==Minecraft.getInstance().player);}\n',
            'src/main/resources/legacyforgebridge.client.mixins.json':json.dumps({'required':True,'package':'dev.yinghuang.legacyforgebridge.mixin.client','client':['EarlierBambooMixin','LegacyLivingBehaviorMixin'],'injectors':{'defaultRequire':1}}),
            P+'convert/LegacyRemoteMenuAnalyzer.java':'KEEP_EXISTING_BAMBOO_TEMPLATE_GATES\n',
            'checkpoints/earlier.txt':'KEEP_EARLIER_CHECKPOINTS\n',
        }
        for n,s in self.files.items():
            f=self.root/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(s)
    def tearDown(self):self.tmp.cleanup()
    def snapshot(self):return {str(f.relative_to(self.root)):f.read_bytes() for f in self.root.rglob('*') if f.is_file()}
    def test_preflight_no_writes(self):
        before=self.snapshot();self.assertEqual(len(delta.apply(self.root,True)),6);self.assertEqual(before,self.snapshot())
    def test_applied_preserves_prior_content(self):
        delta.apply(self.root)
        self.assertEqual((self.root/(P+'BuildInfo.java')).read_text(),self.files[P+'BuildInfo.java'])
        self.assertEqual((self.root/(P+'convert/LegacyRemoteMenuAnalyzer.java')).read_text(),'KEEP_EXISTING_BAMBOO_TEMPLATE_GATES\n')
        self.assertIn('FALL_UNCHANGED',(self.root/(P+'mixin/client/LegacyLivingBehaviorMixin.java')).read_text())
        c=json.loads((self.root/'src/main/resources/legacyforgebridge.client.mixins.json').read_text())
        self.assertEqual(c['client'],['EarlierBambooMixin','LegacyLivingBehaviorMixin','LegacyJumpMotionPacketMixin'])
        self.assertEqual(c['injectors'],{'defaultRequire':1})
    def test_old_root_rejected(self):
        (self.root/(P+'BuildInfo.java')).write_text('rev188');before=self.snapshot()
        with self.assertRaises(ValueError):delta.apply(self.root)
        self.assertEqual(before,self.snapshot())
    def test_missing_anchor_no_changes(self):
        (self.root/(P+'behavior/LegacyBehaviorClient.java')).write_text('changed source');before=self.snapshot()
        with self.assertRaises(ValueError):delta.apply(self.root)
        self.assertEqual(before,self.snapshot())
    def test_duplicate_anchor_rejected(self):
        n=P+'mixin/client/LegacyLivingBehaviorMixin.java';(self.root/n).write_text(self.files[n]*2)
        with self.assertRaises(ValueError):delta.apply(self.root)
    def test_reapply_rejected(self):
        delta.apply(self.root);before=self.snapshot()
        with self.assertRaises(FileExistsError):delta.apply(self.root)
        self.assertEqual(before,self.snapshot())
    def test_no_baseline_mixin_rejected(self):
        n='src/main/resources/legacyforgebridge.client.mixins.json';c=json.loads((self.root/n).read_text());c['client']=[]
        (self.root/n).write_text(json.dumps(c))
        with self.assertRaises(ValueError):delta.apply(self.root)
    def test_path_escape_rejected(self):
        with self.assertRaises(ValueError):delta.safe(self.root,'../outside')
        with self.assertRaises(ValueError):delta.safe(self.root,'/outside')
    def test_symlink_escape_rejected(self):
        p=self.root/(P+'behavior/LegacyJumpEchoWindow.java');outside=Path(self.tmp.name)/'outside';outside.write_text('DO NOT TOUCH')
        p.symlink_to(outside)
        with self.assertRaises(ValueError):delta.apply(self.root)
        self.assertEqual(outside.read_text(),'DO NOT TOUCH')
    def test_mid_write_rollback(self):
        before=self.snapshot();original=delta.os.replace;calls=[]
        def throwing(*args):
            calls.append(args)
            if len(calls)==3:raise OSError('injected failure')
            return original(*args)
        with patch.object(delta.os,'replace',throwing):
            with self.assertRaises(OSError):delta.apply(self.root)
        self.assertEqual(before,self.snapshot())

if __name__=='__main__':unittest.main(verbosity=2)
