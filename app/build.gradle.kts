plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.pulseplay.mpxvqr"
    minSdk = 24
    targetSdk = 36
    val resolvedVersionCode = (project.findProperty("APP_VERSION_CODE")?.toString()?.toIntOrNull()
      ?: System.getenv("APP_VERSION_CODE")?.toIntOrNull()
      ?: 1).coerceAtLeast(1)
    val resolvedVersionName = (project.findProperty("APP_VERSION_NAME")?.toString()
      ?: System.getenv("APP_VERSION_NAME")
      ?: "1.0.0").removePrefix("v").trim().ifEmpty { "1.0.0" }
    val resolvedGitHubRepo = (project.findProperty("GITHUB_REPOSITORY")?.toString()
      ?: System.getenv("GITHUB_REPOSITORY")
      ?: "").trim()

    versionCode = resolvedVersionCode
    versionName = resolvedVersionName
    buildConfigField("String", "GITHUB_REPOSITORY", "\"$resolvedGitHubRepo\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val envKeystorePath = System.getenv("KEYSTORE_PATH")?.trim()?.takeIf { it.isNotEmpty() }
      val defaultUploadKey = file("${rootDir}/my-upload-key.jks")
      val defaultExistingKey = file("${rootDir}/debug.keystore")
      val isGitHubActions = System.getenv("GITHUB_ACTIONS") == "true"
      val resolvedKeystoreFile = when {
        envKeystorePath != null -> file(envKeystorePath)
        defaultUploadKey.exists() || isGitHubActions -> defaultUploadKey
        else -> defaultExistingKey
      }
      val useLocalExistingKeyDefaults =
        !isGitHubActions && envKeystorePath == null && !defaultUploadKey.exists()

      storeFile = resolvedKeystoreFile
      storePassword = (System.getenv("KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD"))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: if (useLocalExistingKeyDefaults) "android" else ""
      val resolvedKeyAlias = (project.findProperty("KEY_ALIAS")?.toString() ?: System.getenv("KEY_ALIAS"))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
      keyAlias = resolvedKeyAlias ?: if (useLocalExistingKeyDefaults) "androiddebugkey" else "upload"
      keyPassword = (
        System.getenv("KEY_PASSWORD")
          ?: System.getenv("KEYSTORE_PASSWORD")
          ?: System.getenv("STORE_PASSWORD")
      )?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: if (useLocalExistingKeyDefaults) "android" else ""
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperty("java.awt.headless", "true")
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.google.play.app.update)
  implementation(libs.google.play.app.update.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  "ksp"(libs.androidx.room.compiler)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
