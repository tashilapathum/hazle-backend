package me.tashila.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class UserChat(
    @SerialName("id") val id: String? = null, // UUID from DB
    @SerialName("user_id") val userId: String, // User's UUID from Supabase Auth
    @SerialName("openai_assistant_id") val openaiAssistantId: String?,
    @SerialName("openai_thread_id") val openaiThreadId: String?,
    @SerialName("created_at") val createdAt: String? = null, // Supabase handles this
    @SerialName("updated_at") val updatedAt: String? = null  // Supabase handles this via trigger
)