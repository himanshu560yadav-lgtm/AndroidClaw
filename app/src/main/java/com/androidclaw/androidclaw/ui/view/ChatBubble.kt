package com.androidclaw.androidclaw.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.androidclaw.androidclaw.model.AiAction
import com.androidclaw.androidclaw.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(msg: ChatMessage, onConfirmAction: (AiAction) -> Unit) {
    val isAi = msg.role == "ai"
    val isSystem = msg.role == "system"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isAi || isSystem) Alignment.Start else Alignment.End
    ) {
        Surface(
            color = when {
                isSystem -> Color.LightGray.copy(alpha = 0.2f)
                isAi -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Display AI's reason (user's language)
                Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)

                // If there's a specific Action, show details
                msg.action?.let { action ->
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Text(
                        text = "Action: ${action.type.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )

                    // If click, show coordinates
                    if (action.type == "click") Text(
                        "Coordinates: (${action.x}, ${action.y})",
                        style = MaterialTheme.typography.labelSmall
                    )
                    // If swipe, show swipe details
                    if (action.type == "swipe") Text(
                        "Swipe: (${action.startX}, ${action.startY}) → (${action.endX}, ${action.endY})",
                        style = MaterialTheme.typography.labelSmall
                    )
                    // If scroll, show direction
                    if (action.type == "scroll") Text(
                        "Scroll: ${action.direction?.uppercase()}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    // If input, show text
                    if (action.type == "input") Text(
                        "Input: \"${action.text}\"",
                        style = MaterialTheme.typography.labelSmall
                    )
                    // If system, show system action
                    if (action.type == "system") {
                        Text(
                            "System: ${action.systemAction?.replaceFirstChar { it.uppercase() }} ${action.systemValue ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Green
                        )
                    }
                    // If shell, show command
                    if (action.type == "sh") Text(
                        "Command: ${action.command}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Red
                    )

                    // Quick confirm button for sensitive actions
                    if (action.type in listOf("click", "swipe", "scroll", "input", "sh")) {
                        Button(
                            onClick = { onConfirmAction(action) },
                            modifier = Modifier.padding(top = 8.dp).height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text("Execute Now", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}