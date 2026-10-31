"""Minimal authenticated HTTPS relay backend. Run behind a TLS reverse proxy."""
import base64
import hmac
import os
import re
import sqlite3
import threading
from flask import Flask, request, jsonify
from signatures import verify

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 128 * 1024
DB = os.environ.get("TIME_IKIZ_DB", "time_ikiz.sqlite3")
LOCK = threading.RLock()
ID = re.compile(r"^[A-Za-z0-9_-]{8,64}$")
MSG = re.compile(r"^[A-Za-z0-9_-]{1,128}$")

def connection():
    db = sqlite3.connect(DB, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys=ON")
    return db

def init_db():
    with LOCK, connection() as db:
        db.executescript("""
        CREATE TABLE IF NOT EXISTS users(
          id TEXT PRIMARY KEY, token TEXT NOT NULL,
          public_key TEXT NOT NULL, signing_public_key TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS contacts(
          owner TEXT NOT NULL, peer TEXT NOT NULL,
          PRIMARY KEY(owner,peer),
          FOREIGN KEY(owner) REFERENCES users(id),
          FOREIGN KEY(peer) REFERENCES users(id));
        CREATE TABLE IF NOT EXISTS messages(
          id TEXT PRIMARY KEY, sender TEXT NOT NULL, recipient TEXT NOT NULL,
          ciphertext TEXT NOT NULL, signature TEXT NOT NULL,
          created_at TEXT DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY(sender) REFERENCES users(id),
          FOREIGN KEY(recipient) REFERENCES users(id));
        """)

def error(message, code):
    return jsonify(error=message), code

def identity(db):
    auth=request.headers.get("Authorization","")
    if not auth.startswith("Bearer "):
        return None
    token=auth[7:]
    if not token or len(token)>256:
        return None
    for row in db.execute("SELECT id,token FROM users"):
        if hmac.compare_digest(row["token"],token):
            return row["id"]
    return None

def mutual(db, a, b):
    return db.execute("""SELECT 1 FROM contacts x JOIN contacts y
       ON x.owner=y.peer AND x.peer=y.owner
       WHERE x.owner=? AND x.peer=?""",(a,b)).fetchone() is not None

@app.post("/register")
def register():
    data=request.get_json(silent=True) or {}
    user=data.get("id","")
    token=data.get("token","")
    try:
        if not isinstance(user,str) or not isinstance(token,str) or not ID.fullmatch(user) or len(token)>256 or len(base64.b64decode(token,validate=True))<32:
            return error("Invalid registration",400)
        pub=base64.b64decode(data["public_key"],validate=True)
        signing=base64.b64decode(data["signing_public_key"],validate=True)
        if len(signing)!=32 or len(pub)>2048 or len(pub)<256:
            return error("Invalid keys",400)
        with LOCK,connection() as db:
            db.execute("INSERT INTO users(id,token,public_key,signing_public_key) VALUES(?,?,?,?)",
                       (user,token,data["public_key"],data["signing_public_key"]))
        return jsonify(ok=True)
    except sqlite3.IntegrityError:
        return error("Identity already registered",409)
    except (ValueError,KeyError,TypeError):
        return error("Invalid registration",400)

@app.post("/contact/add")
def add_contact():
    data=request.get_json(silent=True) or {}
    peer=data.get("peer","")
    with LOCK,connection() as db:
        user=identity(db)
        if not user:return error("Unauthorized",401)
        if not isinstance(peer,str) or not ID.fullmatch(peer) or peer==user:return error("Invalid peer",400)
        if not db.execute("SELECT 1 FROM users WHERE id=?",(peer,)).fetchone():return error("Unknown peer",404)
        db.execute("INSERT OR IGNORE INTO contacts(owner,peer) VALUES(?,?)",(user,peer))
        return jsonify(ok=True,mutual=mutual(db,user,peer))

@app.post("/contact/delete")
def delete_contact():
    data=request.get_json(silent=True) or {}
    peer=data.get("peer","")
    with LOCK,connection() as db:
        user=identity(db)
        if not user:return error("Unauthorized",401)
        db.execute("DELETE FROM contacts WHERE owner=? AND peer=?",(user,peer))
        db.execute("DELETE FROM messages WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?)",
                   (user,peer,peer,user))
        return jsonify(ok=True)

@app.get("/key/<peer>")
@app.get("/signing-key/<peer>")
def get_key(peer):
    with connection() as db:
        user=identity(db)
        if not user:return error("Unauthorized",401)
        if not ID.fullmatch(peer) or not mutual(db,user,peer):return error("Mutual contact required",403)
        row=db.execute("SELECT public_key,signing_public_key FROM users WHERE id=?",(peer,)).fetchone()
        if row is None:return error("Unknown peer",404)
        if request.path.startswith("/signing-key/"):
            return jsonify(signing_public_key=row["signing_public_key"])
        return jsonify(public_key=row["public_key"])

@app.post("/send")
def send():
    data=request.get_json(silent=True) or {}
    recipient=data.get("recipient","")
    msg=data.get("message_id","")
    ciphertext=data.get("ciphertext","")
    signature=data.get("signature","")
    with LOCK,connection() as db:
        user=identity(db)
        if not user:return error("Unauthorized",401)
        if not isinstance(recipient,str) or not isinstance(msg,str) or not ID.fullmatch(recipient) or not MSG.fullmatch(msg):return error("Invalid envelope",400)
        if not isinstance(ciphertext,str) or len(ciphertext)>100000 or not isinstance(signature,str) or len(signature)>256:return error("Invalid payload",400)
        if not mutual(db,user,recipient):return error("Mutual contact required",403)
        key=db.execute("SELECT signing_public_key FROM users WHERE id=?",(user,)).fetchone()["signing_public_key"]
        if not verify(key,signature,user,recipient,msg,ciphertext):return error("Invalid signature",403)
        try:
            db.execute("INSERT INTO messages(id,sender,recipient,ciphertext,signature) VALUES(?,?,?,?,?)",
                       (msg,user,recipient,ciphertext,signature))
        except sqlite3.IntegrityError:
            return error("Duplicate message ID",409)
        return jsonify(ok=True)

@app.get("/inbox")
def inbox():
    with connection() as db:
        user=identity(db)
        if not user:return error("Unauthorized",401)
        rows=db.execute("""SELECT m.id,m.sender,m.ciphertext,m.signature FROM messages m
            WHERE m.recipient=? AND EXISTS(SELECT 1 FROM contacts a JOIN contacts b
            ON a.owner=b.peer AND a.peer=b.owner WHERE a.owner=m.sender AND a.peer=m.recipient)
            ORDER BY m.created_at,m.id LIMIT 100""",(user,)).fetchall()
        return jsonify(messages=[dict(message_id=r["id"],sender=r["sender"],
                                      ciphertext=r["ciphertext"],signature=r["signature"]) for r in rows])

@app.post("/ack")
def ack():
    data=request.get_json(silent=True) or {}
    msg=data.get("message_id","")
    with LOCK,connection() as db:
        user=identity(db)
        if not user:return error("Unauthorized",401)
        if not isinstance(msg,str) or not MSG.fullmatch(msg):return error("Invalid message ID",400)
        db.execute("DELETE FROM messages WHERE id=? AND recipient=?",(msg,user))
        return jsonify(ok=True)

init_db()
if __name__=="__main__":
    raise SystemExit("Use a production WSGI server behind HTTPS; never expose Flask development server.")
