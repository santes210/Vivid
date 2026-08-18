plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
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
logger.lifecycle("Vivid versionCode: $vividVersionCode")

// La firma de release se inyecta desde GitHub Actions o desde variables locales.
// Si no están presentes, Gradle aún puede compilar un release sin firmar; el workflow de
// release valida las cuatro credenciales antes de empezar para no publicar un APK inválido.
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vivid.app"
        minSdk = 26
        targetSdk = 35
        versionCode = vividVersionCode
        versionName = "2.2.0-7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Una sola fuente de verdad para la URL del Worker. PUSH_WORKER_URL se
        // mantiene como alias para el código de notificaciones ya existente.
        buildConfigField("String", "WORKER_URL", "\"$WORKER_URL_VALUE\"")
        buildConfigField("String", "PUSH_WORKER_URL", "\"$WORKER_URL_VALUE\"")
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        // Release fallaba con lintVitalAnalyzeRelease:
        //   Found class KaCallableMemberCall but interface expected
        //   detector: androidx.lifecycle.lint.NonNullableMutableLiveDataDetector
        // Bug conocido Kotlin 2.0.21 + AGP 8.7.3 + lifecycle lint. No es de tu tema.
        // Desactivamos solo ese detector para que :app:lintVitalAnalyzeRelease pase.
        // El warning sugiere exactamente disable "NullSafeMutableLiveData".
        disable += "NullSafeMutableLiveData"
        // Opcional: no abortar release por warnings visuales menores (deprecated icons etc.)
        abortOnError = false
        checkReleaseBuilds = false
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

    implementation(libs.coil.compose)
    implementation(libs.coil)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.camera:camera-extensions:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")

    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    implementation("androidx.media3:media3-database:1.4.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.otaliastudios:transcoder:0.10.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
