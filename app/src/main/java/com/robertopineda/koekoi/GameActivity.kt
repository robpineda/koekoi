package com.robertopineda.koekoi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.text.font.FontStyle // Keep existing imports
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
import androidx.compose.ui.graphics.drawscope.Stroke // Correct import
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.gson.Gson // <<<--- ADDED: Gson import
import com.google.gson.reflect.TypeToken // <<<--- ADDED: TypeToken import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import java.io.InputStreamReader // <<<--- ADDED: InputStreamReader import


class GameActivity : ComponentActivity() {

    // --- MODIFIED: Phrase data class includes 'grammar' ---
    data class Phrase(
        val spoken: String,
        val expected: String,
        val reading: String,
        val english: String,
        val grammar: String // Added grammar field
    )

    private lateinit var phrases: List<Phrase>
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var mediaPlayer: MediaPlayer
    private var currentOnResult: ((String) -> Unit)? = null
    private var currentOnSpeechEnded: (() -> Unit)? = null
    private lateinit var selectedLanguage: String
    private lateinit var selectedDifficulty: String
    private lateinit var selectedMaterial: String

    // Original speechListener remains the same
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedLanguage = intent.getStringExtra("LANGUAGE") ?: "Japanese"
        selectedDifficulty = intent.getStringExtra("DIFFICULTY") ?: ""
        selectedMaterial = intent.getStringExtra("MATERIAL") ?: "Vocabulary"

        val singlePhraseJson = intent.getStringExtra("PHRASE")
        val gson = Gson() // Gson instance needed here too

        phrases = if (singlePhraseJson != null) {
            // --- MODIFIED: Deserialize single phrase from JSON ---
            try {
                Log.d("GameActivity", "Attempting to load single phrase from JSON: $singlePhraseJson")
                listOf(gson.fromJson(singlePhraseJson, Phrase::class.java))
            } catch (e: Exception) {
                Log.e("GameActivity", "Error deserializing single phrase JSON", e)
                // Fallback to loading the default list if parsing the single phrase fails
                loadPhrasesFromAssets().shuffled().filterNot { isPhraseLearned(it, selectedLanguage, this) }
            }
        } else {
            // Load phrases from JSON asset file
            loadPhrasesFromAssets().shuffled().filterNot { isPhraseLearned(it, selectedLanguage, this) }
        }
        // Use size for brevity in logs
        Log.d("GameActivity", "Phrases loaded (filtered): ${phrases.size} phrases")

        // Initialize SpeechRecognizer and MediaPlayer as before
        speechRecognizer = createSpeechRecognizer()
        // Ensure R.raw.speak exists in res/raw
        mediaPlayer = MediaPlayer.create(this, R.raw.speak)

