
package com.guruswarupa.launch.repository

import com.guruswarupa.launch.api.ChatCompletionRequest
import com.guruswarupa.launch.api.GroqApiClient
import com.guruswarupa.launch.api.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository {
    private val apiService = GroqApiClient.apiService
    
    suspend fun sendMessage(apiKey: String, messages: List<Message>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val authHeader = "Bearer $apiKey"
                val request = ChatCompletionRequest(messages = messages)
                val response = apiService.getChatCompletion(authHeader, request)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.choices.isNotEmpty()) {
                        Result.success(body.choices[0].message.content)
                    } else {
                        Result.failure(Exception("Empty response from API"))
                    }
                } else {
                    Result.failure(Exception("API Error: ${response.code()} ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
