package com.himanshu.himanshu.ui.view

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputArea(
    onSend: (String) -> Unit,
    onVoiceClick: () -> Unit,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    // Internal management of input box text content
    var inputText by remember { mutableStateOf("") }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(), // Automatically handle spacing when keyboard pops up
        tonalElevation = 3.dp, // Slightly increase elevation to distinguish from chat list
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding(), // Handle system navigation bar occlusion
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- Voice button ---
            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Close else Icons.Default.AddCircle,
                    contentDescription = "Voice Input",
                    // Show red during recording to remind
                    tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                )
            }

            // --- Text input field ---
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Enter or voice your command...") },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                maxLines = 4, // Support up to 4 lines of automatic growth
                shape = RoundedCornerShape(24.dp), // Rounded design, more like chat software
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )

            // --- Send button ---
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSend(inputText)
                        inputText = "" // Clear input box after clicking
                    }
                },
                // Disable sending when input is empty or recording
                enabled = inputText.isNotBlank() && !isRecording,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Task",
                    tint = if (inputText.isNotBlank() && !isRecording)
                        MaterialTheme.colorScheme.primary
                    else Color.Gray
                )
            }
        }
    }
}
