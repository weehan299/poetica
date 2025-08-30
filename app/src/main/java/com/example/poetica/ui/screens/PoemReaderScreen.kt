package com.example.poetica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.poetica.data.model.Poem
import com.example.poetica.ui.theme.getResponsivePoemAuthorStyle
import com.example.poetica.ui.theme.getResponsivePoemPadding
import com.example.poetica.ui.theme.getResponsivePoemTextStyle
import com.example.poetica.ui.theme.getResponsivePoemTitleStyle
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
    // LazyColumn state for performance and scroll position memory
    val lazyListState = rememberLazyListState()
    
    // Calculate reading progress for progress indicator
    val readingProgress by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo.size
            
            // If everything fits on screen, progress is always 100%
            if (totalItems == 0 || totalItems <= visibleItems) {
                1f
            } else {
                val scrollableItems = totalItems - visibleItems
                val scrolledItems = lazyListState.firstVisibleItemIndex
                val itemScrollProgress = lazyListState.firstVisibleItemScrollOffset.toFloat() / 
                    maxOf(1, layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1)
                
                ((scrolledItems + itemScrollProgress) / scrollableItems.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    
    // Prepare stanzas for LazyColumn items
    val stanzas = remember(poem.content) {
        poem.content.split("\n\n").filter { it.isNotBlank() }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars) // Respect system bars
    ) {
        // Top App Bar with progress indicator
        TopAppBar(
            title = { 
                // Discreet progress indicator
                LinearProgressIndicator(
                    progress = { readingProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        // LazyColumn with SelectionContainer for text selection
        SelectionContainer {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = getResponsivePoemPadding()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header spacer
                item { 
                    Spacer(modifier = Modifier.height(16.dp)) 
                }
                
                // Title
                item {
                    Box(
                        modifier = Modifier.widthIn(max = 600.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = poem.title,
                            style = getResponsivePoemTitleStyle(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Spacer after title
                item { 
                    Spacer(modifier = Modifier.height(8.dp)) 
                }
                
                // Author
                item {
                    Box(
                        modifier = Modifier.widthIn(max = 600.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = poem.author,
                            style = getResponsivePoemAuthorStyle(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Spacer before poem content
                item { 
                    Spacer(modifier = Modifier.height(32.dp)) 
                }
                
                // Each stanza as separate LazyColumn item for optimal performance
                items(stanzas.size) { index ->
                    Box(
                        modifier = Modifier.widthIn(max = 600.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        ReadableStanza(
                            text = stanzas[index].trim(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Add spacing between stanzas (except after last stanza)
                    if (index < stanzas.size - 1) {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                
                // Footer spacer
                item { 
                    Spacer(modifier = Modifier.height(32.dp)) 
                }
            }
        }
    }
}


@Composable
fun ReadableStanza(
    text: String,
    modifier: Modifier = Modifier
) {
    val textStyle = getResponsivePoemTextStyle()
    
    // Split into individual lines to preserve poet's intended line breaks
    val lines = text.split('\n').filter { it.isNotBlank() }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(0.dp) // No extra spacing between lines within stanza
    ) {
        lines.forEach { line ->
            ReadablePoemLine(
                text = line.trim(),
                textStyle = textStyle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ReadablePoemLine(
    text: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    // Implement hanging indent for wrapped lines
    // First line has no indent, continuation lines are indented
    Text(
        text = text,
        style = textStyle.copy(
            textIndent = TextIndent(
                firstLine = 0.sp,
                restLine = 24.sp // Hanging indent for wrapped lines
            )
        ),
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Start, // Left-aligned for optimal reading
        modifier = modifier,
        softWrap = true // Always allow soft wrapping
    )
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
                        Icons.AutoMirrored.Filled.ArrowBack,
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