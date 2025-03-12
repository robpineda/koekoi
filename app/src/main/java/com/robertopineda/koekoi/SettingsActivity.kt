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
            .background(Color(0xFF212121))
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
                color = Color(0xFFBBDEFB),
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
                            containerColor = Color(0xFF424242)
                        )
                    ) {
                        Text(
                            text = option,
                            fontSize = 18.sp,
                            color = Color(0xFFCE93D8),
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
                containerColor = Color(0xFFCE93D8),
                contentColor = Color(0xFFFCFCFC)
            )
        ) {
            Text("Back")
        }
    }
}