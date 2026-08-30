package com.resqnet.mesh

import java.util.UUID

/**
 * Generates unique, collision-resistant message IDs using UUIDs.
 * Every newly created message gets a unique messageId for deduplication.
 */
object MessageIdGenerator {

    /**
     * Generate a new unique message ID.
     */
    fun generate(): String = UUID.randomUUID().toString()
}
