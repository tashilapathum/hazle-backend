package me.tashila.chat

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import com.aallam.openai.api.assistant.AssistantRequest
import com.aallam.openai.api.assistant.AssistantId
import com.aallam.openai.api.thread.ThreadId
import com.aallam.openai.api.message.MessageRequest
import com.aallam.openai.api.message.MessageContent
import com.aallam.openai.api.message.Message
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.run.RunRequest
import com.aallam.openai.api.run.Run
import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.core.Status
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import me.tashila.model.UserAssistant
import me.tashila.model.UserAssistantCreate
import me.tashila.model.UserThread
import me.tashila.model.UserThreadCreate
import me.tashila.repository.UserAssistantRepository
import me.tashila.repository.UserThreadRepository
import kotlin.time.Duration.Companion.seconds

class ChatService(
    private val userAssistantRepository: UserAssistantRepository,
    private val userThreadRepository: UserThreadRepository,
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val defaultAssistantName = System.getenv("ASSISTANT_NAME")
    private val defaultAssistantInstructions = System.getenv("ASSISTANT_INSTRUCTIONS")
    private val defaultModelId = ModelId(System.getenv("OPENAI_MODEL_ID"))
    private val token: String = System.getenv("OPENAI_API_KEY")
    private val openAI = OpenAI(
        token = token,
        timeout = Timeout(socket = 60.seconds),
        logging = LoggingConfig(LogLevel.Info)
    )

    /**
     * Ensures a user has an OpenAI Assistant record in the database.
     * Creates one if it doesn't exist.
     */
    @OptIn(BetaOpenAI::class) // Required for OpenAI Assistants API
    suspend fun getOrCreateUserAssistant(userId: String): UserAssistant {
        var userAssistant = userAssistantRepository.findByUserId(userId)

        if (userAssistant == null) {
            val assistant = openAI.assistant(
                request = AssistantRequest(
                    name = "$defaultAssistantName for $userId",
                    instructions = defaultAssistantInstructions,
                    model = defaultModelId
                )
            )
            val newUserAssistant = UserAssistantCreate(
                userId = userId,
                openaiAssistantId = assistant.id.id
            )
            userAssistant = userAssistantRepository.create(newUserAssistant)
        }
        return userAssistant
    }

    /**
     * Creates a new OpenAI Thread for a user and stores its reference in the database.
     * Links the thread to the user's primary assistant.
     */
    @OptIn(BetaOpenAI::class) // Required for OpenAI Assistants API
    suspend fun createThreadForUser(userId: String, threadName: String? = null): UserThread {
        val userAssistant = getOrCreateUserAssistant(userId) // Ensure user has an assistant

        val thread = openAI.thread()
        val openaiThreadId = thread.id.id

        val newUserThread = UserThreadCreate(
            userId = userId,
            userAssistantId = userAssistant.id, // Link to the internal DB ID of the user's assistant
            openaiThreadId = openaiThreadId,
            name = threadName
        )
        val createdUserThread = userThreadRepository.create(newUserThread)
        return createdUserThread
    }

    /**
     * Gets an AI response for a specific thread and message.
     */
    @OptIn(BetaOpenAI::class) // Required for OpenAI Assistants API
    suspend fun getAssistantResponse(userId: String, openaiThreadId: String, message: String): String {
        // 1. Load the user's specific thread from the database
        val userThread = userThreadRepository.findByOpenAIThreadId(openaiThreadId)
        if (userThread == null || userThread.userId != userId) { // Also verify ownership
            logger.error("User thread not found or does not belong to user $userId for openaiThreadId: $openaiThreadId.")
            throw Exception("Chat thread not found or access denied. Please try creating a new chat.")
        }

        // 2. Load the user's assistant details
        val userAssistant = userAssistantRepository.findByUserId(userId)
        if (userAssistant == null || userAssistant.id != userThread.userAssistantId) { // Verify consistent assistant linkage
            logger.error("User assistant not found or mismatch for userId: $userId. UserAssistant DB ID: ${userAssistant?.id}, Thread's UserAssistant ID: ${userThread.userAssistantId}")
            throw Exception("AI assistant configuration missing. Please contact support.")
        }
        val assistantIdVal = userAssistant.openaiAssistantId


        // 3. Add message to the thread
        try {
            openAI.message(
                threadId = ThreadId(userThread.openaiThreadId), // Use the specific thread ID
                request = MessageRequest(
                    role = Role.User,
                    content = message,
                )
            )
        } catch (e: Exception) {
            logger.error("ERROR adding message to thread '${userThread.openaiThreadId}' for user $userId: ${e.message}", e)
            throw Exception("Failed to add message to AI thread. Please try again.")
        }

        // 4. Run the assistant on the thread
        val run: Run
        try {
            run = openAI.createRun(
                threadId = ThreadId(userThread.openaiThreadId),
                request = RunRequest(
                    assistantId = AssistantId(assistantIdVal)
                )
            )
        } catch (e: Exception) {
            logger.error("ERROR creating run for user $userId on thread '${userThread.openaiThreadId}': ${e.message}", e)
            throw Exception("Failed to start AI assistant processing. Please try again.")
        }


        // 5. Poll for the run to complete
        var retrievedRun = run
        do {
            delay(1500) // Poll every 1.5 seconds
            try {
                retrievedRun = openAI.getRun(threadId = ThreadId(userThread.openaiThreadId), runId = retrievedRun.id)
            } catch (e: Exception) {
                logger.error("ERROR while polling run status for user $userId (Run ID: ${retrievedRun.id.id}): ${e.message}", e)
                throw Exception("Error while waiting for AI response. Please try again.")
            }
        } while (retrievedRun.status != Status.Completed &&
            retrievedRun.status != Status.Failed &&
            retrievedRun.status != Status.Cancelled &&
            retrievedRun.status != Status.Expired
        )


        if (retrievedRun.status == Status.Completed) {
            // 6. Retrieve messages from the thread
            val messages: List<Message>
            try {
                messages = openAI.messages(threadId = ThreadId(userThread.openaiThreadId))
            } catch (e: Exception) {
                logger.error("ERROR retrieving messages from thread '${userThread.openaiThreadId}' for user $userId: ${e.message}", e)
                throw Exception("Failed to retrieve AI messages. Please try again.")
            }


            // Find the last assistant message
            val assistantMessages = messages
                .filter { it.role == Role.Assistant }
                .sortedByDescending { it.createdAt }
            val latestAssistantMessage = assistantMessages.firstOrNull()

            if (latestAssistantMessage != null) {
                val textContent = latestAssistantMessage.content.first() as? MessageContent.Text
                val response = textContent?.text?.value
                return response ?: "No response content found."
            } else {
                // This case is not an "error" that prevents the flow, but rather an unexpected outcome.
                // It's up to you if you want to throw an exception here or return a specific message.
                // For now, keeping it as a return string as it's not a direct failure from an API call.
                return "No assistant response found."
            }
        } else {
            logger.warn("AI Assistant run for user $userId finished with non-completed status: ${retrievedRun.status}")
            throw Exception("AI processing did not complete successfully. Status: ${retrievedRun.status}. Please try again.")
        }
    }

    /**
     * Retrieves a list of all chat threads for a given user.
     */
    suspend fun getUserThreads(userId: String): List<UserThread> {
        val threads = userThreadRepository.findAllByUserId(userId)
        return threads
    }
}