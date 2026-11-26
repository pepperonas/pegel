import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Signing: locally from keystore.properties (gitignored), on CI from env secrets.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasSigning = keystorePropsFile.exists() || System.getenv("KEYSTORE_PASSWORD") != null

android {
    namespace = "io.celox.pegel"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.celox.pegel"
        // 28, not the house default 26: DynamicsProcessing (the 10-band engine) arrived in Android 9.
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.1"
        vectorDrawables { useSupportLibrary = true }
        // The UI ships in English (default) and German. Without this filter the Material libraries
        // would bring their own French, Spanish, … texts into an otherwise English screen.
        resourceConfigurations += listOf("en", "de")
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                if (keystorePropsFile.exists()) {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                } else {
                    storeFile = rootProject.file(System.getenv("KEYSTORE_FILE") ?: "release.jks")
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
    }

    buildFeatures { compose = true; buildConfig = true }
    lint {
        // AGP 8.7.3's bundled lint crashes against the AndroidX artifacts pulled by material3
        // 1.5.0-alpha18 (same as Brutus and Kleene Petze) — the app uses no LiveData.
        disable += "NullSafeMutableLiveData"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// The drift guards (ReadmeBadgesTest, ReadmeSyncTest, ChangelogTest, ManifestTest, …) read files
// outside the test classpath. Declared as inputs, otherwise Gradle calls the test task up to date
// after a README or workflow change and the guard never runs.
tasks.withType<Test>().configureEach {
    inputs.files(
        rootProject.files("README.md", "README.de.md", "CHANGELOG.md", "CHANGELOG.de.md", "LICENSE")
    ).withPropertyName("docs").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file(".github")).withPropertyName("github").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("build.gradle.kts").withPropertyName("buildFile").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("src/main/AndroidManifest.xml").withPropertyName("manifest").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/assets").withPropertyName("assets").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("schemas").withPropertyName("schemas").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/res").withPropertyName("res").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/java").withPropertyName("sources").withPathSensitivity(PathSensitivity.RELATIVE)
}

// Room writes its expected schema JSON here (checked in) — the reference for migrations.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    // BOM 2026.06.01 maps material3 1.4.0, where the Expressive APIs are still internal, so
    // material3 is pinned past the BOM like Brutus, Flipper and Kleene Petze (alpha19+ needs
    // compileSdk 37 + AGP 9.1).
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Opt-in daily update check. 2.10.x is the newest line still on compileSdk 35.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // android.jar only ships stubs of org.json; the real one lets the JSON codecs run on the JVM.
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
}
