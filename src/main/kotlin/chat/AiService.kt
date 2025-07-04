package me.tashila.chat

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.assistant.AssistantId
import com.aallam.openai.api.assistant.AssistantRequest
import com.aallam.openai.api.assistant.AssistantTool
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.core.Status
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.message.MessageContent
import com.aallam.openai.api.message.MessageRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.run.RunRequest
import com.aallam.openai.api.thread.ThreadId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.delay
import me.tashila.model.UserChat
import me.tashila.repository.UserChatRepository
import kotlin.time.Duration.Companion.seconds

@OptIn(BetaOpenAI::class)
class AiService(private val userChatRepository: UserChatRepository) {
    val token: String = System.getenv("OPENAI_API_KEY")
    val openAI = OpenAI(
        token = token,
        timeout = Timeout(socket = 60.seconds),
        logging = LoggingConfig(LogLevel.Headers)
    )

    private var currentAssistantId: String? = null
    private var currentThreadId: String? = null

    suspend fun getAiResponse(message: String? = "Hello"): String {
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-4o-mini"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "Summarize the content and always answer queries strictly within 256 characters."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = message,
                )
            )
        )
        val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
        val response = completion.choices[0].message.content
        println("RESPONSE_LENGTH (Old Method): ${response?.length}")
        return response ?: "No response"
    }

    // TODO: move these to a configuration file or environment variables
    private val defaultAssistantName = "Summarizer Bot"
    private val defaultAssistantInstructions = "You are a helpful summarizer. Summarize the content and always answer queries strictly within 256 characters."
    private val defaultModelId = ModelId("gpt-4o-mini")

    /**
     * Ensures an OpenAI Assistant and Thread exist for the given user,
     * creating them if necessary and persisting their IDs in the database.
     * @return The updated UserChat object from the database.
     */
    suspend fun getOrCreateUserChatState(userId: String): UserChat {
        val userChat = userChatRepository.findByUserId(userId)
        var assistantIdToUse: String? = userChat?.openaiAssistantId
        var threadIdToUse: String? = userChat?.openaiThreadId

        // Create Assistant if not found
        if (assistantIdToUse == null) {
            val assistant = openAI.assistant(
                request = AssistantRequest(
                    name = "$defaultAssistantName for $userId",
                    instructions = defaultAssistantInstructions,
                    model = defaultModelId,
                    tools = emptyList() // Add tools if needed
                )
            )
            assistantIdToUse = assistant.id.id
        }

        // Create Thread if not found
        if (threadIdToUse == null) {
            val thread = openAI.thread()
            threadIdToUse = thread.id.id
        }

        // Update or Create record in Supabase with the (potentially new) IDs
        return if (userChat == null) {
            println("Creating new chat record for user $userId.")
            userChatRepository.create(
                UserChat(
                    userId = userId,
                    openaiAssistantId = assistantIdToUse,
                    openaiThreadId = threadIdToUse
                )
            )
        } else if (userChat.openaiAssistantId != assistantIdToUse || userChat.openaiThreadId != threadIdToUse) {
            userChatRepository.update(
                userChat.copy(
                    openaiAssistantId = assistantIdToUse,
                    openaiThreadId = threadIdToUse
                )
            )
        } else {
            userChat
        }
    }

    /**
     * Sends a message to the user's assistant and retrieves its response.
     * This method assumes `getOrCreateUserChatState` has been called previously for the user.
     */
    suspend fun getAssistantResponse(userId: String, message: String): String {
        // Always load the latest state from the database for robustness
        val userChat = userChatRepository.findByUserId(userId)
            ?: return "Error: AI state not found for user $userId. Please try again or initialize chat."
        val threadIdVal = userChat.openaiThreadId
            ?: return "Error: Thread not initialized for user $userId. Please initialize chat."
        val assistantIdVal = userChat.openaiAssistantId
            ?: return "Error: Assistant not initialized for user $userId. Please initialize chat."

        // 1. Add message to the thread
        openAI.message(
            threadId = ThreadId(threadIdVal),
            request = MessageRequest(
                role = Role.User,
                content = message,
            )
        )
        println("Message added to thread for user $userId: $message")

        // 2. Run the assistant on the thread
        val run = openAI.createRun(
            threadId = ThreadId(threadIdVal),
            request = RunRequest(
                assistantId = AssistantId(assistantIdVal)
            )
        )
        println("Run created for user $userId with ID: ${run.id.id}")

        // 3. Poll for the run to complete
        var retrievedRun = run
        do {
            delay(1500) // Poll every 1.5 seconds
            retrievedRun = openAI.getRun(threadId = ThreadId(threadIdVal), runId = retrievedRun.id)
            println("Run status for user $userId: ${retrievedRun.status}")
        } while (retrievedRun.status != Status.Completed &&
            retrievedRun.status != Status.Failed &&
            retrievedRun.status != Status.Cancelled &&
            retrievedRun.status != Status.Expired
        )

        if (retrievedRun.status == Status.Completed) {
            // 4. Retrieve messages from the thread
            val messages = openAI.messages(threadId = ThreadId(threadIdVal))

            // Find the last assistant message
            val assistantMessages = messages
                .filter { it.role == Role.Assistant }
                .sortedByDescending { it.createdAt }
            val latestAssistantMessage = assistantMessages.firstOrNull()

            if (latestAssistantMessage != null) {
                val textContent = latestAssistantMessage.content.first() as? MessageContent.Text
                val response = textContent?.text?.value
                println("AI Response for user $userId (length: ${response?.length}): $response")
                return response ?: "No response"
            } else {
                return "No assistant response found."
            }
        } else {
            return "Assistant run finished with status for user $userId: ${retrievedRun.status}"
        }
    }

    suspend fun createAssistant(
        name: String,
        instructions: String,
        modelId: ModelId,
        tools: List<AssistantTool> = emptyList(),
    ): String {
        val assistant = openAI.assistant(
            request = AssistantRequest(
                name = name,
                instructions = instructions,
                model = modelId,
                tools = tools,
            )
        )
        println("Assistant created: ${assistant.id.id}")
        return assistant.id.id
    }

    /**
     * Retrieves an assistant by its ID.
     * @param assistantId The ID of the assistant to retrieve.
     * @return The assistant object, or null if not found.
     */
    suspend fun retrieveAssistant(assistantId: String) =
        openAI.assistant(id = AssistantId(assistantId)).also {
            println("Retrieved Assistant: ${it?.id?.id}")
        }


    /**
     * Modifies an existing assistant.
     * @param assistantId The ID of the assistant to modify.
     * @param name Optional new name.
     * @param instructions Optional new instructions.
     * @param modelId Optional new model.
     * @param tools Optional new tools.
     * @param metadata Optional new metadata.
     * @return The modified assistant object.
     */
    suspend fun modifyAssistant(
        assistantId: String,
        name: String? = null,
        instructions: String? = null,
        modelId: ModelId? = null,
        tools: List<AssistantTool>? = null,
        metadata: Map<String, String>? = null
    ) = openAI.assistant(
        id = AssistantId(assistantId),
        request = AssistantRequest(
            name = name,
            instructions = instructions,
            model = modelId,
            tools = tools,
            metadata = metadata
        )
    ).also {
        println("Modified Assistant: ${it.id.id}")
    }

    /**
     * Deletes an assistant.
     * @param assistantId The ID of the assistant to delete.
     * @return True if deletion was successful.
     */
    suspend fun deleteAssistant(assistantId: String): Boolean {
        val result = openAI.delete(id = AssistantId(assistantId))
        println("Deleted Assistant $assistantId: $result")
        if (currentAssistantId == assistantId) {
            currentAssistantId = null
        }
        return result
    }

    /**
     * Lists all assistants.
     * @return A list of assistants.
     */
    suspend fun listAssistants() = openAI.assistants().also {
        println("Listed ${it.size} Assistants.")
    }

    /**
     * Creates a new thread.
     * @return The ID of the created thread.
     */
    suspend fun createThread(): String {
        val thread = openAI.thread()
        println("Thread created: ${thread.id.id}")
        return thread.id.id
    }

    /**
     * Retrieves a thread by its ID.
     * @param threadId The ID of the thread to retrieve.
     * @return The thread object, or null if not found.
     */
    suspend fun retrieveThread(threadId: String) =
        openAI.thread(id = ThreadId(threadId)).also {
            println("Retrieved Thread: ${it?.id?.id}")
        }

    /**
     * Modifies an existing thread.
     * @param threadId The ID of the thread to modify.
     * @return The modified thread object.
     */
    suspend fun modifyThread(threadId: String) =
        openAI.thread(id = ThreadId(threadId)).also {
            println("Modified Thread: ${it?.id?.id}")
        }


    /**
     * Deletes a thread.
     * @param threadId The ID of the thread to delete.
     * @return True if deletion was successful.
     */
    suspend fun deleteThread(threadId: String): Boolean {
        val result = openAI.delete(id = ThreadId(threadId))
        println("Deleted Thread $threadId: $result")
        if (currentThreadId == threadId) {
            currentThreadId = null
        }
        return result
    }

    /**
     * Lists messages for a given thread.
     * @param threadId The ID of the thread.
     * @return A list of messages in the thread.
     */
    suspend fun listMessages(threadId: String) =
        openAI.messages(threadId = ThreadId(threadId)).also {
            println("Listed ${it.size} messages for Thread $threadId.")
        }

    // Helper to delete the currently active assistant and thread
    suspend fun deleteCurrentAssistantAndThread() {
        currentAssistantId?.let { deleteAssistant(it) }
        currentThreadId?.let { deleteThread(it) }
        println("Current Assistant and Thread (if any) deleted.")
    }

}