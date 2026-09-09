import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val verifyVoiceAssets by tasks.registering {
    doLast {
        val root = rootProject.file("tools/.cache/voice/runtime")
        val required = listOf("sherpa-onnx.aar", "assets/asr/encoder-epoch-99-avg-1.int8.onnx",
            "assets/asr/decoder-epoch-99-avg-1.int8.onnx", "assets/asr/joiner-epoch-99-avg-1.int8.onnx", "assets/asr/tokens.txt")
        check(required.all { root.resolve(it).isFile }) { "Run python tools/prepare_voice.py before building (bundled offline ASR assets missing)." }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyVoiceAssets) }

val prepareLegalAssets by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("generated/legalAssets/legal"))
    from(rootProject.file("LICENSE"))
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("PRIVACY.md"))
    from(rootProject.file("docs/licenses"))
}
tasks.named("preBuild").configure { dependsOn(prepareLegalAssets) }

android {
    namespace = "com.chacha.jadeime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chacha.jadeime"
        minSdk = 29
        targetSdk = 35
        versionCode = 10000
        versionName = "1.0.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    val signingPropertiesFile = file(System.getenv("JADE_SIGNING_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.jadeboard/release-signing.properties")
    if (signingPropertiesFile.isFile) {
        val signingProperties = Properties().apply { signingPropertiesFile.inputStream().use { load(it) } }
        signingConfigs.create("distribution") {
            storeFile = file(signingProperties.getProperty("storeFile"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
        buildTypes["release"].signingConfig = signingConfigs.getByName("distribution")
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
        buildConfig = false
    }

    sourceSets["main"].assets.srcDir(rootProject.file("tools/.cache/voice/runtime/assets"))
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/legalAssets"))
    androidResources { noCompress += "onnx" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // JadeBoard is intentionally distributed only for arm64-v8a phones.
        disable += "ChromeOsAbiSupport"
    }
}

dependencies {
    implementation(files(rootProject.file("tools/.cache/voice/runtime/sherpa-onnx.aar")))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
