package me.tashila.chat

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI

class AiService {
    val token: String = System.getenv("OPENAI_API_KEY")
    val openAI = OpenAI(token)

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
        println("RESPONSE_LENGTH: ${response?.length}")
        return  response ?: "No response"
        //val completions: Flow<ChatCompletionChunk> = openAI.chatCompletions(chatCompletionRequest)
    }
}