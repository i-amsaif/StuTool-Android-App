# StuTool PDF Tools - Master QA Test Plan

## 1. Tool Overview

### 1. PDF Viewer
- **Purpose**: View, read, navigate, and bookmark PDF documents.
- **Processing Flow**: The user opens a PDF (via internal file browser, Recent PDFs list, or external Intent like Chrome share). The `PdfReaderViewModel` loads the file. If encrypted, it prompts for a password, uses PDFBox to decrypt a temporary copy, and then loads it via Android's native `PdfRenderer`. It tracks progress via `PdfProgressRepository` and saves history via `RecentPdfRepository`.
- **Files Involved**: `PdfReaderScreen.kt`, `PdfReaderViewModel.kt`, `RecentPdfRepository.kt`, `PdfProgressRepository.kt`.

### 2. Merge PDF
- **Purpose**: Combine two or more PDF files into a single document.
- **Processing Flow**: User selects multiple PDFs. The `MergePdfViewModel` validates inputs. It then passes URIs to `PdfEngine.mergePdfs`, which uses PDFBox's `PDFMergerUtility` with a hybrid memory strategy (`setupMixed(50MB)`) to combine them, finally saving via Storage Access Framework (SAF).
- **Files Involved**: `MergePdfScreen.kt`, `MergePdfViewModel.kt`, `PdfEngine.kt`.

### 3. Compress PDF
- **Purpose**: Reduce the file size of a PDF by scaling down and compressing its embedded images.
- **Processing Flow**: User selects a PDF and configures compression quality/scale. `PdfEngine.compressPdf` uses PDFBox to iterate through every page's `PDResources`. It extracts `PDImageXObject`s, converts them to Android Bitmaps, scales/compresses them via JPEG, and injects them back into the PDF dictionary.
- **Files Involved**: `CompressPdfScreen.kt`, `CompressPdfViewModel.kt`, `PdfEngine.kt`.

### 4. Extract Pages
- **Purpose**: Create a new PDF containing only a specific subset of pages from the source PDF.
- **Processing Flow**: User selects a PDF and chooses pages to extract. `PdfEngine.extractPages` loads the source using PDFBox, creates a new `PDDocument`, iterates through the requested 0-indexed pages, appends them, and saves the output.
- **Files Involved**: `ExtractPagesScreen.kt`, `ExtractPagesViewModel.kt`, `PdfEngine.kt`.

### 5. Image to PDF
- **Purpose**: Convert one or multiple image files into PDF format (either merged into one PDF or output as separate PDFs).
- **Processing Flow**: User selects images and configures layout (orientation, size, margins). `ImageToPdfViewModel` natively decodes images using `BitmapFactory` with `inSampleSize` memory limits, draws them onto a native Android `android.graphics.pdf.PdfDocument` canvas, and exports the PDF.
- **Files Involved**: `ImageToPdfScreen.kt`, `ImageToPdfViewModel.kt`.

### 6. Pages per Sheet (N-Up Layout)
- **Purpose**: Combine multiple PDF pages or images into a grid layout (e.g., 2, 4, 9 pages per sheet).
- **Processing Flow**: User selects PDFs/images. `PagesPerSheetViewModel` extracts all pages as Bitmaps using `PdfRenderer` and stores them. It then creates a native Android `PdfDocument`, calculates grid cells based on reading direction, draws the Bitmaps scaled to fit, and saves the result.
- **Files Involved**: `PagesPerSheetScreen.kt`, `PagesPerSheetViewModel.kt`.

---

## 2. Functional Tests

For **each tool**, perform the following functional validations:
- **Normal Workflow**: Run the tool with standard inputs (e.g., a 5-page text PDF, or 3 standard JPEGs) and verify the output matches expectations.
- **Minimum Input**: Test the tool with the absolute minimum allowed input (e.g., merging exactly 2 PDFs, extracting exactly 1 page).
- **Maximum UI Input**: Max out UI configurations (e.g., 100% compression, largest margins, 16 pages per sheet).
- **Multiple Files**: Where supported (Merge, Image to PDF, Pages per Sheet), select 10+ files at once.
- **Different Page Counts**: Test with 1-page PDFs, odd-numbered page PDFs (e.g., 7 pages), and large PDFs (100+ pages).
- **Encrypted PDFs**: Supply a password-protected PDF to every tool. Ensure it gracefully prompts for a password (if supported) or clearly rejects the file with a sensible error message.
- **Scanned PDFs**: Provide PDFs where pages are entirely composed of single large images (to test compression algorithms and page extraction).
- **Digitally Created PDFs**: Provide standard text/vector PDFs.

