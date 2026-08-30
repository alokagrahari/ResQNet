package com.resqnet.mesh

/**
 * Validates packets before processing.
 *
 * Checks:
 * - messageId exists and is non-blank
 * - sourceNodeId exists and is non-blank
 * - senderNodeId exists and is non-blank
 * - payload is non-blank
 * - TTL > 0
 * - hopCount >= 0
 *
 * Invalid packets are rejected safely — no crashes.
 */
object PacketValidator {

    /**
     * Result of packet validation.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val reason: String = ""
    ) {
        companion object {
            fun valid(): ValidationResult = ValidationResult(true)
            fun invalid(reason: String): ValidationResult = ValidationResult(false, reason)
        }
    }

    /**
     * Validate a packet's fields.
     * Returns [ValidationResult] with reason if invalid.
     */
    fun validate(packet: Packet): ValidationResult {
        if (packet.messageId.isBlank()) {
            return ValidationResult.invalid("messageId is blank")
        }
        if (packet.sourceNodeId.isBlank()) {
            return ValidationResult.invalid("sourceNodeId is blank")
        }
        if (packet.senderNodeId.isBlank()) {
            return ValidationResult.invalid("senderNodeId is blank")
        }
        if (packet.payload.isBlank()) {
            return ValidationResult.invalid("payload is blank")
        }
        if (packet.ttl <= 0) {
            return ValidationResult.invalid("TTL is invalid: ${packet.ttl}")
        }
        if (packet.hopCount < 0) {
            return ValidationResult.invalid("hopCount is negative: ${packet.hopCount}")
        }
        return ValidationResult.valid()
    }
}
