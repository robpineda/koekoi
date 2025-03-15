package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(
                onBack = { finish() },
                onNavigateToLearnedPhrases = {
                    val intent = Intent(this, LearnedPhrasesActivity::class.java)
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLearnedPhrases: () -> Unit
) {
    val settingsOptions = listOf(
        "Review Learned Phrases"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF007893)) // Teal background
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 32.sp,
                color = Color(0xFFE0F7FA), // Light cyan
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(settingsOptions) { option ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                when (option) {
                                    "Review Learned Phrases" -> onNavigateToLearnedPhrases()
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF015D73) // Darker teal
                        )
                    ) {
                        Text(
                            text = option,
                            fontSize = 18.sp,
                            color = Color(0xFFFFB300), // Amber accent
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF8F00), // Deep amber
                contentColor = Color(0xFFE0F7FA) // Light cyan
            )
        ) {
            Text("Back")
        }
    }
}