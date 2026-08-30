package com.resqnet.storage.entity

/**
 * Local persistence status for an emergency. Independent of mesh
 * [com.resqnet.mesh.SeenMessageCache] (in-memory packet dedup).
 */
enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}
