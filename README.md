# MeshFabric Android App

A native **unofficial** Android client for **MeshCentral** remote management, providing Remote Desktop (KVM), Terminal (SSH/Shell), and File Manager functionality directly from your Android device.

---

## 🚀 Features

- **Device Management**: View all your MeshCentral device groups, online/offline status, and OS details in real-time.
- **Remote Desktop (KVM)**: Smooth touchscreen mouse controls (Left Click, Right Click, Scroll), zoom & pan, customizable FPS/Quality settings, and an embedded virtual keyboard with system hotkeys (`Ctrl+Alt+Del`, `Win+R`, `Alt+Tab`, `F1-F12`, etc.).
- **Terminal**: Native interactive shell with font scaling, Control key shortcuts, and quick command helpers.
- **File Manager**: Directory browsing, file uploading, downloading to Android, text file editing with syntax/line numbers, compressed ZIP handling, and file operations (copy, cut, paste, rename, delete).
- **Internationalization (i18n)**: Full support for English and Spanish languages.

---

## TODO:
- Implement the ability to select multiple files for actions such as copying, cutting, compressing, deleting, and downloading.
- When there is a network change the tunnel in terminal is broken, storing the buffer and indicating that the session has been broken, storing the old buffer to consult until it is closed or deleted manually. It is convenient to add a reconnection logic keeping the old buffer, pending to shape and implement it, either automatically and/or manually

---

## 🛠 Tech Stack & Architecture

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Networking**: OkHttp 4 (WebSockets & HTTPS), Encrypted SharedPreferences
- **Architecture**: MVVM with Kotlin Coroutines & StateFlow
- **Minimum SDK**: Android 7.0 (API Level 24)
- **Target SDK**: Android 14+ (API Level 34)

---

## 🔒 Security

- Credentials and session cookies are securely stored using Android's `EncryptedSharedPreferences` backed by Android KeyStore (`AES256_GCM`).
- Option to support self-signed SSL certificates for private or local MeshCentral deployments.

---

## 📋 Prerequisites & Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/iecxiv/meshfabric.git
   cd meshfabric
   ```
2. Open the project in **Android Studio** (2024.1+ recommended).
3. Build and run on an Android device or emulator running Android 7.0 (API 24) or higher.

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).
