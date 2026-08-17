import java.util.Properties
import java.io.FileInputStream

// Optional: only needed for signed release builds. CI debug builds skip this file.
val keystorePropertiesFile = file("../../keystore.properties")
val keystoreProperties = Properties()
val hasKeystore = keystorePropertiesFile.exists()
if (hasKeystore) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    id("com.android.application")
}

android {
    namespace = "io.opentakserver.opentakicu"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.plasmadancer.psr.icu"
        minSdk = 26
        targetSdk = 35
        versionCode = 10712
        versionName = "1.11.2-psr"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources.excludes.apply {
            add("META-INF/**")
        }
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                keyAlias = keystoreProperties["RELEASE_KEY_ALIAS"] as String
                keyPassword = keystoreProperties["RELEASE_KEY_PASSWORD"] as String
                storeFile = file("../android_cert.jks")
                storePassword = keystoreProperties["RELEASE_STORE_PASSWORD"] as String
            }
        }
        create("psrUpload") {
            val ks = System.getenv("PSR_UPLOAD_KEYSTORE")?.let { file(it) }
                ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (ks.isFile) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("psr") {
            dimension = "distribution"
            isDefault = true
            applicationId = "ru.plasmadancer.psr.icu"
        }
        create("compat") {
            dimension = "distribution"
            applicationId = "io.opentakserver.opentakicu.debug"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            when {
                hasKeystore -> signingConfig = signingConfigs.getByName("release")
                signingConfigs.getByName("psrUpload").storeFile != null ->
                    signingConfig = signingConfigs.getByName("psrUpload")
            }
        }

        getByName("debug") {
            isDebuggable = true
            if (signingConfigs.getByName("psrUpload").storeFile != null) {
                signingConfig = signingConfigs.getByName("psrUpload")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.1")
    //implementation("com.google.android.material:material:1.12.0")
    // Use 1.13.0-alpha08 because it adds orientation to the Slider
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment:2.9.7")
    implementation("androidx.navigation:navigation-ui:2.9.7")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.7")
    implementation("com.github.AppIntro:AppIntro:6.3.1")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.datastore:datastore-preferences-rxjava3:1.2.1")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.21.1")
    implementation("com.fasterxml.woodstox:woodstox-core:7.1.1")
    implementation("javax.xml.stream:stax-api:1.0-2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.sealwu:kscript-tools:1.0.22")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:nio:6.0.0")

    implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.6.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}