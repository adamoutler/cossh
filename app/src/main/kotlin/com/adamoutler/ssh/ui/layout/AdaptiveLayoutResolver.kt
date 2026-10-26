package com.adamoutler.ssh.ui.layout

/**
 * Adaptive display layout mode for varied screen geometries, fold postures, and desktop windowing.
 */
enum class AdaptiveLayoutMode {
    SINGLE_PANE_COMPACT,
    DUAL_PANE_MEDIUM,
    EXPANDED_DESKTOP_DEX,
    TABLETOP_HALF_OPEN,
    BOOK_POSTURE_SPLIT,
}

/**
 * Physical orientation of the hinge or folding feature.
 */
enum class HingeOrientation {
    HORIZONTAL,
    VERTICAL,
}

/**
 * Posture state of a foldable device hinge.
 */
enum class HingePostureState {
    HALF_OPENED,
    FLAT,
    CLOSED,
}

/**
 * Occlusion type indicating whether the hinge physically obscures screen content.
 */
enum class HingeOcclusionType {
    NONE,
    FULL,
}

/**
 * Bounding rectangle of the physical hinge or fold in density-independent pixels.
 */
data class HingeBoundsDp(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * Information describing a detected foldable hinge or display feature.
 */
data class FoldingFeatureInfo(
    val isSeparating: Boolean = false,
    val orientation: HingeOrientation = HingeOrientation.HORIZONTAL,
    val posture: HingePostureState = HingePostureState.FLAT,
    val occlusionType: HingeOcclusionType = HingeOcclusionType.NONE,
    val boundsDp: HingeBoundsDp = HingeBoundsDp(0f, 0f, 0f, 0f),
)

/**
 * Snapshot of the host device's environment and window properties.
 */
data class DeviceEnvironmentInfo(
    val windowWidthDp: Float,
    val windowHeightDp: Float,
    val isSamsungDeX: Boolean = false,
    val isChromeOs: Boolean = false,
    val hasHardwareKeyboard: Boolean = false,
    val foldingFeature: FoldingFeatureInfo? = null,
)

/**
 * Spatial rectangle in density-independent pixels.
 */
data class LayoutRectDp(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * Resolved adaptive layout configuration for UI placement.
 */
data class AdaptiveLayoutState(
    val mode: AdaptiveLayoutMode,
    val primaryPaneRect: LayoutRectDp,
    val secondaryPaneRect: LayoutRectDp? = null,
    val shouldShowVirtualExtraKeys: Boolean,
    val isHingeOccluded: Boolean = false,
    val hingeBounds: HingeBoundsDp? = null,
)

/**
 * Resolves screen geometry, foldable postures (tabletop / book), and desktop environments (DeX / ChromeOS)
 * into optimal layout partitions and virtual keyboard visibility rules.
 */
object AdaptiveLayoutResolver {

    const val COMPACT_MAX_WIDTH_DP = 600f
    const val EXPANDED_MIN_WIDTH_DP = 840f

    /**
     * Resolves the target [AdaptiveLayoutMode] given the device environment.
     */
    fun resolveMode(environment: DeviceEnvironmentInfo): AdaptiveLayoutMode {
        val fold = environment.foldingFeature
        if (fold != null) {
            if (fold.posture == HingePostureState.HALF_OPENED) {
                return if (fold.orientation == HingeOrientation.HORIZONTAL) {
                    AdaptiveLayoutMode.TABLETOP_HALF_OPEN
                } else {
                    AdaptiveLayoutMode.BOOK_POSTURE_SPLIT
                }
            }
            if (fold.isSeparating) {
                return if (fold.orientation == HingeOrientation.HORIZONTAL) {
                    AdaptiveLayoutMode.TABLETOP_HALF_OPEN
                } else {
                    AdaptiveLayoutMode.BOOK_POSTURE_SPLIT
                }
            }
        }

        if (environment.isSamsungDeX || environment.isChromeOs || environment.windowWidthDp >= EXPANDED_MIN_WIDTH_DP) {
            return AdaptiveLayoutMode.EXPANDED_DESKTOP_DEX
        }

        if (environment.windowWidthDp >= COMPACT_MAX_WIDTH_DP) {
            return AdaptiveLayoutMode.DUAL_PANE_MEDIUM
        }

        return AdaptiveLayoutMode.SINGLE_PANE_COMPACT
    }

    /**
     * Computes pane bounds, occlusion, and virtual keyboard visibility.
     */
    fun resolveLayout(environment: DeviceEnvironmentInfo): AdaptiveLayoutState {
        val mode = resolveMode(environment)
        val fold = environment.foldingFeature

        val shouldShowVirtualKeys = if (mode == AdaptiveLayoutMode.TABLETOP_HALF_OPEN) {
            true
        } else {
            !environment.hasHardwareKeyboard
        }

        val isOccluded = fold?.occlusionType == HingeOcclusionType.FULL
        val hingeBounds = fold?.boundsDp

        return when (mode) {
            AdaptiveLayoutMode.TABLETOP_HALF_OPEN -> {
                val topBoundary = fold?.boundsDp?.top ?: (environment.windowHeightDp / 2f)
                val primaryHeight = if (topBoundary > 0f) topBoundary else (environment.windowHeightDp / 2f)
                val bottomBoundary = fold?.boundsDp?.bottom ?: (environment.windowHeightDp / 2f)
                val secondaryY = if (bottomBoundary > 0f) bottomBoundary else (environment.windowHeightDp / 2f)
                val secondaryHeight = (environment.windowHeightDp - secondaryY).coerceAtLeast(0f)

                AdaptiveLayoutState(
                    mode = mode,
                    primaryPaneRect = LayoutRectDp(
                        x = 0f,
                        y = 0f,
                        width = environment.windowWidthDp,
                        height = primaryHeight,
                    ),
                    secondaryPaneRect = LayoutRectDp(
                        x = 0f,
                        y = secondaryY,
                        width = environment.windowWidthDp,
                        height = secondaryHeight,
                    ),
                    shouldShowVirtualExtraKeys = shouldShowVirtualKeys,
                    isHingeOccluded = isOccluded,
                    hingeBounds = hingeBounds,
                )
            }

            AdaptiveLayoutMode.BOOK_POSTURE_SPLIT -> {
                val leftBoundary = fold?.boundsDp?.left ?: (environment.windowWidthDp / 2f)
                val primaryWidth = if (leftBoundary > 0f) leftBoundary else (environment.windowWidthDp / 2f)
                val rightBoundary = fold?.boundsDp?.right ?: (environment.windowWidthDp / 2f)
                val secondaryX = if (rightBoundary > 0f) rightBoundary else (environment.windowWidthDp / 2f)
                val secondaryWidth = (environment.windowWidthDp - secondaryX).coerceAtLeast(0f)

                AdaptiveLayoutState(
                    mode = mode,
                    primaryPaneRect = LayoutRectDp(
                        x = 0f,
                        y = 0f,
                        width = primaryWidth,
                        height = environment.windowHeightDp,
                    ),
                    secondaryPaneRect = LayoutRectDp(
                        x = secondaryX,
                        y = 0f,
                        width = secondaryWidth,
                        height = environment.windowHeightDp,
                    ),
                    shouldShowVirtualExtraKeys = shouldShowVirtualKeys,
                    isHingeOccluded = isOccluded,
                    hingeBounds = hingeBounds,
                )
            }

            AdaptiveLayoutMode.DUAL_PANE_MEDIUM -> {
                val halfWidth = environment.windowWidthDp / 2f
                AdaptiveLayoutState(
                    mode = mode,
                    primaryPaneRect = LayoutRectDp(
                        x = 0f,
                        y = 0f,
                        width = halfWidth,
                        height = environment.windowHeightDp,
                    ),
                    secondaryPaneRect = LayoutRectDp(
                        x = halfWidth,
                        y = 0f,
                        width = halfWidth,
                        height = environment.windowHeightDp,
                    ),
                    shouldShowVirtualExtraKeys = shouldShowVirtualKeys,
                    isHingeOccluded = false,
                    hingeBounds = null,
                )
            }

            AdaptiveLayoutMode.EXPANDED_DESKTOP_DEX -> {
                AdaptiveLayoutState(
                    mode = mode,
                    primaryPaneRect = LayoutRectDp(
                        x = 0f,
                        y = 0f,
                        width = environment.windowWidthDp,
                        height = environment.windowHeightDp,
                    ),
                    secondaryPaneRect = null,
                    shouldShowVirtualExtraKeys = shouldShowVirtualKeys,
                    isHingeOccluded = false,
                    hingeBounds = null,
                )
            }

            AdaptiveLayoutMode.SINGLE_PANE_COMPACT -> {
                AdaptiveLayoutState(
                    mode = mode,
                    primaryPaneRect = LayoutRectDp(
                        x = 0f,
                        y = 0f,
                        width = environment.windowWidthDp,
                        height = environment.windowHeightDp,
                    ),
                    secondaryPaneRect = null,
                    shouldShowVirtualExtraKeys = shouldShowVirtualKeys,
                    isHingeOccluded = false,
                    hingeBounds = null,
                )
            }
        }
    }
}