        setContent {
            // Pass the loaded phrases (now potentially from JSON) to the original GameScreen
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

    // Original onDestroy, requestAudioPermission, createSpeechRecognizer, startListening
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
        delay(50) // Small delay might help stability

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
                    else -> "ja-JP" // Default language
                }
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            // Bounds check before accessing phrases
            if (currentIndex >= 0 && currentIndex < phrases.size) {
                Log.d("GameActivity", "Starting SpeechRecognizer for: ${phrases[currentIndex].spoken}")
                speechRecognizer.startListening(intent)
            } else {
                Log.e("GameActivity", "Error: currentIndex $currentIndex is out of bounds for phrases list size ${phrases.size}")
                onResult("Error: Invalid phrase index.")
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting speech recognizer", e)
            onResult("Error: ${e.message}")
        }
    }


    // --- MODIFIED: loadPhrasesFromAssets reads and parses JSON ---
    private fun loadPhrasesFromAssets(): List<Phrase> {
        val gson = Gson() // Create Gson instance
        val fileName = when (selectedLanguage) {
            "Japanese" -> when (selectedDifficulty) {
                // --- CHANGED FILE EXTENSIONS TO .json ---
                "JLPT N1" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n1_grammar.json" else "phrases_jp_jlpt_n1.json"
                "JLPT N2" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n2_grammar.json" else "phrases_jp_jlpt_n2.json"
                "JLPT N3" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n3_grammar.json" else "phrases_jp_jlpt_n3.json"
                "JLPT N4" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n4_grammar.json" else "phrases_jp_jlpt_n4.json"
                "JLPT N5" -> if (selectedMaterial == "Grammar") "phrases_jp_jlpt_n5_grammar.json" else "phrases_jp_jlpt_n5.json"
                else -> "phrases_jp_jlpt_n1.json" // Default fallback for Japanese
            }
            "Korean" -> when (selectedDifficulty) {
                "TOPIK I" -> "phrases_kr_topik_1.json"
                "TOPIK II" -> "phrases_kr_topik_2.json"
                else -> "phrases_kr_topik_2.json" // Default fallback for Korean
            }
            "Vietnamese" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_vi_beginner.json"
                "Intermediate" -> "phrases_vi_intermediate.json"
                "Advanced" -> "phrases_vi_advanced.json"
                else -> "phrases_vi_beginner.json" // Default fallback for Vietnamese
            }
            "Spanish" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_es_beginner.json"
                "Intermediate" -> "phrases_es_intermediate.json"
                "Advanced" -> "phrases_es_advanced.json"
                else -> "phrases_es_beginner.json" // Default fallback for Spanish
            }
            else -> "phrases_jp_jlpt_n1.json" // Ultimate fallback
        }

        Log.d("GameActivity", "Attempting to load phrases from assets file: $fileName")

        return try {
            // Open the asset file input stream
            assets.open(fileName).use { inputStream ->
                // Create a reader for the input stream
                InputStreamReader(inputStream, "UTF-8").use { reader -> // Specify UTF-8 encoding
                    // Define the type for Gson: List<Phrase>
                    val phraseListType = object : TypeToken<List<Phrase>>() {}.type
                    // Parse the JSON reader directly into a List<Phrase>
                    // Return empty list if parsing results in null
                    gson.fromJson(reader, phraseListType) ?: emptyList<Phrase>().also {
                        Log.w("GameActivity", "Parsed JSON from $fileName resulted in null, returning empty list.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error loading or parsing phrases from assets file: $fileName", e)
            Toast.makeText(this, "Error loading phrases from $fileName", Toast.LENGTH_LONG).show()
            // --- MODIFIED: Return a default list with the updated Phrase structure on error ---
            // Provide more specific default values if desired
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


    // --- GameScreen Composable (Original UI/Flow/Animation Logic) ---
    @Composable
    fun GameScreen(
        phrases: List<Phrase>, // Accepts the updated Phrase type
        selectedLanguage: String,
        onStartListening: suspend (Int, (String) -> Unit, () -> Unit) -> Unit,
        onQuit: () -> Unit,
        onDestroyRecognizer: () -> Unit
    ) {
        // Original state variables
        var currentIndex by remember { mutableStateOf(0) }
        var spokenText by remember { mutableStateOf("") }
        var isCorrect by remember { mutableStateOf<Boolean?>(null) }
        var showResult by remember { mutableStateOf(false) }
        var showHelp by remember { mutableStateOf(false) }
        var speechEnded by remember { mutableStateOf(false) }
        var lastPartialText by remember { mutableStateOf("") }
        var isRecording by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Gracefully handle empty phrases list
        if (phrases.isEmpty() || phrases[0].spoken.startsWith("Error:")) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF212121)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        phrases.getOrNull(0)?.spoken ?: "No phrases found.",
                        color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        phrases.getOrNull(0)?.english ?: "Please check assets or logs.",
                        color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center
                    )
                    Button(onClick = onQuit, modifier = Modifier.padding(top = 20.dp)) {
                        Text("Back")
                    }
                }
            }
            return // Stop rendering the rest if no phrases or error loading
        }

        // Ensure currentIndex is valid, especially after filtering or if list is modified
        LaunchedEffect(phrases) {
            if (currentIndex >= phrases.size) {
                Log.w("GameScreen", "currentIndex $currentIndex was out of bounds for new phrases list (size ${phrases.size}), resetting to 0")
                currentIndex = 0
            }
        }
        // Need to handle if the list becomes empty *after* initial load
        if (phrases.isEmpty()) {
            // Handle as above
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF212121)), contentAlignment = Alignment.Center) {
                Text("No phrases remaining.", color = Color.White, fontSize = 20.sp)
                Button(onClick = onQuit, modifier = Modifier.padding(top = 20.dp)) { Text("Back") }
            }
            return
        }
        // Recalculate favorite/learned status when index or phrases list changes.
        // Use currentIndex and phrases list content as keys.
        val currentPhrase = phrases[currentIndex] // Get current phrase safely after bounds check
        var isFavorite by remember(currentPhrase) { mutableStateOf(isPhraseFavorite(currentPhrase, selectedLanguage, context)) }
        var isLearned by remember(currentPhrase) { mutableStateOf(isPhraseLearned(currentPhrase, selectedLanguage, context)) }
        var showLearnConfirmation by remember { mutableStateOf(false) }


        // Original background color animation
        val backgroundColor by animateColorAsState(
            targetValue = when (isCorrect) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFE57373)
                null -> Color(0xFF212121)
            },
            animationSpec = tween(durationMillis = 300)
        )

        // Original coroutine scope and media players
        val coroutineScope = rememberCoroutineScope()
        val speakMediaPlayer = remember { MediaPlayer.create(context, R.raw.speak) }
        val correctMediaPlayer = remember { MediaPlayer.create(context, R.raw.correct) }

        // Original DisposableEffect
        DisposableEffect(Unit) {
            onDispose {
                speakMediaPlayer.release()
                correctMediaPlayer.release()
                // Consider if onDestroyRecognizer should be called here or just in Activity onDestroy
                // onDestroyRecognizer()
            }
        }

        // Original toReading function
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

        // Original LaunchedEffect for processing spoken text
        LaunchedEffect(spokenText, speechEnded) { // Rely on spokenText and speechEnded status
            if (spokenText.isNotEmpty() && spokenText != "Listening..." && currentIndex < phrases.size) {
                val currentPhraseProcessing = phrases[currentIndex] // Capture phrase for this effect instance
                val expected = currentPhraseProcessing.expected
                // Perform normalization off main thread if complex
                val normalizedExpected = withContext(Dispatchers.Default) {
                    toReading(expected.replace("[\\s、。？！]".toRegex(), "")).lowercase()
                }
                val normalizedSpoken = withContext(Dispatchers.Default) {
                    toReading(spokenText.replace("[\\s、。？！]".toRegex(), "")).lowercase()
                }


                Log.d("GameScreen", "Comparing: SpokenNorm='$normalizedSpoken' vs ExpectedNorm='$normalizedExpected'")

                val isError = spokenText.contains("error", ignoreCase = true) ||
                        spokenText == "No match found. Try again." ||
                        spokenText == "No speech input. Speak louder."

                if (isError) {
                    Log.d("GameScreen", "Error detected in spoken text: $spokenText")
                    // Show error feedback immediately
                    isCorrect = false // Treat as incorrect
                    showResult = true
                    isRecording = false // Stop recording state
                    // Delay to show feedback, then reset
                    delay(1500)
                    spokenText = ""
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
                        showHelp = true // Show help on correct
                        isRecording = false
                        correctMediaPlayer.start()
                        // Don't destroy recognizer here, let user press next/skip
                    } else if (speechEnded && !matches) { // Check if speech ended *and* it's not a match
                        Log.d("GameScreen", "Incorrect result after speech ended.")
                        isCorrect = false
                        showResult = true
                        isRecording = false
                    } else if (!isPrefix && normalizedSpoken.isNotEmpty()) {
                        // It's not a prefix, could be wrong - store it but wait for speech end
                        Log.d("GameScreen", "Partial result is not a prefix: $normalizedSpoken")
                        lastPartialText = spokenText
                        // If speech has ended here, it should have been caught by the previous condition
                        if (speechEnded){
                            isCorrect = false
                            showResult = true
                            isRecording = false
                        }
                    } else {
                        // It's a prefix or still empty, keep listening
                        Log.d("GameScreen", "Partial result is a prefix or empty.")
                        lastPartialText = spokenText
                    }
                }
            }
        }


        // Original LaunchedEffect for currentIndex changes (resetting state)
        LaunchedEffect(currentIndex) {
            Log.d("GameScreen", "Current index changed to: $currentIndex")
            // Reset state for the new phrase
            spokenText = ""
            isCorrect = null
            showResult = false
            showHelp = false // Reset help visibility
            speechEnded = false
            lastPartialText = ""
            isRecording = false
            // Favorite/Learned state is updated via remember(currentPhrase)
        }

        // Original UI Box Structure
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(WindowInsets.systemBars.asPaddingValues())
        ) {
            // Original Back Button
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

            // Original Top Right Icons Row
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Original Favorite Button
                IconButton(
                    onClick = {
                        val phraseToToggle = phrases[currentIndex] // Use checked phrase
                        if (isFavorite) {
                            removeFavoritePhrase(phraseToToggle, selectedLanguage, context)
                            Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                        } else {
                            addFavoritePhrase(phraseToToggle, selectedLanguage, context)
                            Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
                        }
                        isFavorite = !isFavorite // Immediate UI feedback
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

                // Original Learned Button
                IconButton(
                    onClick = {
                        val phraseToToggle = phrases[currentIndex]
                        if (!isLearned) {
                            showLearnConfirmation = true // Show confirmation dialog
                        } else {
                            // Instantly remove if already learned
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

            // Original Learn Confirmation Dialog
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

                                // Logic to move to next *unlearned* phrase implicitly handled by filtering in onCreate
                                // Just trigger moving to the next index in the *current filtered list*
                                val nextIndex = (currentIndex + 1).let {
                                    if (it >= phrases.size) 0 else it // Wrap around
                                }
                                // Check if the list became empty after learning the last one
                                val newListSize = phrases.size - 1 // Hypothetical size after removal
                                if (newListSize <= 0) {
                                    // Handle case where last phrase was learned
                                    Toast.makeText(context, "All phrases learned!", Toast.LENGTH_SHORT).show()
                                    onQuit() // Or navigate somewhere else
                                } else {
                                    // Trigger LaunchedEffect(currentIndex) by changing the index state
                                    currentIndex = nextIndex
                                }
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
                    containerColor = Color(0xFF015D73)
                )
            }


            // Original Central Content Column Structure
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Use original padding
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Original spacing
                Spacer(modifier = Modifier.height(16.dp))

                // Original Correct/Incorrect Animation Icon
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

                // Original spacing
                Spacer(modifier = Modifier.height(40.dp))

                // Original Phrase Text (Spoken)
                Text(
                    text = currentPhrase.spoken, // Use checked phrase
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFE0F7FA)
                )

                // Original spacing
                Spacer(modifier = Modifier.height(16.dp))

                // Original Hiragana Display (Conditional)
                AnimatedVisibility(
                    visible = showHelp && selectedLanguage == "Japanese",
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Hiragana",
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

                // Original spacing
                Spacer(modifier = Modifier.height(24.dp))

                // Original English Display (Conditional)
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
                // This Spacer was missing in the original file, adding it back if needed
                // If the spoken text should be higher, remove or adjust this.
                // Spacer(modifier = Modifier.weight(1f))
            }

            // Original Spoken Text Display (at original position)
            Text(
                text = spokenText,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFFE0F7FA),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Original padding
                    .padding(bottom = 150.dp)
            )

            // Original Mic Button
            IconButton(
                onClick = {
                    // Original logic: Reset state and start listening
                    spokenText = ""
                    isCorrect = null
                    showResult = false
                    // showHelp = false // Decide if help resets on mic press
                    speechEnded = false
                    lastPartialText = ""
                    isRecording = true
                    speakMediaPlayer.start()
                    coroutineScope.launch {
                        onStartListening(currentIndex, { result ->
                            spokenText = result
                        }, {
                            // Original end of speech logic
                            isRecording = false
                            speechEnded = true // Set flag that speech ended
                            Log.d("GameScreen", "Speech ended callback triggered")
                        })
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Original position and style
                    .padding(16.dp)
                    .size(70.dp)
                    .background(
                        color = if (isRecording) Color(0xFFFF9999) else Color(0xFFFFB300),
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

            // Original Help Button
            OutlinedIconButton( // Keep original component type
                onClick = { showHelp = !showHelp },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp), // Original padding
                border = BorderStroke(1.dp, Color(0xFFFF8F00)) // Original border
            ) {
                Icon(
                    // Original icon
                    imageVector = Icons.Default.QuestionMark, // Or HelpOutline if preferred
                    contentDescription = "Show Help",
                    tint = Color(0xFFE0F7FA),
                    modifier = Modifier.size(18.dp) // Original size
                )
            }

            // Original Skip/Next Button
            OutlinedButton(
                onClick = {
                    // Original logic: Reset state and move index
                    spokenText = ""
                    isCorrect = null
                    showResult = false
                    // showHelp = false // Reset help on skip/next?
                    speechEnded = false
                    lastPartialText = ""
                    isRecording = false
                    // Ensure recognizer is stopped if active
                    // speechRecognizer.stopListening() // Or cancel()
                    val nextIndex = (currentIndex + 1).let {
                        if (it >= phrases.size) 0 else it // Wrap around
                    }
                    currentIndex = nextIndex // Trigger LaunchedEffect(currentIndex)
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
        }
    }


    // --- SharedPreferences Functions (Original implementations - work with updated Phrase) ---
    // Gson handles the extra 'grammar' field automatically during serialization/deserialization.

    private fun addFavoritePhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
        val gson = Gson()
        val favoritesJson = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val favorites: MutableList<Phrase> = gson.fromJson(favoritesJson, type) ?: mutableListOf()
        if (!favorites.contains(phrase)) {
            favorites.add(phrase)
            prefs.edit().putString("favorites_$language", gson.toJson(favorites)).apply()
        }
    }

    private fun removeFavoritePhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
        val gson = Gson()
        val favoritesJson = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val favorites: MutableList<Phrase> = gson.fromJson(favoritesJson, type) ?: mutableListOf()
        favorites.remove(phrase) // remove relies on equals() which includes grammar now
        prefs.edit().putString("favorites_$language", gson.toJson(favorites)).apply()
    }

    private fun isPhraseFavorite(phrase: Phrase, language: String, context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
        val gson = Gson()
        val favoritesJson = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<List<Phrase>>() {}.type
        val favorites: List<Phrase> = gson.fromJson(favoritesJson, type) ?: emptyList()
        return favorites.contains(phrase) // contains relies on equals()
    }

    private fun addLearnedPhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("LearnedPrefs", android.content.Context.MODE_PRIVATE)
        val gson = Gson()
        val learnedJson = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val learned: MutableList<Phrase> = gson.fromJson(learnedJson, type) ?: mutableListOf()
        if (!learned.contains(phrase)) {
            learned.add(phrase)
            prefs.edit().putString("learned_$language", gson.toJson(learned)).apply()
        }
    }

    private fun removeLearnedPhrase(phrase: Phrase, language: String, context: android.content.Context) {
        val prefs = context.getSharedPreferences("LearnedPrefs", android.content.Context.MODE_PRIVATE)
        val gson = Gson()
        val learnedJson = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<MutableList<Phrase>>() {}.type
        val learned: MutableList<Phrase> = gson.fromJson(learnedJson, type) ?: mutableListOf()
        learned.remove(phrase)
        prefs.edit().putString("learned_$language", gson.toJson(learned)).apply()
    }

    private fun isPhraseLearned(phrase: Phrase, language: String, context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("LearnedPrefs", android.content.Context.MODE_PRIVATE)
        val gson = Gson()
        val learnedJson = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<List<Phrase>>() {}.type
        val learned: List<Phrase> = gson.fromJson(learnedJson, type) ?: emptyList()
        return learned.contains(phrase)
    }
}