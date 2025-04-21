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
    private lateinit var mediaPlayer: MediaPlayer
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
            currentOnResult?.invoke(errorMessage)
        }

        override fun onBeginningOfSpeech() {
            Log.d("GameActivity", "Speech began")
        }
        override fun onEndOfSpeech() {
            Log.d("GameActivity", "Speech ended");
            currentOnSpeechEnded?.invoke()
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d("GameActivity", "Speech event: $eventType")
        }
    }

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve intent extras
        selectedLanguage = intent.getStringExtra("LANGUAGE") ?: "Japanese"
        selectedDifficulty = intent.getStringExtra("DIFFICULTY") ?: "Unknown"
        selectedMaterial = intent.getStringExtra("MATERIAL") ?: "Vocabulary"

        val singlePhraseJson = intent.getStringExtra("PHRASE")
        val gson = Gson()

        // Load phrases: either single generated phrase or from assets
        phrases = if (singlePhraseJson != null) {
            try {
                Log.d("GameActivity", "Attempting to load single phrase from JSON: $singlePhraseJson")
                listOf(gson.fromJson(singlePhraseJson, Phrase::class.java))
            } catch (e: Exception) {
                Log.e("GameActivity", "Error deserializing single phrase JSON", e)
                loadPhrasesFromAssets().shuffled().filterNot { isPhraseLearned(it, selectedLanguage, this) }
            }
        } else {
            loadPhrasesFromAssets().shuffled().filterNot { isPhraseLearned(it, selectedLanguage, this) }
        }
        Log.d("GameActivity", "Phrases loaded (filtered): ${phrases.size} phrases")

        // Initialize SpeechRecognizer and MediaPlayer
        speechRecognizer = createSpeechRecognizer()
        mediaPlayer = MediaPlayer.create(this, R.raw.speak) // Ensure R.raw.speak exists

        // Set the Composable content
        setContent {
            GameScreen(
                phrases = phrases,
                selectedLanguage = selectedLanguage,
                onStartListening = { index, onResult, onSpeechEnded ->
                    startListening(index, onResult, onSpeechEnded)
                },
                onQuit = { finish() },
                onDestroyRecognizer = { speechRecognizer.destroy() }
            )
        }

        requestAudioPermission()
    }

    // Destroy existing lifecycle, permissions, recognizer setup
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

    // Requests audio permission using the Activity Result API launcher
    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun createSpeechRecognizer(): SpeechRecognizer {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(speechListener)
        return recognizer
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
    // GameScreen Composable (Accepts selectedLanguage)
    @Composable
    fun GameScreen(
        phrases: List<Phrase>,
        selectedLanguage: String, // Now receives the selected language
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
        var lastPartialText by remember { mutableStateOf("") }
        var isRecording by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // State for grammar dialog
        var showGrammarDialog by remember { mutableStateOf(false) }

        // Handle empty/error phrases list
        if (phrases.isEmpty() || phrases[0].spoken.startsWith("Error:")) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF212121)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        phrases.getOrNull(0)?.spoken ?: "No phrases found.",
                        color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        phrases.getOrNull(0)?.english ?: "Please check assets or logs.",
                        color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Button(onClick = onQuit, modifier = Modifier.padding(top = 20.dp)) { Text("Back") }
                }
            }
            return
        }

        // Ensure currentIndex is valid
        LaunchedEffect(phrases) {
            if (currentIndex >= phrases.size && phrases.isNotEmpty()) {
                Log.w("GameScreen", "currentIndex $currentIndex OOB for new list (size ${phrases.size}), reset to 0")
                currentIndex = 0
            }
        }
        if (phrases.isEmpty()) { // Re-check after effect
            Box(
                Modifier.fillMaxSize().background(Color(0xFF212121)), Alignment.Center
            ) {
                Text("No phrases remaining.", color = Color.White, fontSize = 20.sp)
                Button(onClick = onQuit, modifier = Modifier.padding(top = 20.dp)) { Text("Back") }
            }
            return
        }

        // Get current phrase and dependent states
        val currentPhrase = phrases[currentIndex]
        var isFavorite by remember(currentPhrase) { mutableStateOf(isPhraseFavorite(currentPhrase, selectedLanguage, context)) }
        var isLearned by remember(currentPhrase) { mutableStateOf(isPhraseLearned(currentPhrase, selectedLanguage, context)) }
        var showLearnConfirmation by remember { mutableStateOf(false) }

        // Background color animation
        val backgroundColor by animateColorAsState(
            targetValue = when (isCorrect) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFE57373)
                null -> Color(0xFF212121)
            },
            animationSpec = tween(durationMillis = 300)
        )

        // Coroutine scope and media players
        val coroutineScope = rememberCoroutineScope()
        val speakMediaPlayer = remember { MediaPlayer.create(context, R.raw.speak) }
        val correctMediaPlayer = remember { MediaPlayer.create(context, R.raw.correct) }

        // DisposableEffect
        DisposableEffect(Unit) {
            onDispose {
                speakMediaPlayer.release()
                correctMediaPlayer.release()
            }
        }

        // toReading function
        suspend fun toReading(text: String): String {
            return if (selectedLanguage == "Japanese") {
                withContext(Dispatchers.Default) {
                    try {
                        // Ensure tokenizer is initialized - consider making it a member if performance is critical
                        val tokenizer = Tokenizer.Builder().build()
                        val tokens: List<Token> = tokenizer.tokenize(text)
                        tokens.joinToString("") { it.reading ?: it.surface }
                    } catch (e: Exception) {
                        Log.e("GameScreen", "Kuromoji tokenization error", e)
                        text // fallback
                    }
                }
            } else {
                text
            }
        }

        // LaunchedEffect for processing spoken text
        LaunchedEffect(spokenText, speechEnded) {
            if (spokenText.isNotEmpty() && spokenText != "Listening..." && currentIndex < phrases.size) {
                val phrase = phrases[currentIndex]
                val expected = phrase.expected
                val normalizedExpected = withContext(Dispatchers.IO) {
                    toReading(expected.replace("[\\s\\p{Punct}]".toRegex(), "")).lowercase()
                }
                val normalizedSpoken = withContext(Dispatchers.IO) {
                    toReading(spokenText.replace("[\\s\\p{Punct}]".toRegex(), "")).lowercase()
                }
                Log.d("GameScreen", "Comparing: SpokenNorm='$normalizedSpoken' vs ExpectedNorm='$normalizedExpected'")
                val isError = spokenText.contains("error", ignoreCase = true) ||
                        spokenText == "No match found. Try again." ||
                        spokenText == "No speech input. Speak louder."
                if (isError) {
                    Log.d("GameScreen", "Error detected: $spokenText")
                    isCorrect = false
                    showResult = true
                    isRecording = false
                    delay(1500)
                    spokenText = ""
                    isCorrect = null
                    showResult = false
                    lastPartialText = ""
                } else {
                    val isPrefix = normalizedSpoken.isNotEmpty() && normalizedExpected.startsWith(normalizedSpoken)
                    val matches = normalizedExpected == normalizedSpoken
                    Log.d("GameScreen", "Match=$matches, Prefix=$isPrefix, Ended=$speechEnded")
                    if (matches) {
                        Log.d("GameScreen", "Correct match.")
                        isCorrect = true
                        showResult = true
                        showHelp = true
                        isRecording = false
                        correctMediaPlayer.start()
                    } else if (speechEnded && !matches) {
                        Log.d("GameScreen", "Incorrect result after speech ended.")
                        isCorrect = false
                        showResult = true
                        isRecording = false
                    } else if (!isPrefix && normalizedSpoken.isNotEmpty()) {
                        Log.d("GameScreen", "Partial not prefix: $normalizedSpoken")
                        lastPartialText = spokenText
                        if (speechEnded) {
                            isCorrect = false
                            showResult = true
                            isRecording = false
                        }
                    } else {
                        Log.d("GameScreen", "Partial is prefix or empty.")
                        lastPartialText = spokenText
                    }
                }
            }
        }


        // LaunchedEffect for currentIndex changes
        LaunchedEffect(currentIndex) {
            Log.d("GameScreen", "Index changed: $currentIndex")
            spokenText = ""
            isCorrect = null
            showResult = false
            showHelp = false
            speechEnded = false
            lastPartialText = ""
            isRecording = false
        }

        // Main UI Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(WindowInsets.systemBars.asPaddingValues()) // Respect system insets
        ) {
            // Back Button
            IconButton(
                onClick = onQuit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Quit",
                    tint = Color(0xFFE0F7FA),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Top Right Icons Row (Favorite & Learned)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Favorite Button
                IconButton(
                    onClick = {
                        val phraseToToggle = phrases[currentIndex]
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
                        val phraseToToggle = phrases[currentIndex]
                        if (!isLearned) {
                            showLearnConfirmation = true // Show confirmation dialog
                        } else {
                            // If already learned, remove instantly
                            removeLearnedPhrase(phraseToToggle, selectedLanguage, context)
                            isLearned = false
                            Toast.makeText(context, "Removed from learned phrases", Toast.LENGTH_SHORT).show()
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
                    title = { Text("Confirm Learning", color = Color(0xFFE0F7FA)) },
                    text = { Text("Have you fully learned this phrase? It will no longer appear in the game.", color = Color(0xFFE0F7FA)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                val phraseToLearn = phrases[currentIndex]
                                addLearnedPhrase(phraseToLearn, selectedLanguage, context)
                                isLearned = true
                                showLearnConfirmation = false
                                Toast.makeText(context, "Added to learned phrases", Toast.LENGTH_SHORT).show()

                                val nextIndex = (currentIndex + 1).let {
                                    if (it >= phrases.size) 0 else it // Wrap around
                                }
                                currentIndex = nextIndex // Update state to trigger effects
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
                        ) { Text("Yes", color = Color(0xFFE0F7FA)) }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showLearnConfirmation = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF015D73))
                        ) { Text("No", color = Color(0xFFE0F7FA)) }
                    },
                    containerColor = Color(0xFF015D73) // Dialog background
                )
            }


            // Central Content Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp), // Original padding
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Spacer to push content down from top icons
                Spacer(modifier = Modifier.height(16.dp))

                // Correct/Incorrect Animation Icon
                AnimatedVisibility(
                    visible = showResult && isCorrect != null,
                    enter = scaleIn(animationSpec = tween(300)),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val circleProgress by animateFloatAsState(
                            targetValue = if (showResult && isCorrect != null) 1f else 0f,
                            animationSpec = tween(durationMillis = 500)
                        )
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val diameter = size.minDimension
                            drawArc(
                                color = Color(0xFFE0F7FA), startAngle = -90f, sweepAngle = 360f * circleProgress,
                                useCenter = false, style = Stroke(width = 4.dp.toPx()), size = Size(diameter, diameter),
                                topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                            )
                        }
                        Icon(
                            imageVector = if (isCorrect == true) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = if (isCorrect == true) "Correct" else "Incorrect", tint = Color(0xFFE0F7FA),
                            modifier = Modifier.size(40.dp).alpha(if (circleProgress > 0.5f) 1f else 0f)
                        )
                    }
                }

                // Spacer between animation and text
                Spacer(modifier = Modifier.height(40.dp))

                // Phrase Text (Spoken form)
                Text(
                    text = currentPhrase.spoken, // Use checked phrase
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFE0F7FA)
                )

                // Spacer
                Spacer(modifier = Modifier.height(16.dp))

                // Romanization Reading
                AnimatedVisibility(
                    visible = showHelp,// && selectedLanguage == "Japanese",
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Reading", fontSize = 14.sp, color = Color(0xFFE0F7FA))
                        Text(
                            text = currentPhrase.reading, fontSize = 18.sp,
                            textAlign = TextAlign.Center, color = Color(0xFFE0F7FA)
                        )
                    }
                }

                // Spacer
                Spacer(modifier = Modifier.height(24.dp))

                // English Translation (Conditional)
                AnimatedVisibility(
                    visible = showHelp,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Text(
                        text = currentPhrase.english, fontSize = 18.sp, fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center, color = Color(0xFFE0F7FA)
                    )
                }
                // Spacer(modifier = Modifier.weight(1f)) // Removed or adjust if needed for spoken text position
            }

            // Spoken Text Display
            Text(
                text = spokenText, fontSize = 18.sp, textAlign = TextAlign.Center, color = Color(0xFFE0F7FA),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp) // Original position
            )

            // Mic Button
            IconButton(
                onClick = {
                    spokenText = ""
                    isCorrect = null
                    showResult = false
                    speechEnded = false
                    lastPartialText = ""
                    isRecording = true
                    speakMediaPlayer.start()
                    coroutineScope.launch {
                        onStartListening(currentIndex, { result -> spokenText = result }, {
                            isRecording = false
                            speechEnded = true
                            Log.d("GameScreen", "Speech ended callback triggered")
                        })
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).size(70.dp)
                    .background(if (isRecording) Color(0xFFFF9999) else Color(0xFFFFB300), CircleShape)
            ) {
                Icon(Icons.Filled.Mic, "Speak", tint = Color(0xFFE0F7FA), modifier = Modifier.size(36.dp))
            }

            // Row for Bottom End Buttons (Grammar + Help)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp), // Overall padding for the row
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Space between buttons
            ) {
                // Grammar Info Button
                OutlinedIconButton(
                    onClick = {
                        if (currentPhrase.grammar.isNotBlank()) {
                            showGrammarDialog = true // Show dialog
                        } else {
                            Toast.makeText(context, "No grammar point specified.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(48.dp), // Match help button size
                    border = BorderStroke(1.dp, Color(0xFFFF8F00)),
                    enabled = currentPhrase.grammar.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook, contentDescription = "Show Grammar Info",
                        tint = if (currentPhrase.grammar.isNotBlank()) Color(0xFFE0F7FA) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Help Button
                OutlinedIconButton(
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.size(48.dp), // Consistent size
                    border = BorderStroke(1.dp, Color(0xFFFF8F00))
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline, contentDescription = "Show Help",
                        tint = Color(0xFFE0F7FA), modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Skip/Next Button
            OutlinedButton(
                onClick = {
                    spokenText = ""
                    isCorrect = null
                    showResult = false
                    speechEnded = false
                    lastPartialText = ""
                    isRecording = false
                    val nextIndex = (currentIndex + 1).let { if (it >= phrases.size) 0 else it }
                    currentIndex = nextIndex // Update state to trigger effects
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFF8F00)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE0F7FA))
            ) {
                Text(if (isCorrect == true && showResult) "Next" else "Skip")
            }

            // Grammar Dialog - Call updated GrammarDialog passing selectedLanguage
            if (showGrammarDialog) {
                GrammarDialog(
                    grammarPoint = currentPhrase.grammar,
                    language = selectedLanguage, // Pass the selected language here
                    onDismiss = { showGrammarDialog = false }
                )
            }
        } // End of Main Box
    } // End of GameScreen Composable // End of GameScreen Composable

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