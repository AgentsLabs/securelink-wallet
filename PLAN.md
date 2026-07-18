# SecureLink Wallet Plan

## Current Status

- [x] Create Kotlin-native Android project scaffold.
- [x] Add Compose MVP with Chats, Calls, Wallet, and Passwords tabs.
- [x] Add on-device SQLite persistence for demo records.
- [x] Add Android Keystore AES-GCM vault helper.
- [x] Encrypt newly generated credential passwords before database storage.
- [x] Add password generator and manual P2P invite logic.
- [x] Add unit tests for password generation and invite parsing.
- [x] Beautify the Compose app shell with richer chat, contacts, wallet, password, and call screens.
- [x] Add contact selection and trusted-contact creation flow.
- [x] Add camera/microphone manifest and runtime permission flow.
- [x] Add manual no-account WebRTC offer/answer signaling between two Android installs.
- [x] Wire real WebRTC audio/video media streams, ICE candidates, and local/remote video rendering.
- [x] Verify a full offer/answer handshake and live encrypted media state across two Android emulators.
- [x] Enable R8/resource shrinking with WebRTC retention rules and verify the release assembly.
- [ ] Add project-operated TURN credentials for reliable calling behind restrictive NATs.
- [x] Encrypt persisted contact, message, document, and credential fields with Android Keystore AES-GCM.
- [x] Add biometric/device-credential vault unlock before showing credentials.
- [ ] Add real document picker import and encrypted file storage.
- [x] Push the code to the public GitHub repository.
- [x] Prepare ready-to-paste hackathon submission copy and demo flow.
- [x] Prepare a 72-second narrated demo MP4 and upload metadata.
- [ ] Upload the demo video publicly to YouTube.
- [ ] Submit to OpenAI Build Week on Devpost.

## MVP Scope

The hackathon MVP demonstrates the product direction with a buildable Android app:

- WhatsApp-like local chat screen with trusted contact selection.
- Direct WebRTC call setup with camera/microphone permission handling and manually exchanged invite/answer payloads.
- Personal document wallet list with local persistence.
- Credential manager with generated strong passwords.
- Clear README instructions and Devpost demo script.

## Architecture

- UI: Kotlin + Jetpack Compose + Material 3.
- Local database: Android `SQLiteOpenHelper` with field-level AES-GCM values, stored on device.
- Security: Android Keystore AES-GCM helper and biometric/device-credential vault gate.
- P2P: native WebRTC audio/video with direct manual signaling and public STUN route discovery. Production reliability requires project-operated TURN.

## Devpost Checklist

- Category: Apps for your life.
- Repository: public GitHub repo.
- Description: local-first private communication and personal data wallet.
- Demo video: under 3 minutes, public YouTube link, includes audio explaining Codex/GPT-5.6 usage.
- README: includes setup, features, demo script, and collaboration notes.
