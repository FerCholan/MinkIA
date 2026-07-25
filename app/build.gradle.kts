plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.moviles.minkia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moviles.minkia"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    androidResources {
        // El .tflite debe quedar SIN comprimir para poder mapearlo en memoria.
        noCompress.add("tflite")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // MVVM
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.fragment.ktx)

    // Navigation Component: el grafo (res/navigation) declara las pantallas y las
    // transiciones. Sustituye a los Intent entre Activities y al FragmentTransaction
    // manual del bottom nav.
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.taptargetview)
    implementation(libs.shimmer)

    // Inferencia on-device del modelo YOLOv8 (LiteRT, sucesor de TFLite, 16 KB aligned)
    implementation(libs.litert)

    // Firebase (la BoM gestiona las versiones de cada librería)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services) // .await() sobre las Task de Firebase

    // Subida de fotos a Cloudinary (free tier, sin plan de pago) vía multipart
    implementation(libs.okhttp)

    // Carga de imágenes remotas (la foto del reporte desde Cloudinary): Coil,
    // Kotlin-first, liviano. Lo usa el FotoReporteView reutilizable.
    implementation(libs.coil)

    // Mapa real + mapa de calor de los focos (Google Maps)
    implementation(libs.play.services.maps)
    implementation(libs.maps.utils)

    // Google Sign-In con Credential Manager (lo moderno)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
