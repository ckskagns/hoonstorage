package com.uridul.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.BundleCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.DDay
import com.uridul.app.ui.theme.UridulTheme
import com.uridul.app.widget.PhotoStore
import com.uridul.app.widget.PhotoWidget
import com.uridul.app.widget.PhotoWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 사진 디데이 위젯 설정: 사진 고르기 → 위젯 모양 틀에 맞춰 확대·이동 → 저장.
 * 위젯을 처음 놓을 때와, 위젯을 길게 눌러 "설정"을 고를 때 열린다.
 */
class PhotoWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 저장하지 않고 나가면 위젯을 놓지 않는다
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(this)
        val info = manager.getAppWidgetInfo(appWidgetId)
        // 우리 사진 위젯이 아닌 ID 로 열리면 아무것도 하지 않는다
        if (info == null || info.provider != ComponentName(this, PhotoWidgetReceiver::class.java)) {
            finish()
            return
        }
        val widgetAspect = widgetAspect(manager.getAppWidgetOptions(appWidgetId))

        setContent {
            UridulTheme {
                var snapshot by remember { mutableStateOf<WidgetSnapshot?>(null) }
                LaunchedEffect(Unit) { snapshot = WidgetStore.load(this@PhotoWidgetConfigActivity) }
                PhotoConfigScreen(
                    widgetAspect = widgetAspect,
                    hasPhoto = PhotoStore.has(this, appWidgetId),
                    snapshot = snapshot,
                    decode = ::decode,
                    onSave = ::save,
                    onRemove = ::removePhoto,
                    onCancel = ::finish,
                )
            }
        }
    }

    /**
     * 위젯의 가로:세로 (세로 화면 기준: 최소 너비 × 최대 높이).
     * 크기 목록에는 가로 화면 크기도 섞여 있어 평균을 내면 너무 넓어지므로, 목록 중 가장 세로로 긴 크기를 쓴다.
     */
    private fun widgetAspect(options: Bundle): Float {
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (w > 0 && h > 0) return w.toFloat() / h
        val sizes = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.filter { it.width > 0 && it.height > 0 }
        return sizes?.minOfOrNull { it.width / it.height } ?: 1f
    }

    /** 고른 사진 읽기: 회전 정보 반영, 삼성 고효율(HEIC) 사진도 읽힘, 편집용으로 긴 쪽 2048px 이하 */
    private suspend fun decode(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val long = max(info.size.width, info.size.height)
                if (long > 2048) {
                    val ratio = 2048f / long
                    decoder.setTargetSize((info.size.width * ratio).roundToInt(), (info.size.height * ratio).roundToInt())
                }
            }
        }.getOrNull()
    }

    private fun save(cropped: Bitmap) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { PhotoStore.save(this@PhotoWidgetConfigActivity, appWidgetId, cropped) }
            refreshAndFinish()
        }
    }

    private fun removePhoto() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { PhotoStore.delete(this@PhotoWidgetConfigActivity, appWidgetId) }
            refreshAndFinish()
        }
    }

    private suspend fun refreshAndFinish() {
        runCatching {
            val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
            PhotoWidget().update(this, glanceId)
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

private enum class FrameShape(val label: String) { WIDGET("위젯 모양"), SQUARE("정사각형"), WIDE("가로 2:1"), TALL("세로 3:4") }

@Composable
private fun PhotoConfigScreen(
    widgetAspect: Float,
    hasPhoto: Boolean,
    snapshot: WidgetSnapshot?,
    decode: suspend (Uri) -> Bitmap?,
    onSave: (Bitmap) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var shape by remember { mutableStateOf(FrameShape.WIDGET) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        failed = false
        scope.launch {
            source = decode(uri)
            failed = source == null
            loading = false
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // 처음 열리면 바로 사진 선택 창
    LaunchedEffect(Unit) { if (!hasPhoto) pick() }

    val aspect = when (shape) {
        FrameShape.WIDGET -> widgetAspect.coerceIn(0.4f, 3f)
        FrameShape.SQUARE -> 1f
        FrameShape.WIDE -> 2f
        FrameShape.TALL -> 0.75f
    }
    // 틀 모양을 바꾸면 확대·이동을 처음부터
    var cropper by remember(source, aspect) { mutableStateOf(CropState()) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("위젯 사진", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("취소") }
                val src = source
                Button(
                    onClick = { if (src != null) onSave(cropper.crop(src, aspect)) },
                    enabled = src != null,
                ) { Text("저장") }
            }

            val src = source
            when {
                loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                src != null -> {
                    Text(
                        "두 손가락으로 확대하고, 끌어서 보이는 부분을 맞춰 주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CropFrame(
                        image = remember(src) { src.asImageBitmap() },
                        aspect = aspect,
                        state = cropper,
                        onChange = { cropper = it },
                        snapshot = snapshot,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FrameShape.entries.forEach { s ->
                            FilterChip(selected = shape == s, onClick = { shape = s }, label = { Text(s.label, fontSize = 12.sp) })
                        }
                    }
                    Text(
                        "위젯 크기를 바꾸거나 폴드 커버·펼친 화면에서는 가운데를 기준으로 조금 잘려 보일 수 있어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = pick) { Text("다른 사진 고르기") }
                }
                else -> {
                    Spacer(Modifier.height(40.dp))
                    Text(
                        if (failed) "사진을 읽지 못했어요. 다른 사진을 골라 주세요." else "위젯에 넣을 사진을 골라 주세요",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "사진은 이 폰 안에만 저장되고, 서버나 상대 폰으로 보내지 않아요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = pick) { Text("사진 고르기") }
                    if (hasPhoto) {
                        TextButton(onClick = onRemove) { Text("사진 빼기 (기본 모양으로)", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

/** 확대 배율(1 = 틀을 꽉 채우는 크기)과 이동량(틀 크기 대비 비율) */
private data class CropState(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {

    /** 틀 크기(px)와 사진 크기로 화면에 그릴 위치·크기를 계산 */
    fun placement(frame: IntSize, imageW: Int, imageH: Int): Pair<IntOffset, IntSize> {
        val base = max(frame.width.toFloat() / imageW, frame.height.toFloat() / imageH)
        val w = imageW * base * zoom
        val h = imageH * base * zoom
        val x = (frame.width - w) / 2 + panX * frame.width
        val y = (frame.height - h) / 2 + panY * frame.height
        return IntOffset(x.roundToInt(), y.roundToInt()) to IntSize(w.roundToInt(), h.roundToInt())
    }

    /** 틀 밖으로 빈 곳이 생기지 않게 이동량을 제한 */
    fun clamped(frame: IntSize, imageW: Int, imageH: Int): CropState {
        if (frame.width == 0 || frame.height == 0) return this
        val base = max(frame.width.toFloat() / imageW, frame.height.toFloat() / imageH)
        val maxX = ((imageW * base * zoom - frame.width) / 2 / frame.width).coerceAtLeast(0f)
        val maxY = ((imageH * base * zoom - frame.height) / 2 / frame.height).coerceAtLeast(0f)
        return copy(panX = panX.coerceIn(-maxX, maxX), panY = panY.coerceIn(-maxY, maxY))
    }

    /** 틀에 보이는 부분만 잘라 저장용 크기(긴 쪽 최대 1080px)로 */
    fun crop(src: Bitmap, aspect: Float): Bitmap {
        // 틀 크기는 비율만 중요하므로 가상의 1000 기준으로 계산
        val frame = if (aspect >= 1f) IntSize(1000, (1000 / aspect).roundToInt()) else IntSize((1000 * aspect).roundToInt(), 1000)
        val (offset, size) = clamped(frame, src.width, src.height).placement(frame, src.width, src.height)
        val scale = size.width.toFloat() / src.width
        val left = (-offset.x / scale).roundToInt().coerceIn(0, src.width - 1)
        val top = (-offset.y / scale).roundToInt().coerceIn(0, src.height - 1)
        val w = (frame.width / scale).roundToInt().coerceIn(1, src.width - left)
        val h = (frame.height / scale).roundToInt().coerceIn(1, src.height - top)
        val cropped = Bitmap.createBitmap(src, left, top, w, h)
        val long = max(w, h)
        return if (long <= PhotoStore.SAVE_MAX_SIDE) {
            cropped
        } else {
            val r = PhotoStore.SAVE_MAX_SIDE.toFloat() / long
            Bitmap.createScaledBitmap(cropped, (w * r).roundToInt(), (h * r).roundToInt(), true)
        }
    }
}

@Composable
private fun CropFrame(
    image: ImageBitmap,
    aspect: Float,
    state: CropState,
    onChange: (CropState) -> Unit,
    snapshot: WidgetSnapshot?,
    modifier: Modifier,
) {
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var latest by remember { mutableStateOf(state) }
    latest = state
    val zoomMax = remember { mutableFloatStateOf(5f) }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                .onSizeChanged { frame = it }
                .pointerInput(image, aspect) {
                    detectTransformGestures { _, pan: Offset, zoom, _ ->
                        val s = latest
                        val next = s.copy(
                            zoom = (s.zoom * zoom).coerceIn(1f, zoomMax.floatValue),
                            panX = s.panX + pan.x / size.width,
                            panY = s.panY + pan.y / size.height,
                        ).clamped(frame, image.width, image.height)
                        latest = next
                        onChange(next)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val (offset, sz) = state.clamped(frame, image.width, image.height).placement(frame, image.width, image.height)
                drawImage(image, dstOffset = offset, dstSize = sz)
                // 위젯과 같은 아래쪽 그림자
                drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0x99000000)))
            }
            // 위젯에 보일 글자 미리보기
            if (snapshot != null) {
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        listOfNotNull(snapshot.myName, snapshot.partnerName).joinToString(" ♥ "),
                        color = Color(0xE6FFFFFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        DDay.heroText(snapshot.startDate, LocalDate.now(), snapshot.countFromOne),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
