package me.tashila.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO for creating a UserThread (without DB-generated fields)
@Serializable
data class UserThreadCreate(
    @SerialName("user_id") val userId: String,
    @SerialName("user_assistant_id") val userAssistantId: String,
    @SerialName("openai_thread_id") val openaiThreadId: String,
    @SerialName("name") val name: String? = null
)