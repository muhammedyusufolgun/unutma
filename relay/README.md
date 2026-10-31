# Time-ikiz relay (pilot)

Requires Python 3.11+, HTTPS reverse proxy, and a persistent writable SQLite volume.

Install: `python3 -m pip install -r requirements.txt gunicorn`

Run from `relay/`: `TIME_IKIZ_DB=/var/lib/time-ikiz/messages.sqlite3 gunicorn --bind 127.0.0.1:8080 server:app`. Configure a reverse proxy with a valid HTTPS certificate. **Never publish port 8080 directly.**

API: POST /register (id, token, public_key, signing_public_key), POST /contact/add, POST /contact/delete, GET /key/<peer>, GET /signing-key/<peer>, POST /send, GET /inbox, POST /ack. Except registration, requests require `Authorization: Bearer <token>`. Both users must add one another. The client must verify encryption and signing key fingerprints through an independent channel.

Pilot limitations: registration is not identity-proofed; no rate limiting, abuse prevention, credential rotation, key revocation, database migrations, or production monitoring. The relay stores ciphertext, identifiers, and timestamps, not plaintext. Do not deploy publicly without addressing these issues.

Mutating transactions must commit before returning success. Verify the delete -> re-add -> send sequence.
