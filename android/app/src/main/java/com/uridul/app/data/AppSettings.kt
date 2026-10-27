package com.uridul.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 화면 모드 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "폰 설정 따라가기"),
    LIGHT("light", "화이트 모드"),
    DARK("dark", "다크 모드");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * 일정 막대 색 (파스텔). fill = 막대·칸 바탕(글자는 진한 색), strong = 왼쪽 선·점·이름처럼 진하게 쓸 때.
 */
enum class EventColor(val key: String, val label: String, val fill: Long, val strong: Long) {
    PINK("pink", "연핑크", 0xFFFFC9D9, 0xFFD14D72),
    CORAL("coral", "코랄", 0xFFFFC6B5, 0xFFE0623E),
    PEACH("peach", "피치", 0xFFFFE0B8, 0xFFD88A2C),
    LEMON("lemon", "레몬", 0xFFFFF1A8, 0xFFB08F00),
    MINT("mint", "민트", 0xFFC4EEDB, 0xFF2E9E6E),
    SKY("sky", "하늘", 0xFFC3E2FF, 0xFF3E7CC9),
    LAVENDER("lavender", "라벤더", 0xFFDCD3FF, 0xFF6E58C9),
    LILAC("lilac", "라일락", 0xFFF3CDEF, 0xFFA8459E),
    GRAY("gray", "그레이", 0xFFE3DDDF, 0xFF7A6E72);

    companion object {
        fun from(key: String?, default: EventColor) = entries.firstOrNull { it.key == key } ?: default
    }
}

/** 막대 위 글자색 (파스텔 바탕 위라 화이트·다크 모드 모두 진한 색) */
const val ON_EVENT_FILL: Long = 0xFF3A2A30

data class UiSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 내 일정 색 (기본 연핑크) */
    val mine: EventColor = EventColor.PINK,
    /** 상대 일정 색 (기본 하늘) */
    val partner: EventColor = EventColor.SKY,
)

/**
 * 이 폰에만 저장하는 화면 설정 (화면 모드, 일정 색). 앱·위젯이 함께 읽는다.
 */
object AppSettings {
    private const val PREFS = "ui_settings"
    private val _state = MutableStateFlow(UiSettings())
    val state: StateFlow<UiSettings> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context): UiSettings {
        if (!loaded) {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _state.value = UiSettings(
                theme = ThemeMode.from(p.getString("theme", null)),
                mine = EventColor.from(p.getString("mine", null), EventColor.PINK),
                partner = EventColor.from(p.getString("partner", null), EventColor.SKY),
            )
            loaded = true
        }
        return _state.value
    }

    fun update(context: Context, change: (UiSettings) -> UiSettings) {
        val next = change(load(context))
        _state.value = next
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("theme", next.theme.key)
            .putString("mine", next.mine.key)
            .putString("partner", next.partner.key)
            .apply()
    }
}
