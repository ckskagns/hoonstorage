package com.uridul.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 하트 위젯 디자인 15가지: 저장 값, 디자인별 글자, 다음 100일 */
class HeartDesignTest {
    private val start = LocalDate.of(2026, 7, 17)
    private val today = LocalDate.of(2026, 11, 6) // 113일째
    private val face = HeartFace(start, true, "후이", "나나")

    @Test fun keysAreUniqueAndStable() {
        assertEquals(15, HeartDesign.entries.size)
        assertEquals(HeartDesign.entries.size, HeartDesign.entries.map { it.key }.toSet().size)
        assertEquals((0..14).toList(), HeartDesign.entries.map { it.number })
        assertEquals(HeartDesign.ORBIT, HeartDesign.from("orbit"))
        assertEquals(HeartDesign.CLASSIC, HeartDesign.from(null)) // 고른 적 없으면 지금 하트
        assertEquals(HeartDesign.CLASSIC, HeartDesign.from("없는값"))
    }

    @Test fun nextHundredMilestone() {
        assertEquals(Triple(200L, 87L, 13), nextHundred(113))
        assertEquals(Triple(200L, 100L, 0), nextHundred(100)) // 100일 당일엔 다음은 200일
        assertEquals(Triple(100L, 99L, 1), nextHundred(1))
        assertEquals(Triple(100L, 103L, 0), nextHundred(-3)) // 시작 전
    }

    @Test fun textsPerDesign() {
        fun t(d: HeartDesign) = heartTexts(d, face, today)
        assertEquals("113일", t(HeartDesign.CLASSIC).days.toString())
        assertEquals("113", t(HeartDesign.INSIDE).days.toString())
        assertEquals("일째 사랑 중", t(HeartDesign.INSIDE).since)
        assertEquals("D+113", t(HeartDesign.MINIMAL).days.toString())
        assertEquals("후이", t(HeartDesign.NAMES).extra)
        assertEquals("나나", t(HeartDesign.NAMES).extra2)
        assertEquals("200일까지 87일", t(HeartDesign.FILL).since)
        assertEquals(13, t(HeartDesign.FILL).progress)
        assertEquals("일", t(HeartDesign.ORBIT).since)
        assertEquals("days", t(HeartDesign.SERIF).extra)
        assertEquals("일째", t(HeartDesign.CALENDAR).since)
    }

    @Test fun noDesignShowsStartDate() {
        // 사귄 날짜(26.07.17 / 2026.07.17 / 07.17 등)는 어느 디자인에도 보이지 않는다: 하트와 디데이만
        val dateLike = Regex("""\d{2}\.\d{2}""")
        // 말풍선은 글자 꾸미기(SpannableString)가 안드로이드에서만 돌아 제외 (날짜를 쓴 적 없음)
        for (d in HeartDesign.entries.filter { it != HeartDesign.BUBBLE }) {
            val t = heartTexts(d, face, today)
            for (s in listOf(t.days.toString(), t.since, t.extra, t.extra2)) {
                assertTrue("$d: $s", !dateLike.containsMatchIn(s))
            }
        }
        for (d in listOf(HeartDesign.CLASSIC, HeartDesign.PILL, HeartDesign.ECG, HeartDesign.RISE, HeartDesign.MINIMAL, HeartDesign.NEON, HeartDesign.PIXEL, HeartDesign.SERIF)) {
            assertEquals("$d", "", heartTexts(d, face, today).since)
        }
    }

    @Test fun emptyDateLineLetsHeartGrow() {
        // 아랫줄이 없어지면 같은 칸에서 하트·디데이가 더 커진다 (배율이 줄지 않는다)
        val slot = androidx.compose.ui.unit.DpSize(androidx.compose.ui.unit.Dp(99f), androidx.compose.ui.unit.Dp(99f))
        val t = heartTexts(HeartDesign.NEON, face, today)
        assertTrue(HeartDesigns.scaleFor(HeartDesign.NEON, t, slot) >= HeartDesigns.scaleFor(HeartDesign.NEON, t.copy(since = "26.07.17"), slot))
    }

    @Test fun notConnectedShowsGuide() {
        val none = HeartFace(null)
        for (d in HeartDesign.entries.filter { it != HeartDesign.BUBBLE && it != HeartDesign.NAMES }) {
            val t = heartTexts(d, none, today)
            assertTrue("$d", t.since == "앱에서 연결해 주세요")
        }
    }

    @Test fun beforeStartDate() {
        val early = HeartFace(today.plusDays(3))
        assertEquals("D-3", heartTexts(HeartDesign.CLASSIC, early, today).days.toString())
        assertEquals("D-3", heartTexts(HeartDesign.INSIDE, early, today).days.toString())
        assertEquals(0, heartTexts(HeartDesign.FILL, early, today).progress)
    }

    @Test fun designChangeRedraws() {
        assertNotEquals(
            HeartRedraw.signatureOf("1/5", face, today, HeartDesign.CLASSIC),
            HeartRedraw.signatureOf("1/5", face, today, HeartDesign.NEON),
        )
        // 이름이 바뀌면(두 하트 만나기) 다시 그린다
        assertNotEquals(
            HeartRedraw.signatureOf("1/5", face, today, HeartDesign.NAMES),
            HeartRedraw.signatureOf("1/5", face.copy(partnerName = "나나♥"), today, HeartDesign.NAMES),
        )
    }

    @Test fun everyDesignFitsSmallLockScreenSlot() {
        // 잠금화면 정사각 칸(약 99×99dp)·가로 칸(249×99dp)에서도 글자가 너무 작아지지 않는지 (배율 0.35 이상)
        for (d in HeartDesign.entries.filter { it != HeartDesign.CLASSIC && it != HeartDesign.BUBBLE }) {
            val t = heartTexts(d, face, today)
            val square = HeartDesigns.scaleFor(d, t, androidx.compose.ui.unit.DpSize(androidx.compose.ui.unit.Dp(99f), androidx.compose.ui.unit.Dp(99f)))
            val wide = HeartDesigns.scaleFor(d, t, androidx.compose.ui.unit.DpSize(androidx.compose.ui.unit.Dp(249f), androidx.compose.ui.unit.Dp(99f)))
            assertTrue("$d 정사각 $square", square >= 0.35f)
            assertTrue("$d 가로 $wide", wide >= square)
        }
    }
}

class HeartFillLevelTest {
    @Test fun fillCoversHeartShape() {
        org.junit.Assert.assertEquals(11, fillLevel(0)) // 하트 아래 끝
        org.junit.Assert.assertEquals(87, fillLevel(100)) // 하트 꼭대기
        org.junit.Assert.assertTrue(fillLevel(13) in 20..22)
    }
}
