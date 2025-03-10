package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
                onStartGame = { language, difficulty, material ->
                    val intent = Intent(this, GameActivity::class.java).apply {
                        putExtra("LANGUAGE", language)
                        putExtra("DIFFICULTY", difficulty)
                        putExtra("MATERIAL", material) // Pass material type
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun MainScreen(onStartGame: (String, String, String) -> Unit) {
    var selectedLanguage by remember { mutableStateOf("Japanese") } // Default to Japanese
    var selectedDifficulty by remember { mutableStateOf("") } // Store selected difficulty
    var selectedMaterial by remember { mutableStateOf("Vocabulary") } // Default to Vocabulary
    var languageExpanded by remember { mutableStateOf(false) } // Toggle language dropdown
    var difficultyExpanded by remember { mutableStateOf(false) } // Toggle difficulty dropdown
    var materialExpanded by remember { mutableStateOf(false) } // Toggle material dropdown

    // Define language options
    val languageOptions = listOf("Japanese", "Korean", "Vietnamese", "Spanish")

    // Define difficulty options based on language
    val difficultyOptions = when (selectedLanguage) {
        "Japanese" -> listOf("JLPT N1", "JLPT N2", "JLPT N3", "JLPT N4", "JLPT N5")
        "Korean" -> listOf("TOPIK I", "TOPIK II")
        "Vietnamese" -> listOf("Beginner", "Intermediate", "Advanced")
        "Spanish" -> listOf("Beginner", "Intermediate", "Advanced")
        else -> emptyList()
    }

    // Define material options (only for Japanese)
    val materialOptions = listOf("Vocabulary", "Grammar")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121)) // Dark gray background
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "KoeKoi",
            fontSize = 32.sp,
            color = Color(0xFFBBDEFB), // Pastel blue for title
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Language selection with DropdownMenu
        Text(
            text = "Select Language",
            fontSize = 18.sp,
            color = Color(0xFFBBDEFB) // Pastel blue
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { languageExpanded = true },
                modifier = Modifier.fillMaxWidth(0.6f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFCE93D8) // Pastel purple for outline/text
                ),
                border = BorderStroke(1.dp, Color(0xFFCE93D8)) // Pastel purple border
            ) {
                Text(
                    text = selectedLanguage,
                    color = Color(0xFFCE93D8) // Pastel purple
                )
            }
            DropdownMenu(
                expanded = languageExpanded,
                onDismissRequest = { languageExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .background(Color(0xFF424242)) // Dark gray for dropdown
            ) {
                languageOptions.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language, color = Color(0xFFBBDEFB)) }, // Pastel blue text
                        onClick = {
                            selectedLanguage = language
                            selectedDifficulty = "" // Reset difficulty when language changes
                            selectedMaterial = "Vocabulary" // Reset to Vocabulary
                            languageExpanded = false
                        },
                        modifier = Modifier.background(Color(0xFF424242)) // Dark gray item background
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Difficulty selection with DropdownMenu
        Text(
            text = "Select Difficulty",
            fontSize = 18.sp,
            color = Color(0xFFBBDEFB) // Pastel blue
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { difficultyExpanded = true },
                modifier = Modifier.fillMaxWidth(0.6f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFCE93D8) // Pastel purple for outline/text
                ),
                border = BorderStroke(1.dp, Color(0xFFCE93D8)) // Pastel purple border
            ) {
                Text(
                    text = if (selectedDifficulty.isEmpty()) "Choose Difficulty" else selectedDifficulty,
                    color = Color(0xFFCE93D8) // Pastel purple
                )
            }
            DropdownMenu(
                expanded = difficultyExpanded,
                onDismissRequest = { difficultyExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .background(Color(0xFF424242)) // Dark gray for dropdown
            ) {
                difficultyOptions.forEach { difficulty ->
                    DropdownMenuItem(
                        text = { Text(difficulty, color = Color(0xFFBBDEFB)) }, // Pastel blue text
                        onClick = {
                            selectedDifficulty = difficulty
                            difficultyExpanded = false
                        },
                        modifier = Modifier.background(Color(0xFF424242)) // Dark gray item background
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Material type selection with DropdownMenu (enabled only for Japanese)
        Text(
            text = "Select Material",
            fontSize = 18.sp,
            color = Color(0xFFBBDEFB) // Pastel blue
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { if (selectedLanguage == "Japanese") materialExpanded = true },
                modifier = Modifier.fillMaxWidth(0.6f),
                enabled = selectedLanguage == "Japanese", // Enabled only for Japanese
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (selectedLanguage == "Japanese") Color(0xFFCE93D8) else Color(0xFFBBBBBB), // Pastel purple when enabled, gray when disabled
                    disabledContentColor = Color(0xFFBBBBBB) // Light gray when disabled
                ),
                border = BorderStroke(1.dp, if (selectedLanguage == "Japanese") Color(0xFFCE93D8) else Color(0xFF616161)) // Pastel purple or gray border
            ) {
                Text(
                    text = selectedMaterial,
                    color = if (selectedLanguage == "Japanese") Color(0xFFCE93D8) else Color(0xFFBBBBBB) // Pastel purple or gray
                )
            }
            DropdownMenu(
                expanded = materialExpanded,
                onDismissRequest = { materialExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .background(Color(0xFF424242)) // Dark gray for dropdown
            ) {
                materialOptions.forEach { material ->
                    DropdownMenuItem(
                        text = { Text(material, color = Color(0xFFBBDEFB)) }, // Pastel blue text
                        onClick = {
                            selectedMaterial = material
                            materialExpanded = false
                        },
                        modifier = Modifier.background(Color(0xFF424242)) // Dark gray item background
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (selectedDifficulty.isNotEmpty()) {
                    onStartGame(selectedLanguage, selectedDifficulty, selectedMaterial)
                }
            },
            enabled = selectedDifficulty.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFAB47BC), // Darker pastel purple for enabled
                contentColor = Color(0xFFBBDEFB), // Pastel blue text
                disabledContainerColor = Color(0xFF616161), // Muted gray for disabled
                disabledContentColor = Color(0xFFBBBBBB) // Light gray text when disabled
            )
        ) {
            Text("Start Game")
        }
    }
}