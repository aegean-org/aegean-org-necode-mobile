package com.litter.android

/**
 * Returns whether Activity teardown should stop the shared app runtime.
 * Configuration changes recreate the Activity while the application remains
 * alive, so the remote endpoint must survive rotation.
 */
internal fun shouldStopAppModelOnDestroy(isChangingConfigurations: Boolean): Boolean =
    !isChangingConfigurations
