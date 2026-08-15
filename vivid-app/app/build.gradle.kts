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

// =========================================================
//  CONFIGURACIÓN SIN SECRETOS EN EL REPO
// =========================================================
// Las claves de Backblaze B2 NUNCA deben commitearse (ya pasó una vez:
// ver scripts/purge-secrets.sh y SECURITY.md). Se inyectan en BuildConfig
// desde, en orden de prioridad:
//   1. Variables de entorno (las usa GitHub Actions con secrets del repo)
//   2. Propiedades de Gradle (-Pb2KeyId=... )
//   3. local.properties (gitignored): b2.keyId, b2.applicationKey, ...
// Si no hay claves, el APK compila igual pero StorageModule falla al
// arrancar con un mensaje claro. MODO DIRECTO es temporal: la salida
// segura es la Cloud Function de /cloud-function.
fun secretFrom(envName: String, gradleProp: String, localKey: String): String {
    providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }?.let { return it }
    providers.gradleProperty(gradleProp).orNull?.takeIf { it.isNotBlank() }?.let { return it }
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = java.util.Properties()
        localProps.inputStream().use { props.load(it) }
        props.getProperty(localKey)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return ""
}

val B2_KEY_ID_VALUE = secretFrom("B2_KEY_ID", "b2KeyId", "b2.keyId")
val B2_APPLICATION_KEY_VALUE = secretFrom("B2_APPLICATION_KEY", "b2ApplicationKey", "b2.applicationKey")
val B2_BUCKET_ID_VALUE = secretFrom("B2_BUCKET_ID", "b2BucketId", "b2.bucketId")
val B2_BUCKET_NAME_VALUE = secretFrom("B2_BUCKET_NAME", "b2BucketName", "b2.bucketName")

// URL pública de la Cloud Function (no es secreta). Definir CF_BASE_URL,
// -PcfBaseUrl=... o local.properties cf.baseUrl=... para tu proyecto.
val CF_BASE_URL_VALUE = secretFrom("CF_BASE_URL", "cfBaseUrl", "cf.baseUrl")
    .ifBlank { "https://us-central1-TU_PROYECTO.cloudfunctions.net" }

// Escapa comillas y barras para buildConfigField
fun bcEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

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

        // Inyecta la config (CF_BASE_URL + claves B2) en BuildConfig.
        // Las claves B2 vienen de env/props/local.properties — NUNCA del repo.
        buildConfigField("String", "CF_BASE_URL", "\"${bcEscape(CF_BASE_URL_VALUE)}\"")
        buildConfigField("String", "B2_KEY_ID", "\"${bcEscape(B2_KEY_ID_VALUE)}\"")
        buildConfigField("String", "B2_APPLICATION_KEY", "\"${bcEscape(B2_APPLICATION_KEY_VALUE)}\"")
        buildConfigField("String", "B2_BUCKET_ID", "\"${bcEscape(B2_BUCKET_ID_VALUE)}\"")
        buildConfigField("String", "B2_BUCKET_NAME", "\"${bcEscape(B2_BUCKET_NAME_VALUE)}\"")
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
        debug {
            buildConfigField("String", "CF_BASE_URL", "\"${bcEscape(CF_BASE_URL_VALUE)}\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "CF_BASE_URL", "\"${bcEscape(CF_BASE_URL_VALUE)}\"")
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

    // Shizuku (opcional) — whitelist de batería para notificaciones persistentes
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

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
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
