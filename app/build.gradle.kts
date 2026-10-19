import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // google-services parses app/google-services.json to wire Firebase. The
    // plugin block below applies conditionally so dev builds without the
    // file still compile (push notifications no-op when Beams isn't init'd).
}

// Release signing — see keystore.properties.sample. The .jks lives outside
// the repo; if keystore.properties is missing we skip the signing config so
// debug builds still work, and assembleRelease fails explicitly.
val keystoreProps: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { f -> Properties().also { p -> f.inputStream().use { p.load(it) } } }

// Local.properties values — Beams Instance ID is checked in there because
// it's an environment-specific ID (different per project / per developer).
val localProps: Properties = Properties().also { p ->
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { p.load(it) }
}
val beamsInstanceId: String = localProps.getProperty("BEAMS_INSTANCE_ID") ?: ""
// Cloudflared quick tunnel URL (debug only). Rotate by re-running the tunnel
// and pasting the new https://...trycloudflare.com/customers_api/v1/ value
// into local.properties as API_BASE_URL. Falls back to a placeholder so a
// fresh checkout still configures — runtime calls will fail loudly until
// it's set.
val debugApiBaseUrl: String = localProps.getProperty("API_BASE_URL")
    ?: "https://set-API_BASE_URL-in-local-properties.invalid/customers_api/v1/"

// Apply google-services only if google-services.json is present. Lets dev
// builds without Firebase configured still compile cleanly.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    namespace = "com.legumlex.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.legumlex.client"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.1"

        vectorDrawables { useSupportLibrary = true }

        // Bundle native debug symbols (from third-party .so libs) into
        // native-debug-symbols.zip alongside the AAB so Play Console can
        // symbolicate native crashes and ANRs.
        ndk { debugSymbolLevel = "FULL" }
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile     = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias      = keystoreProps.getProperty("keyAlias")
                keyPassword   = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Google OAuth Web Client ID — must match the audience your server validates
        // against (Lexon's lexon_gmail_oauth_client_id Perfex option). Same value
        // for debug and release; if you set up a separate dev OAuth client, override
        // in the debug block only.
        val googleWebClientId =
            "1065355012726-7c7j2ukjr5aoev1f4tlde7dekvg0jud7.apps.googleusercontent.com"

        debug {
            // API_BASE_URL is read from local.properties (see top of this file).
            // To rotate the cloudflared quick tunnel:
            //   ~/bin/cloudflared tunnel --url http://localhost:80 --http-host-header lex.dvl.to
            // The --http-host-header flag is REQUIRED — without it the tunnel
            // hits devilbox's default vhost and the API returns HTML, which
            // Moshi rejects with "malformed JSON at path $".
            // Paste the new https://...trycloudflare.com/customers_api/v1/ URL
            // into local.properties as: API_BASE_URL=https://.../customers_api/v1/
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
            buildConfigField("String", "BEAMS_INSTANCE_ID", "\"$beamsInstanceId\"")
            isMinifyEnabled = false
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://www.legumlex.com/customers_api/v1/\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
            buildConfigField("String", "BEAMS_INSTANCE_ID", "\"$beamsInstanceId\"")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with the release key when keystore.properties is present
            // (create("release") above only runs then). When it's absent this
            // resolves to null, so a Play-bound bundleRelease produces an
            // unsigned artifact and fails loudly — never a silent debug-signed
            // release. See keystore.properties.sample to configure the upload key.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Razorpay's checkout POM requests `com.razorpay:standard-core:latest.integration`.
// Resolving that dynamic version forces a maven-metadata.xml HEAD on Maven Central,
// which Cloudflare answers with 403 (treated as a hard error) — breaking the build
// whenever the dynamic-version cache expires. Pinning a concrete published version
// skips version-listing entirely, so the exact POM/JAR resolves via a normal GET.
configurations.all {
    resolutionStrategy {
        force("com.razorpay:standard-core:1.7.14")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    implementation(libs.coil.compose)

    // Google Sign-In via Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Explicit coroutines (needed for viewModelScope launches + suspend code in repos)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit document scanner (PDF + JPEG output) — used by the doc-request
    // fulfilment flow to let clients scan documents directly from camera.
    implementation(libs.mlkit.document.scanner)

    // Razorpay Checkout SDK — used to pay for Digital Office sessions.
    // The SDK opens its own WebView for the payment UI; the host activity
    // implements PaymentResultWithDataListener to receive the result.
    implementation(libs.razorpay.checkout)

    // Firebase Messaging + Pusher Beams. Beams uses FCM under the hood; we
    // subscribe to `contact-{id}` interests after login and the server fires
    // pushes via the customers_api push_helper. The two libs MUST resolve via
    // the BOM so all firebase-* artifacts stay version-aligned.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.pusher.push.notifications)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
