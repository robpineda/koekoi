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
    private var currentOnSpeechEnded: (() -> Unit)? = null // New callback for onEndOfSpeech
    private lateinit var selectedLanguage: String
    private lateinit var selectedDifficulty: String

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
            currentOnSpeechEnded?.invoke() // Notify GameScreen without altering spokenText
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
                onStartListening = { index, onResult, onSpeechEnded ->
                    startListening(index, onResult, onSpeechEnded)
                },
                onQuit = { finish() },
                onDestroyRecognizer = { speechRecognizer.destroy() }
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

    private suspend fun startListening(
        currentIndex: Int,
        onResult: (String) -> Unit,
        onSpeechEnded: () -> Unit // New parameter
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
        currentOnSpeechEnded = onSpeechEnded // Set the callback

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
            Log.d("GameActivity", "Starting SpeechRecognizer for: ${phrases[currentIndex].spoken}")
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting speech recognizer", e)
            onResult("Error: ${e.message}")
        }
    }

    private fun loadPhrasesFromAssets(): List<Phrase> {
        val phrases = mutableListOf<Phrase>()
        val fileName = when (selectedLanguage) {
            "Japanese" -> when (selectedDifficulty) {
                "JLPT N1" -> "phrases_jp_jlpt_n1.txt"
                "JLPT N2" -> "phrases_jp_jlpt_n2.txt"
                "JLPT N3" -> "phrases_jp_jlpt_n3.txt"
                "JLPT N4" -> "phrases_jp_jlpt_n4.txt"
                "JLPT N5" -> "phrases_jp_jlpt_n5.txt"
                else -> "phrases_jp_jlpt_n1.txt" // Fallback
            }
            "Korean" -> when (selectedDifficulty) {
                "TOPIK I" -> "phrases_kr_topik_1.txt"
                "TOPIK II" -> "phrases_kr_topik_2.txt"
                else -> "phrases_kr_topik_2.txt" // Fallback
            }
            "Vietnamese" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_vi_beginner.txt"
                "Intermediate" -> "phrases_vi_intermediate.txt"
                "Advanced" -> "phrases_vi_advanced.txt"
                else -> "phrases_vi_beginner.txt" // Fallback
            }
            "Spanish" -> when (selectedDifficulty) {
                "Beginner" -> "phrases_es_beginner.txt"
                "Intermediate" -> "phrases_es_intermediate.txt"
                "Advanced" -> "phrases_es_advanced.txt"
                else -> "phrases_es_beginner.txt" // Fallback
            }
            else -> "phrases_jp_jlpt_n1.txt" // Overall fallback
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
                    when (selectedLanguage) {
                        "Japanese" -> "私は晩ご飯に寿司を食べます"
                        "Korean" -> "저는 매일 아침 책을 읽습니다"
                        "Vietnamese" -> "Tôi ăn sáng mỗi ngày"
                        "Spanish" -> "Yo como desayuno todos los días"
                        else -> "私は晩ご飯に寿司を食べます"
                    },
                    when (selectedLanguage) {
                        "Japanese" -> "私は晩ご飯に寿司を食べます"
                        "Korean" -> "저는 매일 아침 책을 읽습니다"
                        "Vietnamese" -> "Tôi ăn sáng mỗi ngày"
                        "Spanish" -> "Yo como desayuno todos los días"
                        else -> "私は晩ご飯に寿司を食べます"
                    },
                    when (selectedLanguage) {
                        "Japanese" -> "わたしはばんごはんにすしをたべます"
                        "Korean" -> "저는 매일 아침 책을 읽습니다"
                        "Vietnamese" -> "Tôi ăn sáng mỗi ngày"
                        "Spanish" -> "Yo como desayuno todos los días"
                        else -> "わたしはばんごはんにすしをたべます"
                    },
                    when (selectedLanguage) {
                        "Japanese" -> "I eat sushi for dinner"
                        "Korean" -> "I read a book every morning"
                        "Vietnamese" -> "I eat breakfast every day"
                        "Spanish" -> "I eat breakfast every day"
                        else -> "I eat sushi for dinner"
                    }
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
    onStartListening: suspend (Int, (String) -> Unit, () -> Unit) -> Unit, // Updated signature
    onQuit: () -> Unit,
    onDestroyRecognizer: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var spokenText by remember { mutableStateOf("") }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var speechEnded by remember { mutableStateOf(false) }
    var lastPartialText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when (isCorrect) {
            true -> Color(0xFF4CAF50)
            false -> Color(0xFFE57373)
            null -> Color(0xFF121212)
        },
        animationSpec = tween(durationMillis = 300)
    )

    val coroutineScope = rememberCoroutineScope()

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
            val normalizedExpected = toReading(expected.replace("[\\s、。？！]".toRegex(), "")).lowercase()
            val normalizedSpoken = toReading(spokenText.replace("[\\s、。？！]".toRegex(), "")).lowercase()

            Log.d("normalizedExpected", normalizedExpected)
            Log.d("normalizedSpoken", normalizedSpoken)

            val isError = spokenText.contains("error", ignoreCase = true) ||
                    spokenText == "No match found. Try again." ||
                    spokenText == "No speech input. Speak louder."

            if (isError) {
                delay(2000)
                spokenText = ""
                speechEnded = false
                isCorrect = null
                showResult = false
                lastPartialText = ""
                isRecording = false
                Log.d("entered 0", "entered 0")
            } else {
                val isPrefix = normalizedExpected.startsWith(normalizedSpoken)
                val matches = normalizedExpected == normalizedSpoken

                if (matches) {
                    isCorrect = true
                    showResult = true
                    showHelp = true
                    onDestroyRecognizer()
                    isRecording = false
                    Log.d("entered 1", "entered 1")
                } else if (!isPrefix && normalizedSpoken.isNotEmpty()) {
                    isCorrect = false
                    showResult = true
                    speechEnded = true
                    onDestroyRecognizer()
                    isRecording = false
                    Log.d("entered 2", "entered 2")
                } else {
                    lastPartialText = spokenText
                    Log.d("entered 3", "entered 3")
                }
            }
            Log.d("GameScreen", "Spoken: $spokenText, Expected: $expected, IsCorrect: $isCorrect, SpeechEnded: $speechEnded, LastPartial: $lastPartialText")
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
                visible = showHelp && selectedLanguage == "Japanese",
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hiragana", fontSize = 14.sp, color = Color.White)
                    Text(
                        text = phrases[currentIndex].reading,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
            }
            if (showHelp && selectedLanguage == "Japanese") Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = showHelp, enter = fadeIn(), exit = fadeOut()) {
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
                showHelp = false
                speechEnded = false
                lastPartialText = ""
                isRecording = true // Turn mic light red
                mediaPlayer.start()
                coroutineScope.launch {
                    onStartListening(currentIndex, { result ->
                        spokenText = result
                    }, {
                        // Callback for onEndOfSpeech
                        isRecording = false // Turn mic back to light blue
                        Log.d("GameScreen", "Speech ended callback triggered")
                    })
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .size(70.dp)
                .background(
                    color = if (isRecording) Color(0xFFFF9999) else Color(0xFF99CCFF), // Light red when recording, light blue otherwise
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Speak",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(
            onClick = { showHelp = !showHelp },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(40.dp)
                .background(Color(0xFFBBDEFB), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.QuestionMark,
                contentDescription = "Show Help",
                tint = Color.Black,
                modifier = Modifier.size(16.dp)
            )
        }

        Button(
            onClick = {
                spokenText = ""
                isCorrect = null
                showResult = false
                showHelp = false
                speechEnded = false
                lastPartialText = ""
                isRecording = false
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