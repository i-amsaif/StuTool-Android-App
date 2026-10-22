# 📱 StuTool - Android App

<div align="center">

[![Made with Kotlin](https://img.shields.io/badge/Made%20with-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/Version-1.2.0-22C55E?style=for-the-badge)](https://github.com/i-amsaif/StuTool-Android-App/releases)
[![GitHub Stars](https://img.shields.io/github/stars/i-amsaif/StuTool-Android-App?style=for-the-badge)](https://github.com/i-amsaif/StuTool-Android-App)
[![GitHub Issues](https://img.shields.io/github/issues/i-amsaif/StuTool-Android-App?style=for-the-badge)](https://github.com/i-amsaif/StuTool-Android-App/issues)

**The All-in-One Android Document & Productivity Companion for Students & Professionals**

[🌐 Visit Website](https://www.stutool.in) • [📲 Download on Google Play](https://play.google.com/store/apps/details?id=com.stutool.stutool) • [🚀 Getting Started](#-getting-started)

[![Get it on Google Play](https://img.shields.io/badge/Get%20it%20on-Google%20Play-414141?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.stutool.stutool)

</div>

---

## 📖 About StuTool

StuTool is a **comprehensive productivity and document management suite** engineered for Android. Built with Jetpack Compose and modern Material 3, StuTool empowers students, job seekers, and working professionals with fast, offline-first tools: multi-format document reading, intelligent camera scanning, professional resume building, and powerful conversion utilities (PDF to Word, Excel, and Image, plus dual-engine OCR).

> **Manage, convert, view, and create documents effortlessly — fast, secure, and privacy-focused.**

---

## ✨ Features & Tools

<table>
  <tr>
    <td width="50%">
      <h3>📄 Multi-Format Document Viewer</h3>
      <ul>
        <li><b>PDF, TXT, DOCX & XLSX</b> native reader</li>
        <li>Continuous vertical scrolling & pinch-to-zoom</li>
        <li>Full-text search with instant jump navigation</li>
        <li>Interactive PDF hyperlink support</li>
        <li>Direct system file opener (<code>VIEW</code> & <code>SEND</code> intents)</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🔄 PDF Conversion & Utilities</h3>
      <ul>
        <li><b>PDF to Image</b>: High-res JPG/PNG export & zip archive</li>
        <li><b>PDF to Word (.docx)</b>: Editable document extraction</li>
        <li><b>PDF to Excel (.xlsx)</b>: Tabular spreadsheet parsing</li>
        <li><b>Compress PDF</b>: Quick file size reduction</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🔍 Dual-Engine OCR System</h3>
      <ul>
        <li><b>Offline-First</b>: On-device ML Kit & Tesseract 4 Android</li>
        <li><b>Multi-Language</b>: English, Kannada, and Hindi support</li>
        <li>Smart native text detection before running OCR</li>
        <li>Privacy dialog with optional OCR.space cloud fallback</li>
      </ul>
    </td>
    <td width="50%">
      <h3>📝 Resume Builder 2.0</h3>
      <ul>
        <li>ATS-friendly professional resume templates</li>
        <li>New <b>Career Timeline</b> & <b>Executive Slate</b> designs</li>
        <li>Dynamic multi-page pagination engine (no awkward cutoffs)</li>
        <li>Pre-filled sample data toggle for fast drafting</li>
        <li>Instant high-fidelity vector PDF export</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>📷 Intelligent Document Scanner</h3>
      <ul>
        <li>Real-time camera scanning via CameraX</li>
        <li>Refined 4-point perspective crop editor</li>
        <li>Corner magnifier loupe with haptic tactile feedback</li>
        <li>Multi-page batch scanning into single PDF</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🎬 Interactive Video Tutorials</h3>
      <ul>
        <li>Built-in streaming video player (AndroidX Media3 ExoPlayer)</li>
        <li>Step-by-step guides for Viewer, Scanner, and Resume Builder</li>
        <li>One-tap replay options from settings & tool screens</li>
        <li>Smart onboarding opt-in prompts</li>
      </ul>
    </td>
  </tr>
</table>

### 💎 Key Highlights

- **🎨 Modern Material 3 UI** - Clean, distraction-free interface with edge-to-edge design
- **🌓 Dark & Light Modes** - Native dual-theme support with true black palette
- **🔒 Privacy & Offline-First** - Core tools and OCR process on-device without cloud dependencies
- **⚡ Memory-Safe Architecture** - Aggressive bitmap recycling to handle large multi-page PDFs
- **🛡️ Secure Credential Handling** - Zero hardcoded API keys; safe environment & `local.properties` resolution

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

| Layer | Technologies |
|---|---|
| **Language** | Kotlin (1.9+) |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **Build System** | Android Gradle Plugin 8.13.2, Gradle 8.13, Target SDK 36, Min SDK 24 |
| **Local Storage** | SharedPreferences & Document Repositories |
| **Concurrency** | Kotlin Coroutines & Flow |
| **Networking** | OkHttp 4.12.0 + Retrofit |
| **Media & Video** | AndroidX Media3 (ExoPlayer 1.4.1) |
| **OCR Engines** | Tesseract 4 Android 4.9.0 + Google ML Kit Text Recognition |
| **Document Processing** | PDFBox-Android 2.0.27.0 + Custom OpenXML (DOCX / XLSX) Loaders |

</div>

---

## 📥 Download

<div align="center">

| Platform | Link | Status |
|---|---|---|
| 🌐 **Website** | [stutool.in/android](https://www.stutool.in/android) | ✅ Available |
| 🏬 **Google Play Store** | [Download StuTool](https://play.google.com/store/apps/details?id=com.stutool.stutool) | ✅ Available |
| 🛍️ **Indus AppStore** | [Download](https://indusapp.store/pl3q2rxo) | ✅ Available |
| 📦 **GitHub Releases** | [Download APK](https://github.com/i-amsaif/StuTool-Android-App/releases) | ✅ Available |

</div>

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Koala / Ladybug or later
- Android SDK 36 (Min SDK 24)
- JDK 17
- Git

### Installation Steps

#### 1. Clone the Repository

```bash
git clone https://github.com/i-amsaif/StuTool-Android-App.git
cd StuTool-Android-App
```

#### 2. Configure Local Properties (Optional)

Create or edit `local.properties` in the project root to supply optional external service keys:

```properties
sdk.dir=/path/to/android/sdk

# Optional AI / Cloud OCR keys (App falls back to local engines or placeholders if omitted)
GEMINI_API_KEY=your_gemini_key_here
GROQ_API_KEY=your_groq_key_here
OPENROUTER_API_KEY=your_openrouter_key_here
OCR_SPACE_API_KEY=your_ocr_space_key_here
WEB3FORMS_ACCESS_KEY=your_web3forms_key_here
```

> **Note:** StuTool builds and runs completely without these keys. Offline OCR, PDF rendering, document viewing, and resume building are 100% functional locally.

#### 3. Build the Project

Using Android Studio:
1. Open the project directory
2. Let Gradle sync dependencies
3. Click **Run 'app'** (or press Shift+F10)

Or via command line:

```bash
# Assemble Debug APK
./gradlew assembleDebug

# Run Unit & Robolectric Tests
./gradlew testDebugUnitTest
```

---

## 📁 Project Structure

```
StuTool-Android-App/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/              # Offline OCR traineddata & vector fonts
│   │   │   │   ├── fonts/           # Noto Sans & Kannada typefaces
│   │   │   │   └── tessdata/        # eng, hin, kan models
│   │   │   ├── java/com/stutool/
│   │   │   │   ├── data/            # Local repositories & remote clients
│   │   │   │   ├── domain/          # PDF engine, OCR router, models
│   │   │   │   ├── navigation/      # Jetpack Compose navigation graphs
│   │   │   │   ├── tutorial/        # Media3 video tutorial system
│   │   │   │   └── ui/              # Screens, components, resume templates
│   │   │   └── AndroidManifest.xml  # Permissions & document file intent filters
│   │   └── test/                    # Comprehensive unit & Robolectric test suite
│   ├── build.gradle.kts             # App dependencies & secure BuildConfig
│   └── proguard-rules.pro
├── gradle/                          # Gradle wrapper & version catalogs (libs.versions.toml)
├── CHANGELOG.md                     # Version release history
├── CONTRIBUTING.md                  # Development guidelines
├── SECURITY.md                      # Vulnerability reporting & key policy
└── README.md
```

---

## 🗺️ Roadmap

- [x] Document Scanner with perspective crop
- [x] Professional Resume Builder with multi-page pagination
- [x] Multi-format Document Viewer (PDF, DOCX, XLSX, TXT)
- [x] Dual-engine OCR (Offline ML Kit + Tesseract, Cloud fallback)
- [x] PDF conversion suite (PDF to Image, Word, Excel)
- [x] In-app video tutorial system
- [ ] Tablet & foldable screen UI optimization
- [ ] Full internationalization & app localization (i18n)
- [ ] Cloud backup & sync options
- [ ] Advanced PDF annotations (drawing, signatures)

---

## 🤝 Contributing

Contributions are warmly welcomed! Please read [CONTRIBUTING.md](./CONTRIBUTING.md) for details on code style, commit conventions, and our pull request process.

---

## 🔒 Security

For security vulnerabilities and disclosure instructions, please consult [SECURITY.md](./SECURITY.md). Never submit credentials or API keys in pull requests.

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](./LICENSE) file for details.

---

## 💬 Support & Contact

- 🐛 [GitHub Issues](https://github.com/i-amsaif/StuTool-Android-App/issues) - Bug reports & feature requests
- ✉️ **Email** - [i.am.mohd.saif2006@gmail.com](mailto:i.am.mohd.saif2006@gmail.com)
- 🌐 **Website** - [stutool.in](https://www.stutool.in)

---

<div align="center">

**Developed with ❤️ by [@i-amsaif](https://github.com/i-amsaif) for students & professionals worldwide**

</div>
