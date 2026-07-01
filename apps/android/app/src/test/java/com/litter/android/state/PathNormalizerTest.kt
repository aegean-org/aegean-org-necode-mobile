package com.litter.android.state

import org.junit.Assert.assertEquals
import org.junit.Test

class PathNormalizerTest {
    @Test
    fun normalizeRemovesRepeatedWindowsAbsolutePathSegments() {
        val path = "d:\\project\\iNexGrow-code\\d:\\project\\iNexGrow-code\\d:\\project\\iNexGrow-code"

        assertEquals("d:\\project\\iNexGrow-code", PathNormalizer.normalize(path))
    }

    @Test
    fun normalizePreservesLastRepeatedWindowsAbsolutePathSegment() {
        val path = "d:\\project\\iNexGrow-code\\d:\\project\\iNexGrow-code\\frontend"

        assertEquals("d:\\project\\iNexGrow-code\\frontend", PathNormalizer.normalize(path))
    }

    @Test
    fun normalizeRemovesMixedSeparatorRepeatedWindowsAbsolutePathSegments() {
        val path = "d:\\project\\iNexGrow-code/d:\\project\\iNexGrow-code"

        assertEquals("d:\\project\\iNexGrow-code", PathNormalizer.normalize(path))
    }

    @Test
    fun detectsWindowsAbsolutePath() {
        assertEquals(true, PathNormalizer.isWindowsAbsolute("D:\\project"))
        assertEquals(false, PathNormalizer.isWindowsAbsolute("project"))
    }

    @Test
    fun normalizeKeepsDriveRootTrailingSeparator() {
        assertEquals("D:\\", PathNormalizer.normalize("D:\\"))
    }

    @Test
    fun normalizeTrimsTrailingSeparatorsFromNormalPaths() {
        assertEquals("/Users/dev/project", PathNormalizer.normalize("/Users/dev/project///"))
    }
}
