package com.uridul.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.uridul.app.data.WidgetStore
import com.uridul.app.ui.theme.UridulTheme
import com.uridul.app.widget.HeartDesign
import com.uridul.app.widget.HeartDesignStore
import com.uridul.app.widget.HeartFace
import com.uridul.app.widget.HeartRedraw
import com.uridul.app.widget.HeartWidget
import com.uridul.app.widget.HeartWidgetReceiver
import kotlinx.coroutines.launch
import com.uridul.app.ui.HeartDesignScreen

/** 디자인을 바꿀 대상: 놓인 하트 위젯 하나 (id) */
internal data class HeartTarget(val appWidgetId: Int, val label: String)

/**
 * 하트 위젯 디자인 고르기 (지금 하트 + 14종).
 *  - 앱 메뉴 "하트 위젯 디자인": 모든 하트 위젯, 또는 놓인 위젯 하나씩 (잠금화면 · 정사각 처럼 이름 붙여)
 *  - 홈 화면에서 위젯을 길게 눌러 "설정": 그 위젯이 골라진 채로 열린다
 * 삼성 잠금화면 편집기(LockStar)는 위젯 "설정" 을 열어 주지 않아서(폴드8 확인), 잠금화면 위젯은 앱 메뉴에서 바꾼다.
 * 미리보기는 실제 위젯 그림을 그대로 띄워서 하트도 똑같이 움직인다.
 */
class HeartWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val forWidget = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        if (forWidget) {
            // 우리 하트 위젯이 아닌 ID 로 열리면 아무것도 하지 않는다
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
            if (info == null || info.provider != ComponentName(this, HeartWidgetReceiver::class.java)) {
                finish()
                return
            }
        }
        val targets = placedTargets()

        setContent {
            UridulTheme {
                var face by remember { mutableStateOf<HeartFace?>(null) }
                var placed by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    face = HeartFace.of(WidgetStore.load(this@HeartWidgetConfigActivity))
                    placed = runCatching { GlanceAppWidgetManager(this@HeartWidgetConfigActivity).getGlanceIds(HeartWidget::class.java).size }.getOrNull()
                }
                HeartDesignScreen(
                    targets = targets,
                    initialTarget = if (forWidget) appWidgetId else null,
                    designOf = { id -> if (id == null) HeartDesignStore.forAll(this) else HeartDesignStore.forWidget(this, id) },
                    face = face,
                    placedCount = placed,
                    onApply = ::apply,
                    onCancel = ::finish,
                )
            }
        }
    }

    /**
     * 놓인 하트 위젯 목록과 이름. 홈 화면(삼성 런처는 크기 정보에 hsMode 를 넣는다)인지 잠금화면인지, 정사각인지 가로인지로 부른다.
     * 같은 이름이 둘 이상이면 (폴드는 바깥·안쪽 잠금화면이 따로) 번호를 붙인다.
     */
    private fun placedTargets(): List<HeartTarget> {
        val manager = AppWidgetManager.getInstance(this)
        val ids = runCatching { manager.getAppWidgetIds(ComponentName(this, HeartWidgetReceiver::class.java)) }.getOrNull() ?: return emptyList()
        val named = ids.sorted().map { id ->
            val o = manager.getAppWidgetOptions(id)
            val where = if (isLockScreen(o)) "잠금화면" else "홈 화면"
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            id to if (w > 0 && h > 0 && w > h * 1.4f) "$where · 가로" else "$where · 정사각"
        }
        val total = named.groupingBy { it.second }.eachCount()
        val seen = mutableMapOf<String, Int>()
        return named.map { (id, name) ->
            val n = (seen[name] ?: 0) + 1
            seen[name] = n
            HeartTarget(id, if ((total[name] ?: 0) > 1) "$name $n" else name)
        }
    }

    /**
     * 잠금화면 위젯인지: 삼성 홈 화면은 크기 정보에 hsMode 를 넣고, 삼성 잠금화면은 sem… 정보만 넣는다
     * (둘 다 위젯 종류를 "홈 화면" 으로 알려 줘서 종류만으로는 못 가른다). 다른 폰은 위젯 종류로.
     */
    private fun isLockScreen(o: android.os.Bundle): Boolean = when {
        o.containsKey("hsMode") -> false
        o.keySet().any { it.startsWith("sem") } -> true
        else -> o.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1) == android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
    }

    /** target = null 이면 모든 하트 위젯 */
    private fun apply(design: HeartDesign, target: Int?) {
        if (target != null) HeartDesignStore.setWidget(this, target, design) else HeartDesignStore.setAll(this, design)
        lifecycleScope.launch {
            runCatching { HeartRedraw.updateChanged(applicationContext) }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            }
            finish()
        }
    }
}
