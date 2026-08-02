import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    jacoco
}

// Propiedades de local.properties (no versionado). Se lee una sola vez.
val propiedadesLocales: Properties = Properties().apply {
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use(::load)
}

/** Valor de local.properties, con respaldo en la variable de entorno homonima. */
fun secreto(clave: String): String? =
    propiedadesLocales.getProperty(clave) ?: System.getenv(clave)

// La API key de Maps se lee de local.properties (no versionado) o de la variable
// de entorno MAPS_API_KEY, para que un checkout limpio o un CI puedan construir.
val mapsApiKey: String = secreto("MAPS_API_KEY") ?: ""

// Firma de release. El keystore NO se versiona: si falta, la variante release se
// construye sin firmar (util para CI) en vez de romper el build de todo el proyecto.
val rutaKeystore: String? = secreto("MINKIA_KEYSTORE")
val hayKeystore: Boolean = rutaKeystore != null && rootProject.file(rutaKeystore).exists()

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

        // Genera @string/google_maps_key, que consume el manifest en
        // com.google.android.geo.API_KEY.
        resValue("string", "google_maps_key", mapsApiKey)
    }

    signingConfigs {
        if (hayKeystore) {
            create("release") {
                storeFile = rootProject.file(rutaKeystore!!)
                storePassword = secreto("MINKIA_KEYSTORE_PASSWORD")
                keyAlias = secreto("MINKIA_KEY_ALIAS")
                keyPassword = secreto("MINKIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Habilita la instrumentacion de JaCoCo sobre las pruebas unitarias:
            // sin esto no se genera el archivo .exec del que sale la cobertura.
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sin keystore la variante queda sin firmar, igual que antes. Con
            // keystore, assembleRelease produce un APK instalable.
            if (hayKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        // Necesario en AGP 9 para el resValue de google_maps_key.
        resValues = true
    }
    androidResources {
        // El .tflite debe quedar SIN comprimir para poder mapearlo en memoria.
        noCompress.add("tflite")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        // Las clases generadas por Kotlin/Android no siempre traen informacion de
        // ubicacion; sin esta bandera JaCoCo las descarta y la cobertura sale en 0.
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

/**
 * Cobertura de las pruebas unitarias. Se ejecuta con:
 *   gradlew jacocoTestReport
 * El reporte navegable queda en app/build/reports/jacoco/jacocoTestReport/html/.
 */
tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Cobertura de codigo de las pruebas unitarias (variante debug)."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // Se excluye el codigo GENERADO (ViewBinding, R, BuildConfig) y las propias
    // clases de prueba: medirlos inflaria la cifra sin decir nada del proyecto.
    val excluidos = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "**/databinding/**", "**/*Binding.*",
        "**/*_Factory.*", "**/*Companion*.*"
    )

    // OJO: AGP 9 compila Kotlin con su compilador incorporado y deja las clases en
    // intermediates/built_in_kotlinc/..., no en el tmp/kotlin-classes/ de las
    // versiones anteriores. Apuntar a la ruta vieja da un reporte vacio.
    val dirsClases = files(
        fileTree(
            layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
        ) { exclude(excluidos) },
        fileTree(
            layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
        ) { exclude(excluidos) }
    )
    classDirectories.setFrom(dirsClases)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(fileTree(layout.buildDirectory) { include("**/*.exec") })
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

    // Reintento de la cola offline garantizado por el sistema, incluso si el
    // proceso fue matado: el callback de red solo vive mientras la app vive.
    implementation(libs.androidx.work.runtime)

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
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
