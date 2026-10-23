import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val sdkRootPath = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME") ?: ""
val hasNdkAndCmake = if (sdkRootPath.isNotEmpty()) {
  val ndkDir = File(sdkRootPath, "ndk")
  val cmakeDir = File(sdkRootPath, "cmake")
  ndkDir.exists() && (ndkDir.listFiles()?.isNotEmpty() == true) &&
      cmakeDir.exists() && (cmakeDir.listFiles()?.isNotEmpty() == true)
} else {
  false
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.ebsynthstudio.vfxkqz"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0-PRO"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    if (hasNdkAndCmake) {
      externalNativeBuild {
        cmake {
          cppFlags += listOf("-std=c++17", "-O3", "-ffast-math", "-DNDEBUG", "-fexceptions")
          arguments += listOf("-DANDROID_STL=c++_shared")
        }
      }
      ndk {
        abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
      }
    }
  }

  if (hasNdkAndCmake) {
    externalNativeBuild {
      cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
      }
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Automated task: copyDebugApk bound to assembleDebug
// Creates rootProject/debug/ directory and copies the generated debug APK as debug/debug.apk
val rootDebugDir = File(rootProject.projectDir, "debug")
val debugApkOutputsDirProvider = layout.buildDirectory.dir("outputs/apk/debug")

tasks.register("copyDebugApk") {
  group = "build"
  description = "Copies the generated debug APK to the root debug/debug.apk path automatically."
  val targetDebugDir = rootDebugDir
  val apkDirProvider = debugApkOutputsDirProvider
  doLast {
    if (!targetDebugDir.exists()) {
      targetDebugDir.mkdirs()
    }
    val apkSearchRoot = apkDirProvider.get().asFile
    val sourceApk = if (apkSearchRoot.exists()) {
      apkSearchRoot.walkTopDown().firstOrNull { it.isFile && it.extension == "apk" }
    } else {
      null
    }
    if (sourceApk != null && sourceApk.exists()) {
      val destinationApk = File(targetDebugDir, "debug.apk")
      sourceApk.copyTo(destinationApk, overwrite = true)
      println("EbSynth Build Automation: Copied ${sourceApk.name} -> ${destinationApk.absolutePath}")
    } else {
      println("EbSynth Build Automation: No debug APK found in ${apkSearchRoot.absolutePath} yet.")
    }
  }
}

tasks.matching { it.name == "assembleDebug" || it.name == "packageDebug" }.configureEach {
  finalizedBy("copyDebugApk")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.firestore)
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
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
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
