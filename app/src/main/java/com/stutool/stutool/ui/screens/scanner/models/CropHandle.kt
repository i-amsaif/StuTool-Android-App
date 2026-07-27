package com.stutool.stutool.ui.screens.scanner.models

/**
 * Semantic identity for crop handles.
 * Replaces bare integer indices to prevent handle teleportation and logic errors during mode switches.
 */
enum class CropHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    TOP,
    RIGHT,
    BOTTOM,
    LEFT;

    val isCorner: Boolean
        get() = this == TOP_LEFT || this == TOP_RIGHT || this == BOTTOM_RIGHT || this == BOTTOM_LEFT

    val isEdge: Boolean
        get() = !isCorner
}
