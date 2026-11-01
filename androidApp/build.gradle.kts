import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { alias(libs.plugins.androidApplication); alias(libs.plugins.composeCompiler) }
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
android {
    namespace = "pe.edu.upeu.nullpointerpos"
    compileSdk = 36
    defaultConfig {
        applicationId = "pe.edu.upeu.nullpointerpos"
        minSdk = 24; targetSdk = 36; versionCode = 1; versionName = "1.0.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
