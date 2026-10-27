package com.uridul.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place
import com.uridul.app.data.PlaceLinks
import com.uridul.app.data.PlaceShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        if (start(context, searchIntent(context, app, query.ifBlank { "맛집" }))) {
            Toast.makeText(context, "장소를 고른 뒤 공유 → 우리둘 을 눌러 주세요", Toast.LENGTH_LONG).show()
            return true
        }
        return start(context, webSearch(app, query))
    }

    /** 저장된 장소 열기: 링크 → 그 지도 앱에서, 링크가 없으면 이름으로 검색 */
    fun open(context: Context, place: Place) {
        val appContext = context.applicationContext
        val app = place.app ?: place.url?.let { PlaceShare.appOf(it) } ?: MapApp.NAVER
        val url = place.url
        if (url == null || !PlaceLinks.isShortLink(url)) {
            openResolved(appContext, place, app, url)
            return
        }
        // 짧은 링크는 실제 주소를 알아낸 뒤 연다 (네트워크, 최대 몇 초)
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { PlaceLinks.resolveRedirects(url) }
            openResolved(appContext, place, PlaceShare.appOf(resolved) ?: app, resolved)
        }
    }

    private fun openResolved(context: Context, place: Place, app: MapApp, url: String?) {
        val installed = isInstalled(context, app)
        // 1) 지도 앱에서 그 장소를 바로 (장소 번호·지도 주소)
        val appUri = url?.let { PlaceLinks.appUri(app, it, context.packageName) }
        if (installed && appUri != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appUri))
            if (app == MapApp.GOOGLE) intent.setPackage(app.packageName)
            if (start(context, intent)) return
        }
        // 2) 지도 앱에서 이름으로 검색
        if (installed && start(context, searchIntent(context, app, place.name))) return
        // 3) 앱이 없으면 링크(브라우저) 또는 웹 지도 검색
        if (url != null && start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) return
        start(context, webSearch(app, place.name))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    /** 일정 카드의 [장소 찾기] 에서 시작했으면 (일정 ID, 시간대 번호: -1 은 하루 종일) */
    data class EventSlot(val eventId: String, val slot: Int)

    @Volatile
    private var target: EventSlot? = null

    @Volatile
    private var targetAt = 0L

    /** 카드에서 시작한 장소 찾기 (10분이 지나면 잊는다: 한참 뒤의 공유가 엉뚱한 일정에 들어가지 않게) */
    val eventTarget: EventSlot?
        get() = target?.takeIf { System.currentTimeMillis() - targetAt < TARGET_TTL_MS }

    private const val TARGET_TTL_MS = 10 * 60 * 1000L

    /** 입력 창에서 장소 찾기를 시작함 (받은 장소는 입력 창이 가져간다) */
    fun expectEditor() {
        target = null
    }

    /** 저장된 일정 카드에서 장소 찾기를 시작함 (받은 장소를 그 일정에 바로 저장) */
    fun expectEvent(eventId: String, slot: Int) {
        target = EventSlot(eventId, slot)
        targetAt = System.currentTimeMillis()
    }

    fun offer(place: Place) {
        _place.value = place
    }

    fun consume() {
        _place.value = null
        target = null
    }
}
