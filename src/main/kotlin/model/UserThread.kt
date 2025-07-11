package me.tashila.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// UserThread.kt
@Serializable
data class UserThread(
    @SerialName("id") val id: String, // DB-generated UUID
    @SerialName("user_id") val userId: String,
    @SerialName("user_assistant_id") val userAssistantId: String, // Link to your internal UserAssistant ID
    @SerialName("openai_thread_id") val openaiThreadId: String,
    @SerialName("name") val name: String? = null, // Optional user-defined name
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)