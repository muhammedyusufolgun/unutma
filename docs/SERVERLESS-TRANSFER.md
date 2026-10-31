# Time-ikiz: managed serverless cross-city transfer

## Architecture
Use Firebase Authentication (anonymous account per device), Cloud Firestore (encrypted mailbox), and Firebase Cloud Messaging (wake-up hint only). No privately operated server is required, but a Firebase project is mandatory. Do not put reminder text or decryption keys in FCM notifications.

## Pairing
Each installation generates device-local encryption and signing keys. Users mutually pair by exchanging and comparing key fingerprints out of band (QR codes or in person). Pairing approval is required once per device; no approval per reminder. Store an explicit allowlist of paired device IDs and pinned signing/encryption public-key fingerprints. Never trust phone number alone.

## Send
Generate random unique message ID. Encrypt reminder (time, repeat, text) to recipient key using authenticated encryption. Sign envelope containing sender, recipient, message ID and ciphertext. Write only encrypted envelope to recipient mailbox under authenticated Firebase identity. Apply Firebase Security Rules to restrict writes to authorized paired senders and reads to mailbox owner. Never allow public writes.

## Receive
Listen for mailbox updates while app is active. Use FCM data notification to trigger fetch when feasible, and WorkManager for eventual catch-up. Authenticate Firebase user, verify sender against local allowlist, verify pinned signing key and signature, decrypt, reject malformed payloads, atomically deduplicate and persist via Store.addIncomingAtomic, then Alarms.plan. Acknowledge after durable persistence. Use server timestamps, TTL, quotas, rate limits and audit events. Android may delay background execution and offline delivery.

## Security and deployment requirements
Firebase project and app-specific google-services.json, Firebase Authentication and Firestore enabled, appropriate Firestore rules deployed, FCM configured. Never commit secrets or service-account keys. Firebase config alone is not sufficient security: rules and pairing are essential. Do not request RECEIVE_SMS or READ_SMS. Existing SMS receiver is experimental and must be removed before release. Existing custom HTTPS relay is NOT Firebase and cannot be silently repurposed as Firebase without client implementation. No working Firebase APK can be claimed until project configuration, implementation, build and real-device tests succeed.

## Acceptance tests
Two distinct devices in different cities, reciprocal pairing, encrypted cross-delivery, no per-message prompt, duplicates rejected, invalid signature rejected, unpaired sender rejected, offline catch-up, reboot alarm restoration, background restrictions, correct alarm time, Play Protect installation assessment, and Android permission review.
