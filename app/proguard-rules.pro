# WebRTC loads native bindings and observers through its public Java API.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
