package com.uridul.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.uridul.app.data.Place
import com.uridul.app.data.PlaceLinks
import com.uridul.app.data.PlaceShare
import com.uridul.app.ui.PlaceInbox
import com.uridul.app.widget.MainExtras

/**
 * 지도 앱(네이버 지도·카카오맵·구글 지도)의 "공유 → 우리둘" 을 받는 화면 없는 활동.
 * 공유된 글에서 장소 이름·링크를 뽑고(짧은 링크는 실제 장소 주소로 바꿔서) 보관한 뒤,
 * 우리둘(원래 열려 있던 화면)로 돌아간다.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        val place = PlaceShare.parse(text, subject)
        if (place == null) {
            Toast.makeText(this, "장소 정보를 찾지 못했어요", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val url = place.url
        if (url == null || !PlaceLinks.isShortLink(url)) {
            deliver(place)
            return
        }
        // naver.me 같은 짧은 링크는 실제 장소 주소(장소 번호가 들어 있음)로 바꿔 둔다 → 나중에 지도 앱에서 그 장소를 바로 연다
        Thread {
            val resolved = PlaceLinks.resolveRedirects(url).takeIf { it.length <= Place.URL_MAX } ?: url
            runOnUiThread { deliver(place.copy(url = resolved, app = place.app ?: PlaceShare.appOf(resolved))) }
        }.start()
    }

    private fun deliver(place: Place) {
        PlaceInbox.offer(place)
        // 이미 열려 있는 우리둘 화면(일정 입력 중이면 그 창)으로 돌아간다
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainExtras.TAB, "calendar")
                .putExtra(MainExtras.PLACE_SHARED, true),
        )
        finish()
    }
}
