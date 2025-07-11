package me.tashila.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import me.tashila.model.UserThread
import me.tashila.model.UserThreadCreate
import org.slf4j.LoggerFactory

class UserThreadRepository(private val supabaseClient: SupabaseClient) {
    private val tableName = "user_threads"
    private val logger = LoggerFactory.getLogger(UserThreadRepository::class.java)

    /**
     * Finds a user's thread by its OpenAI thread ID.
     */
    suspend fun findByOpenAIThreadId(openaiThreadId: String): UserThread? {
        return try {
            supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("openai_thread_id", openaiThreadId)
                    }
                }
                .decodeSingleOrNull()
        } catch (e: RestException) {
            logger.error("Supabase REST error finding thread by OpenAI ID {}: {}", openaiThreadId, e.message, e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error finding thread by OpenAI ID {}: {}", openaiThreadId, e.message, e)
            null
        }
    }

    /**
     * Finds all threads for a given user ID.
     */
    suspend fun findAllByUserId(userId: String): List<UserThread> {
        return try {
            supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList()
        } catch (e: RestException) {
            logger.error("Supabase REST error finding all threads for user {}: {}", userId, e.message, e)
            emptyList()
        } catch (e: Exception) {
            logger.error("Unexpected error finding all threads for user {}: {}", userId, e.message, e)
            emptyList()
        }
    }

    /**
     * Creates a new user thread entry.
     */
    suspend fun create(userThread: UserThreadCreate): UserThread {
        return try {
            supabaseClient.postgrest[tableName]
                .insert(userThread) { select() }
                .decodeSingle()
        } catch (e: RestException) {
            logger.error("Supabase REST error creating thread for user {}: {}", userThread.userId, e.message, e)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error creating thread for user {}: {}", userThread.userId, e.message, e)
            throw e
        }
    }

    /**
     * Updates an existing user thread entry.
     */
    suspend fun update(userThread: UserThread): UserThread {
        return try {
            supabaseClient.postgrest[tableName]
                .update(userThread) {
                    filter { eq("id", userThread.id) }
                    select()
                }
                .decodeSingle()
        } catch (e: RestException) {
            logger.error("Supabase REST error updating thread with ID {}: {}", userThread.id, e.message, e)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error updating thread with ID {}: {}", userThread.id, e.message, e)
            throw e
        }
    }
}
