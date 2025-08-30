package com.example.poetica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
        
        // Poem content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = getResponsivePoemPadding())
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
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Split into individual lines to preserve poet's intended line breaks
    val lines = text.split('\n').filter { it.isNotBlank() }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        lines.forEach { line ->
            PoemLine(
                text = line.trim(),
                screenWidth = screenWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PoemLine(
    text: String,
    screenWidth: Dp,
    modifier: Modifier = Modifier
) {
    val textStyle = getResponsivePoemTextStyle()
    
    // Estimate if line might be too long based on character count and screen size
    val screenWidthValue = screenWidth.value
    val estimatedLineIsTooLong = when {
        screenWidthValue >= 600f -> text.length > 80  // Tablets can handle longer lines
        screenWidthValue >= 480f -> text.length > 60  // Medium screens
        screenWidthValue >= 360f -> text.length > 45  // Regular phones
        else -> text.length > 35                      // Compact phones
    }
    
    if (estimatedLineIsTooLong) {
        // For very long lines, use horizontal scroll as elegant fallback
        Box(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = textStyle,
                color = MaterialTheme.colorScheme.onBackground,
                overflow = TextOverflow.Visible,
                softWrap = false,  // Prevent wrapping, let horizontal scroll handle overflow
                modifier = Modifier
                    .padding(horizontal = 24.dp)  // Ensure text has breathing room
                    .wrapContentWidth()
            )
        }
    } else {
        // Normal lines with responsive text styling
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = modifier,
            overflow = TextOverflow.Visible,
            softWrap = true  // Allow soft wrapping for normal-length lines
        )
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