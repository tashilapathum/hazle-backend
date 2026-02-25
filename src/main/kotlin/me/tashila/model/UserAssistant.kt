package me.tashila.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAssistant(
    @SerialName("id") val id: String, // DB-generated UUID
    @SerialName("user_id") val userId: String,
    @SerialName("openai_assistant_id") val openaiAssistantId: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)