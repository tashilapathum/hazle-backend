package me.tashila.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO for creating a UserAssistant (without DB-generated fields)
@Serializable
data class UserAssistantCreate(
    @SerialName("user_id") val userId: String,
    @SerialName("openai_assistant_id") val openaiAssistantId: String
)