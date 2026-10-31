"""Protocol compatibility regression tests for the Android client's canonical envelope."""
import base64
import json
import unittest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
from signatures import canonical, verify

class ProtocolCompatibilityTests(unittest.TestCase):
    def test_android_canonical_envelope(self):
        sender="alice0001"
        recipient="bob00002"
        message_id="6f3b0f48-3333-4444-8888-999999999999"
        ciphertext='{"v":1,"key":"AQID","iv":"BAUG","data":"BwgJ"}'
        # Android JSONObject.quote for these ASCII inputs, in the exact key order
        # used by InternetMessaging.canonical.
        expected=('{"ciphertext":'+json.dumps(ciphertext,separators=(",",":"))+
                  ',"message_id":'+json.dumps(message_id)+
                  ',"recipient":'+json.dumps(recipient)+
                  ',"sender":'+json.dumps(sender)+',"v":1}').encode("utf-8")
        self.assertEqual(canonical(sender,recipient,message_id,ciphertext),
                         b"TIME-IKIZ-MSG-V1\x00"+expected)

    def test_known_seed_sign_and_verify(self):
        seed=bytes(range(32))
        private=Ed25519PrivateKey.from_private_bytes(seed)
        public=base64.b64encode(private.public_key().public_bytes(
            serialization.Encoding.Raw,serialization.PublicFormat.Raw)).decode()
        args=("alice0001","bob00002","message_01",'{"data":"cipher"}')
        signature=base64.b64encode(private.sign(canonical(*args))).decode()
        self.assertTrue(verify(public,signature,*args))
        self.assertFalse(verify(public,signature,args[0],args[1],args[2],"tampered"))

if __name__=="__main__":
    unittest.main()
