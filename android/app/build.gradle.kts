import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firebase 콘솔에서 받은 google-services.json 을 app/ 폴더에 넣으면 자동으로 연결된다.
// 파일이 없어도 빌드는 되며, 앱은 "설정 필요" 화면을 보여준다.
val googleServicesFile = file("google-services.json")
val firebaseProjectId: String = if (googleServicesFile.exists()) {
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(googleServicesFile) as Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    (json["project_info"] as Map<String, Any>)["project_id"] as String
} else {
    "your-project-id"
}
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// 초대 링크 도메인 (Firebase Hosting 기본 도메인)
val inviteHost = "$firebaseProjectId.web.app"

android {
    namespace = "com.uridul.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.uridul.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        manifestPlaceholders["inviteHost"] = inviteHost
        buildConfigField("String", "INVITE_HOST", "\"$inviteHost\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
}
