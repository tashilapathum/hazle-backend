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
import com.aallam.openai.api.run.AssistantStreamEvent
import com.aallam.openai.api.run.AssistantStreamEventType
import com.aallam.openai.api.run.MessageDelta
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.extension.getData
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val json: Json,
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val defaultAssistantName = System.getenv("ASSISTANT_NAME")
    private val defaultAssistantInstructions = System.getenv("ASSISTANT_INSTRUCTIONS")
    private val defaultModelId = ModelId("gpt-4o")
    private val token: String = System.getenv("OPENAI_API_KEY")
    private val openAI = OpenAI(
        token = token,
        timeout = Timeout(socket = 60.seconds),
        logging = LoggingConfig(LogLevel.All)
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
        logger.info("ASSISTANT_ID: ${userAssistant.openaiAssistantId}")
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

        // 3. Steam the response and build a string
        val responseBuilder = StringBuilder()
        try {
            openAI.message(
                threadId = ThreadId(userThread.openaiThreadId),
                request = MessageRequest(
                    role = Role.User,
                    content = message,
                )
            )

            openAI.createStreamingRun(
                threadId = ThreadId(userThread.openaiThreadId),
                request = RunRequest(
                    assistantId = AssistantId(assistantIdVal),
                    stream = true, // Ensure streaming is enabled
                )
            )
                .onEach { assistantStreamEvent: AssistantStreamEvent ->
                    when (assistantStreamEvent.type) {
                        AssistantStreamEventType.THREAD_MESSAGE_DELTA -> {
                            val rawJsonString = assistantStreamEvent.data

                            if (rawJsonString.isNullOrBlank()) {
                                logger.warn("Received empty or null raw JSON string for THREAD_MESSAGE_DELTA.")
                                return@onEach
                            }

                            try {
                                val fullEventJson = json.parseToJsonElement(rawJsonString).jsonObject // Expecting a top-level object
                                val deltaJson = fullEventJson["delta"]?.jsonObject
                                val contentArray = deltaJson?.get("content")?.jsonArray

                                if (contentArray != null && contentArray.isNotEmpty()) {
                                    // Take the first item in the content array
                                    val firstContentPart = contentArray[0].jsonObject

                                    // Check if it's a "text" type
                                    if (firstContentPart["type"]?.jsonPrimitive?.content == "text") {
                                        val textObject = firstContentPart["text"]?.jsonObject
                                        val textValue = textObject?.get("value")?.jsonPrimitive?.content
                                        if (textValue != null)
                                            responseBuilder.append(textValue)
                                    } else {
                                        logger.warn("First content part is not 'text' type or 'type' field is missing: $firstContentPart")
                                    }
                                } else {
                                    logger.warn("Content array is null or empty in delta: $rawJsonString")
                                }

                            } catch (e: Exception) {
                                logger.error("Error parsing raw JSON string for THREAD_MESSAGE_DELTA: $rawJsonString", e)
                            }
                        }
                        AssistantStreamEventType.THREAD_RUN_COMPLETED -> {
                            logger.debug("Run completed for user $userId.")
                        }
                        AssistantStreamEventType.THREAD_MESSAGE_COMPLETED -> {
                            logger.debug("Message completed for user $userId.")
                        }
                        AssistantStreamEventType.THREAD_RUN_FAILED,
                        AssistantStreamEventType.THREAD_RUN_CANCELLED,
                        AssistantStreamEventType.THREAD_RUN_EXPIRED -> {
                            val run = assistantStreamEvent.getData<Run>()
                            logger.error("AI Assistant run failed/cancelled/expired for user $userId with status: ${run.status}")
                        }
                        else -> {
                            logger.debug(
                                "Received AssistantStreamEvent: {} - Data: {}",
                                assistantStreamEvent.type,
                                assistantStreamEvent.data
                            )
                        }
                    }
                }
                .collect()
        } catch (e: Exception) {
            logger.error("ERROR while streaming AI Assistant response for user $userId on thread '${userThread.openaiThreadId}': ${e.message}", e)
            throw Exception("Failed to get AI assistant response via streaming. Please try again.")
        }

        // After the stream closes (which means the run has completed or terminated)
        return if (responseBuilder.isEmpty()) {
            "Something went wrong."
        } else {
            responseBuilder.toString().trim()
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