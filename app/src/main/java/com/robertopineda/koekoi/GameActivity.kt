package com.robertopineda.koekoi

// --- Keep existing imports ---
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.text.font.FontStyle
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import java.io.InputStreamReader
// --- Imports for WebView Dialog ---
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


class GameActivity : ComponentActivity() {

    // Phrase data class including 'grammar' - Stays the same
    data class Phrase(
        val spoken: String,
        val expected: String,
        val reading: String,
        val english: String,
        val grammar: String
    )

    // Existing properties
    private lateinit var phrases: List<Phrase>
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var mediaPlayer: MediaPlayer
    private var currentOnResult: ((String) -> Unit)? = null
    private var currentOnSpeechEnded: (() -> Unit)? = null
    private lateinit var selectedLanguage: String
    private lateinit var selectedDifficulty: String
    private var selectedMaterial: String = "Vocabulary" // Default if not passed

    // Existing speechListener - Stays the same
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
                SpeechRecognizer.ERROR_CLIENT -> "Client side error (Might need restart)" // Added hint
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
            currentOnSpeechEnded?.invoke() // Also signal end on error
        }


        override fun onBeginningOfSpeech() {
            Log.d("GameActivity", "Speech began")
        }

        override fun onEndOfSpeech() {
            Log.d("GameActivity", "Speech ended")
            currentOnSpeechEnded?.invoke()
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d("GameActivity", "Speech event: $eventType")
        }
    }

    // *** MODIFIED onCreate ***
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedLanguage = intent.getStringExtra("LANGUAGE") ?: "Japanese" // Keep default
        selectedDifficulty = intent.getStringExtra("DIFFICULTY") ?: "Unknown" // Could be "Generated", "Favorites", "Learned", or JLPT/TOPIK level
        selectedMaterial = intent.getStringExtra("MATERIAL") ?: "Vocabulary" // Needed for asset loading fallback

        val singlePhraseJson = intent.getStringExtra("PHRASE")
        val gson = Gson()

        // Determine how to load phrases: single generated/selected phrase OR from assets
        phrases = if (singlePhraseJson != null) {
            try {
                Log.d("GameActivity", "Attempting to load single phrase from JSON extra: $singlePhraseJson")
                val singlePhrase = gson.fromJson(singlePhraseJson, Phrase::class.java)
                if (singlePhrase != null) {
                    listOf(singlePhrase) // Create a list containing only this phrase
                } else {
                    Log.e("GameActivity", "Deserialized single phrase JSON was null.")
                    loadErrorPhrase("Error: Invalid phrase data received.")
                }
            } catch (e: Exception) {
                Log.e("GameActivity", "Error deserializing single phrase JSON", e)
                loadErrorPhrase("Error: Could not load phrase data.")
            }
        } else {
            // --- Fallback to loading from assets ---
            Log.d("GameActivity", "No PHRASE extra found. Loading from assets for Lang=$selectedLanguage, Diff=$selectedDifficulty, Mat=$selectedMaterial")
            val loadedPhrases = loadPhrasesFromAssets() // Load based on Lang/Diff/Mat
            if (loadedPhrases.isNotEmpty() && !loadedPhrases[0].spoken.startsWith("Error:")) {
                // Filter out learned phrases ONLY if loading from assets
                loadedPhrases
                    .filterNot { isPhraseLearned(it, selectedLanguage, this) }
                    .shuffled() // Shuffle the remaining list
            } else {
                loadedPhrases // Keep the error phrase list if loading failed
            }
        }

        // If filtering left the asset list empty, show an error/message phrase
        if (phrases.isEmpty() && singlePhraseJson == null) { // Check if empty *after* potential filtering
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
            // Handle this critical failure - maybe finish the activity?
            phrases = loadErrorPhrase("Error: Speech Recognizer unavailable.")
            // Can't proceed without recognizer, maybe disable mic button in UI?
        }

        mediaPlayer = MediaPlayer.create(this, R.raw.speak) // Ensure R.raw.speak exists

        setContent {
            GameScreen(
                phrases = phrases, // Pass the final list (could be 1 or many)
                selectedLanguage = selectedLanguage,
                onStartListening = ::startListening, // Use function reference
                onQuit = { finish() },
                onDestroyRecognizer = { if(::speechRecognizer.isInitialized) speechRecognizer.destroy() }
            )
        }

        requestAudioPermission()
    }

    // Helper to create a placeholder phrase list on error
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


    // Existing lifecycle, permissions, recognizer setup - Stays the same
    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun createSpeechRecognizer(): SpeechRecognizer {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("GameActivity", "Speech recognition NOT AVAILABLE on this device.")
            throw IllegalStateException("Speech recognition service is not available.")
            // Or handle this more gracefully in onCreate
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(speechListener)
        Log.d("GameActivity", "SpeechRecognizer created successfully.")
        return recognizer
    }

    // --- MODIFIED startListening - added check for initialization ---
    private suspend fun startListening(
        currentIndex: Int,
        onResult: (String) -> Unit,
        onSpeechEnded: () -> Unit
    ) {
        if (!::speechRecognizer.isInitialized) {
            Log.e("GameActivity", "startListening called but SpeechRecognizer not initialized.")
            onResult("Error: Speech Recognizer unavailable.")
            onSpeechEnded() // Ensure state resets
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult("Permission denied")
            onSpeechEnded()
            requestAudioPermission() // Prompt again
            return
        }

        // Recreate recognizer to handle potential "Client side error" or busy state
        try {
            Log.d("GameActivity", "Recreating SpeechRecognizer before listening...")
            speechRecognizer.cancel() // Cancel any ongoing listening
            speechRecognizer.destroy()
            delay(50) // Short delay before creating new one
            speechRecognizer = createSpeechRecognizer()
            delay(50) // Short delay before starting
        } catch (e: Exception) {
            Log.e("GameActivity", "Error recreating SpeechRecognizer", e)
            onResult("Error: Cannot start microphone.")
            onSpeechEnded()
            return
        }


        currentOnResult = onResult
        currentOnSpeechEnded = onSpeechEnded
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                when (selectedLanguage) {
                    "Japanese" -> "ja-JP"
                    "Korean" -> "ko-KR"
                    "Vietnamese" -> "vi-VN"
                    "Spanish" -> "es-ES"
                    else -> "en-US" // Sensible default
                }
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Optional: Add hints for specific phrases (might improve accuracy)
            // putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, arrayListOf(phrases[currentIndex].expected))
        }
        try {
            if (currentIndex >= 0 && currentIndex < phrases.size) {
                Log.d("GameActivity", "Starting SpeechRecognizer for [${intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)}]: ${phrases[currentIndex].spoken}")
                speechRecognizer.startListening(intent)
                // Log.d("GameActivity", "Calling startListening on recognizer instance: $speechRecognizer")
            } else {
                Log.e("GameActivity", "Error: currentIndex $currentIndex out of bounds for phrases list size ${phrases.size}")
                onResult("Error: Invalid phrase index.")
                onSpeechEnded()
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting speech recognizer", e)
            onResult("Error starting mic: ${e.message}")
            onSpeechEnded()
        }
    }

    // loadPhrasesFromAssets - Stays the same (used as fallback)
    private fun loadPhrasesFromAssets(): List<Phrase> {
        val gson = Gson()
        // Determine filename based on language, difficulty, material
        val fileName = when (selectedLanguage) {
            "Japanese" -> when (selectedDifficulty) {
                // Ensure "Generated", "Favorites", "Learned" don't try to load specific files here
                // If difficulty is one of those, maybe return empty or default N5? Let's default.
                "JLPT N1" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n1_grammar.json" else "phrases_jp_jlpt_n1.json"
                "JLPT N2" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n2_grammar.json" else "phrases_jp_jlpt_n2.json"
                "JLPT N3" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n3_grammar.json" else "phrases_jp_jlpt_n3.json"
                "JLPT N4" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n4_grammar.json" else "phrases_jp_jlpt_n4.json"
                "JLPT N5" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n5_grammar.json" else "phrases_jp_jlpt_n5.json"
                else -> "phrases_jp_jlpt_n5.json" // Default to N5 if difficulty is unknown/Generated etc.
            }
            "Korean" -> when (selectedDifficulty) {
                "TOPIK I" -> "phrases_kr_topik_1.json"
                "TOPIK II" -> "phrases_kr_topik_2.json"
                else -> "phrases_kr_topik_1.json" // Default to TOPIK I
            }
            "Vietnamese" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_vi_beginner.json"
                "Intermediate" -> "phrases_vi_intermediate.json"
                "Advanced" -> "phrases_vi_advanced.json"
                else -> "phrases_vi_beginner.json" // Default Beginner
            }
            "Spanish" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_es_beginner.json"
                "Intermediate" -> "phrases_es_intermediate.json"
                "Advanced" -> "phrases_es_advanced.json"
                else -> "phrases_es_beginner.json" // Default Beginner
            }
            else -> "phrases_jp_jlpt_n5.json" // Ultimate fallback
        }
        Log.d("GameActivity", "Attempting to load phrases from assets file: $fileName")
        return try {
            assets.open(fileName).use { inputStream ->
                InputStreamReader(inputStream, "UTF-8").use { reader ->
                    val phraseListType = object : TypeToken<List<Phrase>>() {}.type
                    gson.fromJson(reader, phraseListType) ?: emptyList<Phrase>().also {
                        Log.w("GameActivity", "Parsed JSON from $fileName resulted in null, returning empty list.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error loading or parsing phrases from assets file: $fileName", e)
            // Toast.makeText(this, "Error loading phrases from $fileName", Toast.LENGTH_LONG).show() // Avoid toast in background thread
            // Return list with error phrase instead of crashing
            loadErrorPhrase("Could not load phrases from $fileName")
        }
    }


    // GameScreen Composable - Stays largely the same, but ensure it handles list size 1 gracefully
    @Composable
    fun GameScreen(
        phrases: List<Phrase>,
        selectedLanguage: String,
        onStartListening: suspend (Int, (String) -> Unit, () -> Unit) -> Unit,
        onQuit: () -> Unit,
        onDestroyRecognizer: () -> Unit
    ) {
        // Existing state variables
        var currentIndex by remember { mutableStateOf(0) }
        var spokenText by remember { mutableStateOf("") }
        var isCorrect by remember { mutableStateOf<Boolean?>(null) }
        var showResult by remember { mutableStateOf(false) }
        var showHelp by remember { mutableStateOf(false) }
        var speechEnded by remember { mutableStateOf(false) }
        var lastPartialText by remember { mutableStateOf("") } // Keep tracking partials
        var isRecording by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // State for grammar dialog
        var showGrammarDialog by remember { mutableStateOf(false) }

        // Handle empty/error phrases list
        if (phrases.isEmpty() || phrases[0].spoken.startsWith("Error:")) {
            LaunchedEffect(Unit) { // Show toast on main thread if error detected
                if (phrases.isNotEmpty() && phrases[0].spoken.startsWith("Error:")) {
                    Toast.makeText(context, phrases[0].english, Toast.LENGTH_LONG).show()
                } else if (phrases.isEmpty()) {
                    Toast.makeText(context, "No phrases available.", Toast.LENGTH_LONG).show()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF212121)), // Use base background
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        phrases.getOrNull(0)?.spoken ?: "No Phrases",
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
                    ) {
                        Text("Back", color = Color(0xFFE0F7FA))
                    }
                }
            }
            // Destroy recognizer when leaving due to error
            DisposableEffect(Unit) {
                onDispose { onDestroyRecognizer() }
            }
            return // Stop rendering the rest of the GameScreen
        }


        // Ensure currentIndex is valid (robustness)
        LaunchedEffect(phrases) {
            if (currentIndex >= phrases.size && phrases.isNotEmpty()) {
                Log.w("GameScreen", "currentIndex $currentIndex OOB for list (size ${phrases.size}), resetting to 0")
                currentIndex = 0
            } else if (phrases.isEmpty()){
                Log.w("GameScreen", "Phrase list became empty.")
                // Handled by the block above, but good to log
            }
        }

        // Get current phrase safely, check index validity again just in case
        val currentPhrase = if (currentIndex < phrases.size) phrases[currentIndex] else phrases.first() // Fallback just in case

        var isFavorite by remember(currentPhrase) { mutableStateOf(isPhraseFavorite(currentPhrase, selectedLanguage, context)) }
        var isLearned by remember(currentPhrase) { mutableStateOf(isPhraseLearned(currentPhrase, selectedLanguage, context)) }
        var showLearnConfirmation by remember { mutableStateOf(false) }

        // Background color animation
        val backgroundColor by animateColorAsState(
            targetValue = when (isCorrect) {
                true -> Color(0xFF4CAF50) // Green
                false -> Color(0xFFE57373) // Red
                null -> Color(0xFF007893) // Default Teal background
            },
            animationSpec = tween(durationMillis = 400), label = "BackgroundColorAnim"
        )

        // Coroutine scope and media players
        val coroutineScope = rememberCoroutineScope()
        // Use remember with context key to recreate if context changes (unlikely but safe)
        val speakMediaPlayer = remember(context) { MediaPlayer.create(context, R.raw.speak) }
        val correctMediaPlayer = remember(context) { MediaPlayer.create(context, R.raw.correct) }

        // Cleanup MediaPlayers
        DisposableEffect(Unit) {
            onDispose {
                speakMediaPlayer?.release()
                correctMediaPlayer?.release()
                Log.d("GameScreen", "MediaPlayers released")
                // Ensure recognizer is stopped and destroyed when screen leaves
                onDestroyRecognizer()
            }
        }

        // Kuromoji Tokenizer instance (consider making it lazy or injecting)
        val japaneseTokenizer by remember { mutableStateOf(if (selectedLanguage == "Japanese") Tokenizer.Builder().build() else null) }

        // toReading function for normalization (Japanese specific)
        suspend fun normalizeText(text: String): String {
            if (text.isBlank()) return ""
            // 1. Basic normalization for all languages: lowercase, remove punctuation/whitespace
            val basicNormalized = text.replace("[\\s、。？！.,;:\"'()\\[\\]{}<>]".toRegex(), "").lowercase()

            // 2. Japanese specific: Convert to reading using Kuromoji
            return if (selectedLanguage == "Japanese" && japaneseTokenizer != null) {
                withContext(Dispatchers.Default) { // Run Kuromoji off the main thread
                    try {
                        val tokens: List<Token> = japaneseTokenizer!!.tokenize(basicNormalized)
                        tokens.joinToString("") { token ->
                            // Use reading if available, otherwise surface form. Handle Katakana/unknown readings.
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
                Log.d("GameScreen", "Basic Norm: '$text' -> '$basicNormalized'")
                basicNormalized // Only basic normalization for other languages
            }
        }


        // LaunchedEffect for processing spoken text (includes partial results logic)
        LaunchedEffect(spokenText, speechEnded) {
            if (spokenText.isNotEmpty() && spokenText != "Listening..." && !spokenText.startsWith("Error")) {
                val expected = currentPhrase.expected
                val normalizedExpected = normalizeText(expected)
                val normalizedSpoken = normalizeText(spokenText)

                if (normalizedExpected.isBlank()) { // Should not happen with valid phrases
                    Log.e("GameScreen", "Normalized expected text is blank!")
                    return@LaunchedEffect
                }

                val isFinalResult = speechEnded // Check if speech recognition has fully ended
                Log.d("GameScreen", "Processing: SpokenNorm='$normalizedSpoken', ExpectedNorm='$normalizedExpected', Ended=$isFinalResult")

                if (normalizedExpected == normalizedSpoken) {
                    Log.d("GameScreen", "Correct match.")
                    if (isCorrect != true) { // Play sound only on first correct match
                        correctMediaPlayer?.start()
                    }
                    isCorrect = true
                    showResult = true
                    showHelp = true // Show help on correct answer
                    isRecording = false // Stop recording state
                } else {
                    // Handle partial results: Check if the current partial is *not* a prefix of the target
                    val isPrefix = normalizedSpoken.isNotEmpty() && normalizedExpected.startsWith(normalizedSpoken)

                    if (!isPrefix && normalizedSpoken.isNotEmpty()) {
                        Log.d("GameScreen", "Incorrect partial (not a prefix): '$normalizedSpoken'")
                        isCorrect = false
                        showResult = true // Show incorrect immediately if deviate
                        isRecording = false
                        // No need to wait for speechEnded if it's already wrong
                    } else if (isFinalResult) { // If speech ended and it's not a full match (but was potentially a prefix)
                        Log.d("GameScreen", "Incorrect final result (speech ended, no match).")
                        isCorrect = false
                        showResult = true
                        isRecording = false
                    } else {
                        Log.d("GameScreen", "Partial is prefix or empty: '$normalizedSpoken'. Waiting...")
                        // It's still potentially correct, keep listening / wait for end
                        // Keep isCorrect as null, don't show result yet
                    }
                }
                lastPartialText = spokenText // Update last partial text seen

            } else if (spokenText.startsWith("Error")) {
                Log.d("GameScreen", "Error message received: $spokenText")
                isCorrect = false
                showResult = true
                isRecording = false
                // Optionally clear error message after a delay
                // delay(2000)
                // if (spokenText.startsWith("Error")) spokenText = ""
            }
        }

        // LaunchedEffect for currentIndex changes
        LaunchedEffect(currentIndex, phrases) { // React to phrase list changes too
            if (currentIndex >= 0 && currentIndex < phrases.size) {
                Log.d("GameScreen", "Index/Phrase changed to: $currentIndex - '${phrases[currentIndex].spoken}'")
                // Reset state for the new phrase
                spokenText = ""
                isCorrect = null
                showResult = false
                showHelp = false
                speechEnded = false
                lastPartialText = ""
                isRecording = false
                isFavorite = isPhraseFavorite(phrases[currentIndex], selectedLanguage, context) // Update favorite status
                isLearned = isPhraseLearned(phrases[currentIndex], selectedLanguage, context)   // Update learned status
            }
        }

        // --- Main UI Box (Structure remains similar) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor) // Animated background
                .padding(WindowInsets.systemBars.asPaddingValues())
        ) {
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
                        val phraseToToggle = currentPhrase // Use currentPhrase directly
                        if (isFavorite) {
                            removeFavoritePhrase(phraseToToggle, selectedLanguage, context)
                            Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                        } else {
                            addFavoritePhrase(phraseToToggle, selectedLanguage, context)
                            Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
                        }
                        isFavorite = !isFavorite
                    },
                    modifier = Modifier.size(40.dp) // Keep consistent size
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF9999) else Color(0xFFE0F7FA), // Pinkish red when favorite
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Learned Button
                IconButton(
                    onClick = {
                        val phraseToToggle = currentPhrase
                        if (!isLearned) {
                            showLearnConfirmation = true // Show confirmation dialog
                        } else {
                            // If already learned, remove instantly
                            removeLearnedPhrase(phraseToToggle, selectedLanguage, context)
                            isLearned = false
                            Toast.makeText(context, "Marked as 'not learned'", Toast.LENGTH_SHORT).show() // Adjusted message
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isLearned) Icons.Filled.Lightbulb else Icons.Filled.LightbulbCircle, // Or LightbulbOutline
                        contentDescription = "Learned",
                        tint = if (isLearned) Color(0xFFFFD700) else Color(0xFFE0F7FA), // Gold when learned
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

                                // Move to next phrase *only if* loading from assets (list size > 1)
                                if (phrases.size > 1) {
                                    val nextIndex = (currentIndex + 1) % phrases.size // Safe wrap around
                                    currentIndex = nextIndex
                                } else {
                                    // If it was a single generated phrase, maybe just stay or offer to go back?
                                    // For now, just stay on the screen. User can use Skip/Next or Back.
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
                    containerColor = Color(0xFF015D73) // Dialog background
                )
            }


            // Central Content Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    // Add padding to avoid overlap with top/bottom buttons
                    .padding(top = 60.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.Center, // Center vertically in the available space
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Correct/Incorrect Animation Icon
                AnimatedVisibility(
                    visible = showResult && isCorrect != null,
                    enter = scaleIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(150)) // Faster fade out
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).padding(bottom=20.dp), // Added padding below icon
                        contentAlignment = Alignment.Center
                    ) {
                        val circleProgress by animateFloatAsState(
                            targetValue = if (showResult && isCorrect != null) 1f else 0f,
                            animationSpec = tween(durationMillis = 500), label = "ResultCircleProgress"
                        )
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val diameter = size.minDimension
                            drawArc(
                                color = Color(0xFFE0F7FA).copy(alpha = 0.7f), // Slightly transparent white
                                startAngle = -90f, sweepAngle = 360f * circleProgress,
                                useCenter = false, style = Stroke(width = 4.dp.toPx()), size = Size(diameter, diameter),
                                topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                            )
                        }
                        Icon(
                            imageVector = if (isCorrect == true) Icons.Filled.CheckCircle else Icons.Filled.Cancel, // Use filled icons
                            contentDescription = if (isCorrect == true) "Correct" else "Incorrect", tint = Color(0xFFE0F7FA),
                            modifier = Modifier.size(40.dp).alpha(if (circleProgress > 0.5f) 1f else 0f) // Fade in icon
                        )
                    }
                }

                // Spacer between animation and text - adjust as needed
                // Spacer(modifier = Modifier.height(10.dp)) // Reduced spacer

                // Phrase Text (Spoken form)
                Text(
                    text = currentPhrase.spoken,
                    fontSize = 28.sp, // Slightly larger
                    textAlign = TextAlign.Center,
                    color = Color(0xFFE0F7FA), // Light Cyan
                    modifier = Modifier.padding(bottom = 16.dp) // Add padding below
                )

                // Hiragana Reading (Conditional)
                AnimatedVisibility(
                    visible = showHelp && selectedLanguage == "Japanese" && currentPhrase.reading.isNotBlank(),
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Reading", fontSize = 14.sp, color = Color(0xFFB0BEC5)) // Lighter Gray
                        Text(
                            text = currentPhrase.reading, fontSize = 18.sp,
                            textAlign = TextAlign.Center, color = Color(0xFFE0F7FA)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // English Translation (Conditional)
                AnimatedVisibility(
                    visible = showHelp,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Meaning", fontSize = 14.sp, color = Color(0xFFB0BEC5))
                        Text(
                            text = currentPhrase.english, fontSize = 18.sp, fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center, color = Color(0xFFE0F7FA)
                        )
                    }
                }

                // Spacer(modifier = Modifier.weight(1f)) // Push content up if needed, but center arrangement should handle it
            } // End Central Content Column

            // Spoken Text Display (Near bottom, above mic)
            Text(
                // Display last partial or final result, clear if error shown for too long?
                text = if (isRecording) spokenText else (if (showResult) spokenText else ""), // Show only when recording or result is shown
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFFE0F7FA).copy(alpha = 0.8f), // Slightly dimmed
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp) // Position above mic button
                    .fillMaxWidth(0.8f) // Limit width
            )

            // Mic Button
            Box( // Wrapper Box for alignment
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp) // Pad from bottom edge
            ) {
                val micButtonColor by animateColorAsState(
                    targetValue = if (isRecording) Color(0xFFFF5252) else Color(0xFFFFB300), // Red when recording, Amber otherwise
                    animationSpec = tween(200), label = "MicColorAnim"
                )
                IconButton(
                    onClick = {
                        if (!isRecording && !showResult) { // Only allow starting if not already recording AND not showing a result
                            spokenText = ""
                            isCorrect = null
                            // showResult = false // Handled by index change or implicitly
                            speechEnded = false
                            lastPartialText = ""
                            isRecording = true
                            speakMediaPlayer?.start()
                            coroutineScope.launch {
                                // Pass current index to know which phrase is being attempted
                                onStartListening(currentIndex,
                                    { result -> if (isRecording) spokenText = result }, // Update state only if still recording
                                    {
                                        if (isRecording) { // Check if still recording before setting ended state
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
                            // If showing result, pressing mic again should reset for a new attempt
                            Log.d("GameScreen", "Mic pressed while showing result. Resetting state.")
                            spokenText = ""
                            isCorrect = null
                            showResult = false
                            speechEnded = false
                            lastPartialText = ""
                            isRecording = false // Ensure reset before potentially starting again immediately
                            // Optionally, could trigger a new recording start here if desired
                            // For now, it just resets the state. User needs to tap again to record.
                        } else {
                            Log.d("GameScreen", "Mic clicked while already recording. Ignored.")
                            // Maybe provide feedback like a small shake?
                        }
                    },
                    modifier = Modifier
                        .size(70.dp)
                        .background(micButtonColor, CircleShape)
                        // Add pulsing animation if recording
                        .then(
                            if (isRecording) {
                                val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 1.1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ), label = "MicScale"
                                )
                                Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                            } else Modifier
                        )

                ) {
                    Icon(
                        imageVector = if (showResult) Icons.Filled.Replay else Icons.Filled.Mic, // Show Replay icon if result is shown
                        contentDescription = if (showResult) "Try Again" else "Speak",
                        tint = Color(0xFFE0F7FA),
                        modifier = Modifier.size(36.dp)
                    )
                }
            } // End Mic Button wrapper


            // Row for Bottom End Buttons (Grammar + Help)
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.size(48.dp),
                    border = BorderStroke(1.dp, if (currentPhrase.grammar.isNotBlank()) Color(0xFFFFB300) else Color(0xFF455A64)),
                    enabled = currentPhrase.grammar.isNotBlank() // Disable if no grammar point
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook, contentDescription = "Show Grammar Info",
                        tint = if (currentPhrase.grammar.isNotBlank()) Color(0xFFE0F7FA) else Color(0xFF90A4AE),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Help Button
                OutlinedIconButton(
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.size(48.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB300)) // Amber border
                ) {
                    Icon(
                        imageVector = if (showHelp) Icons.Default.Help else Icons.Default.HelpOutline, // Toggle icon based on state
                        contentDescription = "Show/Hide Help",
                        tint = Color(0xFFE0F7FA), modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Skip/Next Button
            OutlinedButton(
                onClick = {
                    // Always reset state before moving
                    spokenText = ""
                    isCorrect = null
                    showResult = false
                    speechEnded = false
                    lastPartialText = ""
                    isRecording = false
                    if (::speechRecognizer.isInitialized) { // Stop listening if active
                        speechRecognizer.stopListening()
                        speechRecognizer.cancel()
                    }

                    if (phrases.size > 1) {
                        val nextIndex = (currentIndex + 1) % phrases.size // Wrap around list
                        currentIndex = nextIndex
                    } else {
                        // If only one phrase (generated), maybe just quit? Or disable button?
                        Toast.makeText(context, "Last phrase practiced.", Toast.LENGTH_SHORT).show()
                        onQuit() // Go back to main menu if only one phrase was shown
                    }
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB300)), // Amber border
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE0F7FA))
            ) {
                // Change text based on context
                Text(if (isCorrect == true && showResult && phrases.size > 1) "Next" else if (phrases.size > 1) "Skip" else "Done")
            }


            // Grammar Dialog (Pass language)
            if (showGrammarDialog) {
                GrammarDialog(
                    grammarPoint = currentPhrase.grammar,
                    language = selectedLanguage, // Pass the language
                    onDismiss = { showGrammarDialog = false }
                )
            }
        } // End of Main Box
    } // End of GameScreen Composable

    // --- SharedPreferences Functions (Add/Remove/Check Favorite/Learned) ---
    // --- These remain the same as they work with the Phrase data class ---
    private fun addFavoritePhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("FavoritesPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val favs: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        if (!favs.contains(phrase)) {
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
        if (favs.remove(phrase)) {
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
        // Use contains check which relies on Phrase data class equality
        val isFav = favs.contains(phrase)
        // Log.d("Prefs", "Checking favorite status for $language - ${phrase.spoken}: $isFav")
        return isFav
    }

    private fun addLearnedPhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("LearnedPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val learned: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        if (!learned.contains(phrase)) {
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
        if(learned.remove(phrase)) {
            prefs.edit().putString("learned_$language", gson.toJson(learned)).apply()
            Log.d("Prefs", "Removed learned for $language: ${phrase.spoken}")
        } else {
            Log.w("Prefs", "Attempted to remove non-existent learned for $language: ${phrase.spoken}")
        }
    }

    private fun isPhraseLearned(phrase: Phrase, language: String, context: android.content.Context): Boolean {
        // Don't check learned status if the phrase came from Favorites or Learned review itself
        if (selectedDifficulty == "Favorites" || selectedDifficulty == "Learned") {
            return false // Treat as not learned in these contexts to avoid filtering them out
        }

        val prefs = context.getSharedPreferences("LearnedPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<List<Phrase>>() {}.type
        val learned: List<Phrase> = gson.fromJson(json, type) ?: emptyList()
        val isLearned = learned.contains(phrase)
        // Log.d("Prefs", "Checking learned status for $language - ${phrase.spoken}: $isLearned")
        return isLearned
    }


    // GrammarDialog Composable - Stays the same
    @Composable
    fun GrammarDialog(
        grammarPoint: String,
        language: String,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        val langCode = when (language) {
            "Japanese" -> "jp"
            "Korean" -> "kr"
            "Spanish" -> "es"
            "Vietnamese" -> "vi"
            else -> "misc"
        }
        val assetPath = "file:///android_asset/grammar/html/$langCode/${grammarPoint}.html"
        val errorBaseUrl = "file:///android_asset/grammar/html/"
        val errorHtml = """
            <html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><link rel="stylesheet" href="../css/grammar_style.css"></head>
            <body><h1>Error Loading Explanation</h1><p>Could not load explanation for: <code>$grammarPoint</code></p><hr><p>Looked for: <code>assets/grammar/html/$langCode/${grammarPoint}.html</code></p></body></html>
            """.trimIndent()

        Log.d("GrammarDialog", "Attempting to load URL: $assetPath")

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF015D73))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grammar: $grammarPoint",
                            color = Color(0xFFE0F7FA),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, "Close Dialog", tint = Color(0xFFE0F7FA))
                        }
                    }
                    Box(modifier = Modifier.weight(1f).background(Color.White)) { // Added white background for WebView content area
                        AndroidView(
                            factory = { webViewContext ->
                                WebView(webViewContext).apply {
                                    settings.javaScriptEnabled = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    webViewClient = object : WebViewClient() {
                                        var hasError = false // Track if error occurred
                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            // Only load error page if the main request failed and it's for the primary URL
                                            if (request?.isForMainFrame == true && !hasError) {
                                                hasError = true
                                                val errorDesc = error?.description ?: "Unknown WebView error"
                                                val errCode = error?.errorCode ?: -1
                                                val failUrl = request.url?.toString() ?: assetPath
                                                Log.e("GrammarDialog", "WebView Error ($errCode: $errorDesc) loading $failUrl")
                                                view?.loadDataWithBaseURL(errorBaseUrl, errorHtml, "text/html", "UTF-8", null)
                                            }
                                        }
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            // Reset error flag if navigation succeeds later (e.g., user clicks a link in error page)
                                            if (url != null && !url.startsWith("data:")) {
                                                hasError = false
                                            }
                                            Log.d("GrammarDialog", "WebView finished loading: $url")
                                        }
                                    }
                                    loadUrl(assetPath)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }


} // End of GameActivity class