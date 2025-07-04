package me.tashila.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.RestException
import me.tashila.model.UserChat
import org.slf4j.LoggerFactory

class UserChatRepository(private val supabaseClient: SupabaseClient) {
    private val tableName = "user_chats" // Your table name
    private val logger = LoggerFactory.getLogger(UserChatRepository::class.java)


    /**
     * Finds a user's chat state by their Supabase user ID.
     */
    suspend fun findByUserId(userId: String): UserChat? {
        return try {
            supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("user_id", userId) // Filter by the user_id column
                    }
                }
                .decodeSingleOrNull() // Decodes the single result into UserChat or null
        } catch (e: RestException) {
            logger.error("Supabase REST error finding chat for user {}: {}", userId, e.message, e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error finding chat for user {}: {}", userId, e.message, e)
            null
        }
    }

    /**
     * Creates a new user chat entry.
     * Supabase RLS will ensure the `user_id` matches `auth.uid()`.
     */
    suspend fun create(userChat: UserChat): UserChat {
        return supabaseClient.postgrest[tableName]
            .insert(userChat) { select() }
            .decodeSingle()
    }

    /**
     * Updates an existing user chat entry.
     * Supabase RLS will ensure the `user_id` matches `auth.uid()`.
     */
    suspend fun update(userChat: UserChat): UserChat {
        // Ensure you update by a unique identifier, like user_id, which is also protected by RLS
        return supabaseClient.postgrest[tableName]
            .update(userChat) {
                filter {
                    eq("user_id", userChat.userId) // Ensure we update the correct user's record
                }
                select() // Important: returns the updated object
            }
            .decodeSingle()
    }
}