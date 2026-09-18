# WineApp - Vivino-like Wine Scanner

A complete Android application skeleton for a wine scanner app built with Clean Architecture + MVI pattern.

## Tech Stack

- **Architecture**: Clean Architecture + MVI (Model-View-Intent)
- **DI**: Hilt (Dagger)
- **UI**: Jetpack Compose with Material3
- **Navigation**: Navigation Compose
- **Database**: Room
- **Network**: Retrofit + Kotlinx Serialization
- **Camera**: CameraX
- **Image Loading**: Coil
- **Language**: Kotlin

## Project Structure

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/wineapp/
│   │   ├── WineApplication.kt          # Hilt Application
│   │   ├── MainActivity.kt             # Entry point with NavHost
│   │   ├── di/
│   │   │   └── AppModule.kt           # Hilt modules (Database, Network, Repository)
│   │   ├── data/
│   │   │   ├── local/                 # Room database
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── WineDao.kt
│   │   │   │   ├── WineHistoryEntity.kt
│   │   │   │   └── converter/Converters.kt
│   │   │   ├── remote/                # Retrofit API
│   │   │   │   ├── ApiService.kt
│   │   │   │   ├── dto/ApiDtos.kt
│   │   │   │   └── mapper/ (WineMapper, ScanMapper, SearchMapper)
│   │   │   ├── repository/
│   │   │   │   └── WineRepositoryImpl.kt
│   │   │   └── file/                  # Camera & Gallery
│   │   │       ├── CameraHelper.kt
│   │   │       └── GalleryPicker.kt
│   │   ├── domain/
│   │   │   ├── model/                 # Domain models
│   │   │   │   └── Wine.kt
│   │   │   ├── repository/
│   │   │   │   └── WineRepository.kt
│   │   │   └── usecase/               # 5 UseCases
│   │   │       ├── ScanWineUseCase.kt
│   │   │       ├── SearchWinesUseCase.kt
│   │   │       ├── GetWineDetailsUseCase.kt
│   │   │       ├── SaveToHistoryUseCase.kt
│   │   │       └── GetHistoryUseCase.kt
│   │   └── presentation/
│   │       ├── common/                # MVI Base classes & UI components
│   │       │   ├── MviBase.kt
│   │       │   └── UiComponents.kt
│   │       ├── scanner/               # Scanner screen
│   │       │   ├── ScannerState.kt
│   │       │   ├── ScannerViewModel.kt
│   │       │   └── ScannerScreen.kt
│   │       ├── search/                # Search screen
│   │       │   ├── SearchState.kt
│   │       │   ├── SearchViewModel.kt
│   │       │   └── SearchScreen.kt
│   │       ├── detail/                # Wine detail screen
│   │       │   ├── DetailState.kt
│   │       │   ├── DetailViewModel.kt
│   │       │   └── DetailScreen.kt
│   │       ├── sommelier/             # AI Sommelier chat
│   │       │   ├── SommelierState.kt
│   │       │   ├── SommelierViewModel.kt
│   │       │   └── SommelierScreen.kt
│   │       ├── notfound/              # Wine not found screen
│   │       │   └── NotFoundScreen.kt
│   │       ├── agegate/               # Age verification screen
│   │       │   └── AgeGateScreen.kt
│   │       └── navigation/            # Navigation graph
│   │           └── AppNavHost.kt
│   └── res/                           # Resources
│       ├── values/strings.xml
│       ├── values/themes.xml
│       ├── values/colors.xml
│       └── xml/ (file_paths, backup_rules, data_extraction_rules)
└── proguard-rules.pro
```

## MVI Pattern

Each screen follows the MVI pattern:
- **State**: Sealed interface representing all possible UI states
- **Intent**: Sealed interface representing user actions
- **Reducer**: Pure function (in ViewModel) that reduces Intent + State -> New State
- **ViewModel**: Holds StateFlow, processes Intents through Reducer

Example:
```kotlin
sealed interface ScannerState : BaseState {
    data class Ready(val flashMode: Int = 0) : ScannerState
    data class Processing(val imagePath: String) : ScannerState
    data class Success(val result: ScanResult, val imagePath: String) : ScannerState
    data class Error(override val message: String) : ScannerState, BaseState.Error(message)
}

sealed interface ScannerIntent : BaseIntent {
    data class CapturePhoto(val imagePath: String) : ScannerIntent
    data class ProcessImage(val imagePath: String) : ScannerIntent
    // ...
}
```

## Key Features (Skeleton)

1. **Scanner Screen**: CameraX preview with capture, flash toggle, gallery picker
2. **Search Screen**: Search wines with pagination, lazy loading
3. **Detail Screen**: Wine details with image, rating, description, food pairing
4. **Sommelier Screen**: AI chat interface for wine questions
5. **History**: Room database for scan history
6. **Age Gate**: First-launch age verification

## TODOs for Implementation

All UseCases have TODO comments for:
- Image preprocessing and ML-based label recognition
- Actual API integration (Vivino or similar)
- AI Sommelier integration (OpenAI, etc.)
- Image to base64 conversion
- Proper JSON serialization for food pairing
- Navigation integration between screens
- Unit and UI tests

## Building

```bash
./gradlew assembleDebug
```

Requires:
- JDK 17+
- Android SDK 35
- Gradle 8.7+