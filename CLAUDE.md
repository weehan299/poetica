# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Poetica is a poetry viewing Android application built with Kotlin and Jetpack Compose. It provides fast search capabilities and a minimalist, classy reading experience for poetry. The app features a **pre-populated SQLite database** with 33,651+ poems and is architected for both offline and online experiences with hybrid API integration.

## Key Technologies
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **Database**: Room with KSP (Kotlin Symbol Processing) + Pre-populated SQLite
- **Navigation**: Jetpack Navigation Compose
- **Serialization**: kotlinx.serialization
- **API Integration**: Retrofit with hybrid local/remote data strategy
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

### Database Management
```bash
# Regenerate pre-populated database from poems_bundle.json
python3 scripts/convert_poems_to_sqlite.py

# Clear app data to test fresh database installation
adb shell pm clear com.example.poetica
```

## Project Architecture

### Layer Structure
The app follows **Clean Architecture** principles with three main layers:

#### Presentation Layer (`ui/`)
- **Screens**: `HomeScreen.kt` (poem of the day), `DiscoverScreen.kt` (search & browse), `PoemReaderScreen.kt` (reading experience)
- **ViewModels**: `HomeViewModel.kt`, `DiscoverViewModel.kt`, `PoemReaderViewModel.kt` with StateFlow
- **Theme**: Custom poetry-focused Material 3 theme with serif typography
- **Search**: Custom fast search engine with indexing and relevance scoring

#### Data Layer (`data/`)
- **Models**: `Poem.kt` (Room entity with indexes), `PoemCollection.kt`, `SearchResult.kt`
- **Database**: Room database (`PoeticaDatabase.kt`) with pre-populated SQLite file and optimized `PoemDao.kt`
- **Repository**: `PoemRepository.kt` - hybrid local/remote data source with intelligent caching
- **API**: Complete Retrofit integration (`ApiConfig.kt`, `PoeticaApiService.kt`) with configuration management
- **Converters**: Room type converters for List<String> and enums

#### Navigation
- **Navigation**: Two-tab bottom navigation with Compose Navigation (`PoeticaNavigation.kt`)
- **Tabs**: Home (poem of the day) and Discover (search and browse)

### Data Flow
```
Pre-populated SQLite DB → Repository → ViewModel → UI State → Compose Screen
                    ↑              ↓
API (cached) ←------+              ↓
                                   ↓
Search Engine ← Repository ← Optimized DAO ← User Input
```

### Hybrid Data Strategy
- **Primary**: Pre-populated SQLite database with 33,651+ poems (instant access)
- **Secondary**: REST API integration for additional content and remote features  
- **Caching**: In-memory API response caching with intelligent content upgrading
- **Fallback**: JSON parsing as ultimate fallback for corrupted databases

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

### Content Collection
- **33,651+ poems** from 351+ authors pre-loaded in SQLite database
- **Source files**: `poems_bundle.json` (54MB) converted to optimized SQLite (131MB with indexes)
- **Instant availability**: No loading delays, works fully offline
- **Legacy support**: Fallback to smaller `poems.json` (7 poems) if needed

## Important Implementation Details

### Database Setup
- Uses **KSP** instead of KAPT for Room annotation processing
- **Pre-populated database**: `createFromAsset("databases/poetica_poems.db")` for instant data access
- **Memory optimizations**: Selective column queries, pagination, and indexed searches
- **Entity indexes**: `@Index` annotations on `Poem.kt` for fast search performance
- Room entities require TypeConverters for complex types (`Converters.kt`)

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
- Single Activity architecture with Compose Navigation and bottom navigation bar
- Two main tabs: Home (poem of the day) and Discover (search and browse)
- Route definitions in `PoeticaDestinations` sealed class
- ViewModels created per navigation destination using `ViewModelFactory`

## Development Guidelines

### Adding New Poems
1. **For large collections**: Update `poems_bundle.json` and regenerate database using Python script
2. **For small additions**: Update `poems.json` (fallback file) 
3. **API integration**: Poems from API are automatically cached and integrated
4. Follow existing JSON structure with required fields

### Extending Search
- Modify `SearchEngine.buildIndex()` to include new searchable fields
- Update relevance scoring in `calculateRelevanceScore()`
- Add new `MatchType` enum values if needed

### UI Customization
- Poetry-specific typography in `Type.kt` (serif fonts, optimal line heights)
- Color scheme in `Color.kt` (warm, reading-focused palette)
- Theme logic in `PoeticaTheme.kt`

### API Integration Architecture
- **Configuration-driven**: `PoeticaConfig.kt` manages environment-specific API settings
- **Hybrid strategy**: Local-first with API enrichment (poem of the day prioritizes local database)
- **Intelligent caching**: API responses cached in-memory with preview content detection
- **Content upgrading**: Automatically fetches full content when API returns previews
- **Environment support**: Debug (local server) and production (Google Cloud Run) endpoints
- **SourceType differentiation**: `BUNDLED`, `REMOTE`, `USER_ADDED` for data provenance tracking

## Performance and Memory Management

### Critical Memory Optimizations
- **Poem of the Day**: Uses `getPoemByIndex()` instead of loading all 33K+ poems
- **Selective queries**: DAO methods with `SELECT id, title, author` for lists (90% memory reduction)
- **Pagination**: `LIMIT`/`OFFSET` queries prevent loading large datasets
- **Search limits**: Maximum 100 search results to prevent OOM errors
- **LazyColumn**: Efficient rendering of large poem collections

### Database Query Patterns
```kotlin
// ❌ Memory-intensive (loads all content)
getAllPoemsSync(): List<Poem>

// ✅ Memory-efficient (metadata only)  
getPoemsMetadata(limit: Int, offset: Int): List<Poem>
getPoemByIndex(index: Int): Poem?
```

### Debugging Database Issues
- Monitor memory usage in Android Studio profiler
- Room generates SQL queries visible in logs with tag `PoemRepository`
- Use Database Inspector to examine pre-populated SQLite file
- Check `poetica_poems.db` file integrity if crashes occur

## Testing Approach
- Unit tests focus on ViewModels and Repository logic
- UI tests use Compose Testing framework  
- Database tests can use in-memory Room database
- **API integration tests**: Mock `PoeticaApiService` for hybrid data flow testing
- **Memory testing**: Verify optimized queries don't cause OOM errors
- Search functionality should have comprehensive unit tests

## Environment Configuration
- **Debug build**: Uses local development API (`http://172.30.28.71:8000`)
- **Production build**: Uses Google Cloud Run API (`https://poetica-api-544010023223.us-central1.run.app`)
- **Configuration management**: `PoeticaConfig.kt` handles environment detection and API switching
- **BuildConfig integration**: Automatically selects correct API based on build variant