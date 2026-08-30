package com.resqnet.mesh

/**
 * Centralized logging for the mesh engine.
 *
 * Uses a pluggable [logOutput] function so it works both:
 * - In JVM unit tests (default: prints to stdout)
 * - On Android (can be swapped to android.util.Log)
 *
 * Log tags follow the convention:
 * [RECEIVE], [VALIDATE], [DEDUP], [TTL], [HOP], [FORWARD], [SEND], [DROP], [DELIVERED]
 */
object MeshLogger {

    /**
     * Pluggable log output. Override for Android or testing.
     * Default prints to stdout for JVM test compatibility.
     */
    var logOutput: (tag: String, message: String) -> Unit = { tag, message ->
        println("[$tag] $message")
    }

    fun receive(messageId: String, fromNodeId: String) {
        logOutput("RECEIVE", "$messageId from $fromNodeId")
    }

    fun validatePass(messageId: String) {
        logOutput("VALIDATE", "PASS - $messageId")
    }

    fun validateFail(messageId: String, reason: String) {
        logOutput("VALIDATE", "FAIL - $messageId: $reason")
    }

    fun dedupNew(messageId: String) {
        logOutput("DEDUP", "NEW - $messageId")
    }

    fun dedupDuplicate(messageId: String) {
        logOutput("DEDUP", "DUPLICATE - $messageId")
    }

    fun ttlUpdate(messageId: String, oldTtl: Int, newTtl: Int) {
        logOutput("TTL", "$messageId: $oldTtl -> $newTtl")
    }

    fun ttlExpired(messageId: String) {
        logOutput("TTL", "EXPIRED - $messageId")
    }

    fun hopUpdate(messageId: String, oldHop: Int, newHop: Int) {
        logOutput("HOP", "$messageId: $oldHop -> $newHop")
    }

    fun forward(messageId: String, fromNodeId: String, toNodeId: String) {
        logOutput("FORWARD", "$messageId: $fromNodeId -> $toNodeId")
    }

    fun send(messageId: String) {
        logOutput("SEND", messageId)
    }

    fun drop(messageId: String, reason: String) {
        logOutput("DROP", "$messageId: $reason")
    }

    fun delivered(messageId: String, nodeId: String) {
        logOutput("DELIVERED", "$messageId at $nodeId")
    }

    fun destination(messageId: String, destinationNodeId: String, decision: String) {
        logOutput("DEST", "$messageId dest=$destinationNodeId $decision")
    }
}
