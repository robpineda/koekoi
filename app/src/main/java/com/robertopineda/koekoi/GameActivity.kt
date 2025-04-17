package com.robertopineda.koekoi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class GameActivity : ComponentActivity() {

    // --- Activity Result Launcher for Permissions ---
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private fun initializePermissionLauncher() {
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    Log.d("GameActivity", "RECORD_AUDIO permission granted.")
                } else {
                    Log.w("GameActivity", "RECORD_AUDIO permission denied.")
                    Toast.makeText(
                        this,
                        "Audio permission is required to practice speaking.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // --- Data Classes and Properties ---
    data class Phrase(
        val spoken: String,
        val expected: String,
        val reading: String,
        val english: String,
        val grammar: String
    )

    private lateinit var phrases: List<Phrase>
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var mediaPlayer: MediaPlayer // For 'speak' sound
    private var currentOnResult: ((String) -> Unit)? = null
    private var currentOnSpeechEnded: (() -> Unit)? = null
    private lateinit var selectedLanguage: String
    private lateinit var selectedDifficulty: String
    private var selectedMaterial: String = "Vocabulary"

    // --- Recognition Listener ---
    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("GameActivity", "Speech recognizer ready for speech")
            currentOnResult?.invoke("Listening...")
        }

        override fun onResults(results: Bundle?) {
            val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
            Log.d("GameActivity", "Speech result received: '$spokenText'")
            currentOnResult?.invoke(spokenText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
            Log.d("GameActivity", "Partial result: '$partialText'")
            if (partialText.isNotEmpty()) currentOnResult?.invoke(partialText)
        }

        override fun onError(error: Int) {
            Log.e("GameActivity", "Speech error code: $error")
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error (Might need restart)"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found. Try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input. Speak louder."
                else -> "Unknown error $error"
            }
            Log.e("GameActivity", "Speech error details: $errorMessage")
            currentOnResult?.invoke(errorMessage) // Pass error message to UI
            currentOnSpeechEnded?.invoke() // Signal end on error
        }

        override fun onBeginningOfSpeech() { Log.d("GameActivity", "Speech began") }
        override fun onEndOfSpeech() { Log.d("GameActivity", "Speech ended"); currentOnSpeechEnded?.invoke() }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) { Log.d("GameActivity", "Speech event: $eventType") }
    }

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializePermissionLauncher()

        // Retrieve intent extras
        selectedLanguage = intent.getStringExtra("LANGUAGE") ?: "Japanese"
        selectedDifficulty = intent.getStringExtra("DIFFICULTY") ?: "Unknown"
        selectedMaterial = intent.getStringExtra("MATERIAL") ?: "Vocabulary"

        val singlePhraseJson = intent.getStringExtra("PHRASE")
        val gson = Gson()

        // Load phrases: either single generated phrase or from assets
        phrases = if (singlePhraseJson != null) {
            try {
                Log.d("GameActivity", "Attempting to load single phrase from JSON extra: $singlePhraseJson")
                val singlePhrase = gson.fromJson(singlePhraseJson, Phrase::class.java)
                if (singlePhrase != null) {
                    listOf(singlePhrase)
                } else {
                    Log.e("GameActivity", "Deserialized single phrase JSON was null.")
                    loadErrorPhrase("Error: Invalid phrase data received.")
                }
            } catch (e: Exception) {
                Log.e("GameActivity", "Error deserializing single phrase JSON", e)
                loadErrorPhrase("Error: Could not load phrase data.")
            }
        } else {
            // Load phrases from asset files
            Log.d("GameActivity", "No PHRASE extra found. Loading from assets for Lang=$selectedLanguage, Diff=$selectedDifficulty, Mat=$selectedMaterial")
            val loadedPhrases = loadPhrasesFromAssets()
            if (loadedPhrases.isNotEmpty() && !loadedPhrases[0].spoken.startsWith("Error:")) {
                // Filter out learned phrases only when loading from assets
                loadedPhrases
                    .filterNot { isPhraseLearned(it, selectedLanguage, this) }
                    .shuffled() // Shuffle the remaining list
            } else {
                loadedPhrases // Keep the error phrase list if loading failed
            }
        }

        // Handle case where filtering leaves the asset list empty
        if (phrases.isEmpty() && singlePhraseJson == null) {
            Log.w("GameActivity", "Phrase list is empty after filtering learned phrases or initial load.")
            phrases = loadErrorPhrase("No new phrases available for this set.")
        }

        Log.d("GameActivity", "Final phrases list size: ${phrases.size}. First phrase: ${phrases.firstOrNull()?.spoken}")

        // Initialize SpeechRecognizer and MediaPlayer
        try {
            speechRecognizer = createSpeechRecognizer()
        } catch (e: Exception) {
            Log.e("GameActivity", "Failed to create SpeechRecognizer", e)
            Toast.makeText(this, "Speech recognition service unavailable on this device.", Toast.LENGTH_LONG).show()
            phrases = loadErrorPhrase("Error: Speech Recognizer unavailable.")
        }

        // Initialize MediaPlayer (can fail if resource is missing)
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.speak)
            if (mediaPlayer == null) {
                Log.e("GameActivity", "Failed to create MediaPlayer for R.raw.speak")
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Exception creating MediaPlayer", e)
        }

        // Set the Composable content
        setContent {
            GameScreen(
                phrases = phrases,
                selectedLanguage = selectedLanguage,
                onStartListening = ::startListening, // Use function reference
                onQuit = { finish() },
                onDestroyRecognizer = { if(::speechRecognizer.isInitialized) speechRecognizer.destroy() }
            )
        }

        requestAudioPermission() // Check permission status on create
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        mediaPlayer.release()
    }

    // --- Helper Methods ---
    private fun loadErrorPhrase(message: String): List<Phrase> {
        return listOf(
            Phrase(
                spoken = "Error: Check Logcat",
                expected = "Error",
                reading = "",
                english = message,
                grammar = ""
            )
        )
    }

    private fun createSpeechRecognizer(): SpeechRecognizer {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("GameActivity", "Speech recognition NOT AVAILABLE on this device.")
            throw IllegalStateException("Speech recognition service is not available.")
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(speechListener)
        Log.d("GameActivity", "SpeechRecognizer created successfully.")
        return recognizer
    }

    // Requests audio permission using the Activity Result API launcher
    private fun requestAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted
                Log.d("GameActivity", "RECORD_AUDIO permission already granted.")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Explain why the permission is needed (e.g., in a dialog) then request
                Log.i("GameActivity", "Showing permission rationale for RECORD_AUDIO.")
                // Example: Show a simple Toast explanation before requesting
                Toast.makeText(this,"Microphone access is needed to recognize your speech.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                // No explanation needed; request the permission directly
                Log.d("GameActivity", "Requesting RECORD_AUDIO permission.")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Starts the speech recognizer after checking permissions
    private suspend fun startListening(
        currentIndex: Int,
        onResult: (String) -> Unit,
        onSpeechEnded: () -> Unit
    ) {
        // 1. Check for permission *before* attempting to use the microphone
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult("Permission denied")
            return
        }

        // 3. Recreate recognizer instance to avoid potential issues (e.g., ERROR_CLIENT)
        Log.d("GameActivity", "Destroying and recreating SpeechRecognizer")
        speechRecognizer.destroy()
        speechRecognizer = createSpeechRecognizer()
        delay(50)
        currentOnResult = onResult
        currentOnSpeechEnded = onSpeechEnded

        // Determine the BCP-47 language code for the recognizer intent
        val recognizerLanguageCode = when (selectedLanguage) {
            "Japanese" -> "ja-JP"
            "Korean" -> "ko-KR"
            "Vietnamese" -> "vi-VN"
            "Spanish" -> "es-ES" // Or "es-MX", "es-US", etc. depending on target dialect
            "French" -> "fr-FR"
            "German" -> "de-DE"
            "Mandarin Chinese" -> "zh-CN" // Covers Simplified Mandarin, Mainland China
            "Italian" -> "it-IT"
            "Portuguese" -> "pt-BR" // Common default (Brazilian Portuguese)
            "Russian" -> "ru-RU"
            "Arabic" -> "ar-SA" // Modern Standard Arabic (often default)
            "Hindi" -> "hi-IN"
            else -> "en-US" // Fallback to English (United States)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognizerLanguageCode) // Set the target language
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Enable partial results
        }

        // 5. Start listening
        try {
            if (currentIndex >= 0 && currentIndex < phrases.size) {
                Log.d("GameActivity", "Starting SpeechRecognizer for [$recognizerLanguageCode]: ${phrases[currentIndex].spoken}")
                speechRecognizer.startListening(intent)
            } else {
                Log.e("GameActivity", "Error: currentIndex $currentIndex out of bounds for phrases list size ${phrases.size}")
                onResult("Error: Invalid phrase index.")
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting speech recognizer", e)
            onResult("Error starting mic: ${e.message}")
        }
    }

    // Loads phrases from asset JSON files based on selections
    private fun loadPhrasesFromAssets(): List<Phrase> {
        val gson = Gson()
        // Define which languages actually have corresponding asset files
        val languagesWithAssets = listOf("Japanese", "Korean", "Vietnamese", "Spanish")

        // If the selected language doesn't have assets, return an error list immediately.
        // This check prevents trying to load non-existent files.
        if (selectedLanguage !in languagesWithAssets) {
            Log.w("GameActivity", "Attempting to load assets for '$selectedLanguage', but no predefined assets exist.")
            return loadErrorPhrase("No predefined sets available for $selectedLanguage.")
            // Note: MainActivity UI should prevent reaching here if difficulty/material dropdowns are disabled.
        }

        // Determine filename based ONLY on languages known to have assets
        val fileName = when (selectedLanguage) {
            "Japanese" -> when (selectedDifficulty) {
                "JLPT N1" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n1_grammar.json" else "phrases_jp_jlpt_n1.json"
                "JLPT N2" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n2_grammar.json" else "phrases_jp_jlpt_n2.json"
                "JLPT N3" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n3_grammar.json" else "phrases_jp_jlpt_n3.json"
                "JLPT N4" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n4_grammar.json" else "phrases_jp_jlpt_n4.json"
                "JLPT N5" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n5_grammar.json" else "phrases_jp_jlpt_n5.json"
                else -> "phrases_jp_jlpt_n5.json" // Default for unknown Japanese difficulty
            }
            "Korean" -> when (selectedDifficulty) {
                "TOPIK I" -> "phrases_kr_topik_1.json"
                "TOPIK II" -> "phrases_kr_topik_2.json"
                else -> "phrases_kr_topik_1.json" // Default for unknown Korean difficulty
            }
            "Vietnamese" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_vi_beginner.json"
                "Intermediate" -> "phrases_vi_intermediate.json"
                "Advanced" -> "phrases_vi_advanced.json"
                else -> "phrases_vi_beginner.json" // Default for unknown Vietnamese difficulty
            }
            "Spanish" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_es_beginner.json"
                "Intermediate" -> "phrases_es_intermediate.json"
                "Advanced" -> "phrases_es_advanced.json"
                else -> "phrases_es_beginner.json" // Default for unknown Spanish difficulty
            }
            // No 'else' needed because of the `languagesWithAssets` check above
            else -> "" // Should be unreachable, indicates logic error if hit
        }

        if (fileName.isEmpty()) {
            Log.e("GameActivity", "Internal error: Could not determine asset filename for $selectedLanguage.")
            return loadErrorPhrase("Internal error determining asset file.")
        }

        Log.d("GameActivity", "Attempting to load phrases from assets file: $fileName")
        return try {
            // Open, read, and parse the JSON file
            assets.open(fileName).use { inputStream ->
                InputStreamReader(inputStream, "UTF-8").use { reader -> // Specify UTF-8
                    val phraseListType = object : TypeToken<List<Phrase>>() {}.type
                    gson.fromJson(reader, phraseListType) ?: emptyList<Phrase>().also {
                        Log.w("GameActivity", "Parsed JSON from $fileName resulted in null, returning empty list.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error loading or parsing phrases from assets file: $fileName", e)
            // Return an error phrase list instead of crashing
            loadErrorPhrase("Could not load phrases from $fileName")
        }
    }

    // --- GameScreen Composable ---
    @Composable
    fun GameScreen(
        phrases: List<Phrase>,
        selectedLanguage: String,
        onStartListening: suspend (Int, (String) -> Unit, () -> Unit) -> Unit,
        onQuit: () -> Unit,
        onDestroyRecognizer: () -> Unit
    ) {
        // State variables for the UI
        var currentIndex by remember { mutableStateOf(0) }
        var spokenText by remember { mutableStateOf("") }
        var isCorrect by remember { mutableStateOf<Boolean?>(null) }
        var showResult by remember { mutableStateOf(false) }
        var showHelp by remember { mutableStateOf(false) }
        var speechEnded by remember { mutableStateOf(false) }
        var lastPartialText by remember { mutableStateOf("") } // Store last partial for comparison logic
        var isRecording by remember { mutableStateOf(false) } // Track if mic is actively listening
        val context = LocalContext.current
        var showGrammarDialog by remember { mutableStateOf(false) } // State for grammar popup

        // Handle empty/error phrases list gracefully
        if (phrases.isEmpty() || phrases[0].spoken.startsWith("Error:")) {
            LaunchedEffect(Unit) { // Show toast on main thread if error detected
                if (phrases.isNotEmpty() && phrases[0].spoken.startsWith("Error:")) {
                    Toast.makeText(context, phrases[0].english, Toast.LENGTH_LONG).show()
                } else if (phrases.isEmpty()) {
                    Toast.makeText(context, "No phrases available.", Toast.LENGTH_LONG).show()
                }
            }
            // Display an error message and a back button
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF212121)), // Dark background for error
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        phrases.getOrNull(0)?.spoken ?: "No Phrases Found",
                        color = Color(0xFFE0F7FA), fontSize = 20.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        phrases.getOrNull(0)?.english ?: "Please go back and select another set or generate a phrase.",
                        color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onQuit,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)) // Use theme color
                    ) { Text("Back", color = Color(0xFFE0F7FA)) }
                }
            }
            // Ensure recognizer is destroyed if leaving due to error
            DisposableEffect(Unit) { onDispose { onDestroyRecognizer() } }
            return // Stop rendering the rest of the GameScreen UI
        }

        // Ensure currentIndex remains valid (e.g., if list changes)
        LaunchedEffect(phrases) {
            if (currentIndex >= phrases.size && phrases.isNotEmpty()) {
                Log.w("GameScreen", "currentIndex $currentIndex OOB for list (size ${phrases.size}), resetting to 0")
                currentIndex = 0
            } else if (phrases.isEmpty()){
                Log.w("GameScreen", "Phrase list became empty during LaunchedEffect check.")
                // Error state above should handle this, but log just in case
            }
        }

        // Get current phrase safely, check index validity again just in case
        val currentPhrase = if (currentIndex < phrases.size) phrases[currentIndex] else {
            // This fallback should ideally not be needed if index/list checks work
            Log.e("GameScreen", "Fallback triggered: currentIndex $currentIndex >= phrases.size ${phrases.size}")
            phrases.first()
        }

        // State for favorite/learned status and confirmation dialog
        var isFavorite by remember(currentPhrase) { mutableStateOf(isPhraseFavorite(currentPhrase, selectedLanguage, context)) }
        var isLearned by remember(currentPhrase) { mutableStateOf(isPhraseLearned(currentPhrase, selectedLanguage, context)) }
        var showLearnConfirmation by remember { mutableStateOf(false) }

        // Animate background color based on correctness
        val backgroundColor by animateColorAsState(
            targetValue = when (isCorrect) {
                true -> Color(0xFF4CAF50) // Green for correct
                false -> Color(0xFFE57373) // Red for incorrect
                null -> Color(0xFF007893) // Default Teal background
            },
            animationSpec = tween(durationMillis = 400), label = "BackgroundColorAnim"
        )

        // Coroutine scope and media players (remember ensures they persist across recompositions)
        val coroutineScope = rememberCoroutineScope()
        val speakMediaPlayer = remember(context) { try { MediaPlayer.create(context, R.raw.speak) } catch (e:Exception) { null } }
        val correctMediaPlayer = remember(context) { try { MediaPlayer.create(context, R.raw.correct) } catch (e:Exception) { null } }

        // Cleanup MediaPlayers and Recognizer when the Composable leaves the screen
        DisposableEffect(Unit) {
            onDispose {
                speakMediaPlayer?.release()
                correctMediaPlayer?.release()
                Log.d("GameScreen", "MediaPlayers released via DisposableEffect")
                onDestroyRecognizer() // Ensure recognizer is stopped/destroyed
            }
        }

        // Kuromoji Tokenizer instance (initialized only if needed for Japanese)
        val japaneseTokenizer by remember {
            mutableStateOf(
                if (selectedLanguage == "Japanese") {
                    try { Tokenizer.Builder().build() } catch (e: Exception) {
                        Log.e("GameScreen", "Failed to initialize Kuromoji Tokenizer", e)
                        null // Handle initialization failure
                    }
                } else null
            )
        }

        // Normalization function (handles basic punctuation/case and Japanese reading)
        suspend fun normalizeText(text: String): String {
            if (text.isBlank()) return ""
            // 1. Basic normalization for all languages
            val basicNormalized = text.replace("[\\s、。？！.,;:\"'()\\[\\]{}<>]".toRegex(), "").lowercase()

            // 2. Japanese specific: Convert to reading using Kuromoji
            return if (selectedLanguage == "Japanese" && japaneseTokenizer != null) {
                withContext(Dispatchers.Default) { // Run Kuromoji off the main thread
                    try {
                        val tokens: List<Token> = japaneseTokenizer!!.tokenize(basicNormalized)
                        // Join tokens using reading if available, else surface form. Handle "*" reading.
                        tokens.joinToString("") { token ->
                            token.reading?.takeIf { it != "*" } ?: token.surface
                        }.also { reading ->
                            Log.d("GameScreen", "Kuromoji Norm: '$text' -> '$basicNormalized' -> '$reading'")
                        }
                    } catch (e: Exception) {
                        Log.e("GameScreen", "Kuromoji tokenization error for '$basicNormalized'", e)
                        basicNormalized // Fallback to basic normalization on error
                    }
                }
            } else {
                // Only basic normalization for other languages
                Log.d("GameScreen", "Basic Norm: '$text' -> '$basicNormalized'")
                basicNormalized
            }
        }

        // LaunchedEffect to process spoken text results (including partials)
        LaunchedEffect(spokenText, speechEnded) {
            if (spokenText.isNotEmpty() && spokenText != "Listening..." && !spokenText.startsWith("Error")) {
                val expected = currentPhrase.expected
                val normalizedExpected = normalizeText(expected)
                val normalizedSpoken = normalizeText(spokenText)

                if (normalizedExpected.isBlank()) { // Safety check
                    Log.e("GameScreen", "Normalized expected text is blank for phrase: ${currentPhrase.spoken}")
                    return@LaunchedEffect
                }

                val isFinalResult = speechEnded // Flag indicating if speech recognizer finished
                Log.d("GameScreen", "Processing: SpokenNorm='$normalizedSpoken', ExpectedNorm='$normalizedExpected', Ended=$isFinalResult")

                if (normalizedExpected == normalizedSpoken) {
                    // Correct match found
                    Log.d("GameScreen", "Correct match.")
                    if (isCorrect != true) { // Play sound only on the first transition to correct
                        correctMediaPlayer?.start()
                    }
                    isCorrect = true
                    showResult = true
                    showHelp = true // Automatically show help on correct answer
                    isRecording = false // Turn off recording indicator
                } else {
                    // Not a perfect match, check partials and final state
                    val isPrefix = normalizedSpoken.isNotEmpty() && normalizedExpected.startsWith(normalizedSpoken)

                    if (!isPrefix && normalizedSpoken.isNotEmpty()) {
                        // If the partial result is already wrong (not a prefix)
                        Log.d("GameScreen", "Incorrect partial (not a prefix): '$normalizedSpoken'")
                        isCorrect = false
                        showResult = true // Show incorrect status immediately
                        isRecording = false
                        // No need to wait for speechEnded if it already diverged
                    } else if (isFinalResult) {
                        // If speech ended and it wasn't a perfect match (even if it was a prefix)
                        Log.d("GameScreen", "Incorrect final result (speech ended, no full match).")
                        isCorrect = false
                        showResult = true
                        isRecording = false
                    } else {
                        // Partial result is still a potential prefix, keep listening/waiting
                        Log.d("GameScreen", "Partial is prefix or empty: '$normalizedSpoken'. Waiting...")
                        // isCorrect remains null, showResult remains false
                    }
                }
                lastPartialText = spokenText // Always update the last seen partial text

            } else if (spokenText.startsWith("Error")) {
                // Handle error messages from the recognizer
                Log.d("GameScreen", "Error message received from recognizer: $spokenText")
                isCorrect = false
                showResult = true
                isRecording = false
                // Optionally clear the error message after a delay or on mic press
            }
        }

        // LaunchedEffect to reset state when the phrase (currentIndex or phrases list) changes
        LaunchedEffect(currentIndex, phrases) {
            if (currentIndex >= 0 && currentIndex < phrases.size) {
                Log.d("GameScreen", "Index/Phrase changed to: $currentIndex - '${phrases[currentIndex].spoken}'")
                // Reset all relevant UI state for the new phrase
                spokenText = ""
                isCorrect = null
                showResult = false
                showHelp = false
                speechEnded = false
                lastPartialText = ""
                isRecording = false
                // Update favorite/learned status based on the new phrase
                isFavorite = isPhraseFavorite(phrases[currentIndex], selectedLanguage, context)
                isLearned = isPhraseLearned(phrases[currentIndex], selectedLanguage, context)
            }
        }

        // --- Main UI Box ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor) // Apply animated background
                .padding(WindowInsets.systemBars.asPaddingValues()) // Handle system bars
        ) {
            // --- Top Buttons ---
            // Back Button
            IconButton(
                onClick = onQuit,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, "Quit", tint = Color(0xFFE0F7FA), modifier = Modifier.size(24.dp))
            }

            // Top Right Icons Row (Favorite & Learned)
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Favorite Button
                IconButton(
                    onClick = {
                        val phraseToToggle = currentPhrase
                        if (isFavorite) {
                            removeFavoritePhrase(phraseToToggle, selectedLanguage, context)
                            Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                        } else {
                            addFavoritePhrase(phraseToToggle, selectedLanguage, context)
                            Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
                        }
                        isFavorite = !isFavorite // Update UI state immediately
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF9999) else Color(0xFFE0F7FA),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Learned Button
                IconButton(
                    onClick = {
                        val phraseToToggle = currentPhrase
                        if (!isLearned) {
                            showLearnConfirmation = true // Show confirmation dialog first
                        } else {
                            // If already learned, remove instantly (unlearn)
                            removeLearnedPhrase(phraseToToggle, selectedLanguage, context)
                            isLearned = false
                            Toast.makeText(context, "Marked as 'not learned'", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isLearned) Icons.Filled.Lightbulb else Icons.Filled.LightbulbCircle,
                        contentDescription = "Learned",
                        tint = if (isLearned) Color(0xFFFFD700) else Color(0xFFE0F7FA),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Learn Confirmation Dialog
            if (showLearnConfirmation) {
                AlertDialog(
                    onDismissRequest = { showLearnConfirmation = false },
                    title = { Text("Mark as Learned?", color = Color(0xFFE0F7FA)) },
                    text = { Text("Marking this phrase as learned will hide it from future predefined sets. You can review learned phrases in Settings.", color = Color(0xFFE0F7FA)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                val phraseToLearn = currentPhrase
                                addLearnedPhrase(phraseToLearn, selectedLanguage, context)
                                isLearned = true
                                showLearnConfirmation = false
                                Toast.makeText(context, "Marked as learned", Toast.LENGTH_SHORT).show()

                                // Move to the next phrase only if multiple phrases exist (i.e., loaded from assets)
                                if (phrases.size > 1) {
                                    val nextIndex = (currentIndex + 1) % phrases.size // Safe wrap-around
                                    currentIndex = nextIndex
                                } else {
                                    // If it was the only phrase (generated), stay on screen. User can use "Done" button.
                                    Log.d("GameScreen", "Learned the only phrase. Staying on screen.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
                        ) { Text("Yes, Mark Learned", color = Color(0xFFE0F7FA)) }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showLearnConfirmation = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF015D73))
                        ) { Text("Cancel", color = Color(0xFFE0F7FA)) }
                    },
                    containerColor = Color(0xFF015D73) // Dialog background color
                )
            }

            // --- Central Content Area ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    // Adjust padding to prevent overlap with fixed top/bottom elements
                    .padding(top = 60.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.Center, // Center content vertically
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Correct/Incorrect Animation Icon
                AnimatedVisibility(
                    visible = showResult && isCorrect != null,
                    enter = scaleIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(150))
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).padding(bottom=20.dp), // Space below icon
                        contentAlignment = Alignment.Center
                    ) {
                        val circleProgress by animateFloatAsState(
                            targetValue = if (showResult && isCorrect != null) 1f else 0f,
                            animationSpec = tween(durationMillis = 500), label = "ResultCircleProgress"
                        )
                        // Draw the progress circle
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val diameter = size.minDimension
                            drawArc(
                                color = Color(0xFFE0F7FA).copy(alpha = 0.7f),
                                startAngle = -90f, sweepAngle = 360f * circleProgress,
                                useCenter = false, style = Stroke(width = 4.dp.toPx()), size = Size(diameter, diameter),
                                topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                            )
                        }
                        // Display Check or Close icon based on correctness
                        Icon(
                            imageVector = if (isCorrect == true) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = if (isCorrect == true) "Correct" else "Incorrect",
                            tint = Color(0xFFE0F7FA),
                            modifier = Modifier.size(40.dp).alpha(if (circleProgress > 0.5f) 1f else 0f) // Fade icon in
                        )
                    }
                }

                // Phrase Text (Spoken form)
                Text(
                    text = currentPhrase.spoken,
                    fontSize = 28.sp, // Larger font size for the main phrase
                    textAlign = TextAlign.Center,
                    color = Color(0xFFE0F7FA), // Light Cyan text color
                    modifier = Modifier.padding(bottom = 16.dp) // Space below the phrase
                )

                // Hiragana/Reading Display (Conditional)
                AnimatedVisibility(
                    visible = showHelp && selectedLanguage == "Japanese" && currentPhrase.reading.isNotBlank(),
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Reading", fontSize = 14.sp, color = Color(0xFFB0BEC5)) // Label color
                        Text(
                            text = currentPhrase.reading, fontSize = 18.sp,
                            textAlign = TextAlign.Center, color = Color(0xFFE0F7FA)
                        )
                        Spacer(modifier = Modifier.height(8.dp)) // Space after reading
                    }
                }

                // English Translation Display (Conditional)
                AnimatedVisibility(
                    visible = showHelp,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Meaning", fontSize = 14.sp, color = Color(0xFFB0BEC5)) // Label color
                        Text(
                            text = currentPhrase.english, fontSize = 18.sp, fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center, color = Color(0xFFE0F7FA)
                        )
                    }
                }
            } // End Central Content Column

            // --- Bottom UI Elements ---
            // Spoken Text Display (User's input)
            Text(
                // Show text only while recording or after a result is displayed
                text = if (isRecording || showResult) spokenText else "",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFFE0F7FA).copy(alpha = 0.8f), // Slightly transparent
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp) // Position above the mic button
                    .fillMaxWidth(0.8f) // Limit width to avoid edge overlap
            )

            // Microphone Button
            Box( // Wrapper Box for alignment and padding
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
            ) {
                // Animate mic button color based on recording state
                val micButtonColor by animateColorAsState(
                    targetValue = if (isRecording) Color(0xFFFF5252) else Color(0xFFFFB300), // Red while recording, Amber otherwise
                    animationSpec = tween(200), label = "MicColorAnim"
                )
                IconButton(
                    onClick = {
                        if (!isRecording && !showResult) {
                            // Start recording: reset state, play sound, launch listener
                            spokenText = ""
                            isCorrect = null
                            speechEnded = false
                            lastPartialText = ""
                            isRecording = true
                            speakMediaPlayer?.start()
                            coroutineScope.launch {
                                onStartListening(currentIndex,
                                    { result -> if (isRecording) spokenText = result }, // Update text if still recording
                                    {
                                        if (isRecording) { // Only update state if we were actually recording
                                            isRecording = false
                                            speechEnded = true
                                            Log.d("GameScreen", "Speech ended callback triggered while recording was true.")
                                        } else {
                                            Log.d("GameScreen", "Speech ended callback triggered but recording was already false.")
                                        }
                                    }
                                )
                            }
                        } else if (showResult) {
                            // Reset state if mic pressed while showing a result (allows retry)
                            Log.d("GameScreen", "Mic pressed while showing result. Resetting state for retry.")
                            spokenText = ""
                            isCorrect = null
                            showResult = false
                            speechEnded = false
                            lastPartialText = ""
                            isRecording = false // Ensure recording is stopped
                            // User needs to tap again to actually start recording
                        } else {
                            Log.d("GameScreen", "Mic clicked while already recording. Ignored.")
                            // Optional: Add visual feedback like a shake animation
                        }
                    },
                    modifier = Modifier
                        .size(70.dp)
                        .background(micButtonColor, CircleShape)
                        // Add pulsing effect when recording
                        .then(
                            if (isRecording) {
                                val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 1f, targetValue = 1.1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ), label = "MicScale"
                                )
                                Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                            } else Modifier // Apply no transformation if not recording
                        )
                ) {
                    Icon(
                        // Show replay icon if result is shown, otherwise mic icon
                        imageVector = if (showResult) Icons.Filled.Replay else Icons.Filled.Mic,
                        contentDescription = if (showResult) "Try Again" else "Speak",
                        tint = Color(0xFFE0F7FA),
                        modifier = Modifier.size(36.dp)
                    )
                }
            } // End Mic Button wrapper

            // Row for Bottom End Buttons (Grammar + Help)
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Space between buttons
            ) {
                // Grammar Info Button
                OutlinedIconButton(
                    onClick = {
                        if (currentPhrase.grammar.isNotBlank()) {
                            showGrammarDialog = true
                        } else {
                            Toast.makeText(context, "No specific grammar point for this phrase.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(48.dp), // Consistent size
                    // Enable button only if grammar field is not blank
                    enabled = currentPhrase.grammar.isNotBlank(),
                    border = BorderStroke(1.dp, if (currentPhrase.grammar.isNotBlank()) Color(0xFFFFB300) else Color(0xFF455A64))
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = "Show Grammar Info",
                        tint = if (currentPhrase.grammar.isNotBlank()) Color(0xFFE0F7FA) else Color(0xFF90A4AE), // Dim if disabled
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Help Button
                OutlinedIconButton(
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.size(48.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB300)) // Always Amber border
                ) {
                    Icon(
                        // Toggle icon based on help visibility state
                        imageVector = if (showHelp) Icons.Default.Help else Icons.Default.HelpOutline,
                        contentDescription = "Show/Hide Help",
                        tint = Color(0xFFE0F7FA), // Light cyan tint
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Skip/Next/Done Button (Bottom Start)
            OutlinedButton(
                onClick = {
                    // 1. Always reset state before moving
                    spokenText = ""
                    isCorrect = null
                    showResult = false
                    speechEnded = false
                    lastPartialText = ""
                    isRecording = false
                    // Stop any active listening
                    if (::speechRecognizer.isInitialized) {
                        try {
                            speechRecognizer.stopListening()
                            speechRecognizer.cancel()
                        } catch (e: Exception) { Log.e("GameScreen", "Error stopping/cancelling recognizer on skip", e) }
                    }

                    // 2. Determine action based on number of phrases
                    if (phrases.size > 1) {
                        // If multiple phrases (from assets), move to the next one
                        val nextIndex = (currentIndex + 1) % phrases.size // Wrap around
                        currentIndex = nextIndex
                    } else {
                        // If only one phrase (generated), treat as "Done" and quit
                        Toast.makeText(context, "Last phrase practiced.", Toast.LENGTH_SHORT).show()
                        onQuit() // Go back to main menu
                    }
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB300)), // Amber border
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE0F7FA)) // Light cyan text
            ) {
                // Change button text dynamically
                Text(
                    when {
                        isCorrect == true && showResult && phrases.size > 1 -> "Next" // Correct answer, more phrases
                        phrases.size > 1 -> "Skip" // Not correct or no result yet, more phrases
                        else -> "Done" // Only one phrase left (or shown)
                    }
                )
            }

            // Grammar Explanation Dialog
            if (showGrammarDialog) {
                GrammarDialog(
                    grammarPoint = currentPhrase.grammar,
                    language = selectedLanguage, // Pass language for correct path finding
                    onDismiss = { showGrammarDialog = false }
                )
            }
        } // End of Main Box (Root UI)
    } // End of GameScreen Composable

    // --- SharedPreferences Functions ---
    // (Add/Remove/Check Favorite/Learned - Implementations remain the same)
    private fun addFavoritePhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("FavoritesPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val favs: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        if (!favs.contains(phrase)) { // Avoid duplicates
            favs.add(phrase)
            prefs.edit().putString("favorites_$language", gson.toJson(favs)).apply()
            Log.d("Prefs", "Added favorite for $language: ${phrase.spoken}")
        }
    }

    private fun removeFavoritePhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("FavoritesPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val favs: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        if (favs.remove(phrase)) { // Check if removal was successful
            prefs.edit().putString("favorites_$language", gson.toJson(favs)).apply()
            Log.d("Prefs", "Removed favorite for $language: ${phrase.spoken}")
        } else {
            Log.w("Prefs", "Attempted to remove non-existent favorite for $language: ${phrase.spoken}")
        }
    }

    private fun isPhraseFavorite(phrase: Phrase, language: String, context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("FavoritesPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<List<Phrase>>() {}.type
        val favs: List<Phrase> = gson.fromJson(json, type) ?: emptyList()
        return favs.contains(phrase) // Relies on Phrase data class equals()
    }

    private fun addLearnedPhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("LearnedPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val learned: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        if (!learned.contains(phrase)) { // Avoid duplicates
            learned.add(phrase)
            prefs.edit().putString("learned_$language", gson.toJson(learned)).apply()
            Log.d("Prefs", "Added learned for $language: ${phrase.spoken}")
        }
    }

    private fun removeLearnedPhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("LearnedPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val learned: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        if(learned.remove(phrase)) { // Check if removal was successful
            prefs.edit().putString("learned_$language", gson.toJson(learned)).apply()
            Log.d("Prefs", "Removed learned for $language: ${phrase.spoken}")
        } else {
            Log.w("Prefs", "Attempted to remove non-existent learned for $language: ${phrase.spoken}")
        }
    }

    private fun isPhraseLearned(phrase: Phrase, language: String, context: android.content.Context): Boolean {
        // Don't filter out phrases if user is specifically reviewing Favorites or Learned lists
        if (selectedDifficulty == "Favorites" || selectedDifficulty == "Learned" || selectedDifficulty == "Generated") {
            return false // Treat as not learned in these contexts
        }

        // Check actual learned status for phrases loaded from regular asset sets
        val prefs = context.getSharedPreferences("LearnedPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<List<Phrase>>() {}.type
        val learned: List<Phrase> = gson.fromJson(json, type) ?: emptyList()
        return learned.contains(phrase) // Relies on Phrase data class equals()
    }

    // --- GrammarDialog Composable ---
    @Composable
    fun GrammarDialog(
        grammarPoint: String,
        language: String,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        // Determine language subfolder for grammar HTML files
        val langCode = when (language) {
            "Japanese" -> "jp"
            "Korean" -> "kr" // Assuming you might add these later
            "Spanish" -> "es"
            "Vietnamese" -> "vi"
            "French" -> "fr"
            "German" -> "de"
            // Add mappings for other languages if you create grammar files
            else -> "misc" // Fallback folder
        }
        val assetPath = "file:///android_asset/grammar/html/$langCode/${grammarPoint}.html"
        val errorBaseUrl = "file:///android_asset/grammar/html/" // Base for resolving CSS in error page
        // Simple HTML error page with link to CSS
        val errorHtml = """
            <html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><link rel="stylesheet" href="../css/grammar_style.css"></head>
            <body><h1>Error Loading Explanation</h1><p>Could not load explanation for: <code>$grammarPoint</code></p><hr><p>Looked for: <code>assets/grammar/html/$langCode/${grammarPoint}.html</code></p><p>(Ensure the file exists and the grammar name matches exactly)</p></body></html>
            """.trimIndent()

        Log.d("GrammarDialog", "Attempting to load Grammar URL: $assetPath")

        // Use Compose Dialog for modal display
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false) // Allow custom sizing
        ) {
            Surface( // Provides background, shape, elevation for the dialog
                modifier = Modifier
                    .fillMaxWidth(0.95f) // Use 95% of screen width
                    .fillMaxHeight(0.85f), // Use 85% of screen height
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, // Use theme surface color
                tonalElevation = 8.dp // Add a shadow
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Dialog Title Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF015D73)) // Header background color
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text( // Display grammar point in title
                            text = "Grammar: $grammarPoint",
                            color = Color(0xFFE0F7FA),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f), // Allow title to take space
                            maxLines = 1 // Prevent wrapping
                        )
                        // Close Button
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, "Close Dialog", tint = Color(0xFFE0F7FA))
                        }
                    }
                    // WebView Container
                    Box(modifier = Modifier.weight(1f).background(Color.White)) { // White background for HTML content
                        AndroidView(
                            factory = { webViewContext ->
                                // Create and configure WebView instance
                                WebView(webViewContext).apply {
                                    settings.javaScriptEnabled = false // Disable JS for security unless needed
                                    settings.loadWithOverviewMode = true // Zoom out to fit content
                                    settings.useWideViewPort = true // Respect viewport meta tag
                                    settings.builtInZoomControls = true // Allow pinch zoom
                                    settings.displayZoomControls = false // Hide +/- zoom buttons

                                    // Handle page loading errors
                                    webViewClient = object : WebViewClient() {
                                        var hasError = false // Track if error occurred to prevent multiple loads
                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            // Load custom error page only if the main frame request failed
                                            if (request?.isForMainFrame == true && !hasError) {
                                                hasError = true
                                                val errorDesc = error?.description ?: "Unknown WebView error"
                                                val errCode = error?.errorCode ?: -1
                                                val failUrl = request.url?.toString() ?: assetPath
                                                Log.e("GrammarDialog", "WebView Error ($errCode: $errorDesc) loading $failUrl")
                                                // Load the error HTML using a base URL so CSS resolves
                                                view?.loadDataWithBaseURL(errorBaseUrl, errorHtml, "text/html", "UTF-8", null)
                                            }
                                        }
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            // Reset error flag if a subsequent navigation succeeds
                                            if (url != null && !url.startsWith("data:")) { // Ignore the error page load itself
                                                hasError = false
                                            }
                                            Log.d("GrammarDialog", "WebView finished loading: $url")
                                        }
                                    }
                                    // Load the target grammar HTML file from assets
                                    loadUrl(assetPath)
                                }
                            },
                            modifier = Modifier.fillMaxSize() // WebView fills the Box
                        )
                    }
                } // End of Column
            } // End of Surface
        } // End of Dialog
    } // End of GrammarDialog Composable

} // End of GameActivity class