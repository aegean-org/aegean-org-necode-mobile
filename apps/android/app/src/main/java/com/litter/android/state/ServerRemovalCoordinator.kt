package com.litter.android.state

/** Operations required to remove one host without racing its active transport teardown. */
internal data class ServerRemovalOperations(
    val disconnect: suspend () -> Unit,
    val removeSavedServer: suspend () -> Unit,
    val deleteAlleycatToken: suspend () -> Unit,
    val closeSshSession: suspend () -> Unit,
    val refreshProjection: suspend () -> Unit,
)

/** Executes host removal only after the shared transport reports complete shutdown. */
internal suspend fun executeServerRemoval(operations: ServerRemovalOperations) {
    operations.disconnect()
    operations.removeSavedServer()
    operations.deleteAlleycatToken()
    operations.closeSshSession()
    operations.refreshProjection()
}
