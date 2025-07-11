package me.tashila.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import me.tashila.model.UserAssistant
import me.tashila.model.UserAssistantCreate
import org.slf4j.LoggerFactory

class UserAssistantRepository(private val supabaseClient: SupabaseClient) {
    private val tableName = "user_assistants"
    private val logger = LoggerFactory.getLogger(UserAssistantRepository::class.java)

    /**
     * Finds a user's assistant state by their Supabase user ID.
     */
    suspend fun findByUserId(userId: String): UserAssistant? {
        return try {
            supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull()
        } catch (e: RestException) {
            logger.error("Supabase REST error finding assistant for user $userId: ${e.message}", e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error finding assistant for user $userId: ${e.message}", e)
            null
        }
    }

    /**
     * Creates a new user assistant entry.
     * Supabase RLS will ensure the `user_id` matches `auth.uid()`.
     */
    suspend fun create(userAssistant: UserAssistantCreate): UserAssistant {
        return try {
            supabaseClient.postgrest[tableName]
                .insert(userAssistant) { select() }
                .decodeSingle()
        } catch (e: RestException) {
            logger.error("Supabase REST error creating assistant for user ${userAssistant.userId}: ${e.message}", e)
            throw e // Re-throw to propagate the error
        } catch (e: Exception) {
            logger.error("Unexpected error creating assistant for user ${userAssistant.userId}: ${e.message}", e)
            throw e
        }
    }

    /**
     * Updates an existing user assistant entry.
     * Supabase RLS will ensure the `user_id` matches `auth.uid()`.
     */
    suspend fun update(userAssistant: UserAssistant): UserAssistant {
        return try {
            supabaseClient.postgrest[tableName]
                .update(userAssistant) {
                    filter {
                        eq("id", userAssistant.id) // Filter by the primary key 'id'
                    }
                    select()
                }
                .decodeSingle()
        } catch (e: RestException) {
            logger.error("Supabase REST error updating assistant with ID ${userAssistant.id}: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error updating assistant with ID ${userAssistant.id}: ${e.message}", e)
            throw e
        }
    }
}