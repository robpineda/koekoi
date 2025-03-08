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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

class GameActivity : ComponentActivity() {
    data class Phrase(
        val spoken: String,
        val expected: String,
        val reading: String,
        val english: String
    )

    private lateinit var phrases: List<Phrase>
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var mediaPlayer: MediaPlayer
    private var currentOnResult: ((String) -> Unit)? = null
    private lateinit var selectedLanguage: String
    private lateinit var selectedDifficulty: String

    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("GameActivity", "Speech recognizer ready for speech")
            currentOnResult?.invoke("Listening...")
        }

        override fun onResults(results: Bundle?) {
            val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
            Log.d("GameActivity", "Speech result received: $spokenText")
            currentOnResult?.invoke(spokenText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialText = partialResults?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            Log.d("GameActivity", "Partial result: $partialText")
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

        val pm = packageManager
        val activities = pm.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
        if (activities.size == 0) {
            Log.e("GameActivity", "No activities found to handle speech recognition intent")
            Toast.makeText(this, "Speech recognition not supported on this device", Toast.LENGTH_LONG).show()
        } else {
            Log.d("GameActivity", "Found ${activities.size} activities to handle speech recognition")
        }

        phrases = loadPhrasesFromAssets().shuffled()
        Log.d("GameActivity", "Phrases loaded and shuffled: ${phrases.map { it.spoken }}")

        speechRecognizer = createSpeechRecognizer()
        mediaPlayer = MediaPlayer.create(this, R.raw.speak)

        setContent {
            GameScreen(
                phrases = phrases,
                selectedLanguage = selectedLanguage,
                onStartListening = { index, onResult -> startListening(index, onResult) },
                onQuit = { finish() }
            )
        }

        requestAudioPermission()
    }

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

    private suspend fun startListening(currentIndex: Int, onResult: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult("Permission denied")
            return
        }

        Log.d("GameActivity", "Destroying and recreating SpeechRecognizer")
        speechRecognizer.destroy()
        speechRecognizer = createSpeechRecognizer()
        delay(50)

        currentOnResult = onResult

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (selectedLanguage == "Japanese") "ja-JP" else "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            Log.d("GameActivity", "Starting SpeechRecognizer for: ${phrases[currentIndex].spoken}")
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting speech recognizer", e)
            onResult("Error: ${e.message}")
        }
    }

    private fun loadPhrasesFromAssets(): List<Phrase> {
        val phrases = mutableListOf<Phrase>()
        val fileName = when {
            selectedLanguage == "Japanese" -> "phrases_jp_jlpt_n1.txt" // Default for Japanese, can expand with difficulty later
            selectedLanguage == "Korean" && selectedDifficulty == "TOPIK I" -> "phrases_kr_topik_1.txt"
            selectedLanguage == "Korean" && selectedDifficulty == "TOPIK II" -> "phrases_kr_topik_2.txt"
            else -> "phrases_kr_topik_2.txt" // Fallback for Korean if difficulty not set
        }
        try {
            assets.open(fileName).bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val parts = line.split("|")
                    if (parts.size == 4) {
                        phrases.add(Phrase(parts[0], parts[1], parts[2], parts[3]))
                    } else {
                        Log.w("GameActivity", "Invalid line in $fileName: $line")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error loading phrases from assets", e)
            Toast.makeText(this, "Error loading phrases", Toast.LENGTH_SHORT).show()
            return listOf(
                Phrase(
                    if (selectedLanguage == "Japanese") "私は晩ご飯に寿司を食べます" else "저는 매일 아침 책을 읽습니다",
                    if (selectedLanguage == "Japanese") "私は晩ご飯に寿司を食べます" else "저는 매일 아침 책을 읽습니다",
                    if (selectedLanguage == "Japanese") "わたしはばんごはんにすしをたべます" else "저는 매일 아침 책을 읽습니다",
                    if (selectedLanguage == "Japanese") "I eat sushi for dinner" else "I read a book every morning"
                )
            )
        }
        return phrases
    }
}

@Composable
fun GameScreen(
    phrases: List<GameActivity.Phrase>,
    selectedLanguage: String,
    onStartListening: suspend (Int, (String) -> Unit) -> Unit,
    onQuit: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var spokenText by remember { mutableStateOf("") }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when (isCorrect) {
            true -> Color(0xFF4CAF50)
            false -> Color(0xFFE57373)
            null -> Color(0xFF121212)
        },
        animationSpec = tween(durationMillis = 300)
    )

    val coroutineScope = rememberCoroutineScope()

    val isListening = spokenText == "Listening..."
    val pulseScale by animateFloatAsState(
        targetValue = if (isListening) 1.2f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    suspend fun toReading(text: String): String {
        return if (selectedLanguage == "Japanese") {
            withContext(Dispatchers.Default) {
                val tokenizer = Tokenizer.Builder().build()
                val tokens: List<Token> = tokenizer.tokenize(text)
                tokens.joinToString("") { it.reading ?: it.surface }
            }
        } else {
            text
        }
    }

    LaunchedEffect(spokenText) {
        if (spokenText.isNotEmpty() && spokenText != "Listening...") {
            val expected = phrases[currentIndex].expected
            val normalizedExpected = toReading(expected.replace(" ", ""))
            val normalizedSpoken = toReading(spokenText.replace(" ", ""))

            Log.d("normalizedExpected", normalizedExpected)
            Log.d("normalizedSpoken", normalizedSpoken)

            val isError = spokenText.contains("error", ignoreCase = true) ||
                    spokenText == "No match found. Try again." ||
                    spokenText == "No speech input. Speak louder."

            if (isError) {
                delay(2000)
                spokenText = ""
            } else {
                isCorrect = normalizedExpected == normalizedSpoken
                Log.d("GameScreen", "Spoken: $spokenText, Expected: $expected, IsCorrect: $isCorrect")
                showResult = true
                if (isCorrect == true) {
                    showHelp = true // Show reading and English when correct
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Button(
            onClick = onQuit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBBDEFB))
        ) {
            Text("Quit", color = Color.Black)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = phrases[currentIndex].spoken,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = showHelp,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (selectedLanguage == "Japanese") "Hiragana" else "Hangul",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = phrases[currentIndex].reading,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
            }
            if (showHelp) Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = showHelp,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = phrases[currentIndex].english,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
            if (showHelp) Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = showResult,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 500)
                ),
                exit = fadeOut()
            ) {
                isCorrect?.let {
                    Text(
                        text = if (it) "Correct!" else "Incorrect!",
                        color = Color.White,
                        fontSize = 30.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Text(
            text = spokenText,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp)
        )

        val context = LocalContext.current
        val mediaPlayer = remember { MediaPlayer.create(context, R.raw.speak) }

        IconButton(
            onClick = {
                spokenText = ""
                isCorrect = null
                showResult = false
                mediaPlayer.start()
                coroutineScope.launch {
                    onStartListening(currentIndex) { result ->
                        spokenText = result
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .size(70.dp)
                .background(Color.Gray, shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Speak",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .scale(pulseScale)
            )
        }

        IconButton(
            onClick = { showHelp = !showHelp },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(40.dp)
                .background(Color.Gray, shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.QuestionMark,
                contentDescription = "Show Help",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Button(
            onClick = {
                spokenText = ""
                isCorrect = null
                showResult = false
                showHelp = false
                val newIndex = (currentIndex + 1) % phrases.size
                currentIndex = newIndex
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBBDEFB))
        ) {
            Text(
                text = if (isCorrect == true && showResult) "Next" else "Skip",
                color = Color.Black
            )
        }
    }
}