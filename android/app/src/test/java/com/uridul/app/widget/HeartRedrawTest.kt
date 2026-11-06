package com.uridul.app.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 하트 위젯을 언제 다시 그릴지 (보이는 내용이 바뀔 때만) */
class HeartRedrawTest {
    private val start = LocalDate.of(2026, 7, 17)
    private val today = LocalDate.of(2026, 11, 6)
    private val face = HeartFace(start, countFromOne = true)

    @Test fun daysText() {
        assertEquals("113일", face.daysText(today))
        assertNull(HeartFace(null).daysText(today))
    }

    @Test fun sameDaySameSignature() {
        // 15분마다 상대 일정 확인·앱 열기: 같은 날·같은 사귄 날이면 다시 그리지 않는다
        assertEquals(HeartRedraw.signatureOf("1/5", face, today), HeartRedraw.signatureOf("1/5", face, today))
    }

    @Test fun redrawWhenVisibleContentChanges() {
        val base = HeartRedraw.signatureOf("1/5", face, today)
        assertNotEquals(base, HeartRedraw.signatureOf("1/5", face, today.plusDays(1))) // 자정
        assertNotEquals(base, HeartRedraw.signatureOf("1/5", face.copy(start = start.minusDays(1)), today)) // 사귄 날 변경
        assertNotEquals(base, HeartRedraw.signatureOf("1/5", face.copy(countFromOne = false), today)) // 첫날 셈 변경
        assertNotEquals(base, HeartRedraw.signatureOf("2/5", face, today)) // 앱 업데이트 (그림 파일이 바뀜)
        assertNotEquals(base, HeartRedraw.signatureOf("1/6", face, today)) // 폰 재부팅 (홈 화면이 그림을 잃음)
        assertNotEquals(base, HeartRedraw.signatureOf("1/5", HeartFace(null), today)) // 연결 해제
    }

    @Test fun stateRoundTrip() {
        val p = mutablePreferencesOf()
        assertNull(HeartFace.from(p)) // 한 번도 안 넣었으면 처음 읽은 값을 쓴다
        face.writeTo(p)
        assertEquals(face, HeartFace.from(p))
        HeartFace(null, countFromOne = false).writeTo(p)
        assertEquals(HeartFace(null, countFromOne = false), HeartFace.from(p))
    }
}
