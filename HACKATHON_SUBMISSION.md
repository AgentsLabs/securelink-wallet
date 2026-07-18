# SecureLink Wallet

## Tagline

A local-first private communication wallet for the people and records you trust most.

## Category

Apps for your life

## Project Story

Sensitive everyday work is fragmented across chat apps, cloud drives, password managers, and document folders. SecureLink Wallet brings those workflows into one Android app: trusted contacts, local chat, direct audio/video calling, a document wallet, and a protected credential vault.

The app is designed around local control. Contact, message, document, and credential fields are encrypted at rest with Android Keystore-backed AES-GCM encryption. Credential values remain masked until the owner completes biometric or device-credential authentication.

For calling, two SecureLink installs exchange WebRTC offer and answer payloads directly through a channel the users choose. There is no account system or application signaling backend. Once the payloads are exchanged, WebRTC negotiates peer connectivity with STUN route discovery and establishes DTLS-SRTP protected audio/video media.

## What Works

- Trusted contact list with selection and creation.
- Local chat and document wallet records stored with encrypted fields.
- Android Keystore-backed generated passwords gated by biometric or device credential authentication.
- Camera and microphone runtime permissions.
- Direct WebRTC audio/video calling with local and remote rendering.
- Manual offer/answer exchange between two Android installs with no signaling account or backend.
- R8-optimized release assembly with WebRTC retention rules.

## Demo Flow

1. Open Chats, select a trusted contact, add a contact, and send a message.
2. Open Passwords and unlock the credential vault using biometrics or device credential.
3. Generate a credential and show that the password returns to a masked state when the app backgrounds.
4. On device A, open Calls, grant camera/microphone permission, and create an invite.
5. Send the copied invite to device B through a trusted channel.
6. On device B, apply the invite, copy the generated answer, and return it to device A.
7. Apply the answer on device A and show both phones in the live encrypted call state.
8. Open Wallet and show the locally stored document record.

## Built With Codex

Codex was used to plan the MVP, implement the Compose interface and encrypted storage flows, integrate WebRTC calling and manual signaling, test the Android build on emulators, and maintain the project documentation.

## Submission Links

- Repository: `REPLACE_WITH_GITHUB_REPOSITORY_URL`
- Demo video: `REPLACE_WITH_PUBLIC_YOUTUBE_URL`
- Devpost submission: `REPLACE_WITH_DEVPOST_URL`

## Production Notes

The demo uses public STUN route discovery. A public production release should add a project-operated TURN service with short-lived credentials for reliable connectivity on restrictive cellular and corporate networks, and must be signed with the project release key before distribution.
