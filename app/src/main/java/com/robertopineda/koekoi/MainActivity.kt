package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onStartGame = { language ->
                    val intent = Intent(this, GameActivity::class.java).apply {
                        putExtra("LANGUAGE", language)
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun MainScreen(onStartGame: (String) -> Unit) {
    var selectedLanguage by remember { mutableStateOf("Japanese") } // Default to Japanese

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

        // Language selection dropdown
        Text("Select Language", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { selectedLanguage = "Japanese" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedLanguage == "Japanese") Color(0xFFBBDEFB) else Color.Gray
                )
            ) {
                Text("Japanese", color = Color.Black)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { selectedLanguage = "Korean" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedLanguage == "Korean") Color(0xFFBBDEFB) else Color.Gray
                )
            ) {
                Text("Korean", color = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Difficulty selection (placeholder for future use)
        Button(onClick = { /* TODO: Difficulty selection */ }) {
            Text("Select Difficulty")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onStartGame(selectedLanguage) }) {
            Text("Start Game")
        }
    }
}