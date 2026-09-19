package com.auroraai.chat.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.auroraai.chat.data.SettingsStore
import com.auroraai.chat.ui.components.ChatBubble
import com.auroraai.chat.ui.components.ChatInputBar
import com.auroraai.chat.ui.components.ProviderBadge
import com.auroraai.chat.viewmodel.AppUiState
import kotlinx.coroutines.launch

private data class HeroSuggestion(val icon: ImageVector, val title: String, val subtitle: String, val prompt: String)

private val HERO_SUGGESTIONS = listOf(
    HeroSuggestion(Icons.Default.Lightbulb, "Brainstorm ideas", "Get unstuck fast", "Help me brainstorm ideas for: "),
    HeroSuggestion(Icons.Default.Edit, "Write something", "Emails, posts, drafts", "Write a first draft of: "),
    HeroSuggestion(Icons.Default.School, "Explain a concept", "Clear, no jargon", "Explain this like I'm new to it: "),
    HeroSuggestion(Icons.Default.AutoAwesome, "Improve my writing", "Tighter, clearer", "Improve the clarity and flow of this: ")
)

private data class QuickChip(val label: String, val prompt: String)

private val QUICK_CHIPS = listOf(
    QuickChip("💡 Brainstorm", "Help me brainstorm ideas for: "),
    QuickChip("📝 Write", "Write a first draft of: "),
    QuickChip("🎓 Explain", "Explain this like I'm new to it: "),
    QuickChip("✨ Improve", "Improve the clarity and flow of this: ")
)

@Composable
fun ChatScreen(
    state: AppUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenProviderPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    Column(modifier) {
        if (state.messages.isEmpty()) {
            EmptyChatHint(modifier = Modifier.weight(1f), onSuggestionSelected = { draft = TextFieldValue(it, TextRange(it.length)) })
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(
                        role = message.role,
                        text = message.text,
                        isStreaming = message.isStreaming,
                        isError = message.isError
                    )
                }
                if (state.isAgentRunning) {
                    item {
                        Box(Modifier.padding(start = 4.dp)) {
                            StatusRow(state.statusLabel)
                        }
                    }
                }
            }
        }

        val profile = state.activeProviderProfile
        val providerLabel = profile?.let { SettingsStore.providerLabel(it.baseUrl) } ?: "No provider"
        val isReady = profile != null && (profile.apiKey.isNotBlank() || SettingsStore.isKeylessLocal(profile.baseUrl))

        if (state.messages.isEmpty() && !state.isAgentRunning) {
            QuickChipsRow(onPick = { draft = TextFieldValue(it, TextRange(it.length)) })
        }

        ChatInputBar(
            value = draft,
            onValueChange = { draft = it },
            onSend = { onSend(draft.text); draft = TextFieldValue("") },
            onStop = onStop,
            isRunning = state.isAgentRunning,
            providerBadge = {
                Box(Modifier.clickable(onClick = onOpenProviderPicker)) {
                    ProviderBadge(providerLabel, profile?.model ?: "add a provider", isReady)
                }
            }
        )
    }
}

@Composable
private fun QuickChipsRow(onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (chip in QUICK_CHIPS) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable { onPick(chip.prompt) }
            ) {
                Text(
                    chip.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp).size(14.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier, onSuggestionSelected: (String) -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("How can I help?", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ask anything, or start from one of these.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(HERO_SUGGESTIONS) { suggestion ->
                HeroCard(suggestion) { onSuggestionSelected(suggestion.prompt) }
            }
        }
    }
}

@Composable
private fun HeroCard(suggestion: HeroSuggestion, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(suggestion.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Text(suggestion.title, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(
                suggestion.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
