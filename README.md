# SecureLink Wallet

SecureLink Wallet is a local-first Android MVP for OpenAI Build Week. It combines WhatsApp-style secure communication with a personal document wallet and credential manager.

## Features

- Polished Jetpack Compose shell for contacts, chat, calling, wallet, and passwords.
- Local SQLite-backed contact list, chat, document wallet, and credential records.
- Android Keystore encryption for generated credential payloads plus biometric/device-credential vault access.
- Password generator with unit tests.
- Runtime camera and microphone permission flow for audio/video calling.
- Native WebRTC audio/video tracks with local and remote video rendering.
- No-account manual signaling: securely copy a WebRTC invite/answer between two SecureLink installs. A public STUN server is used only to discover viable peer routes, never to relay signaling or media.
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

Demo script:

1. Show trusted contacts, select a peer, and send a local chat demo message.
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

**What is working now:** The Android build runs on emulator, includes contact selection, local chat persistence, Android Keystore-backed generated credentials gated by biometrics or device credentials, and direct WebRTC audio/video calling. Two installs exchange compact offer/answer payloads manually; the app then captures camera/microphone media, negotiates ICE candidates, and renders local or remote video.

**Next hardening steps:** Add a project-operated TURN service for restrictive NATs, encrypt all persisted sensitive fields, add document import, and prepare a signed release build.

## Calling Notes

WebRTC encrypts call media in transit with DTLS-SRTP. The copied offer/answer contains connection metadata, so only share it through a trusted channel and discard it after the call. The included STUN configuration is appropriate for a demo; a production release should use a project-operated TURN service with short-lived credentials so calls work consistently on restrictive mobile and corporate networks.
