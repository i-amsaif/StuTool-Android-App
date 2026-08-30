# 📚 StuTool - Android App

<div align="center">

[![Made with Kotlin](https://img.shields.io/badge/Made%20with-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](./LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/i-amsaif/StuTool-Android-App?style=for-the-badge)](https://github.com/i-amsaif/StuTool-Android-App)
[![GitHub Issues](https://img.shields.io/github/issues/i-amsaif/StuTool-Android-App?style=for-the-badge)](https://github.com/i-amsaif/StuTool-Android-App/issues)

**The All-in-One Android Companion for Students & Professionals**

[🌐 Visit Website](https://www.stutool.in) • [📥 Download](#download) • [🚀 Getting Started](#-getting-started) • [📖 Documentation](#-documentation)

</div>

---

## ✨ About StuTool

StuTool is a **comprehensive productivity application** designed specifically for students and professionals who need to manage documents efficiently. Whether you're scanning class materials, editing PDFs, or building a professional resume, StuTool has you covered with **fast, offline-first performance** and an intuitive interface.

> **Optimize your productivity with smart document tools and AI-powered features.**

---

## 🎯 Features

<table>
  <tr>
    <td width="50%">
      <h3>📄 PDF Management</h3>
      <ul>
        <li>Open & view PDF documents</li>
        <li>Organize and manage your library</li>
        <li>Smooth scrolling & zoom controls</li>
        <li>Full-text search capability</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🎓 Resume Builder</h3>
      <ul>
        <li>Create ATS-friendly resumes</li>
        <li>Professional templates</li>
        <li>AI-powered suggestions</li>
        <li>Export as PDF</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>📸 Document Scanner</h3>
      <ul>
        <li>Scan documents with camera</li>
        <li>Intelligent edge detection</li>
        <li>Auto-cropping & perspective fix</li>
        <li>High-quality output</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🤖 AI Integration</h3>
      <ul>
        <li>Powered by Gemini, Groq & OpenRouter</li>
        <li>Smart text extraction</li>
        <li>Content enhancement</li>
        <li>Context-aware suggestions</li>
      </ul>
    </td>
  </tr>
</table>

### 🌓 Additional Highlights

- **🎨 Modern UI/UX** - Clean, distraction-free interface
- **🌙 Dark & Light Mode** - Full theme support
- **⚡ Offline-First** - Works without internet
- **🔒 Privacy Focused** - Your data, your control
- **⚙️ Performance Optimized** - Smooth & responsive

---

## 📸 Screenshots

<div align="center">
  <h3>App Interface Preview</h3>
  <p>
    <img src="images/stutoolart1.png" width="160" alt="Screenshot 1" />
    &nbsp;&nbsp;
    <img src="images/stutoolart2.png" width="160" alt="Screenshot 2" />
    &nbsp;&nbsp;
    <img src="images/stutoolart3.png" width="160" alt="Screenshot 3" />
    &nbsp;&nbsp;
    <img src="images/stutoolart4.png" width="160" alt="Screenshot 4" />
  </p>
</div>

---

## 🛠️ Tech Stack

<div align="center">

| Component | Technology |
|-----------|----------|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Architecture** | MVVM + Clean Architecture |
| **Database** | Room Database |
| **Async** | Kotlin Coroutines & Flow |
| **Networking** | Retrofit + OkHttp |

</div>

### 📦 Key Libraries

```
🎨 UI & Rendering
  • Jetpack Compose - Modern UI framework
  • Material 3 - Google's design system
  • Coil - Image loading

📄 Document Handling
  • PDFBox-Android - PDF manipulation
  • UCrop - Image cropping
  
📷 Camera & Scanning
  • CameraX - Camera functionality
  • ML Kit - Machine learning features

✨ Polish & Effects
  • Shimmer - Loading animations
  • Accompanist - Compose utilities
```

---

## 📥 Download

<div align="center">

| Platform | Status |
|----------|--------|
| Google Play Store | Coming Soon 🚀 |
| Direct APK | [Available](#) |
| F-Droid | Pending |

</div>

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 24+ (API level 24)
- Kotlin 1.9+
- Gradle 8.0+

### Installation Steps

#### 1️⃣ Clone the Repository

```bash
git clone https://github.com/i-amsaif/StuTool-Android-App.git
cd StuTool-Android-App
```

#### 2️⃣ Setup API Keys

Create a `local.properties` file in the root directory:

```properties
# API Keys (Optional - app works with placeholders)
GEMINI_API_KEY=your_gemini_key_here
GROQ_API_KEY=your_groq_key_here
OPENROUTER_API_KEY=your_openrouter_key_here
```

> **Note:** The app will still build and function without these keys using placeholder values.

#### 3️⃣ Build the Project

Using Android Studio:
1. Open the project
2. Sync Gradle files
3. Click **Run** (or press Shift+F10)

**Or via command line:**

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

#### 4️⃣ Run on Device/Emulator

```bash
./gradlew installDebug
```

---

## 📂 Project Structure

```
StuTool-Android-App/
├── app/                           # Main application module
│   ├── src/main/
│   │   ├── kotlin/com/stutool/   # Source code
│   │   ├── res/                   # Resources
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts           # App-level build config
│   └── proguard-rules.pro         # ProGuard rules
├── gradle/                        # Gradle wrapper
├── build.gradle.kts               # Project-level build config
├── settings.gradle.kts            # Gradle settings
├── local.properties               # Local config (git-ignored)
└── README.md                      # This file
```

---

## 🔧 Configuration

### Android Manifest Permissions

```xml
<!-- Required -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- For AI features (if using) -->
<uses-permission android:name="android.permission.INTERNET" />
```

### Build Variants

```bash
# Development
./gradlew assembleDebug

# Production
./gradlew assembleRelease

# Run tests
./gradlew test
./gradlew androidTest
```

---

## 🤝 Contributing

We welcome contributions from the community! Please follow these steps:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to your branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Code Guidelines

- Follow Kotlin naming conventions
- Use meaningful variable names
- Add comments for complex logic
- Test your changes before submitting
- Update documentation as needed

See [CONTRIBUTING.md](./CONTRIBUTING.md) for detailed guidelines.

---

## 📋 Roadmap

- [ ] Google Play Store release
- [ ] F-Droid integration
- [ ] Tablet UI optimization
- [ ] Multi-language support
- [ ] Cloud sync feature
- [ ] Collaborative document editing
- [ ] Advanced PDF annotations
- [ ] OCR integration

See [CHANGELOG.md](./CHANGELOG.md) for release notes and version history.

---

## 🐛 Issue Tracking

Found a bug? Have a feature request? Please create an [issue](https://github.com/i-amsaif/StuTool-Android-App/issues)!

**When reporting issues, please include:**
- Device model & Android version
- App version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots or logs (if applicable)

---

## 🔒 Security

For security vulnerabilities, please see [SECURITY.md](./SECURITY.md) for responsible disclosure.

---

## 📝 License

This project is licensed under the **MIT License** - see the [LICENSE](./LICENSE) file for details.

---

## 🙋 Support & Community

- 💬 [GitHub Discussions](#) - Ask questions
- 📧 Email - [Get in touch](mailto:support@stutool.in)
- 🌐 Website - [stutool.in](https://www.stutool.in)
- 🐦 Twitter - [@StuToolOfficial](#)

---

## 👨‍💻 Author

**StuTool** is developed and maintained by [i-amsaif](https://github.com/i-amsaif)

<div align="center">

**Made with ❤️ for students & professionals**

---

[![Star History](https://api.star-history.com/svg?repos=i-amsaif/StuTool-Android-App&type=Date)](https://star-history.com/#i-amsaif/StuTool-Android-App&Date)

</div>