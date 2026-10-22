# 📝 Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### 💡 Planned Features
- Tablet UI optimization
- Full UI multi-language localization (i18n)
- Cloud synchronization
- Collaborative document editing
- Advanced PDF annotations (freehand drawing & digital signatures)

---

## [1.2.0] - 2026-10-22

### 🚀 Added
- **New PDF Conversion & Extraction Tools**:
  - **PDF to Image**: High-resolution page-by-page rendering to JPEG/PNG with customizable DPI and batch zip export.
  - **Dual-Engine OCR for PDF**:
    - Privacy-first **offline OCR** powered by Google ML Kit and Tesseract 4 Android.
    - Multi-language offline recognition supporting **English, Kannada, and Hindi** (`eng.traineddata`, `hin.traineddata`, `kan.traineddata`).
    - Smart text detector that checks for authentic digital text before initiating OCR.
    - Cloud OCR fallback via OCR.space with explicit user consent dialog (`OcrCloudConsentDialog`).
  - **PDF to Word (.docx)**: Extracts document layout, headings, and paragraphs into editable Microsoft Word documents.
  - **PDF to Excel (.xlsx)**: Parses tabular data and structures into formatted Microsoft Excel spreadsheets.
- **Universal Multi-Document Viewer**:
  - Added native viewing support for **TXT, DOCX, and XLSX** documents alongside PDF.
  - Android Intent Filters to open `.txt`, `.docx`, `.xlsx`, and `.pdf` files directly from file managers and external apps (`VIEW` & `SEND`).
  - Dedicated repositories and recents tracking for PDF, DOCX, XLSX, and TXT files.
  - In-document full-text search with instant highlight navigation.
  - Interactive PDF hyperlink support with safe external URL routing.
- **Resume Builder 2.0**:
  - Two new professional templates: **Career Timeline** and **Executive Slate** (alongside Classic, Modern, Minimal, and Persona).
  - Dynamic multi-page pagination engine (`PaginatedColumn`, `PaginationRegistry`) preventing awkward page overflows and clipped items.
  - Pre-filled sample data toggle for fast authoring and previewing.
  - Vector typography rendering with Noto Sans and Noto Sans Kannada.
- **Refined Document Scanner**:
  - Advanced 4-point perspective cropping editor (`SharedCropEditor`, `EdgeCropScreen`, `Quad`).
  - Corner magnifier loupe with tactile feedback for pinpoint crop adjustment.
- **Integrated Video Tutorial System**:
  - Native video streaming player powered by AndroidX Media3 (ExoPlayer).
  - Built-in video tutorials for PDF Viewer, Document Scanner, and Resume Builder with replay options in tools/settings.
  - Version-tracked opt-in prompts for first-time onboarding.
- **Secure Configuration Management**:
  - Sourced all external API credentials (Gemini, Groq, OpenRouter, OCR.space, Web3Forms) via `local.properties` with fallback placeholders.
  - Added secure release signing configuration via environment variables and local properties.

### ⚡ Performance & Stability
- Memory-safe PDF rendering with aggressive bitmap recycling to prevent `OutOfMemoryError` on large multi-page files.
- Upgraded build environment to Android Gradle Plugin 8.13.2, Gradle 8.13, compileSdk and targetSdk 36.
- Comprehensive test suite covering OCR routing, document loaders, resume pagination, and lifecycle handling.
- General bug fixes and UI responsiveness enhancements.

---

## [1.0.1] - 2026-08-28

### 🐛 Fixed
- Bug fixes and performance improvements

---

## [1.0.0] - 2026-07-31

### 🎉 Initial Release

#### Core Features
- **PDF Viewer** - Full-featured PDF document viewing with:
  - Smooth scrolling and zoom controls
  - Page navigation
  - Landscape/portrait orientation support
  - Bookmarks support

- **Document Scanner** - Intelligent document scanning with:
  - Real-time camera preview
  - Automatic edge detection
  - Perspective correction
  - Image cropping and alignment
  - High-quality output export

- **Resume Builder** - Professional resume creation with:
  - Multiple resume templates
  - ATS-friendly formatting
  - Real-time preview
  - PDF export capability
  - AI-powered content suggestions

- **AI Integration** - Advanced features powered by:
  - Google Gemini API
  - Groq API
  - OpenRouter API
  - Smart text extraction
  - Context-aware suggestions

#### UI/UX
- Modern Material 3 design system
- Full dark mode and light mode support
- Jetpack Compose-based responsive UI
- Smooth animations and transitions
- Loading shimmer effects
- Bottom navigation for easy access

#### Technical
- MVVM architecture with Clean Architecture principles
- Kotlin Coroutines for async operations
- Flow for reactive data streams
- Room Database for local storage
- CameraX for advanced camera features
- ML Kit integration for document analysis
- Retrofit + OkHttp for API calls
- Coil for efficient image loading

#### Performance
- Offline-first architecture
- Minimal network calls
- Optimized database queries
- Efficient memory management
- Fast app startup time

---

## Versioning

This project follows [Semantic Versioning](https://semver.org/):

- **MAJOR** version when you make incompatible API changes
- **MINOR** version when you add functionality in a backward compatible manner
- **PATCH** version when you make backward compatible bug fixes

---

## 🏷️ Legend

- 💡 **Planned** - Features in development or planned
- 🚀 **Added** - New features
- 🔄 **Changed** - Changes in existing functionality
- ⚠️ **Deprecated** - Soon-to-be removed features
- 🗑️ **Removed** - Removed features
- 🐛 **Fixed** - Bug fixes
- 🔒 **Security** - Security improvements

---

## 🔗 Links

- [GitHub Issues](https://github.com/i-amsaif/StuTool-Android-App/issues) - Report bugs or request features
- [GitHub Releases](https://github.com/i-amsaif/StuTool-Android-App/releases) - Download releases
- [Website](https://www.stutool.in) - Visit our website
- [Android Download](https://www.stutool.in/android) - Download the app

---

## 💬 Support

For questions or issues, please:
1. Check existing [GitHub Issues](https://github.com/i-amsaif/StuTool-Android-App/issues)
2. Create a new issue if your problem isn't listed
3. Contact us at i.am.mohd.saif2006@gmail.com

---

**Last Updated:** 2026-10-22  
**Maintained by:** [@i-amsaif](https://github.com/i-amsaif)