---

## 3. Edge Cases

- **Empty Files**: Select a 0-byte PDF or Image. (Merge PDF has explicit validation for this; verify the others do not crash).
- **Corrupted PDFs**: Provide a file with a `.pdf` extension that contains random text data.
- **Duplicate Files**: Select the exact same file multiple times in a single operation.
- **Duplicate Page Selections**: In Extract Pages, select the same page multiple times (e.g., pages 1, 1, 1).
- **Unsupported Inputs**: Try to pass a `.docx` or `.gif` file via Intent filtering bypassing the standard picker.
- **Cancelled Operations**: Cancel the file picker, or press the back button while a heavy background operation is running.
- **Storage Permission Changes**: Revoke storage permissions in Android Settings while the app is in the background, then return to the tool.
- **Inaccessible URIs**: Use a file from Google Drive (via SAF), turn off Wi-Fi, and attempt to process it.
- **Cloud Providers**: Select files directly from OneDrive, Dropbox, or Google Drive via the Android file picker.
- **Renamed/Deleted Files**: Select a file, use a file manager to delete or rename it in the background, then press "Start/Merge" in the app.
- **Extremely Long Filenames**: Process a file with a 200+ character name to ensure SAF saving does not crash.

---

## 4. Stress Tests

- **1000-Page PDFs**: Pass a massive text-based PDF to Viewer, Extract Pages, and Pages per Sheet.
- **200MB+ PDFs**: Pass a gigantic file to Merge and Compress tools to ensure OOM limits (like the hybrid memory setting) are functioning correctly.
- **Many PDFs at Once**: Merge 50+ small PDFs in a single operation.
- **Repeated Operations**: Run Compress PDF on the same file 10 times consecutively without leaving the screen.
- **Rapid Button Taps**: Spam tap the "Merge", "Extract", or "Compress" button to ensure multiple coroutines do not spawn simultaneously.
- **Repeated Open/Close Cycles**: Open a large PDF in the Viewer, immediately close it, and repeat 20 times to hunt for `ParcelFileDescriptor` or `PdfRenderer` leaks.
- **Low Storage Situations**: Attempt to generate a 50MB merged PDF on an emulator/device with only 10MB of free space left.

---

## 5. Performance Tests

During stress tests, use Android Studio Profiler to observe:
- **Startup Time**: Time taken from tapping the tool icon to the UI being fully responsive.
- **Processing Time**: Compare the time taken to compress a 50MB PDF vs merging two 25MB PDFs.
- **Memory Behaviour**: Watch the Java Heap. Specifically monitor the **Pages per Sheet** tool, which may aggressively load bitmaps into RAM. Verify memory drops back to baseline after the tool finishes.
- **CPU Usage**: Ensure heavy PDFBox processing is offloaded to `Dispatchers.IO` and does not cause main thread frame drops (jank).
- **Temporary Storage Usage**: Check the app's cache directory during large operations (like Merge) to ensure temporary swap files are deleted upon completion or failure.
- **UI Responsiveness**: The UI must remain fluid and scrollable while processing bars are spinning.

---

## 6. Background & Lifecycle Tests

Start a heavy operation (e.g., compressing a 100MB PDF) and immediately:
- **Rotate Device**: Ensure the configuration change does not restart the operation or lose the `ViewModel` state.
- **Background App**: Press the Home button, wait 5 seconds, and return.
- **Kill App (System)**: Force a low-memory state so Android kills the app in the background, then restore it.
- **Incoming Phone Call**: Simulate a phone call overlaying the app.
- **Split-Screen**: Drag the app into split-screen mode while rendering a PDF.

---

## 7. Error Handling Tests

