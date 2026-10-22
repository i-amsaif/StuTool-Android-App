# 🤝 Contributing to StuTool

First off, thank you for considering contributing to **StuTool**! It's people like you that make StuTool such an empowering open-source tool for students and professionals.

## 📑 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Pull Request Process](#pull-request-process)
- [Commit Messages](#commit-messages)
- [Testing](#testing)
- [Documentation](#documentation)

---

## 📜 Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](./CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to maintainers.

---

## 💡 How Can I Contribute?

### 🐛 Reporting Bugs

Before creating bug reports, please check the [GitHub Issues list](https://github.com/i-amsaif/StuTool-Android-App/issues) to avoid duplicates.

**When filing a bug report, include:**
- **Title**: Clear, descriptive title
- **Description**: What you were doing when the bug occurred
- **Steps to Reproduce**: Exact steps to reproduce the issue
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happened
- **Environment**:
  - Android version & Device model
  - App version (e.g., v1.2.0)
  - Any relevant logcat output
- **Screenshots / Videos**: Visual evidence (if applicable)

### 🚀 Suggesting Features

Feature suggestions are welcome! Before creating a request, check existing [issues](https://github.com/i-amsaif/StuTool-Android-App/issues) and our [roadmap](./README.md#-roadmap).

**Include in feature requests:**
- **Use Case**: Why is this feature needed?
- **Description**: Clear explanation of the intended workflow
- **Benefits**: How it improves productivity for users
- **Screenshots / Mockups**: Visual references (if applicable)

### 📚 Improving Documentation

Documentation improvements are always appreciated:
- Clarify existing sections or fix typos
- Add architectural notes or code snippets
- Improve quick-start guides

---

## 🛠️ Development Setup

### Prerequisites

- Android Studio Koala / Ladybug or later
- Android SDK 36 (Min SDK 24)
- JDK 17
- Git

### Local Setup

1. **Fork the repository** on GitHub.
2. **Clone your fork**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/StuTool-Android-App.git
   cd StuTool-Android-App
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/i-amsaif/StuTool-Android-App.git
   ```
4. **Create a branch for your work**:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```
5. **Setup API Keys** (Optional):
   Create a `local.properties` file in the project root if you want to test AI or cloud OCR features:
   ```properties
   GEMINI_API_KEY=your_key
   GROQ_API_KEY=your_key
   OPENROUTER_API_KEY=your_key
   OCR_SPACE_API_KEY=your_key
   WEB3FORMS_ACCESS_KEY=your_key
   ```
6. **Open in Android Studio**:
   - File ➔ Open ➔ Select repository directory.
   - Wait for Gradle sync to complete.

---

## 📐 Coding Standards

### Kotlin Style Guide

We follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

- Indentation: 4 spaces (no tabs)
- Prefer immutability (`val` over `var`)
- Explicit types for public APIs
- Avoid magic numbers and strings (use Constants/Tokens)

### Naming Conventions

```kotlin
// Classes - PascalCase
class DocumentScannerScreen { }
class ResumeViewModel { }

// Functions & Variables - camelCase
fun getDocumentPath(): Uri { }
val isProcessing = true

// Constants - UPPER_SNAKE_CASE
const val MAX_FILE_SIZE = 10_000_000

// Backing properties - prefix with underscore
private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

### Compose Best Practices

- Keep Composables modular and single-responsibility.
- Hoist state up to ViewModels when handling business logic or side effects.
- Provide sensible defaults for optional modifier and lambda arguments.
- Always handle edge cases like dark/light theme switching and screen rotation.

---

## 🔄 Pull Request Process

### Before Submitting a PR

1. **Sync with upstream**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```
2. **Run tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. **Verify Git cleanliness**:
   - Ensure zero build artifacts (`.apk`, `.aab`), keystores, or API keys are staged.

### Creating a PR

1. Push your branch:
   ```bash
   git push origin feature/your-feature-name
   ```
2. Open a Pull Request against `main`.
3. Provide a clear summary of changes, linked issues, and screenshots for UI modifications.

---

## 💬 Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <subject>

<body>
```

### Common Types

- `feat:` - A new feature
- `fix:` - A bug fix
- `docs:` - Documentation changes
- `style:` - Formatting (no code changes)
- `refactor:` - Code restructuring
- `perf:` - Performance optimization
- `test:` - Adding or updating tests
- `chore:` - Tooling, build config, dependencies

---

## 🧪 Testing

```bash
# Run unit & Robolectric tests
./gradlew testDebugUnitTest

# Run on connected device
./gradlew connectedDebugAndroidTest
```

---

## 📬 Questions & Community

- 🐛 [GitHub Issues](https://github.com/i-amsaif/StuTool-Android-App/issues)
- ✉️ Email: [i.am.mohd.saif2006@gmail.com](mailto:i.am.mohd.saif2006@gmail.com)
- 🌐 Website: [stutool.in](https://www.stutool.in)

---

**Happy coding! 🚀**
