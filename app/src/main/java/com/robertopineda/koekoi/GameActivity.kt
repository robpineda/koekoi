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
// --- ADDED Imports for WebView Dialog ---
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


class GameActivity : ComponentActivity() {

    // Phrase data class including 'grammar'
    data class Phrase(
        val spoken: String,
        val expected: String,
        val reading: String,
        val english: String,
        val grammar: String // Added grammar field
    )

    // Existing properties
    private lateinit var phrases: List<Phrase>
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var mediaPlayer: MediaPlayer
    private var currentOnResult: ((String) -> Unit)? = null
    private var currentOnSpeechEnded: (() -> Unit)? = null
    private lateinit var selectedLanguage: String
    private lateinit var selectedDifficulty: String
    private lateinit var selectedMaterial: String

    // Existing speechListener
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
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
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
            Log.d("GameActivity", "Speech ended")
            currentOnSpeechEnded?.invoke()
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d("GameActivity", "Speech event: $eventType")
        }
    }

    // Existing onCreate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedLanguage = intent.getStringExtra("LANGUAGE") ?: "Japanese"
        selectedDifficulty = intent.getStringExtra("DIFFICULTY") ?: ""
        selectedMaterial = intent.getStringExtra("MATERIAL") ?: "Vocabulary"

        val singlePhraseJson = intent.getStringExtra("PHRASE")
        val gson = Gson()

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

        speechRecognizer = createSpeechRecognizer()
        mediaPlayer = MediaPlayer.create(this, R.raw.speak) // Ensure R.raw.speak exists

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

    // Existing lifecycle, permissions, recognizer setup
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        mediaPlayer.release()
    }

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

    private suspend fun startListening(
        currentIndex: Int,
        onResult: (String) -> Unit,
        onSpeechEnded: () -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult("Permission denied")
            return
        }
        Log.d("GameActivity", "Destroying and recreating SpeechRecognizer")
        speechRecognizer.destroy()
        speechRecognizer = createSpeechRecognizer()
        delay(50)
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
                    else -> "ja-JP"
                }
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            if (currentIndex >= 0 && currentIndex < phrases.size) {
                Log.d("GameActivity", "Starting SpeechRecognizer for: ${phrases[currentIndex].spoken}")
                speechRecognizer.startListening(intent)
            } else {
                Log.e("GameActivity", "Error: currentIndex $currentIndex out of bounds for phrases list size ${phrases.size}")
                onResult("Error: Invalid phrase index.")
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting speech recognizer", e)
            onResult("Error: ${e.message}")
        }
    }

    // MODIFIED: loadPhrasesFromAssets (reads JSON)
    private fun loadPhrasesFromAssets(): List<Phrase> {
        val gson = Gson()
        val fileName = when (selectedLanguage) {
            "Japanese" -> when (selectedDifficulty) {
                "JLPT N1" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n1_grammar.json" else "phrases_jp_jlpt_n1.json"
                "JLPT N2" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n2_grammar.json" else "phrases_jp_jlpt_n2.json"
                "JLPT N3" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n3_grammar.json" else "phrases_jp_jlpt_n3.json"
                "JLPT N4" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n4_grammar.json" else "phrases_jp_jlpt_n4.json"
                "JLPT N5" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n5_grammar.json" else "phrases_jp_jlpt_n5.json"
                else -> "phrases_jp_jlpt_n1.json"
            }
            "Korean" -> when (selectedDifficulty) {
                "TOPIK I" -> "phrases_kr_topik_1.json"
                "TOPIK II" -> "phrases_kr_topik_2.json"
                else -> "phrases_kr_topik_2.json"
            }
            "Vietnamese" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_vi_beginner.json"
                "Intermediate" -> "phrases_vi_intermediate.json"
                "Advanced" -> "phrases_vi_advanced.json"
                else -> "phrases_vi_beginner.json"
            }
            "Spanish" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_es_beginner.json"
                "Intermediate" -> "phrases_es_intermediate.json"
                "Advanced" -> "phrases_es_advanced.json"
                else -> "phrases_es_beginner.json"
            }
            else -> "phrases_jp_jlpt_n1.json"
        }
        Log.d("GameActivity", "Attempting to load phrases from assets file: $fileName")
        return try {
            assets.open(fileName).use { inputStream ->
                // Ensure UTF-8 encoding is used, crucial for non-Latin scripts
                InputStreamReader(inputStream, "UTF-8").use { reader ->
                    val phraseListType = object : TypeToken<List<Phrase>>() {}.type
                    gson.fromJson(reader, phraseListType) ?: emptyList<Phrase>().also {
                        Log.w("GameActivity", "Parsed JSON from $fileName resulted in null, returning empty list.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error loading or parsing phrases from assets file: $fileName", e)
            Toast.makeText(this, "Error loading phrases from $fileName", Toast.LENGTH_LONG).show()
            // Return a specific error phrase
            listOf(
                Phrase(
                    spoken = "Error: Check Logcat",
                    expected = "Error: Check Logcat",
                    reading = "error",
                    english = "Could not load phrases. File: $fileName",
                    grammar = "" // Default empty grammar
                )
            )
        }
    }

    // GameScreen Composable (Original UI + New Button + Dialog State)
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
        var lastPartialText by remember { mutableStateOf("") }
        var isRecording by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // ADDED: State for grammar dialog
        var showGrammarDialog by remember { mutableStateOf(false) }

        // Handle empty/error phrases list gracefully
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
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        phrases.getOrNull(0)?.english ?: "Please check assets or logs.",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Button(
                        onClick = onQuit,
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Text("Back")
                    }
                }
            }
            return // Stop rendering the rest
        }

        // Ensure currentIndex is valid after potential filtering/loading changes
        LaunchedEffect(phrases) {
            if (currentIndex >= phrases.size && phrases.isNotEmpty()) {
                Log.w("GameScreen", "currentIndex $currentIndex OOB for new list (size ${phrases.size}), reset to 0")
                currentIndex = 0
            }
        }

        // Handle edge case where list becomes empty after filtering/learning
        if (phrases.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF212121)), Alignment.Center) {
                Text("No phrases remaining.", color = Color.White, fontSize = 20.sp)
                Button(onClick = onQuit, modifier = Modifier.padding(top = 20.dp)) { Text("Back") }
            }
            return
        }

        // Get current phrase and dependent states safely
        val currentPhrase = phrases[currentIndex]
        var isFavorite by remember(currentPhrase) { mutableStateOf(isPhraseFavorite(currentPhrase, selectedLanguage, context)) }
        var isLearned by remember(currentPhrase) { mutableStateOf(isPhraseLearned(currentPhrase, selectedLanguage, context)) }
        var showLearnConfirmation by remember { mutableStateOf(false) }

        // Existing background color animation
        val backgroundColor by animateColorAsState(
            targetValue = when (isCorrect) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFE57373)
                null -> Color(0xFF212121)
            },
            animationSpec = tween(durationMillis = 300)
        )

        // Existing scope and media players
        val coroutineScope = rememberCoroutineScope()
        val speakMediaPlayer = remember { MediaPlayer.create(context, R.raw.speak) } // Ensure exists
        val correctMediaPlayer = remember { MediaPlayer.create(context, R.raw.correct) } // Ensure exists

        // Existing DisposableEffect
        DisposableEffect(Unit) {
            onDispose {
                speakMediaPlayer.release()
                correctMediaPlayer.release()
            }
        }

        // Existing toReading function
        suspend fun toReading(text: String): String {
            return if (selectedLanguage == "Japanese") {
                withContext(Dispatchers.Default) {
                    try {
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

        // Existing LaunchedEffect for processing spoken text
        LaunchedEffect(spokenText, speechEnded) {
            if (spokenText.isNotEmpty() && spokenText != "Listening..." && currentIndex < phrases.size) {
                val phrase = phrases[currentIndex] // Capture phrase for this effect instance
                val expected = phrase.expected

                // Run normalization potentially off the main thread
                val normalizedExpected = withContext(Dispatchers.IO) {
                    toReading(expected.replace("[\\s、。？！]".toRegex(), "")).lowercase()
                }
                val normalizedSpoken = withContext(Dispatchers.IO) {
                    toReading(spokenText.replace("[\\s、。？！]".toRegex(), "")).lowercase()
                }

                Log.d("GameScreen", "Comparing: SpokenNorm='$normalizedSpoken' vs ExpectedNorm='$normalizedExpected'")

                val isError = spokenText.contains("error", ignoreCase = true) ||
                        spokenText == "No match found. Try again." ||
                        spokenText == "No speech input. Speak louder."

                if (isError) {
                    Log.d("GameScreen", "Error detected in spoken text: $spokenText")
                    isCorrect = false // Treat errors as incorrect for UI feedback
                    showResult = true
                    isRecording = false // Stop recording state if error occurs
                    delay(1500) // Show feedback briefly
                    spokenText = "" // Reset text after feedback
                    isCorrect = null
                    showResult = false
                    lastPartialText = ""
                } else {
                    val isPrefix = normalizedSpoken.isNotEmpty() && normalizedExpected.startsWith(normalizedSpoken)
                    val matches = normalizedExpected == normalizedSpoken

                    Log.d("GameScreen", "Match=$matches, Prefix=$isPrefix, SpeechEnded=$speechEnded")

                    if (matches) {
                        Log.d("GameScreen", "Correct match found.")
                        isCorrect = true
                        showResult = true
                        showHelp = true // Show help automatically on correct
                        isRecording = false
                        correctMediaPlayer.start()
                        // Don't destroy recognizer, user clicks next/skip
                    } else if (speechEnded && !matches) { // Only mark incorrect if speech has ended AND it's not a match
                        Log.d("GameScreen", "Incorrect result after speech ended.")
                        isCorrect = false
                        showResult = true
                        isRecording = false
                    } else if (!isPrefix && normalizedSpoken.isNotEmpty()) {
                        // Partial result is not a prefix. Could be wrong. Store it.
                        // If speech ends now, the above condition will catch it.
                        Log.d("GameScreen", "Partial result is not a prefix: $normalizedSpoken")
                        lastPartialText = spokenText
                        if (speechEnded) { // If speech ended *right now* and it wasn't a prefix
                            isCorrect = false
                            showResult = true
                            isRecording = false
                        }
                    } else {
                        // It's a prefix or still empty, keep listening/waiting
                        Log.d("GameScreen", "Partial result is a prefix or empty.")
                        lastPartialText = spokenText
                    }
                }
            }
        }

        // Existing LaunchedEffect for currentIndex changes (resetting state)
        LaunchedEffect(currentIndex) {
            Log.d("GameScreen", "Current index changed to: $currentIndex")
            // Reset state for the new phrase
            spokenText = ""
            isCorrect = null
            showResult = false
            showHelp = false // Reset help visibility too
            speechEnded = false
            lastPartialText = ""
            isRecording = false
            // isFavorite/isLearned are updated via remember(currentPhrase)
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

                                // Trigger moving to the next index in the *current filtered list*
                                val nextIndex = (currentIndex + 1).let {
                                    // Wrap around if it reaches the end of the current list
                                    if (it >= phrases.size) 0 else it
                                }

                                // After learning, the list size effectively decreases for the next load.
                                // We just need to change the current index to trigger the LaunchedEffect.
                                // Checking if the list *will become* empty is complex here.
                                // The filtering in onCreate handles showing an empty state if needed.
                                currentIndex = nextIndex // Update state to trigger recomposition and effects
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
                        ) {
                            Text("Yes", color = Color(0xFFE0F7FA))
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showLearnConfirmation = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF015D73))
                        ) {
                            Text("No", color = Color(0xFFE0F7FA))
                        }
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
                                color = Color(0xFFE0F7FA),
                                startAngle = -90f,
                                sweepAngle = 360f * circleProgress,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx()),
                                size = Size(diameter, diameter),
                                topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                            )
                        }

                        Icon(
                            imageVector = if (isCorrect == true) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = if (isCorrect == true) "Correct" else "Incorrect",
                            tint = Color(0xFFE0F7FA),
                            modifier = Modifier
                                .size(40.dp)
                                .alpha(if (circleProgress > 0.5f) 1f else 0f)
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

                // Hiragana Reading (Conditional)
                AnimatedVisibility(
                    visible = showHelp && selectedLanguage == "Japanese",
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Hiragana", // Label
                            fontSize = 14.sp,
                            color = Color(0xFFE0F7FA) // Original color
                        )
                        Text(
                            text = currentPhrase.reading,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFFE0F7FA) // Original color
                        )
                    }
                }

                // Spacer
                Spacer(modifier = Modifier.height(24.dp))

                // English Translation (Conditional)
                AnimatedVisibility(
                    visible = showHelp,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = currentPhrase.english,
                        fontSize = 18.sp,
                        fontStyle = FontStyle.Italic, // Original style
                        textAlign = TextAlign.Center,
                        color = Color(0xFFE0F7FA) // Original color
                    )
                }
                // If spoken text should be higher, remove or adjust this Spacer
                // Spacer(modifier = Modifier.weight(1f))
            }

            // Spoken Text Display (at original position near bottom)
            Text(
                text = spokenText,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFFE0F7FA),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp) // Original padding from bottom
            )

            // Mic Button (Bottom Center)
            IconButton(
                onClick = {
                    if (!isRecording) { // Prevent multiple clicks
                        // Original logic: Reset state and start listening
                        spokenText = ""
                        isCorrect = null
                        showResult = false
                        // showHelp = false // Optional: reset help on mic press
                        speechEnded = false
                        lastPartialText = ""
                        isRecording = true
                        speakMediaPlayer.start() // Play sound effect
                        coroutineScope.launch {
                            onStartListening(currentIndex, { result ->
                                spokenText = result // Update state, effect handles comparison
                            }, {
                                // Original end of speech callback
                                isRecording = false
                                speechEnded = true // Set flag that recognizer stopped
                                Log.d("GameScreen", "Speech ended callback triggered")
                            })
                        }
                    } else {
                        Log.d("GameScreen", "Mic clicked while already recording.")
                        // Optional: Stop current recognition if clicked again while recording
                        // speechRecognizer.stopListening() // or cancel()
                        // isRecording = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp) // Original padding
                    .size(70.dp) // Original size
                    .background(
                        color = if (isRecording) Color(0xFFFF9999) else Color(0xFFFFB300), // Red/Amber
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Speak",
                    tint = Color(0xFFE0F7FA),
                    modifier = Modifier.size(36.dp) // Original size
                )
            }

            // MODIFIED: Row for Bottom End Buttons (Grammar + Help)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp), // Overall padding for the row
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Space between buttons
            ) {
                // ADDED: Grammar Info Button
                OutlinedIconButton(
                    onClick = {
                        // Show dialog only if grammar field is not empty/blank
                        if (currentPhrase.grammar.isNotBlank()) {
                            showGrammarDialog = true
                        } else {
                            Toast.makeText(context, "No grammar point specified for this phrase.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(48.dp), // Match help button size for alignment
                    border = BorderStroke(1.dp, Color(0xFFFF8F00)), // Amber outline
                    enabled = currentPhrase.grammar.isNotBlank() // Disable if no grammar data
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook, // Icon for grammar/book
                        contentDescription = "Show Grammar Info",
                        tint = if (currentPhrase.grammar.isNotBlank()) Color(0xFFE0F7FA) else Color.Gray, // Dim if disabled
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Original Help Button (now inside the Row)
                OutlinedIconButton(
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.size(48.dp), // Ensure consistent size
                    border = BorderStroke(1.dp, Color(0xFFFF8F00)) // Original border
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline, // Using HelpOutline consistently
                        contentDescription = "Show Help",
                        tint = Color(0xFFE0F7FA),
                        modifier = Modifier.size(24.dp) // Consistent icon size
                    )
                }
            }


            // Skip/Next Button (Bottom Start)
            OutlinedButton(
                onClick = {
                    // Original logic: Reset state, move index, trigger recomposition/effects
                    spokenText = ""
                    isCorrect = null
                    showResult = false
                    // showHelp = false // Optional: reset help on skip/next
                    speechEnded = false
                    lastPartialText = ""
                    isRecording = false
                    // Consider stopping recognizer if active
                    // speechRecognizer.stopListening() // or cancel()

                    val nextIndex = (currentIndex + 1).let {
                        // Wrap around if it reaches the end of the current list
                        if (it >= phrases.size) 0 else it
                    }
                    currentIndex = nextIndex // Update state to trigger LaunchedEffect(currentIndex)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp), // Original padding
                border = BorderStroke(1.dp, Color(0xFFFF8F00)), // Original border
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFE0F7FA) // Original color
                )
            ) {
                // Original text logic
                Text(
                    text = if (isCorrect == true && showResult) "Next" else "Skip"
                )
            }

            // ADDED: Grammar Dialog - Shown conditionally based on state
            if (showGrammarDialog) {
                GrammarDialog(
                    grammarPoint = currentPhrase.grammar, // Pass the grammar string from current phrase
                    onDismiss = { showGrammarDialog = false } // Action to hide the dialog
                )
            }
        } // End of Main Box
    } // End of GameScreen Composable


    // SharedPreferences Functions (Original implementations - work with updated Phrase)
    private fun addFavoritePhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("FavoritesPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val favs: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        if (!favs.contains(phrase)) {
            favs.add(phrase)
            prefs.edit().putString("favorites_$language", gson.toJson(favs)).apply()
        }
    }

    private fun removeFavoritePhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("FavoritesPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val favs: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        favs.remove(phrase)
        prefs.edit().putString("favorites_$language", gson.toJson(favs)).apply()
    }

    private fun isPhraseFavorite(phrase: Phrase, language: String, context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("FavoritesPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<List<Phrase>>() {}.type
        val favs: List<Phrase> = gson.fromJson(json, type) ?: emptyList()
        return favs.contains(phrase)
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
        }
    }

    private fun removeLearnedPhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("LearnedPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val learned: MutableList<Phrase> = gson.fromJson(json, type) ?: mutableListOf()
        learned.remove(phrase)
        prefs.edit().putString("learned_$language", gson.toJson(learned)).apply()
    }

    private fun isPhraseLearned(phrase: Phrase, language: String, context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("LearnedPrefs", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<List<Phrase>>() {}.type
        val learned: List<Phrase> = gson.fromJson(json, type) ?: emptyList()
        return learned.contains(phrase)
    }


    // ADDED: Composable for the Grammar Dialog with WebView
    @Composable
    fun GrammarDialog(
        grammarPoint: String,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        // Construct the Android asset path. Ensure grammarPoint exactly matches the filename.
        // If filenames were sanitized (e.g., replacing '〜' with '_'), do that here too.
        val assetPath = "file:///android_asset/grammar/html/${grammarPoint}.html"

        // Fallback HTML content in case the file isn't found or WebView fails
        val errorHtml = """
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="../css/grammar_style.css">
            </head>
            <body>
                <h1>Error Loading Explanation</h1>
                <p>Could not load the grammar explanation for:</p>
                <p><code>$grammarPoint</code></p>
                <hr>
                <p>Please check if the file exists at:</p>
                <p><code>assets/grammar/html/${grammarPoint}.html</code></p>
                <p>(Note: Filename must match exactly, including special characters.)</p>
            </body>
            </html>
            """.trimIndent()

        Log.d("GrammarDialog", "Attempting to load URL: $assetPath")

        // Use Compose Dialog for modal behavior
        Dialog(
            onDismissRequest = onDismiss,
            // Allow dialog to size itself based on content within constraints
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Surface provides background, shape, elevation for the dialog content
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f) // Use most of the screen width
                    .fillMaxHeight(0.85f), // Use most of the screen height
                shape = RoundedCornerShape(16.dp), // Rounded corners
                color = MaterialTheme.colorScheme.surface, // Use theme's surface color
                tonalElevation = 8.dp // Add some elevation
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Dialog Title Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF015D73)) // Darker teal header consistent with other buttons
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display the grammar point in the title
                        Text(
                            text = "Grammar: $grammarPoint",
                            color = Color(0xFFE0F7FA), // Light text on dark header
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f), // Take available space
                            maxLines = 1 // Prevent wrapping if too long
                        )
                        // Close Button
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Dialog",
                                tint = Color(0xFFE0F7FA) // Light icon on dark header
                            )
                        }
                    }

                    // WebView Container
                    Box(modifier = Modifier.weight(1f)) { // WebView takes remaining space
                        AndroidView(
                            factory = { webViewContext ->
                                // Create and configure WebView instance
                                WebView(webViewContext).apply {
                                    // Basic settings
                                    settings.javaScriptEnabled = false // Disable JS unless needed for HTML content
                                    settings.loadWithOverviewMode = true // Zoom out to fit content
                                    settings.useWideViewPort = true // Allow viewport meta tag
                                    settings.builtInZoomControls = true // Enable pinch-to-zoom
                                    settings.displayZoomControls = false // Hide +/- zoom buttons

                                    // WebViewClient to handle events within the WebView
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            // Log the error in detail
                                            val errorDescription = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                error?.description ?: "Unknown error"
                                            } else {
                                                "Unknown error"
                                            }
                                            val errorCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                error?.errorCode ?: -1
                                            } else {
                                                -1
                                            }
                                            val failingUrl = request?.url?.toString() ?: assetPath
                                            Log.e("GrammarDialog", "WebView Error ($errorCode: $errorDescription) loading $failingUrl")

                                            // Load fallback HTML directly on error
                                            // Use the assetPath as baseURL so relative CSS path still works
                                            view?.loadDataWithBaseURL(assetPath, errorHtml, "text/html", "UTF-8", null)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            // Log success or if the error page was loaded
                                            Log.d("GrammarDialog", "WebView finished loading: $url")
                                        }
                                    }
                                    // Load the target HTML file from assets
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