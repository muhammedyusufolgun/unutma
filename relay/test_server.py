import base64
import os
import tempfile
import unittest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
from signatures import canonical
from server import app, init_db

class RelayTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory()
  import server
  server.DB=os.path.join(self.tmp.name,"test.sqlite3")
  init_db()
  self.client=app.test_client()
  self.signers={}
 def tearDown(self):
  self.tmp.cleanup()
 def register(self,user):
  key=Ed25519PrivateKey.generate()
  self.signers[user]=key
  signing=base64.b64encode(key.public_key().public_bytes(
   serialization.Encoding.Raw,serialization.PublicFormat.Raw)).decode()
  payload=dict(id=user,token=base64.b64encode((user+"X"*40).encode()).decode(),
               public_key=base64.b64encode(b"P"*300).decode(),signing_public_key=signing)
  r=self.client.post("/register",json=payload)
  self.assertEqual(r.status_code,200,r.json)
  return {"Authorization":"Bearer "+payload["token"]}
 def test_mutual_consent_signature_and_delete(self):
  alice=self.register("alice0001");bob=self.register("bob00002")
  self.assertEqual(self.client.post("/contact/add",headers=alice,json={"peer":"bob00002"}).status_code,200)
  self.assertEqual(self.client.get("/key/alice0001",headers=bob).status_code,403)
  self.assertEqual(self.client.post("/contact/add",headers=bob,json={"peer":"alice0001"}).status_code,200)
  msg=dict(recipient="bob00002",message_id="msg1",ciphertext="encrypted")
  sig=self.signers["alice0001"].sign(canonical("alice0001","bob00002","msg1","encrypted"))
  msg["signature"]=base64.b64encode(sig).decode()
  self.assertEqual(self.client.post("/send",headers=alice,json=msg).status_code,200)
  self.assertEqual(len(self.client.get("/inbox",headers=bob).json["messages"]),1)
  tampered=dict(msg,ciphertext="changed",message_id="msg2")
  self.assertEqual(self.client.post("/send",headers=alice,json=tampered).status_code,403)
  self.assertEqual(self.client.post("/contact/delete",headers=bob,json={"peer":"alice0001"}).status_code,200)
  self.assertEqual(len(self.client.get("/inbox",headers=bob).json["messages"]),0)
  self.assertEqual(self.client.post("/send",headers=alice,json=msg).status_code,403)
 def test_ack_is_recipient_scoped(self):
  alice=self.register("alice0001");bob=self.register("bob00002")
  self.client.post("/contact/add",headers=alice,json={"peer":"bob00002"})
  self.client.post("/contact/add",headers=bob,json={"peer":"alice0001"})
  msg=dict(recipient="bob00002",message_id="msg1",ciphertext="encrypted")
  msg["signature"]=base64.b64encode(self.signers["alice0001"].sign(
   canonical("alice0001","bob00002","msg1","encrypted"))).decode()
  self.client.post("/send",headers=alice,json=msg)
  self.client.post("/ack",headers=alice,json={"message_id":"msg1"})
  self.assertEqual(len(self.client.get("/inbox",headers=bob).json["messages"]),1)
  self.client.post("/ack",headers=bob,json={"message_id":"msg1"})
  self.assertEqual(len(self.client.get("/inbox",headers=bob).json["messages"]),0)

 def test_malformed_requests_rejected(self):
  alice=self.register("alice0001")
  self.assertEqual(self.client.post("/register",json={"id":None,"token":False}).status_code,400)
  self.assertEqual(self.client.post("/contact/add",headers=alice,json={"peer":None}).status_code,400)
  self.assertEqual(self.client.post("/send",headers=alice,json={"recipient":None,"message_id":12}).status_code,400)
  self.assertEqual(self.client.post("/ack",headers=alice,json={"message_id":None}).status_code,400)
  oversized="X"*(129*1024)
  self.assertEqual(self.client.post("/send",headers=alice,data=oversized,content_type="application/json").status_code,413)

if __name__=="__main__":
 unittest.main()
