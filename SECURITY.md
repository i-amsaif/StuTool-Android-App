# 🔒 Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in StuTool, please **do not** create a public GitHub issue. Instead, please report it responsibly to us.

### How to Report

📧 **Email:** [i.am.mohd.saif2006@gmail.com](mailto:i.am.mohd.saif2006@gmail.com)

**Please include:**
- Description of the vulnerability
- Steps to reproduce (if possible)
- Potential impact
- Suggested fix (if available)

We will acknowledge receipt of your report within 48 hours and provide regular updates as we investigate and work on a fix.

---

## Security Best Practices

### For Users

- ✅ Keep the app updated to the latest version
- ✅ Use strong, unique passwords on your device
- ✅ Enable system-level biometrics (fingerprint/face unlock)
- ✅ Review app permissions before granting them
- ❌ Don't share API credentials or private keys with anyone
- ❌ Don't store sensitive personal passwords in plain text notes

### For Developers

- ✅ Keep dependencies and build tools updated
- ✅ Use ProGuard/R8 for production release builds with code obfuscation
- ✅ Validate all user inputs and external URIs before parsing
- ✅ Use HTTPS/TLS for all external network communications
- ✅ Ensure zero sensitive files (`local.properties`, keystores, API keys) are committed to git
- ❌ Don't hardcode secrets, access tokens, or API keys in source code
- ❌ Don't log sensitive user documents or personal identifiers

---

## Known Security Considerations

### API Keys Management

All external API keys (Gemini, Groq, OpenRouter, OCR.space, Web3Forms) are strictly managed via `local.properties`:

- **Development:** Loaded locally via Gradle from git-ignored `local.properties`.
- **Fallbacks:** Safe placeholder defaults (`"PLACEHOLDER"` / `""`) are provided when keys are missing.
- **Repository Safety:** The `local.properties` file, keystore files (`*.jks`, `*.keystore`), and build outputs (`*.apk`, `*.aab`) are permanently excluded in `.gitignore`.

### Document & Data Storage

- Core PDF tools, document parsing, and OCR run **entirely offline** on the device.
- Temporary files generated during file conversions (e.g., PDF to Image, DOCX, XLSX) are scoped to the application's internal cache directory and cleaned up appropriately.
- Cloud OCR requires explicit user opt-in through a dedicated privacy consent dialogue.

---

## Supported Versions

| Version | Supported | Release Date |
|---|---|---|
| 1.2.0 | ✅ Yes | 2026-10-22 |
| 1.0.1 | ✅ Yes | 2026-08-28 |
| 1.0.0 | ⚠️ Security fixes only | 2026-07-31 |

---

## Third-Party Services

| Service | Purpose | Privacy Policy |
|---|---|---|
| Google Gemini | Optional AI suggestions | [Google Privacy Policy](https://policies.google.com/privacy) |
| Groq | Optional AI inference | [Groq Privacy Policy](https://www.groq.com/privacy) |
| OpenRouter | Optional AI model routing | [OpenRouter Privacy Policy](https://openrouter.ai/privacy) |
| OCR.space | Optional cloud OCR fallback | [OCR.space Privacy](https://ocr.space/privacypolicy) |
| Web3Forms | User feedback transmission | [Web3Forms Privacy](https://web3forms.com/privacy) |

---

## Contact

- 🔒 **Security Reports:** [i.am.mohd.saif2006@gmail.com](mailto:i.am.mohd.saif2006@gmail.com)
- 🌐 **Website:** [https://www.stutool.in](https://www.stutool.in)
- 📱 **Android App:** [https://www.stutool.in/android](https://www.stutool.in/android)

---

**Last Updated:** 2026-10-22  
**Maintained by:** [@i-amsaif](https://github.com/i-amsaif)
