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

// 우리 캐릭터 그림: scripts/import_characters.ps1 이 src/chars/res 에 만든다 (개인 그림이라 git 에는 없음).
// 폴더가 있을 때만 앱에 넣고, 없으면(새로 받은 저장소) 예전 캐릭터·하트 아이콘 그대로 빌드된다.
val charsRes = file("src/chars/res")
val hasCharIcon = file("src/chars/res/mipmap-anydpi-v26/ic_launcher_char.xml").exists()

android {
    namespace = "com.uridul.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.uridul.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 21
        versionName = "0.19.0"

        manifestPlaceholders["inviteHost"] = inviteHost
        buildConfigField("String", "INVITE_HOST", "\"$inviteHost\"")
        // 앱 아이콘: 캐릭터 그림이 있으면 "하트 쥔 두 얼굴", 없으면 하트
        manifestPlaceholders["appIcon"] = if (hasCharIcon) "@mipmap/ic_launcher_char" else "@mipmap/ic_launcher"
        manifestPlaceholders["appRoundIcon"] = if (hasCharIcon) "@mipmap/ic_launcher_char_round" else "@mipmap/ic_launcher_round"
    }

    sourceSets {
        getByName("main") {
            if (charsRes.exists()) res.srcDir(charsRes)
        }
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
    // 디데이 사진에서 앱 색 뽑기 (Apache 2.0)
    implementation(libs.androidx.palette)
    // 상대 일정 알림: 15분마다 서버 확인 (무료 요금제, 서버 함수 없이)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    // 단위 테스트에서 org.json 을 실제로 쓰기 위해 (안드로이드 기본 org.json 은 테스트에서 동작하지 않음)
    testImplementation("org.json:json:20240303")
}
