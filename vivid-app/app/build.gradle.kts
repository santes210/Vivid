import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // Kotlin Android es integrado en AGP 9; aplicar el plugin antiguo falla.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

configurations.all {
    resolutionStrategy.force("com.squareup:javapoet:1.13.0")
}

hilt {
    enableAggregatingTask = false
}

// Room exporta el esquema JSON a app/schemas/ en cada build.
// Commitear esos archivos permite validar migraciones con tests
// (MigrationTestHelper) antes de que un usuario sufra un crash por
// un cambio de esquema sin migración.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// =========================================================
//  CONFIGURACIÓN DEL BACKEND (sin credenciales en el APK)
// =========================================================
// El Cloudflare Worker actúa como backend único: envía las notificaciones push
// y hace de broker con Backblaze B2. Las claves de B2 viven como secretos
// cifrados dentro del Worker, NUNCA dentro del APK.
//
// URL pública del Worker, por ejemplo https://vivid-push.<cuenta>.workers.dev.
//   ./gradlew assembleRelease -PvividWorkerUrl=https://...
// o con la variable de entorno VIVID_WORKER_URL en GitHub Actions.
//
// Se acepta el nombre antiguo (vividPushWorkerUrl / VIVID_PUSH_WORKER_URL) para
// no romper configuraciones existentes.
val WORKER_URL_VALUE = providers.gradleProperty("vividWorkerUrl")
    .orElse(providers.environmentVariable("VIVID_WORKER_URL"))
    .orElse(providers.gradleProperty("vividPushWorkerUrl"))
    .orElse(providers.environmentVariable("VIVID_PUSH_WORKER_URL"))
    .orElse("")
    .get()
    .trimEnd('/')

// Cert pinning del Worker (defensa en profundidad, OPCIONAL).
// Formato: "sha256/<base64>;sha256/<base64>" (pin primario + pin de respaldo).
//   ./gradlew assembleRelease -PvividWorkerPin='sha256/AAAA...;sha256/BBBB...'
// o con la variable de entorno VIVID_WORKER_PIN en GitHub Actions.
// Vacío (default) = sin pinning. Recomendado activarlo SOLO cuando el Worker
// tenga un dominio propio fijo: los certificados de *.workers.dev rotan y un
// pin desactualizado dejaría la app sin conexión. Cómo generar los pins:
// ver SECURITY.md → "Cert pinning".
val WORKER_PIN_VALUE = providers.gradleProperty("vividWorkerPin")
    .orElse(providers.environmentVariable("VIVID_WORKER_PIN"))
    .orElse("")
    .get()
    .trim()

// =========================================================
//  VERSIONADO (esquema MAJOR.MINOR.PATCH-build)
// =========================================================
// versionName = "<base>[-<build>]", p. ej. "2.2.0-7":
//   MAJOR.MINOR.PATCH → cambios de producto, se editan A MANO aquí
//                       (subir MINOR por features, PATCH por fixes).
//   -<build>           → sufijo automático = versionCode. En CI el
//                       versionCode es GITHUB_RUN_NUMBER (crece con cada
//                       ejecución); en local se puede fijar con:
//                         ./gradlew assembleRelease -PvividVersionCode=1234
//                       Sin CI ni propiedad explícita no se muestra el
//                       sufijo para no simular builds que no existen.
// Changelog para usuarios: com.vivid.app.util.VividChangelog (en código,
// sin .md) → se muestra en Ajustes → Acerca de → Novedades.
val VIVID_VERSION_BASE = "2.2.0"

// En GitHub Actions, GITHUB_RUN_NUMBER crece automáticamente en cada ejecución del workflow.
// Para una compilación manual se puede usar: ./gradlew assembleRelease -PvividVersionCode=1234
val configuredVersionCode = providers.gradleProperty("vividVersionCode")
    .orElse(providers.environmentVariable("VIVID_VERSION_CODE"))
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
    .orNull
