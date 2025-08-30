package com.example.poetica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        
        // Poem content with reading width constraint
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = getResponsivePoemPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp) // Constrain reading width
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                text = poem.title,
                style = getResponsivePoemTitleStyle(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Author
            Text(
                text = poem.author,
                style = getResponsivePoemAuthorStyle(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Poem content with proper line breaks and spacing
            ReadablePoemContent(
                content = poem.content,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            }
        }
    }
}

@Composable
fun ReadablePoemContent(
    content: String,
    modifier: Modifier = Modifier
) {
    // Split content into stanzas (separated by double line breaks)
    val stanzas = content.split("\n\n").filter { it.isNotBlank() }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start, // Left-align for better readability
        verticalArrangement = Arrangement.spacedBy(24.dp) // Optimal stanza spacing
    ) {
        stanzas.forEach { stanza ->
            ReadableStanza(
                text = stanza.trim(),
                modifier = Modifier.fillMaxWidth()
            )
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