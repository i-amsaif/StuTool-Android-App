# 🤝 Contributing to StuTool

First off, thank you for considering contributing to **StuTool**! It's people like you that make StuTool such a great tool.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Pull Request Process](#pull-request-process)
- [Commit Messages](#commit-messages)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features](#suggesting-features)

---

## 📜 Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to maintainers.

### Our Pledge

We are committed to providing a welcoming and inspiring community for all. We pledge to:
- Be respectful and inclusive
- Welcome people from all backgrounds
- Create a harassment-free environment

---

## 🎯 How Can I Contribute?

### 🐛 Reporting Bugs

Before creating bug reports, please check the [issue list](https://github.com/i-amsaif/StuTool-Android-App/issues) to avoid duplicates.

**When filing a bug report, include:**
- **Title**: Clear, descriptive title
- **Description**: What you were doing when the bug occurred
- **Steps to Reproduce**: Exact steps to reproduce the issue
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happened
- **Environment**:
  - Android version
  - Device model
  - App version
  - Any relevant logs or crash reports
- **Screenshots**: Visual evidence (if applicable)

### 💡 Suggesting Features

We love feature suggestions! Before creating a request, check the [issues](https://github.com/i-amsaif/StuTool-Android-App/issues) and [roadmap](./README.md#-roadmap).

**Include in feature requests:**
- **Use Case**: Why do you need this feature?
- **Description**: Clear explanation of the feature
- **Benefits**: How it improves the app
- **Possible Implementation**: If you have ideas
- **Screenshots/Mockups**: Visual references (if applicable)

### 📝 Improving Documentation

Documentation improvements are always welcome! You can:
- Fix typos or clarify sections
- Add examples or code snippets
- Improve README or guides
- Add API documentation

### 💻 Contributing Code

See [Development Setup](#development-setup) and [Pull Request Process](#pull-request-process).

---

## 🛠️ Development Setup

### Prerequisites

```bash
# Required
- Android Studio Hedgehog or later
- Android SDK 24+ (API level 24)
- Kotlin 1.9+
- Git
- JDK 11+
```

### Local Setup

1. **Fork the repository**
   ```bash
   # Click "Fork" on GitHub
   ```

2. **Clone your fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/StuTool-Android-App.git
   cd StuTool-Android-App
   ```

3. **Add upstream remote**
   ```bash
   git remote add upstream https://github.com/i-amsaif/StuTool-Android-App.git
   ```

4. **Create a branch for your work**
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

5. **Setup API Keys** (if needed)
   ```bash
   # Create local.properties in project root
   echo "GEMINI_API_KEY=your_key" > local.properties
   echo "GROQ_API_KEY=your_key" >> local.properties
   ```

6. **Open in Android Studio**
   - File → Open
   - Select the cloned directory
   - Wait for Gradle sync to complete

---

## 💻 Coding Standards

### Kotlin Style Guide

We follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

**Key Points:**
- Use 4 spaces for indentation (no tabs)
- Max line length: 120 characters
- Use meaningful variable names
- `val` over `var` when possible
- Prefer immutability

### Naming Conventions

```kotlin
// Classes - PascalCase
class DocumentScanner { }
class ResumBuilder { }

// Functions & Variables - camelCase
fun getDocumentPath() { }
val isLoading = true

// Constants - UPPER_SNAKE_CASE
const val MAX_FILE_SIZE = 10_000_000

// Private members - prefix with underscore
private val _selectedItem = MutableLiveData<Item>()
```

### Compose Best Practices

```kotlin
// Keep composables focused and single-responsibility
@Composable
fun DocumentCard(document: Document) {
    // Implementation
}

// Use meaningful parameter names
@Composable
fun MyScreen(
    viewModel: MyViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit = {}
) {
    // Implementation
}
```

### Code Quality

- ✅ Write self-documenting code
- ✅ Add comments for complex logic
- ✅ Keep functions small and focused
- ✅ Handle errors gracefully
- ✅ Use type-safe approaches
- ❌ Avoid deeply nested code
- ❌ Avoid magic numbers (use constants)
- ❌ Avoid suppressing warnings without reason

---

## 🔄 Pull Request Process

### Before Creating a PR

1. **Sync with upstream**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests locally**
   ```bash
   ./gradlew test
   ./gradlew androidTest
   ```

3. **Check code quality**
   ```bash
   ./gradlew lint
   ```

4. **Ensure code follows standards**
   - No unused imports
   - Proper formatting
   - No console/debug logs in production code

### Creating a PR

1. **Push your branch**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Go to GitHub** and create a Pull Request
   - Base: `main` branch of `i-amsaif/StuTool-Android-App`
   - Compare: Your feature branch

3. **Fill PR template** with:
   - Clear title (use conventional commits)
   - Description of changes
   - Related issues (fixes #123)
   - Screenshots (if UI changes)
   - Testing notes
   - Checklist completion

### PR Checklist

```markdown
- [ ] Code follows style guidelines
- [ ] I have performed a self-review
- [ ] I have commented complex areas
- [ ] No new warnings generated
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] No breaking changes (or documented)
```

### Review & Merge

- Maintainers will review your PR
- Respond to feedback and make requested changes
- Once approved, your PR will be merged!

---

## 📝 Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat:` - A new feature
- `fix:` - A bug fix
- `docs:` - Documentation changes
- `style:` - Formatting, linting (no code change)
- `refactor:` - Code restructuring
- `perf:` - Performance improvement
- `test:` - Adding or updating tests
- `chore:` - Build, dependencies, tooling

### Examples

```bash
# Feature
git commit -m "feat(scanner): add document edge detection"

# Bug fix
git commit -m "fix(pdf): resolve crash on large file loading"

# Documentation
git commit -m "docs: update setup instructions"

# Refactor with breaking change
git commit -m "refactor(api): restructure authentication flow

BREAKING CHANGE: Auth API now requires token refresh"
```

---

## 🧪 Testing

### Running Tests

```bash
# Unit tests
./gradlew test

# Android instrumented tests
./gradlew connectedAndroidTest

# All tests
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

### Writing Tests

- Write tests for new features
- Update tests for modified features
- Aim for reasonable coverage
- Use clear test names

---

## 📚 Documentation

### When to Update Docs

- Adding new features → Update README
- Changing APIs → Update inline documentation
- New setup steps → Update guides
- Bug fixes → Update if documentation was wrong

### Style Guide for Docs

- Use clear, simple language
- Include code examples
- Add screenshots for UI features
- Link to related resources
- Keep consistent formatting

---

## ❓ Questions?

- 💬 Use [GitHub Issues](https://github.com/i-amsaif/StuTool-Android-App/issues)
- 📧 Email: i.am.mohd.saif2006@gmail.com
- 🌐 Visit [stutool.in](https://www.stutool.in)

---

## 🎉 Thank You!

Thank you for contributing to StuTool! Your efforts help make this project better for everyone.

**Happy coding! 🚀**
