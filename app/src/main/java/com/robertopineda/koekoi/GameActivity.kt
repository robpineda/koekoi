package com.robertopineda.koekoi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
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
        "어제는 비가 와서 집에 있었어요" to "어제는 비가 와서 집에 있었n어요",
    )

    private var spokenText by mutableStateOf("")

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val resultText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
        spokenText = resultText
        Log.d("GameActivity", "Spoken text received: $spokenText") // Debug log
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameScreen(
                sentences = sentences,
                spokenText = spokenText,
                onSpeak = { index -> startListening(index) },
                onSkip = { startListening(it) },
                onQuit = { finish() }
            )
        }
        requestAudioPermission()
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun startListening(currentIndex: Int) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: ${sentences[currentIndex].first}")
        }
        Log.d("GameActivity", "Starting speech recognition for index: $currentIndex, prompt: ${sentences[currentIndex].first}")
        speechLauncher.launch(intent)
    }
}

@Composable
fun GameScreen(
    sentences: List<Pair<String, String>>,
    spokenText: String,
    onSpeak: (Int) -> Unit,
    onSkip: (Int) -> Unit,
    onQuit: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var showResult by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when (isCorrect) {
            true -> Color(0xFFB2DFDB) // Soft pastel green
            false -> Color(0xFFFFCCCB) // Soft pastel red
            null -> Color(0xFFE6F0FA) // Very soft blue
        },
        animationSpec = tween(durationMillis = 300)
    )

    LaunchedEffect(spokenText) {
        if (spokenText.isNotEmpty()) {
            val expected = sentences[currentIndex].second
            isCorrect = spokenText.equals(expected, ignoreCase = true)
            Log.d("GameScreen", "Spoken: $spokenText, Expected: $expected, IsCorrect: $isCorrect")
            showResult = true
            if (isCorrect == true) {
                delay(1500)
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

        IconButton(
            onClick = { onSpeak(currentIndex) },
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
                onSkip(newIndex)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("Skip")
        }
    }
}