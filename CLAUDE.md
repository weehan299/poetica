# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Poetica is a poetry viewing Android application built with Kotlin and Jetpack Compose. It provides fast search capabilities and a minimalist, classy reading experience for poetry. The app features bundled classic poems and is architected to scale with remote poem fetching in the future.

## Key Technologies
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **Database**: Room with KSP (Kotlin Symbol Processing)
- **Navigation**: Jetpack Navigation Compose
- **Serialization**: kotlinx.serialization
- **Build System**: Gradle with Kotlin DSL
- **Min SDK**: 21, Target SDK: 36
- **JVM Target**: Java 11

## Build and Development Commands

### Building the project
```bash
./gradlew build
```

### Running tests
```bash
# Unit tests (JVM)
./gradlew test

# Instrumented tests (Android device/emulator required)
./gradlew connectedAndroidTest
```

### Installing debug build
```bash
./gradlew installDebug
```

### Clean build
```bash
./gradlew clean
```

## Project Architecture

### Layer Structure
The app follows **Clean Architecture** principles with three main layers:

#### Presentation Layer (`ui/`)
- **Screens**: `HomeScreen.kt` (search & list), `PoemReaderScreen.kt` (reading experience)
- **ViewModels**: `HomeViewModel.kt`, `PoemReaderViewModel.kt` with StateFlow
- **Theme**: Custom poetry-focused Material 3 theme with serif typography
- **Search**: Custom fast search engine with indexing and relevance scoring

#### Data Layer (`data/`)
- **Models**: `Poem.kt` (Room entity), `Stanza.kt`, `PoemCollection.kt`
- **Database**: Room database with `PoeticaDatabase.kt` and `PoemDao.kt`
- **Repository**: `PoemRepository.kt` - single source of truth, supports bundled + future remote
- **Converters**: Room type converters for List<String> and enums

#### Navigation
- **Navigation**: Compose Navigation with `PoeticaNavigation.kt`

### Data Flow
```
JSON Assets → Repository → Room DB → ViewModel → UI State → Compose Screen
     ↓
Search Engine ← Repository ← Room DB ← ViewModel ← User Input
```

## Key Features

### Poetry Reading Experience
- Minimalist full-screen reader with elegant serif typography
- Custom color palette (warm golds, creams, browns)
- Proper stanza spacing and center-aligned text
- Theme information and tags display

### Fast Search
- Custom indexing system for instant text search
- Search by title, author, content, and tags
- Relevance scoring and smart result ranking
- Debounced input (300ms) for optimal performance

### Sample Content
- 7 bundled classic poems from renowned poets (Frost, Shakespeare, Dickinson, etc.)
- Stored in `app/src/main/assets/poems.json`
- Loaded into Room database on first launch

## Important Implementation Details

### Database Setup
- Uses **KSP** instead of KAPT for Room annotation processing
- Room entities require TypeConverters for complex types
- `Converters.kt` handles List<String> and SourceType enum conversions

### ViewModel Pattern
- ViewModels use StateFlow for reactive UI updates
- Repository injected via constructor (manual DI approach)
- Proper coroutine scoping with viewModelScope
- **Important**: When collecting flows in ViewModels, use separate coroutines to avoid blocking initialization

### Search Implementation
- Custom `SearchEngine.kt` builds in-memory indexes for fast lookup
- Searches across titles, authors, content, and tags
- Returns `SearchResult` objects with match type and relevance scores

### Navigation
- Single Activity architecture with Compose Navigation
- Route definitions in `PoeticaDestinations` object
- ViewModels created per navigation destination

## Development Guidelines

### Adding New Poems
1. Update `poems.json` in assets folder
2. Follow existing JSON structure with required fields
3. Database will auto-update on next app launch

### Extending Search
- Modify `SearchEngine.buildIndex()` to include new searchable fields
- Update relevance scoring in `calculateRelevanceScore()`
- Add new `MatchType` enum values if needed

### UI Customization
- Poetry-specific typography in `Type.kt` (serif fonts, optimal line heights)
- Color scheme in `Color.kt` (warm, reading-focused palette)
- Theme logic in `PoeticaTheme.kt`

### Future Remote Integration
- Repository pattern ready for API integration
- Retrofit dependencies already included
- Room database configured for caching
- `SourceType` enum differentiates bundled vs remote poems

## Common Development Tasks

### Running with Fresh Database
```bash
# Clear app data to reload JSON poems
adb shell pm clear com.example.poetica
```

### Debugging Database Issues
- Room generates SQL queries visible in logs
- Use Database Inspector in Android Studio
- Check TypeConverters for custom data types

### Performance Optimization
- LazyColumn used for efficient poem lists
- StateFlow prevents unnecessary recompositions  
- Custom search indexing avoids database queries during search
- Room queries use Flow for automatic UI updates

## Testing Approach
- Unit tests focus on ViewModels and Repository logic
- UI tests use Compose Testing framework
- Database tests can use in-memory Room database
- Search functionality should have comprehensive unit tests