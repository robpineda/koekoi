package com.robertopineda.koekoi

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class GameActivity : ComponentActivity() {
    private val sentences = listOf(
        "안녕하세요" to "안녕하세요",
        "감사합니다" to "감사합니다",
        "네" to "네",
        "아니요" to "아니요",
        "사랑해요" to "사랑해요",
        "좋아요" to "좋아요",
        "저는 오늘 학교에 버스를 타고 갔어요" to "저는 오늘 학교에 버스를 타고 갔어요",
        "저는 친구와 함께 영화를 보러 갈 거예요" to "저는 친구와 함께 영화를 보러 갈 거예요",
        "오늘 점심으로 김치찌개를 먹었어요" to "오늘 점심으로 김치찌개를 먹었어요",
        "저는 주말에 공원에서 책을 읽을 거예요" to "저는 주말에 공원에서 책을 읽을 거예요",
        "어제는 비가 와서 집에 있었어요" to "어제는 비가 와서 집에 있었어요"
    )

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var mediaPlayer: MediaPlayer

    // RecognizerIntent (commented out)
    /*
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
        Log.d("GameActivity", "RecognizerIntent result: $spokenText")
        onSpeechResult(spokenText)
    }
    private var onSpeechResult: (String) -> Unit = {}
    */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use Google's speech service explicitly
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        //speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@ComponentActivity, ComponentName("com.google.android.googlequicksearchbox", "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"))
        mediaPlayer = MediaPlayer.create(this, R.raw.speak) // Ensure speak.mp3 is in res/raw
        setContent {
            GameScreen(
                sentences = sentences,
                onStartListening = { index, onResult -> startListening(index, onResult) },
                onQuit = { finish() }
            )
        }
        requestAudioPermission()
        // Check if SpeechRecognizer is available
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("GameActivity", "Speech recognition not available on this device")
        }
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

    private fun startListening(currentIndex: Int, onResult: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                //putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("GameActivity", "Ready for speech at index: $currentIndex")
                    onResult("Listening...")
                }

                override fun onResults(results: Bundle?) {
                    val spokenText = results?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
                    Log.d("GameActivity", "Final result: $spokenText")
                    onResult(spokenText)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partialText = partialResults?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
                    Log.d("GameActivity", "Partial result: $partialText")
                    if (partialText.isNotEmpty()) onResult(partialText)
                }

                override fun onError(error: Int) {
                    Log.e("GameActivity", "Speech error: $error")
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found. Try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timed out. Speak louder."
                        else -> "Error $error"
                    }
                    onResult(errorMessage)
                }

                override fun onBeginningOfSpeech() {
                    Log.d("GameActivity", "Speech began")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    Log.d("GameActivity", "RMS: $rmsdB")
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    Log.d("GameActivity", "Buffer received: ${buffer?.size ?: 0} bytes")
                }

                override fun onEndOfSpeech() {
                    Log.d("GameActivity", "Speech ended")
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            Log.d("GameActivity", "Starting SpeechRecognizer for: ${sentences[currentIndex].first}")
            speechRecognizer.startListening(intent)
        } else {
            onResult("Permission denied")
        }
    }

    // RecognizerIntent version (commented out)
    /*
    private fun startListeningRecognizerIntent(currentIndex: Int, onResult: (String) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: ${sentences[currentIndex].first}")
        }
        onSpeechResult = onResult
        Log.d("GameActivity", "Starting RecognizerIntent for: ${sentences[currentIndex].first}")
        speechLauncher.launch(intent)
    }
    */
}

@Composable
fun GameScreen(
    sentences: List<Pair<String, String>>,
    onStartListening: (Int, (String) -> Unit) -> Unit,
    onQuit: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var spokenText by remember { mutableStateOf("") }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var showResult by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when (isCorrect) {
            true -> Color(0xFFB2DFDB)
            false -> Color(0xFFFFCCCB)
            null -> Color(0xFFE6F0FA)
        },
        animationSpec = tween(durationMillis = 300)
    )

    LaunchedEffect(spokenText) {
        if (spokenText.isNotEmpty() && spokenText != "Listening...") {
            val expected = sentences[currentIndex].second
            isCorrect = spokenText.equals(expected, ignoreCase = true)
            Log.d("GameScreen", "Spoken: $spokenText, Expected: $expected, IsCorrect: $isCorrect")
            showResult = true
            if (isCorrect == true) {
                delay(1500)
                spokenText = ""
                isCorrect = null
                showResult = false
                currentIndex = (currentIndex + 1) % sentences.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Button(
            onClick = onQuit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text("Quit")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Say: ${sentences[currentIndex].first}",
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "You said: $spokenText",
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                        modifier = Modifier.background(if (it) Color.Green else Color(0xFFEF5350)),
                        color = Color.White,
                        fontSize = 30.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        val context = LocalContext.current
        val mediaPlayer = remember { MediaPlayer.create(context, R.raw.speak) }
        IconButton(
            onClick = {
                mediaPlayer.start()
                onStartListening(currentIndex) { result ->
                    spokenText = result
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(56.dp)
                .background(Color.Gray, shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Speak",
                tint = Color.White
            )
        }

        Button(
            onClick = {
                val newIndex = (currentIndex + 1) % sentences.size
                currentIndex = newIndex
                mediaPlayer.start()
                onStartListening(newIndex) { result ->
                    spokenText = result
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("Skip")
        }
    }
}