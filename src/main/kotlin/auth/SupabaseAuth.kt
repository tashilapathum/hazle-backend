package me.tashila.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SupabaseSignUpRequest(
    val email: String,
    val password: String,
)

@Serializable
data class SupabaseSignInRequest(
    val email: String,
    val password: String
)

@Serializable
data class SupabaseAuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user") val user: SupabaseUser
)

@Serializable
data class SupabaseErrorResponse(
    val code: Int? = null, // HTTP status code from Supabase's internal API
    @SerialName("error_code")
    val errorCode: String? = null,
    val msg: String? = null,
    val error: String? = null // Supabase sometimes uses 'error' for the message
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class SupabaseRefreshTokenBody(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class SupabaseUser(
    val id: String,
    val aud: String,
    val role: String,
    val email: String,
    val phone: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
    @SerialName("phone_confirmed_at") val phoneConfirmedAt: String? = null,
    @SerialName("confirmation_sent_at") val confirmationSentAt: String? = null,
    @SerialName("confirmed_at") val confirmedAt: String? = null,
    @SerialName("last_sign_in_at") val lastSignInAt: String? = null,
    @SerialName("app_metadata") val appMetadata: JsonElement? = null,
    @SerialName("user_metadata") val userMetadata: JsonElement? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_anonymous") val isAnonymous: Boolean,
    val identities: List<UserIdentity>? = null
)

@Serializable
data class UserIdentity(
    val provider: String,
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("identity_data") val identityData: JsonElement? = null,
    @SerialName("last_sign_in_at") val lastSignInAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("email") val email: String
)