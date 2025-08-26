package com.example.poetica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.poetica.data.model.Poem
import com.example.poetica.ui.viewmodel.PoemReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoemReaderScreen(
    viewModel: PoemReaderViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        uiState.poem != null -> {
            PoemReaderContent(
                poem = uiState.poem!!,
                onBackClick = onBackClick,
                modifier = modifier
            )
        }
        
        uiState.error != null -> {
            ErrorContent(
                error = uiState.error!!,
                onBackClick = onBackClick,
                modifier = modifier
            )
        }
        
        else -> {
            ErrorContent(
                error = "Poem not found",
                onBackClick = onBackClick,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoemReaderContent(
    poem: Poem,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar - minimal
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        // Poem content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                text = poem.title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Author
            Text(
                text = poem.author,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Poem content with proper line breaks and spacing
            PoemContent(
                content = poem.content,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            
        }
    }
}

@Composable
fun PoemContent(
    content: String,
    modifier: Modifier = Modifier
) {
    // Split content into stanzas (separated by double line breaks)
    val stanzas = content.split("\n\n").filter { it.isNotBlank() }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        stanzas.forEach { stanza ->
            StanzaText(
                text = stanza.trim(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StanzaText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = modifier,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
    )
}

@Composable
fun TagsSection(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Themes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Wrap tags in rows
        val chunkedTags = tags.chunked(3)
        chunkedTags.forEach { rowTags ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowTags.forEach { tag ->
                    AssistChip(
                        onClick = { },
                        label = { 
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall
                            ) 
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        )
                    )
                }
            }
            if (chunkedTags.indexOf(rowTags) < chunkedTags.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorContent(
    error: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(onClick = onBackClick) {
                    Text("Go Back")
                }
            }
        }
    }
}