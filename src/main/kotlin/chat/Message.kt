package chat

import kotlinx.serialization.Serializable
import me.tashila.data.InstantSerializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents a single chat message.
 *
 * @property id A unique identifier for the message, often a timestamp or a server-generated ID.
 * Defaults to the current system time in milliseconds for simplicity.
 * @property text The actual content of the chat message.
 * @property timestamp The time when the message was sent or received. Defaults to the
 * time of object creation.
 * @property isFromMe A boolean flag to indicate if the message was sent by the current user (true)
 * or received from someone else (false). This is crucial for UI alignment
 * and styling.
 */
@Serializable
@OptIn(ExperimentalTime::class)
data class Message constructor(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isFromMe: Boolean,
    val aiThreadId: String? = null,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Clock.System.now()
)