"""Ed25519 signature verification for Time-ikiz relay envelopes."""
import base64
import json
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

DOMAIN = b"TIME-IKIZ-MSG-V1\x00"

def canonical(sender: str, recipient: str, message_id: str, ciphertext: str) -> bytes:
    obj = {"v": 1, "sender": sender, "recipient": recipient,
           "message_id": message_id, "ciphertext": ciphertext}
    return DOMAIN + json.dumps(obj, sort_keys=True, separators=(",", ":"),
                               ensure_ascii=True).encode("ascii")

def verify(key_b64: str, signature_b64: str, sender: str,
           recipient: str, message_id: str, ciphertext: str) -> bool:
    try:
        key = base64.b64decode(key_b64, validate=True)
        signature = base64.b64decode(signature_b64, validate=True)
        if len(key) != 32 or len(signature) != 64:
            return False
        Ed25519PublicKey.from_public_bytes(key).verify(
            signature, canonical(sender, recipient, message_id, ciphertext))
        return True
    except (ValueError, InvalidSignature, TypeError, UnicodeEncodeError):
        return False
