# SecureLink Wallet Demo Video

Target duration: 1 minute 40 seconds.

## Narration

SecureLink Wallet brings trusted contacts, private communication, personal documents, and credentials into one local-first Android app.

The Contacts screen lets you select or add a trusted peer and keep a simple local conversation close to the relationship it belongs to.

The credential vault is protected in two layers. Records are encrypted at rest with Android Keystore AES-GCM, and saved passwords stay masked until the owner unlocks the vault with a biometric or device credential.

For calling, each SecureLink install asks only for the camera and microphone permissions it needs. There is no account system and no application signaling backend. One device creates a WebRTC invite, the other returns an answer, and the phones establish encrypted audio and video directly.

The Wallet keeps personal records alongside those trusted workflows while preserving local control.

I built SecureLink Wallet with Codex, using it to plan the Android MVP, implement the Compose interface, integrate WebRTC, harden encrypted storage, and verify the complete call flow across two emulators.

SecureLink Wallet is a practical foundation for private everyday coordination without making a cloud account the center of the experience.

## YouTube Metadata

Title: SecureLink Wallet | Local-First Private Calling, Vault, and Contacts

Description:

SecureLink Wallet is a local-first Android app for trusted contacts, protected credentials, personal records, and direct WebRTC calling.

The app uses Android Keystore AES-GCM encryption for local fields, biometric or device-credential access for saved passwords, and manual WebRTC offer/answer exchange so two installs can establish encrypted audio/video without an account or signaling backend.

Built for OpenAI Build Week with Codex.

Repository: https://github.com/AgentsLabs/securelink-wallet

Category: Science & Technology

Visibility: Public