val vividVersionCode = configuredVersionCode?.let { rawValue ->
    rawValue.toIntOrNull()
        ?.takeIf { it in 1..2_100_000_000 }
        ?: error("vividVersionCode debe ser un entero entre 1 y 2100000000 (recibido: '$rawValue')")
} ?: 2
// El sufijo -<build> solo aparece cuando hay un número de build real
// (CI o -PvividVersionCode). El valor por defecto local (2) lo omite.
val vividVersionName = if (vividVersionCode > 2) "$VIVID_VERSION_BASE-$vividVersionCode" else VIVID_VERSION_BASE
logger.lifecycle("Vivid versionCode: $vividVersionCode")
logger.lifecycle("Vivid versionName: $vividVersionName")

// La firma de release se inyecta desde GitHub Actions o desde variables locales.
// Si no están presentes, Gradle aún puede compilar un release sin firmar; el workflow de
// release valida las cuatro credenciales antes de empezar para no publicar un APK inválido.

// Subida automática de mapping.txt a Crashlytics. OFF por defecto: el task
// uploadCrashlyticsMappingFileRelease necesita credenciales de Firebase
// (service account) y SIN ellas falla el build. Se activa solo donde existan:
//   ./gradlew assembleRelease -PuploadCrashlyticsMapping=true
// o la variable de entorno UPLOAD_CRASHLYTICS_MAPPING=true en CI.
// Sin esto, mapping.txt se conserva como artefacto de build-apk.yml y se
// puede subir manualmente (Firebase CLI: firebase crashlytics:mappingfile:upload).
val uploadCrashlyticsMappingEnabled = providers.gradleProperty("uploadCrashlyticsMapping")
    .orElse(providers.environmentVariable("UPLOAD_CRASHLYTICS_MAPPING"))
    .orNull == "true"

