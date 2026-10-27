package com.uridul.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.uridul.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 서버(Firebase Hosting)의 download/version.json 내용 */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

/**
 * 링크로 설치한 앱의 자체 업데이트.
 *  1) https://<프로젝트>.web.app/download/version.json 확인
 *  2) 새 버전이면 APK 를 앱 전용 폴더에 내려받고 SHA-256 으로 파일이 맞는지 검사
 *  3) 안드로이드 설치 화면을 연다 (같은 서명 키로 만든 APK 라 데이터는 그대로 유지)
 * 플레이 스토어 없이 두 사람에게만 배포하기 위한 방식이다.
 */
class AppUpdater(private val context: Context) {

    private val base = "https://${BuildConfig.INVITE_HOST}/download"

    suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$base/version.json?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                useCaches = false
            }
            conn.inputStream.use { stream ->
                val o = JSONObject(stream.bufferedReader().readText())
                AppRelease(
                    versionCode = o.getInt("versionCode"),
                    versionName = o.getString("versionName"),
                    notes = o.optString("notes"),
                    apkUrl = o.optString("apkUrl").ifBlank { "$base/uridul.apk" },
                    sha256 = o.getString("sha256").lowercase(),
                    sizeBytes = o.optLong("size"),
                )
            }
        }.getOrNull()
    }

    fun isNewer(release: AppRelease): Boolean = release.versionCode > BuildConfig.VERSION_CODE

    /** APK 내려받기. onProgress 는 0.0~1.0. 파일 검사에 실패하면 예외. */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // 예전 파일 정리
        val file = File(dir, "uridul-${release.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
        conn.inputStream.use { input ->
            file.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != release.sha256) {
            file.delete()
            throw UserFacingException("내려받은 파일이 올바르지 않아요. 잠시 후 다시 시도해 주세요.")
        }
        file
    }

    /** 이 앱이 설치 화면을 열 수 있는지 ("출처를 알 수 없는 앱 설치" 허용 여부) */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 우리둘 앱의 "출처를 알 수 없는 앱 설치" 허용 화면 */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 안드로이드 설치 화면 열기 */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
