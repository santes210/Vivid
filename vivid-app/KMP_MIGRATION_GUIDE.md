# Vivid — Migración a Kotlin Multiplatform (KMP)

## 📋 Resumen

Este documento describe la migración de **Vivid** de una app nativa Android a una arquitectura **Kotlin Multiplatform (KMP)** con UI nativa en SwiftUI para iOS.

### Arquitectura

```
┌──────────────────────────────────────────────────┐
│                  vivid-app/                       │
├──────────────┬────────────────┬──────────────────┤
│   :app       │   :shared      │   iosApp/        │
│  (Android)   │   (KMP)        │   (iOS/SwiftUI)  │
│              │                │                  │
│ Jetpack      │ Modelos        │ SwiftUI Views    │
│ Compose UI   │ Repositorios   │ AVFoundation     │
│ CameraX      │ (interfaces)   │ Apple Auth       │
│ Media3       │ Utilidades     │ Kingfisher       │
│ Hilt         │ expect/actual  │                  │
│              │                │                  │
│              │ ┌──────────┐   │                  │
│              │ │commonMain│   │                  │
│              │ │  ~45%    │   │                  │
│              │ └──────────┘   │                  │
│              │ ┌────┐ ┌────┐  │                  │
│              │ │andr│ │ ios│  │                  │
│              │ │oid │ │Main│  │                  │
│              │ └────┘ └────┘  │                  │
└──────────────┴────────────────┴──────────────────┘
         ↓              ↓                ↓
    Google Play     Firebase         App Store
                   Cloudflare
                   Backblaze B2
```

### ¿Qué se comparte?

| Componente | ¿Compartido? | Notas |
|---|---|---|
| **Modelos de datos** | ✅ Sí | `Message`, `Post`, `Reel`, `Story`, `User`, `Chat`, etc. |
| **Interfaces de repositorio** | ✅ Sí | `ChatRepository`, `FollowRepository`, `ContentRepository`, etc. |
| **Lógica de negocio** | ✅ Sí | Validaciones, formateo, agrupación |
| **Utilidades** | ✅ Sí | `TimeFormatter`, `NetworkUtils`, UUID, Base64 |
| **UI / Vistas** | ❌ No | Jetpack Compose (Android) / SwiftUI (iOS) |
| **Cámara/Video** | ❌ No | CameraX (Android) / AVFoundation (iOS) |
| **DI** | ⚠️ Parcial | Hilt (Android) / Manual (iOS) |
| **Push notifications** | ⚠️ Parcial | FCM en ambas, pero SDK nativos |
| **Firebase SDK** | ⚠️ Parcial | Mismas APIs pero SDKs nativos diferentes |

### Estructura del módulo shared

```
shared/
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/com/vivid/shared/
    │   ├── model/
    │   │   ├── Message.kt          ← Modelo de mensaje + MessageType enum
    │   │   ├── Post.kt             ← Post + Reel
    │   │   ├── Story.kt            ← Story + StoryGroup + groupStoriesByUser()
    │   │   ├── User.kt             ← User + UserPreview
    │   │   └── Chat.kt             ← Chat + FollowRelationshipState + FollowActionResult
    │   ├── repository/
    │   │   ├── AuthRepository.kt    ← Auth + UserRepository interfaces
    │   │   ├── ChatRepository.kt    ← Contrato de mensajería
    │   │   ├── ContentRepository.kt ← Contrato de posts/reels
    │   │   ├── FollowRepository.kt  ← Contrato de follow/bloqueos
    │   │   ├── StorageProvider.kt   ← Contrato de almacenamiento B2
    │   │   └── StoryRepository.kt   ← Contrato de stories
    │   ├── di/
    │   │   └── SharedContainer.kt   ← Contenedor de dependencias compartido
    │   └── util/
    │       ├── NetworkUtils.kt      ← Backoff exponencial, detección de errores
    │       ├── Platform.kt          ← expect declarations (Platform, Clock, Log, UUID, Base64)
    │       └── TimeFormatter.kt     ← Formato relativo y duración
    ├── androidMain/kotlin/com/vivid/shared/
    │   └── Platform.android.kt      ← actual implementations (Logcat, System.currentTimeMillis)
    ├── iosMain/kotlin/com/vivid/shared/
    │   └── Platform.ios.kt          ← actual implementations (os_log, NSDate)
    └── commonTest/kotlin/com/vivid/shared/
        └── SharedModelsTest.kt      ← Tests que corren en ambas plataformas
```

### Estructura del proyecto iOS

```
iosApp/
├── Package.swift                     ← SPM para el framework Shared
├── Podfile                           ← Firebase + GoogleSignIn + Kingfisher
└── iosApp/
    ├── Info.plist                    ← Permisos, URL schemes, push
    ├── VividApp.swift                ← @main, configuración Firebase
    ├── Navigation/
    │   └── MainTabView.swift         ← TabView principal (5 tabs)
    ├── Theme/
    │   └── VividTheme.swift          ← Colores, gradientes, tipografía, modificadores
    ├── ViewModels/
    │   ├── AppState.swift            ← Estado global (auth, navegación, tab)
    │   └── FeedViewModel.swift       ← VM del feed + TimeAgoFormatter
    ├── Views/
    │   ├── RootView.swift            ← Router: Auth ↔ Main ↔ Splash
    │   ├── Auth/AuthView.swift       ← Login con Apple + Google + partículas
    │   ├── Feed/FeedView.swift       ← Stories bar + Post cards + empty state
    │   ├── Explore/ExploreView.swift ← Búsqueda + grid de contenido
    │   ├── Profile/ProfileView.swift ← Header + stats + tabs + grid
    │   ├── Reels/ReelsView.swift     ← Vertical pager + overlay actions
    │   ├── Messages/ChatListView.swift ← Lista de chats + Chat individual
    │   └── Settings/SettingsView.swift ← Secciones + cerrar sesión + eliminar
    └── Services/
        └── SharedBridge.swift        ← Adaptadores Kotlin ↔ Swift
```

