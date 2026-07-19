# SecureLink Wallet

SecureLink Wallet is a local-first Android MVP for OpenAI Build Week. It combines WhatsApp-style secure communication with a personal document wallet and credential manager.

## Features

- Polished Jetpack Compose shell for contacts, chat, calling, wallet, and passwords.
- Local SQLite-backed contact list, chat, document wallet, and credential records with AES-GCM encrypted fields.
- Android Keystore-backed encryption plus biometric/device-credential vault access for credentials.
- Password generator with unit tests.
- Runtime camera and microphone permission flow for audio/video calling.
- Native WebRTC audio/video tracks with local and remote video rendering.
- Contact-addressed, short-lived manual signaling: securely copy a WebRTC invite/answer between two SecureLink installs. Signals expire after five minutes and are rejected by devices other than the intended recipient.
- Pair contacts by exchanging an app-generated pairing code, then compare the displayed fingerprint through a trusted channel before calling.
- Per-contact call history for connected, failed, and ended calls.
- Configurable ICE server support. The demo uses public STUN route discovery; production callers should supply short-lived TURN credentials from their signaling service.
- Jetpack Compose UI with Chats, Calls, Wallet, and Passwords tabs.

## Run Locally

1. Open this repository in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on an emulator or device.

CLI build, if Java is configured:

```bash
./gradlew test
./gradlew assembleDebug
```

On macOS with Android Studio's bundled JDK:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test
```

## Hackathon Notes

Target category: Apps for your life.

The complete ready-to-paste submission is in [HACKATHON_SUBMISSION.md](HACKATHON_SUBMISSION.md).

Demo script:

1. In Chats, copy one device's pairing code, paste it on the other device with a contact name, then compare the displayed fingerprint through a trusted channel.
2. On phone A, open Calls, grant camera/microphone permissions, tap **Create invite**, then copy and send the signal through a trusted channel.
3. On phone B, paste the invite, tap **Apply signal**, then copy its generated answer back to phone A.
4. On phone A, paste and apply that answer to establish the encrypted WebRTC media connection.
5. Open Passwords, unlock the credential vault with biometrics or device credential, then generate a credential.
6. Add a document wallet item.
7. Explain that call signaling is exchanged directly between the two app installs, without an account or a signaling backend.

Codex/GPT-5.6 collaboration: Codex was used to plan the MVP, scaffold the Android project, implement the UI/data/security/calling slices, verify on emulator, and maintain `PLAN.md`.

## Hackathon Submission Draft

**Tagline:** A local-first private communication wallet for the people and records you trust most.

**Problem:** People routinely split sensitive life workflows across chat apps, cloud drives, password managers, and document folders. That creates privacy risk and makes urgent sharing harder.

**Solution:** SecureLink Wallet combines trusted contacts, private chat, direct WebRTC audio/video calling, a personal document wallet, and generated credentials in one Android app designed around on-device storage.

**What is working now:** The Android build runs on emulator, includes contact selection, AES-GCM encrypted local records, Android Keystore-backed generated credentials gated by biometrics or device credentials, and direct WebRTC audio/video calling. Two installs exchange compact offer/answer payloads manually; the app then captures camera/microphone media, negotiates ICE candidates, and renders local or remote video.

**Next hardening steps:** Add a project-operated TURN service for restrictive NATs, encrypt all persisted sensitive fields, add document import, and prepare a signed release build.

## Calling Notes

WebRTC encrypts call media in transit with DTLS-SRTP. The copied offer/answer contains connection metadata, so only share it through a trusted channel and discard it after the call. SecureLink addresses the signal to the selected contact device ID and expires it after five minutes, but this is not a replacement for cryptographic contact verification.

For production, add a contact public-key pairing flow and deliver the encrypted signal through an authenticated relay or trusted out-of-band channel. Supply project-operated TURN servers with short-lived credentials through `IceServerConfig` so calls work consistently on restrictive mobile and corporate networks. Do not embed permanent TURN credentials in the app.

## Optional Signaling Relay

The repository includes a deployable payload-blind relay in [`relay/README.md`](relay/README.md). It queues opaque contact-bound signaling envelopes, not audio/video media, and only accepts delivery when the sender presents the recipient's pairing-derived inbox token. The app encrypts its persistent inbox token with its Android Keystore-backed vault key. The relay is intentionally a small single-instance reference service; configure HTTPS, durable TTL storage, rate limits, and observability before production use.

To use it in the app, deploy the relay over HTTPS, enter its URL on the **Calls** tab on both devices, and save it to register each inbox. Pair devices using a current pairing code, then create an invite and choose **Send relay**. The receiving device chooses **Fetch relay**, reviews the queued signal, and explicitly applies it. Manual copy/paste remains available when no relay is configured.
