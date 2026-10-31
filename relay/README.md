# Time-ikiz relay: test gate

The internet relay is experimental. It is **not production-ready** and must not be exposed publicly without rate limiting, authenticated registration, monitoring, TLS and independent security review.

Mutating database transactions must commit **before** returning HTTP success, otherwise subsequent requests can observe stale contact permissions. Test the delete -> re-add -> send sequence.

Security gates remaining: cryptographically authenticated sender messages; verified recipient keys; robust account/device recovery; Android background delivery; release build and integration tests.
