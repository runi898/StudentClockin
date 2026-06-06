import java.util.Properties

val localBuildConfigFile = rootProject.file("gradle-local.properties")
val localBuildConfig = Properties().apply {
    if (localBuildConfigFile.exists()) {
        localBuildConfigFile.inputStream().use(::load)
    }
}

fun localBuildConfigValue(name: String): String? {
    return localBuildConfig.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
}

val supabaseUrlProvider = providers.gradleProperty("studentclockinSupabaseUrl")
    .orElse(providers.environmentVariable("STUDENTCLOCKIN_SUPABASE_URL"))
val supabasePublicKeyProvider = providers.gradleProperty("studentclockinSupabasePublicKey")
    .orElse(providers.environmentVariable("STUDENTCLOCKIN_SUPABASE_PUBLIC_KEY"))
val supabaseLegacyKeyProvider = providers.gradleProperty("studentclockinSupabaseAnonKey")
    .orElse(providers.environmentVariable("STUDENTCLOCKIN_SUPABASE_ANON_KEY"))
val supabaseUrl = supabaseUrlProvider
    .orElse(localBuildConfigValue("studentclockinSupabaseUrl") ?: "https://demo.supabase.co")
    .get()
val supabaseApiKey = supabasePublicKeyProvider
    .orElse(supabaseLegacyKeyProvider)
    .orElse(
        localBuildConfigValue("studentclockinSupabasePublicKey")
            ?: localBuildConfigValue("studentclockinSupabaseAnonKey")
            ?: "demo-public-key"
    )
    .get()
val hasConfiguredBackend = (
    supabaseUrlProvider.isPresent ||
        supabasePublicKeyProvider.isPresent ||
        supabaseLegacyKeyProvider.isPresent ||
        localBuildConfigValue("studentclockinSupabaseUrl") != null &&
        (
            localBuildConfigValue("studentclockinSupabasePublicKey") != null ||
                localBuildConfigValue("studentclockinSupabaseAnonKey") != null
            )
    )

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.familycheckin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.familycheckin"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_API_KEY", "\"$supabaseApiKey\"")
        buildConfigField(
            "boolean",
            "DEMO_MODE",
            if (hasConfiguredBackend) {
                "false"
            } else {
                "true"
            }
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
