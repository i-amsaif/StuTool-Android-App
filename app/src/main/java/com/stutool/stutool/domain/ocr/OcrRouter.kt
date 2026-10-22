package com.stutool.stutool.domain.ocr

import android.content.Context
import com.stutool.stutool.data.local.OcrPreferences
import com.stutool.stutool.data.remote.ocr.OcrSpaceClient

/**
 * Centralized, shared OCR routing engine for StuTool v1.2.0.
 *
 * Evaluates routing in the strict required sequence:
 * 1. Native text check (if the document already contains authentic extractable text, OCR is not needed).
 * 2. Explicit user offline choice (if user requested "process on-device only", zero network requests).
 * 3. File eligibility (file size <= 1 MB and page count <= 3 pages for OCR.space limits).
 * 4. Validated connectivity (NET_CAPABILITY_VALIDATED internet check).
 * 5. Prior user consent (has user previously accepted the online OCR disclosure?).
 *
 * Cloud OCR is an optimization, not a dependency:
 * - Offline / airplane mode automatically uses local OCR with zero popups or errors.
 * - Ineligible files automatically use local OCR with zero popups.
 * - Eligible files online without prior consent trigger the one-time consent dialog.
 */
object OcrRouter {
    const val MAX_CLOUD_FILE_SIZE_BYTES = 1048576L // 1 MB
    const val MAX_CLOUD_PAGES = 3

    sealed class RouteDecision {
        object NativeTextSufficient : RouteDecision()
        data class UseLocal(val reason: LocalReason) : RouteDecision()
        object NeedsConsent : RouteDecision()
        object UseCloud : RouteDecision()
    }

    enum class LocalReason {
        USER_EXPLICIT_OFFLINE,
        INELIGIBLE_FILE_SIZE,
        INELIGIBLE_PAGE_COUNT,
        OFFLINE_NO_INTERNET,
        CONSENT_DECLINED
    }

    /**
     * Evaluates OCR routing for the current document/page.
     */
    fun evaluate(
        context: Context,
        fileSizeBytes: Long,
        pageCount: Int,
        hasExtractableText: Boolean = false,
        userForceOffline: Boolean = false,
        ocrPreferences: OcrPreferences = OcrPreferences(context),
        isNetworkAvailable: (Context) -> Boolean = { OcrSpaceClient.isNetworkAvailable(it) }
    ): RouteDecision {
        // 1. Native text check
        if (hasExtractableText) {
            return RouteDecision.NativeTextSufficient
        }

        // 2. User explicit offline choice for this operation
        if (userForceOffline) {
            return RouteDecision.UseLocal(LocalReason.USER_EXPLICIT_OFFLINE)
        }

        // 3. File eligibility checks
        if (fileSizeBytes > MAX_CLOUD_FILE_SIZE_BYTES) {
            return RouteDecision.UseLocal(LocalReason.INELIGIBLE_FILE_SIZE)
        }
        if (pageCount > MAX_CLOUD_PAGES) {
            return RouteDecision.UseLocal(LocalReason.INELIGIBLE_PAGE_COUNT)
        }

        // 4. Validated internet connectivity check
        if (!isNetworkAvailable(context)) {
            return RouteDecision.UseLocal(LocalReason.OFFLINE_NO_INTERNET)
        }

        // 5. User consent check
        if (!ocrPreferences.isCloudOptInAcknowledged()) {
            return RouteDecision.NeedsConsent
        }

        // All conditions satisfied: use Cloud OCR
        return RouteDecision.UseCloud
    }
}
