# StuTool - UI/UX Design System & Style Guide

This document serves as the comprehensive design system and style guide for **StuTool**. It is intended to be used as context for AI agents or developers when creating or redesigning new pages for the app, ensuring perfect consistency with the existing user interface.

## 1. Core Design Philosophy
* **Minimalist & Utility-Driven:** The UI is designed to be clean, distraction-free, and highly functional for productivity tasks (PDF manipulation, resume building).
* **Geometry:** Heavy use of rounded corners. Buttons are fully rounded (pill-shaped), and cards/containers use soft, large border radii.
* **Dual Theme:** The app natively supports both **Dark Mode** (true black/dark grey) and **Light Mode** (pure white/light grey).
* **Typography:** **Strictly Adaptive.** The font family *must* always default to the user's system font (e.g., San Francisco on iOS, Roboto/Inter on Android). Do not hardcode custom font families.

---

## 2. Color Palette

The app relies on a neutral monochromatic base (black/white/greys) accented by a vibrant, signature "StuTool Red/Coral".

### Primary Brand Colors (Both Modes)
* **Primary Accent (StuTool Red):** `~#E34A4A` to `#E63946` (A vibrant, slightly soft coral-red used for primary buttons, active states, and core branding).
* **Success Green:** `~#22C55E` or `#10B981` (Used for checkmarks, e.g., "ATS-Friendly", "Instant PDF").

### Dark Mode Theme
* **Background (App Base):** `#000000` (True Black) or `#090909`
* **Surface 1 (Cards, Modals):** `#1C1C1E` or `#121212` (Dark grey, used for feature cards and bottom navigation backgrounds)
* **Surface 2 (Slightly elevated elements):** `#2A2A2C` (Used for inactive icon backgrounds or secondary containers)
* **Text - Primary:** `#FFFFFF` (Pure white for headings and main button text)
* **Text - Secondary:** `#A0A0A5` or `#8E8E93` (Muted grey for descriptions, subtitles, and inactive tabs)
* **Borders/Dividers:** `#2C2C2E` (Very subtle dark borders, if needed)
* **Selection/Active State Background:** `#351A1A` (Very dark, low-opacity red tint for selected cards/chips)

### Light Mode Theme
* **Background (App Base):** `#FFFFFF` (Pure White) or `#F9F9FB`
* **Surface 1 (Cards, Modals):** `#FFFFFF` (Pure white, typically distinguished from a slightly off-white background via subtle borders or very faint drop shadows)
* **Surface 2 (Inputs, Inactive elements):** `#F2F2F7` or `#F4F4F5`
* **Text - Primary:** `#000000` or `#1C1C1E` (Dark for high contrast)
* **Text - Secondary:** `#6B6B70` or `#8E8E93` (Medium grey for body text and descriptions)
* **Borders/Dividers:** `#E5E5EA` (Light grey for card outlines and chip borders)
* **Selection/Active State Background:** `#FDEAEA` or `#FFE5E5` (Soft, very light pink/red background for active chips like "4 Pages" or "Portrait")

---

## 3. Typography Rules
*Reminder: Font Family is ALWAYS the system default.*

* **App Header / Logo Text:** Semi-bold or Bold, 20pt.
* **H1 (Page Titles - e.g., "Build Your Resume"):** Bold, large scale (e.g., 28pt-32pt). Noticeable tracking/kerning.
* **H2 (Section Titles - e.g., "Featured Tools", "PDF Tools"):** Bold, 20pt-22pt.
* **Card Titles (e.g., "Merge PDF"):** Semi-bold, 16pt-18pt.
* **Body Text (Descriptions):** Regular weight, 14pt. Uses Secondary Text color.
* **Button Text:** Semi-bold, 16pt.
* **Microcopy / Step Labels (e.g., "How it works"):** Bold, 16pt for titles, 13pt Regular for descriptions.

---

## 4. UI Components & Geometry

### Buttons
* **Primary Action Buttons (e.g., "Create Resume", "Select PDF Files"):**
    * **Shape:** Pill-shaped (`border-radius: 9999px` or completely rounded ends).
    * **Background:** Solid Primary Accent (StuTool Red). Sometimes has a very subtle gradient but assume solid for base UI generation.
    * **Text:** White (in both Dark and Light modes), centered, semi-bold.
    * **Padding:** Generous vertical padding (approx. 16px - 18px).
* **Secondary/Tertiary Actions (e.g., Header icons):** Icon-only, uses Primary Text color, no background.

### Cards (Feature Grids)
* **Shape:** Large rounded corners (`border-radius: 16px` to `20px`).
* **Background:** * Dark Mode: `#1C1C1E`
    * Light Mode: `#FFFFFF` with a `1px solid #E5E5EA` border.
* **Padding:** ~20px inner padding.
* **Icons within Cards:** Placed at the top-left. Contained inside a smaller rounded square (`border-radius: 10px-12px`). Background of this icon container is the Primary Accent (StuTool Red), and the icon itself is white.

### File Upload Dropzones
* **Shape:** Large rounded rectangle (`border-radius: 20px`).
* **Border:** Dashed outline (`border: 2px dashed`).
    * Dark Mode: Muted/dark red border.
    * Light Mode: Soft pink/light red border.
* **Content:** Centered upload icon (usually inside a soft red circular background) with title and description stacked vertically.

### Selection Chips / Toggles (e.g., Page Orientation, Settings)
* **Shape:** Rounded rectangles (`border-radius: 12px`).
* **Inactive State:** * Dark Mode: Transparent background, subtle grey border.
    * Light Mode: Transparent background, light grey border.
    * Text: Secondary Text color.
* **Active/Selected State:**
    * Border: Solid Primary Accent (StuTool Red).
    * Background: Very subtle red tint (Dark Mode: `#351A1A`, Light Mode: `#FDEAEA`).
    * Text: Primary Accent (StuTool Red).

### Bottom Navigation Bar
* **Container:** Floats slightly or sits at the bottom with a distinct background (Surface 1). 
* **Shape:** The nav container itself has heavily rounded corners (`border-radius: 24px` or similar) and sits detached from the exact edges of the screen, acting as a floating pill-like container (especially visible in some screenshots).
* **Icons:** * Inactive: Outline style, Secondary Text color.
    * Active: Filled style, Primary Accent (StuTool Red). In some versions, the active tab has a circular/pill background tint behind the icon.

### "How it Works" List
* **Step Numbers:** Contained within small circles. The circle background is a very soft red tint, and the number is the Primary Accent (StuTool Red).
* **Layout:** Horizontal flex: Step number on the left, Title and Description stacked vertically on the right, optional small trailing icon on the far right.

---

## 5. Layout & Spacing System
* **Screen Margins:** Consistent left and right padding across the entire app (approx. 16px - 20px).
* **Vertical Rhythm:** Generous spacing between distinct sections (e.g., 24px - 32px between the Hero section and "Featured Tools").
* **Grid System:** Feature cards (PDF Viewer, PDF Tools, etc.) are laid out in a responsive grid, typically 2 columns on mobile devices with ~16px gap between cards.
