package chat

import kotlinx.serialization.Serializable
import me.tashila.data.InstantSerializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents a single chat message.
 *
 * @property id A unique identifier for the message, a timestamp.
 * @property text The actual content of the chat message.
 * @property timestamp The time when the message was sent or received.
 * @property isFromMe A boolean flag to indicate if the message was sent by the current user (true), or the AI (false).
 */
@Serializable
@OptIn(ExperimentalTime::class)
data class Message constructor(
    val id: Long,
    val text: String,
    val isFromMe: Boolean,
    val aiThreadId: String? = null,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant
)