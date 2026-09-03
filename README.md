## Photo App Tech Stack

### Architecture & Patterns
* MVVM architecture
* Jetpack Compose
* Modularization
* Hilt (Dependency Injection)

### UI & Design
* Material Design 3
* Jetpack Compose UI
* Navigation Compose
* Deep Linking
* Theme Switching
* Language Switching
* Splash Screen
* In-App Update Prompts (Google Play Core)

### Storage
* Firebase Firestore
* Firebase Storage
* Room (Local Database)
* DataStore (Preferences)

### Networking & Backend
* Firebase Authentication
* Firebase Remote Config
* Firebase Analytics
* Firebase Crashlytics
* Firebase Cloud Messaging (Push Notifications)
* Firebase In-App Messaging
* Firebase A/B Testing
* Firebase Cloud Functions
* Firebase App Check
* Firebase Performance Monitoring
* Google Cloud

### Machine Learning & AI
* Google Cloud Vision API (Text Detection, Label Detection, Object Localization, Landmark Detection, Logo Detection)

### Media & Images
* Coil (Image Loading)
* ExoPlayer (Media Playback)
* Camera Integration

### Reactive Programming
* Kotlin Coroutines
* Kotlin Flow
* ViewModel

### Testing
* Local Tests (JUnit, Robolectric, MockK, Turbine)
* Instrumented Tests
* UI Testing (Espresso, Compose Testing, Screenshot Testing)

### Development Tools
* Kotlin Symbol Processing (KSP)
* R8 (Code Shrinking & Obfuscation)
* Build Configuration Management
* GitHub Actions CI (Build, Unit Tests, Screenshot Verification, Lint)

### Architecture Overview
SmartPhotos follows the **MVVM (Model-View-ViewModel)** architecture pattern, combined with a clean layered structure to ensure separation of concerns, testability, and scalability.

### Layers
| Layer          | Responsibility                                        | Key Technologies                   |
|----------------|-------------------------------------------------------|------------------------------------|
| **UI**         | Render screens, handle user interactions              | Jetpack Compose, Material Design 3 |
| **ViewModel**  | Hold & manage UI state, expose data as Flow/StateFlow | ViewModel, Kotlin Flow, Coroutines |
| **Repository** | Abstract data sources, coordinate remote/local sync   | Kotlin Coroutines                  |
| **Remote**     | Network calls, cloud storage, authentication          | Firebase, Google Cloud Vision API  |
| **Local**      | Offline caching, user preferences                     | Room, DataStore                    |
