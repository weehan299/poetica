package com.example.poetica.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/**
 * Responsive typography that adapts to screen size for optimal poetry display.
 * Reduces font size and tightens spacing on smaller screens to minimize line wrapping.
 */
@Composable
fun getResponsivePoemTextStyle(): TextStyle {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    return when {
        // Large screens (tablets in landscape, large phones in landscape)
        screenWidth >= 600.dp -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 19.sp,
            lineHeight = 30.sp, // 1.58x ratio for optimal readability
            letterSpacing = 0.15.sp
        )
        // Medium screens (most phones in landscape, small tablets in portrait)
        screenWidth >= 480.dp -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            lineHeight = 28.sp, // 1.56x ratio
            letterSpacing = 0.1.sp
        )
        // Small screens (most phones in portrait)
        screenWidth >= 360.dp -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 26.sp, // 1.53x ratio
            letterSpacing = 0.05.sp
        )
        // Very small screens (compact phones)
        else -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 25.sp, // 1.56x ratio for better readability
            letterSpacing = 0.sp
        )
    }
}

/**
 * Returns responsive horizontal padding for poem content based on screen size.
 * Uses more screen width on smaller devices to fit longer lines while ensuring
 * minimum safe padding to prevent character truncation. Also considers system UI insets.
 */
@Composable
fun getResponsivePoemPadding(): Dp {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    
    // Get system insets for safe area calculation
    val systemBars = WindowInsets.systemBars
    val displayCutout = WindowInsets.displayCutout
    
    // Calculate additional padding needed for safe areas (notches, rounded corners)
    val systemInsetPadding = with(density) {
        val leftSystemBar = systemBars.getLeft(density, layoutDirection).toDp()
        val rightSystemBar = systemBars.getRight(density, layoutDirection).toDp()
        val leftCutout = displayCutout.getLeft(density, layoutDirection).toDp()
        val rightCutout = displayCutout.getRight(density, layoutDirection).toDp()
        
        // Find maximum horizontal inset
        maxOf(leftSystemBar, rightSystemBar, leftCutout, rightCutout)
    }
    
    // Base minimum safe padding to prevent edge truncation
    val minimumSafePadding = 16.dp
    
    val calculatedPadding = when {
        screenWidth >= 600.dp -> 32.dp  // Tablets: generous padding
        screenWidth >= 480.dp -> 24.dp  // Medium screens: standard padding
        screenWidth >= 360.dp -> 18.dp  // Regular phones: slightly reduced padding
        else -> 16.dp                   // Compact phones: safe minimum padding
    }
    
    // Combine base padding with system insets, ensuring minimum safe padding
    val totalPadding = calculatedPadding + systemInsetPadding
    return if (totalPadding < minimumSafePadding) minimumSafePadding else totalPadding
}

/**
 * Helper function to find maximum Dp value from multiple Dp values
 */
private fun maxOf(a: Dp, b: Dp, c: Dp, d: Dp): Dp {
    val first = if (a > b) a else b
    val second = if (c > d) c else d
    return if (first > second) first else second
}

/**
 * Returns responsive title text style that scales appropriately.
 */
@Composable
fun getResponsivePoemTitleStyle(): TextStyle {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    return when {
        screenWidth >= 600.dp -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        )
        screenWidth >= 480.dp -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        )
        else -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp
        )
    }
}

/**
 * Returns responsive author text style.
 */
@Composable
fun getResponsivePoemAuthorStyle(): TextStyle {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    return when {
        screenWidth >= 600.dp -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.15.sp
        )
        screenWidth >= 480.dp -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.15.sp
        )
        else -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.1.sp
        )
    }
}

/**
 * Calculates horizontal padding to center poem content (max 600dp) within the available width.
 * This ensures the entire screen is scrollable while keeping content readable and centered.
 */
@Composable
fun getResponsivePoemContentPadding(): Dp {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val basePadding = getResponsivePoemPadding()
    
    return when {
        // On screens wider than 600dp + 2*basePadding, center the content
        screenWidth > (600.dp + (basePadding * 2)) -> {
            val extraSpace = screenWidth - 600.dp
            extraSpace / 2
        }
        // On narrower screens, use the responsive padding
        else -> basePadding
    }
}