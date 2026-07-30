# Web Porting Context

This document contains all the necessary information, implementation details, and business logic to port the Android resume improvements back to the StuTool Web App.

## 1. AI Career Objective Enhancement

### AI Workflow & States
- **Validation**: Before initiating, check if the input is blank. If so, return an error state: "Please enter a career objective first".
- **Cache**: Maintain an in-memory dictionary of `trim()` inputs. If the user requests enhancement for a previously generated text, return the cached result instantly.
- **Cooldown**: Implement a 12-second (12000ms) cooldown between requests to prevent spam. If triggered, return: "Please wait before trying again".
- **Loading State**: Disable the button and show a loading indicator.
- **Fallback Chain**: The system attempts to generate the text using a sequence of AI providers. If one fails (timeout, rate limit, or empty response), it immediately falls back to the next one in the chain.
- **Success Behavior**: The raw text must be cleaned using `.replace(/\n+/g, " ").trim()` to ensure it stays a concise paragraph. Update the cache, reset the cooldown timer, and present the result in an alert/dialog where the user can accept, retry, or cancel.
- **Error Handling**: If *all* providers fail, apply an extended 30-second penalty to the cooldown timer and display: "AI is currently busy. Please try again shortly."

### System Prompt & Instructions
All providers use the exact same prompt structure:
```text
Rewrite the following career objective into a professional resume-ready version.
Rules:
* Output ONLY the final improved text
* No explanations
* No headings
* Keep it 2–3 lines
* Make it concise and impactful
* Keep meaning aligned with input
Input:
"[USER_INPUT_TEXT_HERE]"
```

### API Keys & Providers
The Android project uses the following providers in this strict fallback order:

#### 1. Gemini (Primary)
- **Model**: `gemini-2.5-flash`
- **Endpoint**: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=[API_KEY]`
- **API Key**: `[REMOVED_FOR_SECURITY]`
- **Purpose**: Primary fast and reliable generator.

#### 2. Groq (First Fallback)
- **Model**: `mixtral-8x7b-32768`
- **Endpoint**: `https://api.groq.com/openai/v1/chat/completions`
- **API Key**: `[REMOVED_FOR_SECURITY]`
- **Purpose**: Ultra-fast fallback if Gemini rate limits.

#### 3. OpenRouter (Final Fallback)
- **Model**: `openrouter/auto`
- **Endpoint**: `https://openrouter.ai/api/v1/chat/completions`
- **API Key**: `[REMOVED_FOR_SECURITY]`
- **Headers Required**: `HTTP-Referer: https://stutool.vercel.app/`
- **Purpose**: Reliable final net to ensure the feature never fails for the user.

---

## 2. Smart Formatting

The auto-formatter was improved to be strictly non-destructive, stopping it from fighting intentional user capitalization while typing.

### Rules
1. **Rule Check (Preserve Intentional Capitals)**: If a word contains *any* uppercase letters, it is preserved exactly as typed. This seamlessly protects acronyms (AES, BCA, PDF, HTML), brand names (GitHub, iPhone), and manual user edits.
2. **Normal Text**: If a word is completely lowercase, its first letter is Title Cased (e.g., `john doe` -> `John Doe`).
3. **Sentence Formatting**: Only the first letter of a sentence is capitalized, and all whitespaces/line-breaks are strictly preserved. Existing uppercase letters within the sentence are left untouched.

### Applied Fields
- **Name Formatter**: Applied to Name, Location, Languages, Company, Position, Institution, Board, Degree, Project Name.
- **Sentence Formatter**: Applied to Career Objective, Experience Description, Education Description, Project Description.

### Examples
- Input `john doe` → Output `John Doe` (Rule 2)
- Input `AES National College` → Output `AES National College` (Rule 1 protects AES)
- Input `van der Sar` → Output `Van Der Sar` (Rule 2 applied per word)
- Input `GitHub Repo` → Output `GitHub Repo` (Rule 1 protects GitHub)

---

## 3. Resume Layout Improvements

To prevent resumes from wrapping excessively and overflowing onto a second page, Android implemented several layout constraints:

### Export Formatting & Layout Calculations
- **Density Scaling Fix**: The primary cause of overflow was the device's display density incorrectly scaling the PDF rendering canvas. The Android fix calculates the exact `dp` width based on the physical pixel width (2480px) divided by the `deviceDensity`. This ensures the UI renders exactly as A4 at 300DPI, rather than magnifying text bounds.
- **Fixed A4 Ratio**: The canvas height is strictly bound to `width * 1.4142` (A4 Ratio).

### Font & Spacing Adjustments
Templates (e.g., Minimal, Modern) were tightened:
- **Typography Limits**: Body text/descriptions are capped at `10pt/10sp`, subheadings at `11sp`, section headers at `13sp`, and main names at `24sp`.
- **Line Height**: Forced tight line-heights (`14sp` for a `10sp` font) to maximize vertical space.
- **Section Spacing**: Outer margins are set to `40dp`. Internal spacing between sections is aggressively reduced to `20dp`, and spacing between items within a section to `8dp`.

---

## 4. Other Resume Improvements

The following features were implemented in Android and should be ported to the web app:

1. **Section Visibility Toggles**: Users can individually hide/show entire sections (Experience, Education, Projects, Skills, Declaration) from their resume.
2. **Section Reordering**: Users can drag and drop to reorder individual items within Experience, Education, and Projects.
3. **Profile Image Cropper**: Users can upload and crop an image (1:1 aspect ratio) before it gets injected into templates (like the Persona template).
4. **Offline Mode**: The PDF exporter works entirely locally on the device canvas (similar to html2canvas/jsPDF on the web), requiring no backend generation.
