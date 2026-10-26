package com.adamoutler.ssh.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutResolverTest {

    @Test
    fun resolveMode_compactPhone_returnsSinglePaneCompact() {
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 390f,
            windowHeightDp = 844f,
            hasHardwareKeyboard = false,
        )

        val mode = AdaptiveLayoutResolver.resolveMode(env)
        val state = AdaptiveLayoutResolver.resolveLayout(env)

        assertEquals(AdaptiveLayoutMode.SINGLE_PANE_COMPACT, mode)
        assertEquals(AdaptiveLayoutMode.SINGLE_PANE_COMPACT, state.mode)
        assertEquals(390f, state.primaryPaneRect.width, 0.01f)
        assertEquals(844f, state.primaryPaneRect.height, 0.01f)
        assertNull(state.secondaryPaneRect)
        assertTrue(state.shouldShowVirtualExtraKeys)
        assertFalse(state.isHingeOccluded)
        assertNull(state.hingeBounds)
    }

    @Test
    fun resolveMode_compactPhoneWithHardwareKeyboard_hidesVirtualKeys() {
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 412f,
            windowHeightDp = 915f,
            hasHardwareKeyboard = true,
        )

        val state = AdaptiveLayoutResolver.resolveLayout(env)

        assertEquals(AdaptiveLayoutMode.SINGLE_PANE_COMPACT, state.mode)
        assertFalse(state.shouldShowVirtualExtraKeys)
    }

    @Test
    fun resolveMode_mediumTablet_returnsDualPaneMedium() {
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 700f,
            windowHeightDp = 1000f,
            hasHardwareKeyboard = false,
        )

        val mode = AdaptiveLayoutResolver.resolveMode(env)
        val state = AdaptiveLayoutResolver.resolveLayout(env)

        assertEquals(AdaptiveLayoutMode.DUAL_PANE_MEDIUM, mode)
        assertEquals(AdaptiveLayoutMode.DUAL_PANE_MEDIUM, state.mode)
        assertEquals(350f, state.primaryPaneRect.width, 0.01f)
        assertEquals(1000f, state.primaryPaneRect.height, 0.01f)
        assertNotNull(state.secondaryPaneRect)
        assertEquals(350f, state.secondaryPaneRect?.x ?: 0f, 0.01f)
        assertEquals(350f, state.secondaryPaneRect?.width ?: 0f, 0.01f)
        assertTrue(state.shouldShowVirtualExtraKeys)
    }

    @Test
    fun resolveMode_expandedDesktop_returnsExpandedDesktopDex() {
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 1200f,
            windowHeightDp = 800f,
            hasHardwareKeyboard = true,
        )

        val mode = AdaptiveLayoutResolver.resolveMode(env)
        val state = AdaptiveLayoutResolver.resolveLayout(env)

        assertEquals(AdaptiveLayoutMode.EXPANDED_DESKTOP_DEX, mode)
        assertEquals(1200f, state.primaryPaneRect.width, 0.01f)
        assertNull(state.secondaryPaneRect)
        assertFalse(state.shouldShowVirtualExtraKeys)
    }

    @Test
    fun resolveMode_samsungDeXEnvironment_returnsExpandedDesktopDex() {
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 600f,
            windowHeightDp = 400f,
            isSamsungDeX = true,
            hasHardwareKeyboard = true,
        )

        val mode = AdaptiveLayoutResolver.resolveMode(env)
        assertEquals(AdaptiveLayoutMode.EXPANDED_DESKTOP_DEX, mode)
    }

    @Test
    fun resolveMode_chromeOsEnvironment_returnsExpandedDesktopDex() {
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 500f,
            windowHeightDp = 400f,
            isChromeOs = true,
            hasHardwareKeyboard = false,
        )

        val state = AdaptiveLayoutResolver.resolveLayout(env)
        assertEquals(AdaptiveLayoutMode.EXPANDED_DESKTOP_DEX, state.mode)
        assertTrue(state.shouldShowVirtualExtraKeys)
    }

    @Test
    fun resolveMode_foldableHalfOpenedTabletop_returnsTabletopHalfOpen() {
        val hinge = FoldingFeatureInfo(
            isSeparating = false,
            orientation = HingeOrientation.HORIZONTAL,
            posture = HingePostureState.HALF_OPENED,
            occlusionType = HingeOcclusionType.NONE,
            boundsDp = HingeBoundsDp(left = 0f, top = 400f, right = 600f, bottom = 440f),
        )
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 600f,
            windowHeightDp = 840f,
            hasHardwareKeyboard = true, // Virtual extra keys must still show in tabletop
            foldingFeature = hinge,
        )

        val mode = AdaptiveLayoutResolver.resolveMode(env)
        val state = AdaptiveLayoutResolver.resolveLayout(env)

        assertEquals(AdaptiveLayoutMode.TABLETOP_HALF_OPEN, mode)
        assertEquals(AdaptiveLayoutMode.TABLETOP_HALF_OPEN, state.mode)
        assertEquals(0f, state.primaryPaneRect.y, 0.01f)
        assertEquals(400f, state.primaryPaneRect.height, 0.01f)
        assertNotNull(state.secondaryPaneRect)
        assertEquals(440f, state.secondaryPaneRect?.y ?: 0f, 0.01f)
        assertEquals(400f, state.secondaryPaneRect?.height ?: 0f, 0.01f)
        assertTrue("Tabletop mode always hosts virtual extra keys in bottom pane", state.shouldShowVirtualExtraKeys)
        assertFalse(state.isHingeOccluded)
        assertEquals(40f, state.hingeBounds?.height ?: 0f, 0.01f)
    }

    @Test
    fun resolveMode_foldableHalfOpenedBook_returnsBookPostureSplit() {
        val hinge = FoldingFeatureInfo(
            isSeparating = true,
            orientation = HingeOrientation.VERTICAL,
            posture = HingePostureState.HALF_OPENED,
            occlusionType = HingeOcclusionType.FULL,
            boundsDp = HingeBoundsDp(left = 340f, top = 0f, right = 360f, bottom = 800f),
        )
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 700f,
            windowHeightDp = 800f,
            hasHardwareKeyboard = false,
            foldingFeature = hinge,
        )

        val mode = AdaptiveLayoutResolver.resolveMode(env)
        val state = AdaptiveLayoutResolver.resolveLayout(env)

        assertEquals(AdaptiveLayoutMode.BOOK_POSTURE_SPLIT, mode)
        assertEquals(340f, state.primaryPaneRect.width, 0.01f)
        assertNotNull(state.secondaryPaneRect)
        assertEquals(360f, state.secondaryPaneRect?.x ?: 0f, 0.01f)
        assertEquals(340f, state.secondaryPaneRect?.width ?: 0f, 0.01f)
        assertTrue(state.isHingeOccluded)
        assertEquals(20f, state.hingeBounds?.width ?: 0f, 0.01f)
        assertTrue(state.shouldShowVirtualExtraKeys)
    }

    @Test
    fun resolveMode_separatingFlatHingeHorizontal_returnsTabletopHalfOpen() {
        val hinge = FoldingFeatureInfo(
            isSeparating = true,
            orientation = HingeOrientation.HORIZONTAL,
            posture = HingePostureState.FLAT,
            boundsDp = HingeBoundsDp(left = 0f, top = 500f, right = 800f, bottom = 520f),
        )
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 800f,
            windowHeightDp = 1000f,
            foldingFeature = hinge,
        )

        val mode = AdaptiveLayoutResolver.resolveMode(env)
        assertEquals(AdaptiveLayoutMode.TABLETOP_HALF_OPEN, mode)
    }

    @Test
    fun resolveMode_separatingFlatHingeVertical_returnsBookPostureSplit() {
        val hinge = FoldingFeatureInfo(
            isSeparating = true,
            orientation = HingeOrientation.VERTICAL,
            posture = HingePostureState.FLAT,
            boundsDp = HingeBoundsDp(left = 400f, top = 0f, right = 420f, bottom = 1000f),
        )
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 820f,
            windowHeightDp = 1000f,
            foldingFeature = hinge,
        )

        val mode = AdaptiveLayoutResolver.resolveMode(env)
        assertEquals(AdaptiveLayoutMode.BOOK_POSTURE_SPLIT, mode)
    }

    @Test
    fun resolveLayout_zeroBoundsHinge_defaultsToHalfSplit() {
        val hinge = FoldingFeatureInfo(
            isSeparating = false,
            orientation = HingeOrientation.HORIZONTAL,
            posture = HingePostureState.HALF_OPENED,
            boundsDp = HingeBoundsDp(0f, 0f, 0f, 0f),
        )
        val env = DeviceEnvironmentInfo(
            windowWidthDp = 600f,
            windowHeightDp = 800f,
            foldingFeature = hinge,
        )

        val state = AdaptiveLayoutResolver.resolveLayout(env)
        assertEquals(400f, state.primaryPaneRect.height, 0.01f)
        assertEquals(400f, state.secondaryPaneRect?.height ?: 0f, 0.01f)
    }
}
