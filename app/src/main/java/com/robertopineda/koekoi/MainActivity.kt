package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onStartGame = {
                    startActivity(Intent(this, GameActivity::class.java))
                }
            )
        }
    }
}

@Composable
fun MainScreen(onStartGame: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "KoeKoi",
            fontSize = 32.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = { /* TODO: Language selection */ }) {
            Text("Select Language")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { /* TODO: Difficulty selection */ }) {
            Text("Select Difficulty")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStartGame) {
            Text("Start Game")
        }
    }
}