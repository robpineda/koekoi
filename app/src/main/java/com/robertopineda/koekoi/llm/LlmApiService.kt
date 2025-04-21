package com.robertopineda.koekoi.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.robertopineda.koekoi.BuildConfig // Ensure this import is correct and BuildConfig is generated
import com.robertopineda.koekoi.GameActivity // Import Phrase data class
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class LlmApiService {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Data classes for DeepSeek API request and response structure
    data class ApiRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Float = 0.7f, // Controls randomness (0=deterministic, higher=more random)
        val max_tokens: Int = 200      // Max length of the generated response
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


    // Function to construct the detailed prompt for the LLM
    private fun createPrompt(language: String, userDescription: String): String {
        // Instructions for the LLM on how to format the response
        return """
        You are a language learning assistant for the KoeKoi app. Your task is to generate ONE practice phrase based on the user's request.

        **Instructions:**
        1.  **Target Language:** $language
        2.  **User Request:** Generate a phrase related to: "$userDescription"
        3.  **Output Format:** Provide ONLY a single JSON object matching the following structure. Do NOT include any explanation, commentary, or text outside the JSON block. Ensure the JSON is valid.
            ```json
            {
              "spoken": "The phrase in the target language as it would be spoken naturally.",
              "expected": "The phrase exactly as it should be recognized by speech-to-text (often the same as spoken, especially for non-Japanese). For Japanese, use standard orthography including Kanji and Kana.",
              "reading": "The phonetic reading or standard romanization of the phrase, using the most common system for the target language.\n*   **Japanese:** Provide the full Hiragana/Katakana reading (Furigana).\n*   **Chinese (Mandarin):** Provide Hanyu Pinyin, preferably with tone marks or numbers (e.g., 'nǐ hǎo' or 'ni3 hao3').\n*   **Korean:** Provide the Revised Romanization of Korean (e.g., 'annyeonghaseyo').\n*   **Russian:** Provide a standard Romanization system (e.g., BGN/PCGN, ISO 9) (e.g., 'privet').\n*   **Vietnamese:** Provide the standard Quốc ngữ script (which includes tones). For specific phonetic detail beyond the script, IPA could be used but is often unnecessary.\n*   **Other Languages:** Provide the standard, commonly used phonetic transcription (e.g., IPA if applicable) or romanization system appropriate for that language (e.g., standard romanization for Arabic, Hindi, Thai, etc.).\n*   **When to use Empty String:** Provide an empty string only if the language's standard orthography is already highly phonetic and commonly understood as such by learners (e.g., Spanish, Italian), OR if no single, widely accepted standard phonetic/romanization system is practical or commonly used for pronunciation guidance in this context.",
              "english": "A concise and accurate English translation of the phrase.",
              "grammar": "Identify the key grammar point or structure demonstrated in the phrase, if applicable (e.g., '〜なければならない', 'Subjunctive Mood', 'Past Tense', 'Conditional'). If the phrase primarily demonstrates vocabulary or is a simple statement without a distinct complex grammar point, provide an empty string."
            }
            ```
        4.  **Phrase Quality:**
            *   The phrase must be grammatically correct in the `$language` language.
            *   Keep the phrase relatively short to medium length, suitable for a language learner to practice speaking aloud.
            *   The phrase should be common, practical, and natural-sounding in everyday conversation or common contexts.
            *   Avoid overly complex sentences, highly obscure vocabulary, slang (unless requested), or sensitive/controversial topics unless the user specifically asks for them.
        5.  **`reading` Field Detail:** Fill the reading field strictly according to the language-specific guidelines detailed within the JSON structure description above. For example: provide Hiragana/Katakana for Japanese, Pinyin for Chinese, Revised Romanization for Korean, standard Quốc ngữ for Vietnamese, a standard Romanization for Russian, and often an empty string for highly phonetic languages like Spanish unless a specific phonetic transcription is more helpful.
        6.  **`grammar` Field Detail:** Be specific if a clear grammar point is targeted by the user request or demonstrated. If it's just vocabulary or a general topic, use `""`.

        **Example (if user wants Japanese N3 grammar for 'must do'):**
        ```json
        {
          "spoken": "宿題をしなければなりません。",
          "expected": "宿題をしなければなりません。",
          "reading": "しゅくだいをしなければなりません。",
          "english": "I must do my homework.",
          "grammar": "〜なければなりません"
        }
        ```

        **Example (if user wants Spanish vocabulary for 'apple'):**
        ```json
        {
          "spoken": "Me gusta comer manzanas rojas.",
          "expected": "Me gusta comer manzanas rojas.",
          "reading": "",
          "english": "I like to eat red apples.",
          "grammar": ""
        }
        ```

        Now, generate ONLY the JSON object for the user's request: Language: $language, Description: "$userDescription"
        """.trimIndent()
    }


    // Function to call the DeepSeek API and parse the generated phrase
    suspend fun generatePhrase(language: String, userDescription: String): Result<GameActivity.Phrase> {
        // Run network operations on the IO dispatcher
        return withContext(Dispatchers.IO) {
            // Pre-check for API key validity
            if (Config.apiKey.isBlank() || Config.apiKey == "\"\"" || Config.apiKey == "YOUR_DEEPSEEK_API_KEY") {
                Log.e("LlmApiService", "API Key is missing or not configured properly in local.properties/BuildConfig!")
                return@withContext Result.failure(IOException("DeepSeek API Key is not configured."))
            }

            // Create the prompt and the request body
            val prompt = createPrompt(language, userDescription)
            val apiRequest = ApiRequest(
                model = Config.apiModel,
                messages = listOf(
                    // You could add a system message here if needed, e.g.:
                    // Message("system", "You generate language practice phrases in JSON format."),
                    Message("user", prompt) // The main prompt with instructions
                )
            )

            val requestBodyString = gson.toJson(apiRequest)
            val requestBody = requestBodyString.toRequestBody(jsonMediaType)

            // Build the HTTP request
            val request = Request.Builder()
                .url(Config.apiUrl)
                .addHeader("Authorization", "Bearer ${Config.apiKey}") // Add the API key as a Bearer token
                .post(requestBody)
                .build()

            Log.d("LlmApiService", "Sending request to DeepSeek API (URL: ${Config.apiUrl}, Model: ${Config.apiModel})...")

            try {
                // Execute the network call synchronously within the IO context
                client.newCall(request).execute().use { response -> // 'use' ensures the response body is closed
                    val responseBodyString = response.body?.string() // Read body once
                    Log.d("LlmApiService", "Response Code: ${response.code}")
                    Log.d("LlmApiService", "Response Body: $responseBodyString") // Log the raw response

                    // Handle unsuccessful HTTP responses
                    if (!response.isSuccessful || responseBodyString == null) {
                        val errorMsg = "API call failed: ${response.code} ${response.message}"
                        Log.e("LlmApiService", errorMsg)
                        // Attempt to parse API error message from response body if available
                        try {
                            val apiResponse = gson.fromJson(responseBodyString, ApiResponse::class.java)
                            if(apiResponse?.error != null) {
                                return@withContext Result.failure(IOException("API Error: ${apiResponse.error.code} - ${apiResponse.error.message}"))
                            }
                        } catch (e: Exception) {
                            Log.w("LlmApiService", "Could not parse error response body: ${e.message}")
                        }
                        // Return generic failure if no specific API error parsed
                        return@withContext Result.failure(IOException(errorMsg))
                    }

                    // Handle successful HTTP response - attempt to parse the content
                    try {
                        val apiResponse = gson.fromJson(responseBodyString, ApiResponse::class.java)
                        // Get the content string from the first choice's message
                        val rawContent = apiResponse?.choices?.firstOrNull()?.message?.content?.trim()

                        if (rawContent.isNullOrBlank()) {
                            val errorMsg = "LLM response content is null or blank."
                            Log.e("LlmApiService", errorMsg)
                            return@withContext Result.failure(IllegalArgumentException(errorMsg))
                        }

                        // --- Logic to extract JSON, handling potential Markdown fences ---
                        val jsonContent: String
                        val jsonStartIndex = rawContent.indexOf('{')
                        val jsonEndIndex = rawContent.lastIndexOf('}')

                        if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
                            // Found curly braces, extract the content between them
                            jsonContent = rawContent.substring(jsonStartIndex, jsonEndIndex + 1)
                            Log.d("LlmApiService", "Extracted JSON content: $jsonContent")
                        } else {
                            // No valid JSON object found within the content string
                            val errorMsg = "Could not find valid JSON object boundaries ({...}) in LLM response: '$rawContent'"
                            Log.e("LlmApiService", errorMsg)
                            return@withContext Result.failure(IllegalArgumentException(errorMsg))
                        }
                        // --- End JSON Extraction Logic ---

                        // Parse the extracted JSON string into our Phrase data class
                        val phrase = gson.fromJson(jsonContent, GameActivity.Phrase::class.java)

                        // Basic validation on the parsed Phrase object
                        if (phrase.spoken.isBlank() || phrase.english.isBlank()) {
                            val errorMsg = "Parsed phrase object is missing required fields (spoken or english)."
                            Log.e("LlmApiService", "$errorMsg Parsed JSON: $jsonContent")
                            return@withContext Result.failure(IllegalArgumentException(errorMsg))
                        }

                        // If everything succeeded, return the parsed Phrase wrapped in Result.success
                        Log.i("LlmApiService", "Successfully generated and parsed phrase: ${phrase.spoken}")
                        Result.success(phrase)

                    } catch (e: JsonSyntaxException) {
                        // Handle errors during JSON parsing specifically
                        Log.e("LlmApiService", "JSON parsing error: ${e.message}. Raw Content: $responseBodyString", e)
                        Result.failure(IllegalArgumentException("Failed to parse LLM response JSON.", e))
                    } catch (e: Exception) {
                        // Catch any other unexpected errors during response processing
                        Log.e("LlmApiService", "Error processing LLM response: ${e.message}. Raw Content: $responseBodyString", e)
                        Result.failure(e)
                    }
                }
            } catch (e: IOException) {
                // Handle network-level errors (no connection, timeout, etc.)
                Log.e("LlmApiService", "Network IO Exception during API call: ${e.message}", e)
                Result.failure(e) // Wrap the IOException
            }
        }
    }
}