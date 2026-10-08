"""Structural unit regressions for Gson method signature mismatches."""
import struct
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile
from verify_gson_abi import audit, refs


def java_class_using(owner: str, method: str, descriptor: str) -> bytes:
    """Valid minimal JVM class with one referenced Gson Methodref in the constant pool."""
    pool=[]
    def utf(s: str):
        data=s.encode('utf-8')
        pool.append(b'\x01'+struct.pack('>H',len(data))+data)
        return len(pool)
    def cls(ref: int):
        pool.append(b'\x07'+struct.pack('>H',ref));return len(pool)
    n_fake=utf('synthetic/SignatureProbe');c_fake=cls(n_fake)
    n_super=utf('java/lang/Object');c_super=cls(n_super)
    n_owner=utf(owner);c_owner=cls(n_owner)
    n_method=utf(method);n_descriptor=utf(descriptor)
    pool.append(b'\x0c'+struct.pack('>HH',n_method,n_descriptor));name_type=len(pool)
    pool.append(b'\x0a'+struct.pack('>HH',c_owner,name_type))
    return (b'\xca\xfe\xba\xbe'+struct.pack('>HHH',0,65,len(pool)+1)
            +b''.join(pool)+struct.pack('>HHHHHHH',0x0021,c_fake,c_super,0,0,0,0))


class GsonLinkageGuardTests(unittest.TestCase):
    def setUp(self):
        self._temp=tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.path=Path(self._temp.name)/'test.jar'
    def check(self, descriptor):
        with ZipFile(self.path,'w') as z:
            z.writestr('synthetic/SignatureProbe.class',java_class_using(
                'com/google/gson/JsonObject','addProperty',descriptor))
        return audit(self.path)
    def test_int_primitive_fails(self):
        self.assertEqual('FAIL_UNSUPPORTED_GSON_METHOD',self.check('(Ljava/lang/String;I)V')['verdict'])
    def test_float_primitive_fails(self):
        self.assertEqual('FAIL_UNSUPPORTED_GSON_METHOD',self.check('(Ljava/lang/String;F)V')['verdict'])
    def test_boolean_primitive_fails(self):
        self.assertEqual('FAIL_UNSUPPORTED_GSON_METHOD',self.check('(Ljava/lang/String;Z)V')['verdict'])
    def test_number_boxed_passes(self):
        self.assertEqual('PASS_STATIC_ABI',self.check('(Ljava/lang/String;Ljava/lang/Number;)V')['verdict'])
    def test_boolean_boxed_passes(self):
        self.assertEqual('PASS_STATIC_ABI',self.check('(Ljava/lang/String;Ljava/lang/Boolean;)V')['verdict'])
    def test_string_passes(self):
        self.assertEqual('PASS_STATIC_ABI',self.check('(Ljava/lang/String;Ljava/lang/String;)V')['verdict'])
    def test_incorrect_array_add_rejected(self):
        with ZipFile(self.path,'w') as z:
            z.writestr('synthetic/SignatureProbe.class',java_class_using(
                'com/google/gson/JsonArray','add','(F)V'))
        self.assertEqual('FAIL_UNSUPPORTED_GSON_METHOD',audit(self.path)['verdict'])
    def test_parser_includes_class_method_name(self):
        code=java_class_using('com/google/gson/JsonObject','addProperty','(Ljava/lang/String;Z)V')
        self.assertIn(('com/google/gson/JsonObject','addProperty','(Ljava/lang/String;Z)V'),refs(code))
    def test_invalid_class_rejected(self):
        with ZipFile(self.path,'w') as z:z.writestr('broken.class',b'garbage')
        with self.assertRaisesRegex(ValueError,'Not a Java'):audit(self.path)

if __name__=='__main__':unittest.main(verbosity=2)
