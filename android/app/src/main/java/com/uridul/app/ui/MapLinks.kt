package com.uridul.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 지도 앱 연동 (API 없이 각 앱의 연동 주소만 사용)
 *  - 찾기: 고른 지도 앱을 검색 상태로 연다 → 사용자가 장소를 고르고 "공유 → 우리둘" 하면 장소 칸이 채워진다
 *  - 열기: 저장된 장소 링크를 그 지도 앱에서 바로 연다 (링크가 없으면 이름으로 검색)
 */
object MapLinks {

    fun isInstalled(context: Context, app: MapApp): Boolean =
        context.packageManager.getLaunchIntentForPackage(app.packageName) != null

    private fun enc(q: String) = Uri.encode(q)

    /** 지도 앱에서 검색 화면 열기 */
    private fun searchIntent(context: Context, app: MapApp, query: String): Intent = when (app) {
        MapApp.NAVER -> Intent(Intent.ACTION_VIEW, Uri.parse("nmap://search?query=${enc(query)}&appname=${context.packageName}"))
        MapApp.KAKAO -> Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://search?q=${enc(query)}"))
        MapApp.GOOGLE -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${enc(query)}")).setPackage(app.packageName)
    }

    /** 앱이 없을 때: 웹 지도 검색 */
    private fun webSearch(app: MapApp, query: String): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
            when (app) {
                MapApp.NAVER -> "https://map.naver.com/p/search/${enc(query)}"
                MapApp.KAKAO -> "https://map.kakao.com/?q=${enc(query)}"
                MapApp.GOOGLE -> "https://www.google.com/maps/search/?api=1&query=${enc(query)}"
            },
        ),
    )

    private fun storeIntent(app: MapApp) =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))

    /**
     * 장소 찾기: 앱이 있으면 검색 화면, 없으면 설치 화면으로 안내.
     * @return 지도 앱을 열었으면 true
     */
    fun startSearch(context: Context, app: MapApp, query: String): Boolean {
        if (!isInstalled(context, app)) {
            Toast.makeText(context, "${app.label} 앱이 없어요. 설치 화면으로 이동할게요.", Toast.LENGTH_LONG).show()
            start(context, storeIntent(app)) || start(context, webSearch(app, query))
            return false
        }
        PlaceInbox.expect()
        if (start(context, searchIntent(context, app, query.ifBlank { "맛집" }))) {
            Toast.makeText(context, "장소를 고른 뒤 공유 → 우리둘 을 눌러 주세요", Toast.LENGTH_LONG).show()
            return true
        }
        return start(context, webSearch(app, query))
    }

    /** 저장된 장소 열기: 링크 → 그 지도 앱에서, 링크가 없으면 이름으로 검색 */
    fun open(context: Context, place: Place) {
        val app = place.app ?: place.url?.let { com.uridul.app.data.PlaceShare.appOf(it) } ?: MapApp.NAVER
        val url = place.url
        if (url != null) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // 앱이 있으면 그 앱으로 바로, 안 되면 기본(브라우저·앱 링크)으로
            if (isInstalled(context, app) && start(context, Intent(view).setPackage(app.packageName))) return
            if (start(context, view)) return
        }
        if (isInstalled(context, app) && start(context, searchIntent(context, app, place.name))) return
        start(context, webSearch(app, place.name))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/**
 * 지도 앱에서 "공유 → 우리둘" 로 받은 장소를 잠깐 보관한다.
 * 일정 입력 창이 열려 있으면 그 창이 가져가고, 없으면 그 장소로 새 일정 입력 창을 연다.
 */
object PlaceInbox {
    private val _place = MutableStateFlow<Place?>(null)
    val place: StateFlow<Place?> = _place.asStateFlow()

    /** 장소 찾기를 시작했다는 표시 (지금은 기록만, 공유 처리와 무관) */
    @Volatile
    var expecting: Boolean = false
        private set

    fun expect() {
        expecting = true
    }

    fun offer(place: Place) {
        _place.value = place
    }

    fun consume() {
        _place.value = null
        expecting = false
    }
}
