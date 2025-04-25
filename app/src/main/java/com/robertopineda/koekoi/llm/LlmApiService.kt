// FILE: com/robertopineda/koekoi/llm/LlmApiService.kt

package com.robertopineda.koekoi.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.robertopineda.koekoi.GameActivity // Import Phrase data class
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

// *** Keep the rest of the imports and the LlmApiService class definition ***
class LlmApiService {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Timeout for establishing connection (default is 10s)
        .readTimeout(90, TimeUnit.SECONDS)    // Timeout for receiving data (!! INCREASED SIGNIFICANTLY !!) - Try 90s first
        .writeTimeout(30, TimeUnit.SECONDS)   // Timeout for sending data (default is 10s)
        .build()

    // --- Keep the existing data classes (ApiRequest, Message, ApiResponse, etc.) ---
    data class ApiRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Float = 0.7f, // Controls randomness (0=deterministic, higher=more random)
        val max_tokens: Int = 1000      // Increase max tokens for 5 phrases
    )

    data class Message(
        val role: String, // Typically "system" or "user"
        val content: String
    )

    data class ApiResponse(
        val id: String?,
        val choices: List<Choice>?,
        val created: Long?,
        val model: String?,
        val system_fingerprint: String?,
        val `object`: String?, // Use backticks as 'object' is a keyword
        val usage: Usage?,
        val error: ApiError? // To capture potential errors from the API
    )

    data class Choice(
        val finish_reason: String?,
        val index: Int?,
        val message: ResponseMessage?,
        val logprobs: Any? // Can be null or a complex object, ignoring details for now
    )

    data class ResponseMessage(
        val content: String?, // This is where the LLM's reply (our JSON) will be
        val role: String? // Should be "assistant"
    )

    data class Usage(
        val completion_tokens: Int?,
        val prompt_tokens: Int?,
        val total_tokens: Int?
    )

    // Structure for potential errors returned in the API response body
    data class ApiError(
        val code: String?,
        val message: String?,
        val param: String?,
        val type: String?
    )
    // --- End of existing data classes ---


    // Function to construct the detailed prompt for the LLM
    private fun createPrompt(language: String, userDescription: String): String {
        // Instructions for the LLM on how to format the response
        // *** MODIFIED PROMPT ***
        return """
        You are a language learning assistant for the KoeKoi app. Your task is to generate FIVE DIFFERENT practice phrases based on the user's request.

        **Instructions:**
        1.  **Target Language:** $language
        2.  **User Request:** Generate phrases related to: "$userDescription"
        3.  **Output Format:** Provide ONLY a single JSON ARRAY containing exactly FIVE JSON objects, each matching the following structure. Do NOT include any explanation, commentary, or text outside the JSON array block. Ensure the JSON is valid.
            ```json
            [
              {
                "spoken": "Phrase 1 in the target language...",
                "expected": "Phrase 1 recognition text...",
                "reading": "Phrase 1 reading/romanization...",
                "english": "Phrase 1 English translation...",
                "grammar": "Phrase 1 grammar point (or empty string)..."
              },
              {
                "spoken": "Phrase 2 in the target language...",
                "expected": "Phrase 2 recognition text...",
                "reading": "Phrase 2 reading/romanization...",
                "english": "Phrase 2 English translation...",
                "grammar": "Phrase 2 grammar point (or empty string)..."
              },
              {
                "spoken": "Phrase 3...",
                "expected": "Phrase 3...",
                "reading": "Phrase 3...",
                "english": "Phrase 3...",
                "grammar": "Phrase 3..."
              },
              {
                "spoken": "Phrase 4...",
                "expected": "Phrase 4...",
                "reading": "Phrase 4...",
                "english": "Phrase 4...",
                "grammar": "Phrase 4..."
              },
              {
                "spoken": "Phrase 5...",
                "expected": "Phrase 5...",
                "reading": "Phrase 5...",
                "english": "Phrase 5...",
                "grammar": "Phrase 5..."
              }
            ]
            ```
        4.  **Phrase Quality (Apply to ALL 5 phrases):**
            *   Must be grammatically correct in `$language`.
            *   Relatively short to medium length.
            *   Common, practical, and natural-sounding.
            *   Avoid overly complex sentences, obscure vocabulary, slang (unless requested), or sensitive topics.
            *   Ensure the FIVE phrases are DISTINCT from each other.
        5.  **`reading` Field Detail (Apply to ALL 5 phrases):** Fill the reading field strictly according to the language-specific guidelines (Japanese: Hiragana/Katakana, Chinese: Pinyin, Korean: Revised Romanization, Vietnamese: Quốc ngữ, Russian: Romanization, Spanish/Italian: often empty string, etc.).
        6.  **`grammar` Field Detail (Apply to ALL 5 phrases):** Be specific if a clear grammar point is targeted or demonstrated. Use `""` if it's just vocabulary or a general topic.

        **Example (if user wants Japanese N3 grammar for 'must do'):**
        ```json
        [
          { "spoken": "宿題をしなければなりません。", "expected": "宿題をしなければなりません。", "reading": "しゅくだいをしなければなりません。", "english": "I must do my homework.", "grammar": "〜なければなりません" },
          { "spoken": "早く起きなければなりません。", "expected": "早く起きなければなりません。", "reading": "はやくおきなければなりません。", "english": "I have to get up early.", "grammar": "〜なければなりません" },
          { "spoken": "この薬を飲まなければなりませんか？", "expected": "この薬を飲まなければなりませんか？", "reading": "このくすりをのまなければなりませんか？", "english": "Do I have to take this medicine?", "grammar": "〜なければなりません" },
          { "spoken": "会議に出席しなければなりませんでした。", "expected": "会議に出席しなければなりませんでした。", "reading": "かいぎにしゅっせきしなければなりませんでした。", "english": "I had to attend the meeting.", "grammar": "〜なければなりません" },
          { "spoken": "もっと勉強しなければなりません。", "expected": "もっと勉強しなければなりません。", "reading": "もっとべんきょうしなければなりません。", "english": "I must study more.", "grammar": "〜なければなりません" }
        ]
        ```

        Now, generate ONLY the JSON array containing 5 distinct phrase objects for the user's request: Language: $language, Description: "$userDescription"
        """.trimIndent()
    }


    // Function to call the DeepSeek API and parse the generated phrases
    // *** MODIFIED FUNCTION SIGNATURE AND PARSING ***
    suspend fun generatePhrases(language: String, userDescription: String): Result<List<GameActivity.Phrase>> { // Changed return type
        // Run network operations on the IO dispatcher
        return withContext(Dispatchers.IO) {
            // --- Keep API Key check ---
            if (Config.apiKey.isBlank() || Config.apiKey == "\"\"" || Config.apiKey == "YOUR_DEEPSEEK_API_KEY") {
                Log.e("LlmApiService", "API Key is missing or not configured properly in local.properties/BuildConfig!")
                return@withContext Result.failure(IOException("DeepSeek API Key is not configured."))
            }

            // Create the prompt and the request body
            val prompt = createPrompt(language, userDescription)
            val apiRequest = ApiRequest(
                model = Config.apiModel, // Use configured model
                messages = listOf(
                    Message("user", prompt)
                ),
                max_tokens = 1000 // Increased max tokens
            )

            val requestBodyString = gson.toJson(apiRequest)
            val requestBody = requestBodyString.toRequestBody(jsonMediaType)

            // Build the HTTP request
            val request = Request.Builder()
                .url(Config.apiUrl) // Use configured URL
                .addHeader("Authorization", "Bearer ${Config.apiKey}")
                .post(requestBody)
                .build()

            Log.d("LlmApiService", "Sending request to DeepSeek API for 5 phrases (URL: ${Config.apiUrl}, Model: ${Config.apiModel})...")
            // Log.d("LlmApiService", "Request Body: $requestBodyString") // Optional: Log request body for debugging

            try {
                // Execute the network call
                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string()
                    Log.d("LlmApiService", "Response Code: ${response.code}")
                    Log.d("LlmApiService", "Response Body: $responseBodyString") // Log raw response

                    // --- Keep HTTP error handling ---
                    if (!response.isSuccessful || responseBodyString == null) {
                        val errorMsg = "API call failed: ${response.code} ${response.message}"
                        Log.e("LlmApiService", errorMsg)
                        try {
                            val apiResponse = gson.fromJson(responseBodyString, ApiResponse::class.java)
                            if(apiResponse?.error != null) {
                                return@withContext Result.failure(IOException("API Error: ${apiResponse.error.code} - ${apiResponse.error.message}"))
                            }
                        } catch (e: Exception) { /* Ignore parsing error body */ }
                        return@withContext Result.failure(IOException(errorMsg))
                    }


                    // Handle successful response - attempt to parse the content
                    try {
                        val apiResponse = gson.fromJson(responseBodyString, ApiResponse::class.java)
                        val rawContent = apiResponse?.choices?.firstOrNull()?.message?.content?.trim()

                        if (rawContent.isNullOrBlank()) {
                            val errorMsg = "LLM response content is null or blank."
                            Log.e("LlmApiService", errorMsg)
                            return@withContext Result.failure(IllegalArgumentException(errorMsg))
                        }


                        // --- Logic to extract JSON ARRAY ---
                        val jsonContent: String
                        val jsonStartIndex = rawContent.indexOf('[')
                        val jsonEndIndex = rawContent.lastIndexOf(']')

                        if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
                            jsonContent = rawContent.substring(jsonStartIndex, jsonEndIndex + 1)
                            Log.d("LlmApiService", "Extracted JSON Array content: $jsonContent")
                        } else {
                            val errorMsg = "Could not find valid JSON array boundaries ([...]) in LLM response: '$rawContent'"
                            Log.e("LlmApiService", errorMsg)
                            return@withContext Result.failure(IllegalArgumentException(errorMsg))
                        }
                        // --- End JSON Extraction Logic ---

                        // *** PARSE JSON ARRAY using TypeToken ***
                        val phraseListType = object : TypeToken<List<GameActivity.Phrase>>() {}.type
                        val phrasesList: List<GameActivity.Phrase> = gson.fromJson(jsonContent, phraseListType)

                        // Basic validation on the parsed list
                        if (phrasesList.isNullOrEmpty()) {
                            val errorMsg = "Parsed phrase list is null or empty."
                            Log.e("LlmApiService", "$errorMsg Parsed JSON: $jsonContent")
                            return@withContext Result.failure(IllegalArgumentException(errorMsg))
                        }
                        if (phrasesList.size != 5) {
                            Log.w("LlmApiService", "Warning: LLM returned ${phrasesList.size} phrases instead of 5. Proceeding with received phrases.")
                            // Decide if this is an error or just a warning. For now, allow it.
                        }
                        // Optional: More detailed validation for each phrase
                        phrasesList.forEachIndexed { index, phrase ->
                            if (phrase.spoken.isBlank() || phrase.english.isBlank()) {
                                val errorMsg = "Parsed phrase at index $index is missing required fields (spoken or english)."
                                Log.e("LlmApiService", "$errorMsg Phrase: $phrase")
                                // Decide whether to fail the whole request or just filter this bad phrase
                                return@withContext Result.failure(IllegalArgumentException("Invalid phrase format received from LLM."))
                            }
                        }


                        Log.i("LlmApiService", "Successfully generated and parsed ${phrasesList.size} phrases.")
                        Result.success(phrasesList) // Return the list

                    } catch (e: JsonSyntaxException) {
                        Log.e("LlmApiService", "JSON array parsing error: ${e.message}. Raw Content: $responseBodyString", e)
                        Result.failure(IllegalArgumentException("Failed to parse LLM response JSON array.", e))
                    } catch (e: Exception) {
                        Log.e("LlmApiService", "Error processing LLM response: ${e.message}. Raw Content: $responseBodyString", e)
                        Result.failure(e)
                    }
                }
            } catch (e: SocketTimeoutException) {
                Log.e("LlmApiService", "Network Timeout during API call: ${e.message}", e)
                Result.failure(IOException("Request timed out. AI generation took too long or the network connection is unstable. Please try again.", e))
            } catch (e: IOException) { // Other network errors (connection refused, DNS, etc.)
                Log.e("LlmApiService", "Network IO Exception during API call: ${e.message}", e)
                Result.failure(IOException("Network error during API call: ${e.message}. Please check your connection.", e))
            } catch (e: Exception) { // Catch unexpected errors during request setup etc.
                Log.e("LlmApiService", "Unexpected error before or during API call execution: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}