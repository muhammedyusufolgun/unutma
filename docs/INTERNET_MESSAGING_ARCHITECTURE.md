# Unutma internet messaging architecture (implementation requirements)

Status: design specification, NOT a working or security-audited APK.

- Preserve existing local alarms, widget, countdown, Turkish TTS and local data.
- Do not require QR pairing. Users add each other by a unique Unutma ID. Sending is allowed only while **both** sides have the other in their contact list; deletion immediately blocks new server deliveries, and re-adding restores permission.
- Avoid phone-number-based identity claims without verification. Use authenticated, rate-limited account/device registration and public-key directory with key-change warnings. No anonymous ID enumeration.
- Encrypt reminder text and schedule on the sender device with an audited end-to-end encryption protocol. Relay only ciphertext, destination routing metadata, and minimum delivery state. Authenticate sender identity and protect against replay, substitution and key rotation attacks.
- Internet delivery to offline recipients needs a reachable relay and queue. The relay may be self-hosted; no Firebase. Direct P2P is optional and cannot guarantee delivery behind NAT/mobile background restrictions.
- Sync incoming ciphertext when the app runs and via an Android-compatible wake-up strategy; without a third-party push service or foreground connection, immediate background delivery cannot be guaranteed.
- Once received and decrypted, store in existing local Store and schedule using AlarmManager, with message-ID deduplication. Permission removal should block subsequent messages, not erase already-saved alarms unless explicitly requested.
- Require TLS, token rotation, server-side authorization checks for **every** send and fetch, rate limits, retention limits, and device-to-device tests before release.

Release gate: compile signed Android APK; unit/integration tests for mutual permission, revoke/re-add, ciphertext-only relay, replay protection, key changes, offline delivery and local TTS. No security verification has yet been performed.
