# SecureLink Signaling Relay

This is a small, payload-blind relay for WebRTC offer/answer envelopes. It does not carry media, decrypt signals, create accounts, or retain messages after a successful poll. Each queue entry expires after five minutes.

## Deploy

Run locally with Node 20 or newer:

```bash
npm test
npm start
```

Or build an image:

```bash
docker build -t securelink-relay .
docker run --rm -p 8080:8080 securelink-relay
```

Deploy it behind HTTPS. This reference implementation is in-memory and single-instance only; production deployment must use a private TTL-backed store such as Redis, enforce rate limits at the edge, restrict CORS to the Android client (or omit it), and monitor abuse without logging payloads or tokens.

## Protocol

The receiving device registers its own inbox. The `Authorization: Bearer <token>` value is a high-entropy pairing secret shared with contacts only through the pairing flow. Tokens must never be written to logs, analytics, crash reports, or source control.

```text
POST /v1/devices/register
Authorization: Bearer <recipient-inbox-token>
{ "deviceId": "device-..." }

POST /v1/signals
Authorization: Bearer <recipient-inbox-token>
{ "recipientDeviceId": "device-...", "senderDeviceId": "device-...", "payload": "sl-contact-..." }

GET /v1/signals?deviceId=device-...
Authorization: Bearer <recipient-inbox-token>
```

The Android app must validate each fetched `sl-contact-` payload locally before applying it. The relay's authorization only limits who can enqueue to a paired device; it is not a replacement for public-key authentication or the envelope expiry check.
