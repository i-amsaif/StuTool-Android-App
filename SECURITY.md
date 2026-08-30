# 🔒 Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in StuTool, please **do not** create a public GitHub issue. Instead, please report it responsibly to us.

### How to Report

📧 **Email:** security@stutool.in

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
- ✅ Use strong, unique passwords
- ✅ Enable system-level security features (face/fingerprint unlock)
- ✅ Review app permissions before granting them
- ✅ Be cautious when entering sensitive information
- ❌ Don't share your API keys with anyone
- ❌ Don't store sensitive passwords in notes

### For Developers

- ✅ Keep dependencies updated
- ✅ Use ProGuard/R8 for production builds
- ✅ Enable code obfuscation
- ✅ Validate all user inputs
- ✅ Use HTTPS for all API communications
- ✅ Encrypt sensitive data at rest
- ✅ Use secure random number generation
- ❌ Don't hardcode secrets or API keys
- ❌ Don't log sensitive information

---

## Known Security Considerations

### API Keys Management

API keys (Gemini, Groq, OpenRouter) must be managed securely:

- **Development:** Use `local.properties` (git-ignored)
- **Production:** Use secure environment variables or secure key management services
- **Never** commit API keys to the repository

### Data Storage

- User data is stored locally in Room Database
- All sensitive data should be encrypted
- Implement proper access controls

### Network Communication

- All API calls use HTTPS/TLS
- Certificate pinning should be considered for production
- Validate SSL certificates

---

## Security Headers & Policies

### Content Security

- No inline scripts
- Safe HTML rendering
- Input validation on all user data

### Privacy

- Minimal data collection
- No tracking without consent
- Transparent data usage policies

---

## Vulnerability Disclosure Timeline

We follow responsible disclosure practices:

1. **Report received** - Initial acknowledgment within 48 hours
2. **Assessment** - Vulnerability confirmed and impact assessed
3. **Patch development** - Fix is developed and tested
4. **Release** - Security patch is released
5. **Disclosure** - Vulnerability details are disclosed responsibly

Typically, we aim to release patches within 7-14 days of receiving a credible report.

---

## Supported Versions

| Version | Supported          | Release Date | End of Life |
|---------|------------------|--------------|------------|
| 0.1.x   | ✅ Yes            | 2026-08-28   | TBD        |
| < 0.1.0 | ❌ No             | -            | -          |

Only the latest version receives security updates.

---

## Dependencies & Vulnerabilities

We regularly:
- ✅ Update dependencies
- ✅ Run security audits
- ✅ Use automated dependency scanning
- ✅ Monitor for CVEs affecting our libraries

### Dependency Security Check

```bash
# Check for vulnerable dependencies
./gradlew dependencyCheckAnalyze

# View security reports
cat build/reports/dependency-check-report.html
```

---

## Security Testing

### Pre-Release Checklist

- [ ] Code review completed
- [ ] OWASP Top 10 Mobile review
- [ ] Dependency vulnerability scan
- [ ] ProGuard rules configured
- [ ] Certificate pinning verified
- [ ] Permissions reviewed
- [ ] Security logging disabled in production
- [ ] Test accounts removed
- [ ] API keys secured

---

## Third-Party Services

### External APIs Used

| Service | Purpose | Privacy Policy |
|---------|---------|----------------|
| Google Gemini | AI features | [Google Privacy](https://policies.google.com/privacy) |
| Groq | AI inference | [Groq Privacy](https://www.groq.com/privacy) |
| OpenRouter | AI routing | [OpenRouter Privacy](https://openrouter.ai/privacy) |

---

## Contact

- 🔒 Security Issues: security@stutool.in
- 💬 Other Questions: support@stutool.in
- 🌐 Website: https://www.stutool.in

---

**Last Updated:** 2026-08-28  
**Maintained by:** [@i-amsaif](https://github.com/i-amsaif)

---

### Security Resources

- [OWASP Mobile Security Top 10](https://owasp.org/www-project-mobile-top-10/)
- [Android Security & Privacy Guidelines](https://developer.android.com/privacy-and-security)
- [Kotlin Security Best Practices](https://kotlinlang.org/)
