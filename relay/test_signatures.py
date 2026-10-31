import base64
import unittest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
from signatures import canonical, verify

class SignatureTests(unittest.TestCase):
    def test_authentication_and_tampering(self):
        key = Ed25519PrivateKey.generate()
        public = base64.b64encode(key.public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw)).decode()
        args = ("sender123", "recipient123", "msg-001", "encrypted")
        signature = base64.b64encode(key.sign(canonical(*args))).decode()
        self.assertTrue(verify(public, signature, *args))
        for index in range(4):
            altered = list(args)
            altered[index] += "-modified"
            self.assertFalse(verify(public, signature, *altered))
        other = Ed25519PrivateKey.generate().public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        self.assertFalse(verify(base64.b64encode(other).decode(), signature, *args))
        self.assertFalse(verify(public, "not-base64!", *args))

if __name__ == "__main__":
    unittest.main()
