# SecureLink Wallet Plan

## Current Status

- [x] Create Kotlin-native Android project scaffold.
- [x] Add Compose MVP with Chats, Calls, Wallet, and Passwords tabs.
- [x] Add on-device SQLite persistence for demo records.
- [x] Add Android Keystore AES-GCM vault helper.
- [x] Encrypt newly generated credential passwords before database storage.
- [x] Add password generator and manual P2P invite logic.
- [x] Add unit tests for password generation and invite parsing.
- [ ] Wire real WebRTC audio/video media streams.
- [ ] Encrypt all persisted sensitive fields or migrate to SQLCipher/Room.
- [ ] Add biometric/PIN vault unlock before showing secrets.
- [ ] Add real document picker import and encrypted file storage.
- [ ] Record public YouTube demo under 3 minutes.
- [ ] Submit to OpenAI Build Week on Devpost.

## MVP Scope

The hackathon MVP demonstrates the product direction with a buildable Android app:

- WhatsApp-like local chat screen.
- Serverless manual peer invite flow for P2P setup.
- Personal document wallet list with local persistence.
- Credential manager with generated strong passwords.
- Clear README instructions and Devpost demo script.

## Architecture

- UI: Kotlin + Jetpack Compose + Material 3.
- Local database: Android `SQLiteOpenHelper`, stored on device.
- Security: Android Keystore AES-GCM helper for vault payload encryption.
- P2P: manual QR/text offer-answer architecture placeholder; WebRTC implementation is the next milestone.

## Devpost Checklist

- Category: Apps for your life.
- Repository: public GitHub repo.
- Description: local-first private communication and personal data wallet.
- Demo video: under 3 minutes, public YouTube link, includes audio explaining Codex/GPT-5.6 usage.
- README: includes setup, features, demo script, and collaboration notes.
