# SecureLink Wallet

SecureLink Wallet is a local-first Android MVP for OpenAI Build Week. It combines WhatsApp-style secure communication with a personal document wallet and credential manager.

## Features

- Local SQLite-backed chat, document wallet, and credential records.
- Android Keystore encryption helper for generated credential payloads.
- Password generator with unit tests.
- Manual peer invite flow for a serverless P2P demo path.
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

1. Show local chat and send a message.
2. Generate a peer invite from Calls.
3. Add a document wallet item.
4. Generate a credential.
5. Explain that the database is on device and P2P signaling is serverless/manual for MVP.

Codex/GPT-5.6 collaboration: Codex was used to plan the MVP, scaffold the Android project, implement the first UI/data/security slices, and maintain `PLAN.md`.