// Válvula de escape puntual para lint (ver bloque `lint { }` más abajo).
//   ./gradlew assembleRelease -PvividLintLenient=true
// o VIVID_LINT_LENIENT=true. Por defecto lint SÍ rompe el build.
val lintLenient = providers.gradleProperty("vividLintLenient")
    .orElse(providers.environmentVariable("VIVID_LINT_LENIENT"))
    .orNull == "true"

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.vivid.app"
    // Ola 1 (Play): targetSdk 36, requerido para actualizaciones publicadas
    // desde el 31 de agosto de 2026. Se conserva para aislar los cambios de
    // comportamiento en runtime de Android 17 hasta completar su QA.
    //
    // Ola 2 (modernización): Compose 1.12 compila contra API 37. AGP 9.1.1
    // soporta esa API con Gradle 9.3.1 y JDK 17.
    compileSdk = 37
    // AGP 9.1.1 usa 36.0.0 como Build Tools por defecto también para API 37.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.vivid.app"
        minSdk = 26
        targetSdk = 36
        versionCode = vividVersionCode
        versionName = vividVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Una sola fuente de verdad para la URL del Worker. PUSH_WORKER_URL se
        // mantiene como alias para el código de notificaciones ya existente.
        buildConfigField("String", "WORKER_URL", "\"$WORKER_URL_VALUE\"")
        buildConfigField("String", "PUSH_WORKER_URL", "\"$WORKER_URL_VALUE\"")
        // Pins SHA-256 opcionales del dominio del Worker (ver arriba). Vacío
        // por defecto: sin pinning. WorkerStorageProvider los aplica con OkHttp.
        buildConfigField("String", "WORKER_PIN", "\"$WORKER_PIN_VALUE\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseKeystorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }

        // Keystore de debug COMPARTIDO y commiteado (contraseñas estándar
        // "android", no guarda secretos: es la práctica habitual en AOSP).
        //
        // ¿Por qué? El workflow "Build Vivid APK" compila assembleDebug en
        // un runner efímero: sin esto, Gradle genera un debug.keystore
        // NUEVO en cada build y su SHA-1 cambia cada vez, con lo que
        // "Continuar con Google" jamás puede funcionar en esos APKs (Google
        // compara el SHA-1 del APK contra los registrados en Firebase).
        // Con este keystore fijo, debug local y debug de CI comparten
        // siempre esta huella:
        //   SHA-1:   DA:E6:2A:08:CA:7C:E9:D2:FC:E5:7A:4C:A3:5C:F8:73:49:B0:0B:C6
        //   SHA-256: 97:63:0F:90:56:17:E2:BD:7B:84:5F:6B:80:66:E8:0E:25:08:FE:3A:39:53:8A:74:F1:E7:61:7E:FA:13:F2:9B
        // (Regístralas también en Firebase Console → Tus apps → Huellas.)
        // Se puede verificar con: python3 scripts/apk-sha1.py vivid-app/app/debug.keystore
        getByName("debug") {
            storeFile = file("debug.keystore")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // Los buildConfigField viven en defaultConfig: aplican a todos los
        // buildTypes sin repetirse.
        debug {
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Crashlytics: desde el plugin 2.6.0 la extensión ya NO se registra
            // a nivel de proyecto, por eso el bloque `firebaseCrashlytics { }`
            // de nivel superior falla con "Unresolved reference / receiver type
            // mismatch" en Kotlin DSL. La forma soportada (documentación oficial
            // de Firebase) es configure<CrashlyticsExtension> DENTRO del build
            // type. La subida del mapping va desactivada por defecto (ver
            // uploadCrashlyticsMappingEnabled arriba) para que el build release
            // nunca falle por falta de credenciales de Firebase.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = uploadCrashlyticsMappingEnabled
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        // Lint vuelve a estar ACTIVO en release.
        //
        // Antes estaba apagado (abortOnError = false / checkReleaseBuilds = false)
        // por un bug de :app:lintVitalAnalyzeRelease del toolchain anterior.
        // AGP 9.1.1 + Kotlin 2.4 lo corrigen; el proyecto tampoco usa LiveData,
        // por lo que no se mantiene ningún `disable` ni interruptor permanente
        // que silencie errores de release.
        abortOnError = true
        checkReleaseBuilds = true
        // Los warnings siguen siendo warnings: solo los errores rompen el build.
        warningsAsErrors = false
        // Reportes legibles (los sube el workflow cuando el build falla).
        htmlReport = true
        xmlReport = true
        sarifReport = false
        // Válvula de escape para una release urgente:
        //   ./gradlew assembleRelease -PvividLintLenient=true
        // o VIVID_LINT_LENIENT=true en el entorno. No usar en CI: es para
        // desbloquear a mano, no para volver a apagar lint de forma permanente.
        if (lintLenient) {
            abortOnError = false
            checkReleaseBuilds = false
        }
    }
}

// AGP 9 integra la compilación Kotlin. `kotlinOptions` y
// `composeOptions.kotlinCompilerExtensionVersion` pertenecían al plugin
// kotlin-android anterior; Compose Compiler queda alineado a Kotlin 2.4 por
// el plugin org.jetbrains.kotlin.plugin.compose.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)

    // Coil 3 requiere un artefacto de red explícito; este proyecto carga
    // fotos y avatares remotos mediante HTTPS/OkHttp.
    implementation(libs.coil.compose)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    // Crash reporting + telemetría de rendimiento (gratis en el plan Spark).
    // Analytics es la base de métricas de Crashlytics (crash-free users).
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)
    // Login con Google: Credential Manager (androidx.credentials) + googleid.
    // Sustituye a com.google.android.gms:play-services-auth / GoogleSignIn,
    // que Google dejó deprecado y está apagando. play-services-auth sigue
    // llegando de forma transitiva vía credentials-play-services-auth.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.androidx.camera.video)

    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.otaliastudios:transcoder:0.10.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.paging.common)

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
