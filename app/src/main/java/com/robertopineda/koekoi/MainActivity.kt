package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.robertopineda.koekoi.llm.LlmApiService // Import the service
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.launch // Import launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Pass the lambdas for the actions MainActivity needs to handle
            MainScreen(
                onStartGameFromAssets = { language, difficulty, material ->
                    val intent = Intent(this, GameActivity::class.java).apply {
                        putExtra("LANGUAGE", language)
                        putExtra("DIFFICULTY", difficulty)
                        putExtra("MATERIAL", material)
                    }
                    startActivity(intent)
                },
                onShowFavorites = {
                    val intent = Intent(this, FavoritesActivity::class.java)
                    startActivity(intent)
                },
                onShowSettings = {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun MainScreen(
    onStartGameFromAssets: (String, String, String) -> Unit,
    onShowFavorites: () -> Unit,
    onShowSettings: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf("Japanese") }
    var selectedDifficulty by remember { mutableStateOf("") }
    var selectedMaterial by remember { mutableStateOf("Vocabulary") }
    var languageExpanded by remember { mutableStateOf(false) }
    var difficultyExpanded by remember { mutableStateOf(false) }
    var materialExpanded by remember { mutableStateOf(false) }

    // State for LLM input
    var llmDescription by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) } // Loading state

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val llmApiService = remember { LlmApiService() } // Instantiate service

    val languageOptions = listOf("Japanese", "Korean", "Vietnamese", "Spanish")
    val difficultyOptions = when (selectedLanguage) {
        "Japanese" -> listOf("JLPT N1", "JLPT N2", "JLPT N3", "JLPT N4", "JLPT N5")
        "Korean" -> listOf("TOPIK I", "TOPIK II")
        "Vietnamese" -> listOf("Beginner", "Intermediate", "Advanced")
        "Spanish" -> listOf("Beginner", "Intermediate", "Advanced")
        else -> emptyList()
    }
    val materialOptions = listOf("Vocabulary", "Grammar")

    // Reset difficulty and material when language changes
    LaunchedEffect(selectedLanguage) {
        selectedDifficulty = ""
        selectedMaterial = "Vocabulary"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF007893)) // Teal background
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(16.dp) // Add overall padding
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Title
            Text(
                text = "KoeKoi",
                fontSize = 38.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE0F7FA),
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp) // Adjusted padding
            )

            // --- General Language Selection ---
            Text(
                text = "Which language do you want to practice?", // New Label
                fontSize = 18.sp,
                color = Color(0xFFE0F7FA),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(modifier = Modifier.fillMaxWidth(0.7f)) {
                OutlinedButton(
                    onClick = { languageExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB300)),
                    border = BorderStroke(1.dp, Color(0xFFFFB300))
                ) { Text(selectedLanguage, color = Color(0xFFFFB300)) }
                DropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false },
                    modifier = Modifier.background(Color(0xFF015D73)).fillMaxWidth(0.7f)
                ) {
                    languageOptions.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language, color = Color(0xFFE0F7FA)) },
                            onClick = {
                                selectedLanguage = language
                                languageExpanded = false
                            },
                            modifier = Modifier.background(Color(0xFF015D73))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp)) // Space after language selection


            // --- Section 1 (NEW): Generate with AI ---
            Text(
                text = "Generate with AI",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE0F7FA),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // LLM Description Input
            OutlinedTextField(
                value = llmDescription,
                onValueChange = { llmDescription = it },
                modifier = Modifier.fillMaxWidth(0.9f),
                label = { Text("Describe what you want to learn...") },
                placeholder = { Text("e.g., Japanese N3 grammar for requests", color = Color(0xFF90A4AE)) },
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFE0F7FA),
                    unfocusedTextColor = Color(0xFFE0F7FA),
                    focusedBorderColor = Color(0xFFFFB300),
                    unfocusedBorderColor = Color(0xFF90A4AE),
                    focusedLabelColor = Color(0xFFFFB300),
                    unfocusedLabelColor = Color(0xFF90A4AE),
                    cursorColor = Color(0xFFFFB300)
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Generate Button
            Button(
                onClick = {
                    if (llmDescription.isNotBlank() && !isGenerating) {
                        isGenerating = true
                        coroutineScope.launch {
                            val result = llmApiService.generatePhrase(selectedLanguage, llmDescription)
                            launch(Dispatchers.Main) {
                                isGenerating = false
                                result.onSuccess { phrase ->
                                    Toast.makeText(context, "Phrase generated!", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(context, GameActivity::class.java).apply {
                                        putExtra("LANGUAGE", selectedLanguage)
                                        putExtra("DIFFICULTY", "Generated")
                                        putExtra("PHRASE", Gson().toJson(phrase))
                                    }
                                    context.startActivity(intent)
                                }.onFailure { error ->
                                    Toast.makeText(context, "Error: ${error.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                    android.util.Log.e("MainScreen", "LLM Generation Failed", error)
                                }
                            }
                        }
                    } else if (isGenerating) {
                        // Optional feedback
                    } else {
                        Toast.makeText(context, "Please describe what you want to learn.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = llmDescription.isNotBlank() && !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1DE9B6),
                    contentColor = Color(0xFF004D40),
                    disabledContainerColor = Color(0xFF455A64),
                    disabledContentColor = Color(0xFF90A4AE)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF004D40),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generating...")
                    } else {
                        Text("Generate Phrase")
                    }
                }
            }

            Divider(
                color = Color(0xFF455A64),
                thickness = 1.dp,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(vertical = 24.dp) // Space around divider
            )

            // --- Section 2 (NEW): Practice Predefined Sets ---
            Text(
                text = "Or try our predefined sets", // Changed Label
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE0F7FA),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Difficulty Dropdown
            Text("Difficulty", fontSize = 16.sp, color = Color(0xFFE0F7FA))
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth(0.7f)) {
                OutlinedButton(
                    onClick = { if (difficultyOptions.isNotEmpty()) difficultyExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = difficultyOptions.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (difficultyOptions.isNotEmpty()) Color(0xFFFFB300) else Color(0xFF90A4AE),
                        disabledContentColor = Color(0xFF90A4AE)
                    ),
                    border = BorderStroke(1.dp, if (difficultyOptions.isNotEmpty()) Color(0xFFFFB300) else Color(0xFF455A64))
                ) { Text(if (selectedDifficulty.isEmpty()) "Choose Difficulty" else selectedDifficulty) }
                DropdownMenu(
                    expanded = difficultyExpanded,
                    onDismissRequest = { difficultyExpanded = false },
                    modifier = Modifier.background(Color(0xFF015D73)).fillMaxWidth(0.7f)
                ) {
                    difficultyOptions.forEach { difficulty ->
                        DropdownMenuItem(
                            text = { Text(difficulty, color = Color(0xFFE0F7FA)) },
                            onClick = {
                                selectedDifficulty = difficulty
                                difficultyExpanded = false
                            },
                            modifier = Modifier.background(Color(0xFF015D73))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Material Dropdown
            val isMaterialEnabled = selectedLanguage == "Japanese"
            Text("Material", fontSize = 16.sp, color = if(isMaterialEnabled) Color(0xFFE0F7FA) else Color(0xFF90A4AE))
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth(0.7f)) {
                OutlinedButton(
                    onClick = { if (isMaterialEnabled) materialExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isMaterialEnabled,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isMaterialEnabled) Color(0xFFFFB300) else Color(0xFF90A4AE),
                        disabledContentColor = Color(0xFF90A4AE)
                    ),
                    border = BorderStroke(1.dp, if (isMaterialEnabled) Color(0xFFFFB300) else Color(0xFF455A64))
                ) { Text(selectedMaterial) }
                DropdownMenu(
                    expanded = materialExpanded,
                    onDismissRequest = { materialExpanded = false },
                    modifier = Modifier.background(Color(0xFF015D73)).fillMaxWidth(0.7f)
                ) {
                    materialOptions.forEach { material ->
                        DropdownMenuItem(
                            text = { Text(material, color = Color(0xFFE0F7FA)) },
                            onClick = {
                                selectedMaterial = material
                                materialExpanded = false
                            },
                            modifier = Modifier.background(Color(0xFF015D73))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Start Game Button (Assets)
            Button(
                onClick = {
                    if (selectedDifficulty.isNotEmpty()) {
                        onStartGameFromAssets(selectedLanguage, selectedDifficulty, selectedMaterial)
                    } else {
                        Toast.makeText(context, "Please select a difficulty for predefined sets.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = selectedDifficulty.isNotEmpty(), // Enable only if difficulty is chosen
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF8F00),
                    contentColor = Color(0xFFE0F7FA),
                    disabledContainerColor = Color(0xFF455A64),
                    disabledContentColor = Color(0xFF90A4AE)
                )
            ) {
                Text("Start Predefined Set")
            }


        } // End Main Column

        // Bottom Icons (Remain at the bottom)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onShowFavorites, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Favorite, "Favorites", tint = Color(0xFFE0F7FA), modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onShowSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Settings, "Settings", tint = Color(0xFFE0F7FA), modifier = Modifier.size(24.dp))
            }
        }
    } // End Main Box
}