Force failures to verify the UI gracefully recovers:
- **Invalid Input**: See Edge Cases (corrupted/0-byte files).
- **Password-Protected PDFs**: Feed to tools that do not support them.
- **Storage Failures**: Manually delete the output directory (`Documents/StuTool`) mid-operation.
- **Permission Denial**: Deny scoped storage / SAF prompts.
- **Insufficient Storage**: Fill the disk completely.
- **User Cancellation**: Ensure backing out of a tool cancels the underlying coroutine and cleans up temporary streams.

---

## 8. UI / UX Tests

- **Loading Indicators**: Ensure circular/linear progress bars appear immediately upon action.
- **Disabled Buttons**: The main action button (e.g., "Merge") MUST be disabled while processing is active (`isMerging == true`).
- **Empty States**: Ensure screens look visually appealing when 0 files are selected.
- **Error Messages**: Ensure exceptions are translated into user-friendly strings (not raw Java stack traces).
- **Snackbars**: Verify Snackbars appear **above** the system navigation bar (using `.navigationBarsPadding()`).
- **Dialogs**: Test password prompts and confirmation dialogs for proper dismissal outside the bounds.
- **Accessibility**: Ensure TalkBack can read file names and button actions.
- **Dark/Light Themes**: Toggle system theme and ensure text remains readable (no dark text on dark backgrounds).
- **Animations**: Verify page transitions and list item reordering (drag-and-drop) animations are smooth.

---

## 9. Regression Checklist

*To be executed manually before every production release:*

- [ ] Open PDF Viewer: Verify normal PDF loads.
- [ ] Open PDF Viewer: Verify encrypted PDF unlocks.
- [ ] Open PDF Viewer: Verify Bookmarks persist.
- [ ] Open PDF Viewer: Verify file appears in Recent PDFs.
- [ ] Merge PDF: Successfully merge 2 valid PDFs.
- [ ] Merge PDF: Verify graceful error on 0-byte PDF.
- [ ] Compress PDF: Successfully compress 1 PDF; verify size reduction.
- [ ] Extract Pages: Extract pages 1 and 3 from a PDF; verify output.
- [ ] Image to PDF: Convert 2 images into 1 merged PDF.
- [ ] Pages per Sheet: Convert a 4-page PDF into a single 4-up grid page.
- [ ] General: Verify all outputs are correctly saved in `Documents/StuTool`.

---

## 10. Master Summary

**Complete List of PDF Tools:**
1. PDF Viewer
2. Merge PDF
3. Compress PDF
4. Extract Pages
5. Image to PDF
6. Pages per Sheet

**Estimated Testing Effort:**
- 2-3 Days for a single QA engineer to manually execute the full suite across 2 different physical devices (one low-end, one high-end).

**Highest-Risk Tools:**
1. **Pages per Sheet**: CRITICAL MEMORY RISK. The implementation in `PagesPerSheetViewModel.kt` reads every page of the input PDF into a massive `Bitmap`, and stores them all in a `mutableListOf<Bitmap>()` concurrently before generating the PDF. This will almost certainly result in an `OutOfMemoryError` if a user inputs a large PDF (e.g., 50+ pages). 
2. **PDF Viewer**: High state complexity regarding password decryption pipelines, bookmark identities, and URI persistence across Chrome/WhatsApp intents.
3. **Compress PDF**: CPU and memory intensive; iterating over raw XObjects can occasionally corrupt malformed PDFs.

**Recommended Testing Order:**
1. **PDF Viewer** (Core fundamental feature; if this breaks, users cannot verify outputs).
2. **Merge PDF** (Recently hardened; verify fixes hold).
3. **Pages per Sheet** (Stress test immediately to confirm the suspected OOM architectural flaw).
4. **Compress PDF** (Heavy load test).
5. **Extract Pages** (Low risk).
6. **Image to PDF** (Low risk).

**Special Attention Before Release:**
- The **Pages per Sheet** tool requires an immediate architectural review. Loading all input pages into memory simultaneously via `Bitmap.createBitmap()` will crash on low-end devices. It needs to be refactored to stream bitmaps one-by-one onto the output canvas rather than collecting them all upfront.
