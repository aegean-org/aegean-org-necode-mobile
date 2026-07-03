package com.litter.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecyclePolicyTest {
    @Test
    fun keepsAppModelRunningDuringConfigurationChangeDestroy() {
        assertFalse(shouldStopAppModelOnDestroy(isChangingConfigurations = true))
    }

    @Test
    fun stopsAppModelDuringRealActivityDestroy() {
        assertTrue(shouldStopAppModelOnDestroy(isChangingConfigurations = false))
    }
}