## 🚀 Setup para desarrollo

### Requisitos

- **Android**: Android Studio Ladybug+, JDK 17, Android SDK 37
- **iOS**: macOS 14+, Xcode 15.4+, CocoaPods, iOS 16+ deployment target
- **Ambos**: Gradle 9.3.1 (incluido en wrapper)

### Compilar el módulo shared

```bash
# Generar framework iOS para simulador (Apple Silicon)
cd vivid-app
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Generar framework iOS para dispositivo real
./gradlew :shared:linkReleaseFrameworkIosArm64

# Compilar tests compartidos (corren en JVM)
./gradlew :shared:allTests
```

### Configurar iOS

```bash
cd vivid-app/iosApp

# Instalar dependencias
pod install

# Abrir en Xcode (usar .xcworkspace, NO .xcodeproj)
open iosApp.xcworkspace
```

### Firebase iOS

1. Crear un proyecto iOS en la Firebase Console (mismo proyecto que Android)
2. Descargar `GoogleService-Info.plist` y colocarlo en `iosApp/iosApp/`
3. Reemplazar `YOUR_CLIENT_ID` en `Info.plist` con el `REVERSED_CLIENT_ID` del plist
4. Habilitar: Authentication (Google + Apple), Firestore, Storage, Messaging, Crashlytics

## 📝 Plan de migración por fases

### Fase 1: Foundation ✅ (este commit)
- [x] Módulo `:shared` con modelos de datos
- [x] Interfaces de repositorio en `commonMain`
- [x] Utilidades compartidas (TimeFormatter, NetworkUtils, Platform)
- [x] Implementaciones `expect/actual` para Android e iOS
- [x] Tests compartidos
- [x] Estructura del proyecto iOS con SwiftUI
- [x] Tema de diseño iOS (VividTheme)
- [x] Pantallas principales en SwiftUI (Feed, Profile, Reels, Messages, Auth, Explore, Settings)

### Fase 2: Implementaciones de repositorio
- [ ] Implementar repositorios Android que usen las interfaces del shared module
- [ ] Migrar `ChatRepository` Android → usa interfaz compartida
- [ ] Migrar `FollowRepository` Android → usa interfaz compartida
- [ ] Migrar `ContentRepository` Android → usa interfaz compartida
- [ ] Implementar repositorios iOS con Firebase iOS SDK
- [ ] SharedContainer inicialización en ambas plataformas

### Fase 3: Features iOS nativas
- [ ] Cámara con AVFoundation (fotos + video)
- [ ] Editor de video (trim + filters con AVFoundation)
- [ ] Grabación de notas de voz (AVAudioRecorder)
- [ ] Reproductor de video (AVPlayer)
- [ ] Subida de archivos (Firebase Storage iOS)
- [ ] Push notifications (APNs + FCM)
- [ ] Sign in with Apple (AuthenticationServices)
- [ ] Google Sign-In (GoogleSignIn SDK)

### Fase 4: Polish y lanzamiento
- [ ] Animaciones y transiciones compartidas
- [ ] Haptics (CoreHaptics)
- [ ] Accesibilidad (VoiceOver)
- [ ] Localización (español + inglés)
- [ ] TestFlight beta
- [ ] App Store submission

## 🔄 Convenciones de código

### Modelos compartidos

```kotlin
// commonMain - Modelo compartido
@Serializable
data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val type: MessageType = MessageType.TEXT,
    // ...
)

// commonMain - Interfaz de repositorio
interface ChatRepository {
    fun getMessagesFlow(chatId: String): Flow<List<Message>>
    suspend fun sendMessage(chatId: String, text: String, receiverId: String)
}

// androidMain - Implementación Android
class AndroidChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messageDao: MessageDao
) : ChatRepository {
    override fun getMessagesFlow(chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChat(chatId).map { entities ->
            entities.map { it.toSharedModel() }
        }
    }
}
```

### Consumo en iOS (Swift)

```swift
// ViewModel de iOS consume el repositorio Kotlin
class ChatViewModel: ObservableObject {
    private let repository: ChatRepository  // Interfaz Kotlin

    func loadMessages(chatId: String) {
        // Kotlin Flow → Swift AsyncSequence
        Task {
            for await messages in repository.getMessagesFlow(chatId: chatId) {
                await MainActor.run {
                    self.messages = messages.map { MessageUI(from: $0) }
                }
            }
        }
    }
}
```

## ⚠️ Consideraciones

1. **Kotlin Flow ↔ Swift async**: Los `Flow<List<T>>` de Kotlin se consumen en Swift como `AsyncSequence`. El bridge `SharedBridge` facilita esta conversión.

2. **Coroutines ↔ GCD**: Kotlin maneja la concurrencia con coroutines. En iOS, el framework KMP usa dispatch queues automáticamente.

3. **Memory management**: En iOS, tener cuidado con retain cycles al pasar lambdas Swift a Kotlin. Usar `[weak self]` en closures.

4. **Firebase**: Aunque las APIs son similares, Firebase Android y Firebase iOS son SDKs diferentes. Los repositorios de cada plataforma usan su SDK nativo.

5. **Room vs SwiftData**: Room (Android) y SwiftData/CoreData (iOS) no se comparten. Las entidades de Room se quedan en Android; iOS usa su propio almacenamiento local.

6. **Versiones mínimas**: Android minSdk 26 (Android 8.0+), iOS deployment target 16.0+.
