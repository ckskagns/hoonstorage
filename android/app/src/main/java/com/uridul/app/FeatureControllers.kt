package com.uridul.app

import android.app.Application
import com.uridul.app.data.Backup
import com.uridul.app.data.BackupFiles
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.ImportPreview
import com.uridul.app.data.PhotoMeta
import com.uridul.app.data.PhotoProcessor
import com.uridul.app.domain.Memories
import com.uridul.app.domain.PhotoQuota
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate

// AppViewModel 에서 덜어낸 기능별 관리 클래스들 (사진첩·백업 파일·앱 업데이트).
// 화면은 viewModel.photos / viewModel.backup / viewModel.updates 로 쓴다.

/** 기능별 관리 클래스가 함께 쓰는 것: 앱, 작업 범위, 저장소, 지금 연결된 커플, 안내 문구 */
class FeatureScope internal constructor(
    val app: Application,
    val scope: CoroutineScope,
    val container: AppContainer,
    private val readyState: () -> AppState.Ready?,
    private val say: (String) -> Unit,
    val errorText: (Exception) -> String?,
    private val busyRunner: (suspend () -> Unit) -> Job,
) {
    /** 커플 공간에 연결된 상태면 그 정보 */
    fun ready(): AppState.Ready? = readyState()

    /** 화면 아래 안내 문구 (넣기만 한다) */
    var message: String?
        get() = null
        set(value) {
            if (value != null) say(value)
        }

    /** 처리 중 표시를 켜고 실행 (오류는 안내 문구로) */
    fun busy(block: suspend () -> Unit): Job = busyRunner(block)
}

/** 일정 사진첩: 이번 주 장수, 올리기·지우기, 크게 보기, 추억 슬라이드 사진 */
class PhotoController internal constructor(private val f: FeatureScope) {

    private val _weekPhotoCount = MutableStateFlow(0)
    /** 이번 주(월~일)에 올린, 아직 남아 있는 사진 수 (두 사람 합계) */
    val weekPhotoCount: StateFlow<Int> = _weekPhotoCount.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val photoUploading: StateFlow<Boolean> = _uploading.asStateFlow()

    /** 이번 주 사진 수 다시 세기 (사진은 받지 않고 개수만) */
    private var weekCountJob: kotlinx.coroutines.Job? = null

    fun refreshWeekPhotoCount() {
        val ready = f.ready() ?: return
        val repo = f.container.photos ?: return
        // 여러 사진첩이 한꺼번에 불러도 한 번만 센다
        if (weekCountJob?.isActive == true) return
        weekCountJob = f.scope.launch {
            runCatching { repo.countWeek(ready.couple.id, PhotoQuota.weekKey(LocalDate.now())) }
                .onSuccess { _weekPhotoCount.value = it }
        }
    }

    /** 일정 하루의 사진첩 */
    fun albumFlow(event: CalendarEvent): Flow<List<PhotoMeta>> {
        val ready = f.ready() ?: return flowOf(emptyList())
        val repo = f.container.photos ?: return flowOf(emptyList())
        return repo.observeAlbum(ready.couple.id, event.id, event.date)
    }

    /**
     * 사진 올리기: 이번 주 남은 장수·이 일정 남은 장수만큼만 (넘치면 앞에서부터, 안내).
     * 한 장씩 줄여서 올리고, 올릴 때마다 이번 주 수를 다시 센다.
     */
    fun uploadPhotos(event: CalendarEvent, uris: List<android.net.Uri>, inAlbum: Int) {
        val ready = f.ready() ?: return
        val repo = f.container.photos ?: return
        if (uris.isEmpty() || _uploading.value) return
        f.scope.launch {
            _uploading.value = true
            try {
                val today = LocalDate.now()
                val used = runCatching { repo.countWeek(ready.couple.id, PhotoQuota.weekKey(today)) }.getOrDefault(_weekPhotoCount.value)
                _weekPhotoCount.value = used
                val room = PhotoQuota.remaining(used, inAlbum)
                if (room == 0) {
                    f.message = if (used >= PhotoQuota.WEEKLY_LIMIT) {
                        "이번 주 사진 ${PhotoQuota.WEEKLY_LIMIT}장을 다 올렸어요. 이번 주에 올린 사진을 지우면 다시 올릴 수 있어요"
                    } else {
                        "이 일정에는 사진을 ${PhotoQuota.PER_EVENT_MAX}장까지 올릴 수 있어요"
                    }
                    return@launch
                }
                var done = 0
                for (uri in uris.take(room)) {
                    val photo = PhotoProcessor.process(f.app, uri)
                    repo.upload(ready.couple.id, ready.myUid, event, photo, today)
                    done++
                    _weekPhotoCount.value = used + done
                }
                f.message = buildString {
                    append("📷 사진 ${done}장을 올렸어요")
                    if (uris.size > room) append(" (남은 ${room}장만, 나머지 ${uris.size - room}장은 빼고)")
                }
                refreshMemoryPhotos()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                f.message = f.errorText(e) ?: "사진을 올리지 못했어요."
            } finally {
                _uploading.value = false
                refreshWeekPhotoCount()
            }
        }
    }

    fun deletePhoto(photo: PhotoMeta) {
        val ready = f.ready() ?: return
        val repo = f.container.photos ?: return
        f.scope.launch {
            runCatching { repo.delete(ready.couple.id, photo.id) }
                .onSuccess {
                    f.message = if (photo.week == PhotoQuota.weekKey(LocalDate.now())) {
                        "사진을 지웠어요 · 이번 주에 한 장 더 올릴 수 있어요"
                    } else {
                        "사진을 지웠어요"
                    }
                    refreshMemoryPhotos()
                }
                .onFailure { e -> f.message = (e as? Exception)?.let(f.errorText) ?: "사진을 지우지 못했어요." }
            refreshWeekPhotoCount()
        }
    }

    /** 크게 볼 사진 (그때 받아온다) */
    suspend fun loadPhoto(id: String): ByteArray? {
        val ready = f.ready() ?: return null
        return runCatching { f.container.photos?.fetchPhoto(ready.couple.id, id) }.getOrNull()
    }

    private val _memoryPhotos = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    /** 추억 슬라이드에 깔 사진 (일정ID_날짜 → 첫 사진의 작은 사진) */
    val memoryPhotos: StateFlow<Map<String, ByteArray>> = _memoryPhotos.asStateFlow()

    /** 지금 홈에 보이는 추억 슬라이드 일정들 (사진을 다시 받을 때 쓴다) */
    private var slideEvents: List<CalendarEvent> = emptyList()

    /** 추억 슬라이드가 바뀌면 그 일정들의 사진만 받는다 */
    internal fun setMemorySlides(slides: List<Memories.Slide>) {
        slideEvents = slides.map { it.event }
        refreshMemoryPhotos()
    }

    /**
     * 슬라이드 일정마다 사진 하나(작은 사진)만 받는다.
     * (지난 한 달 사진을 전부 받지 않아서, 사진이 많아도 최대 20장 × 40KB)
     */
    internal fun refreshMemoryPhotos() {
        val ready = f.ready() ?: return
        val repo = f.container.photos ?: return
        val events = slideEvents
        if (events.isEmpty()) return
        f.scope.launch {
            val found = runCatching {
                kotlinx.coroutines.coroutineScope {
                    events.map { e -> async { repo.fetchFirst(ready.couple.id, e.id, e.date) } }.mapNotNull { it.await() }
                }
            }.getOrNull() ?: return@launch
            _memoryPhotos.value = found.associate { it.albumKey to it.thumb }
        }
    }

}

/** 백업 파일 저장·불러오기 */
class BackupController internal constructor(private val f: FeatureScope) {

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    /** 불러올 백업 파일 요약 (확인 창) */
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    /** 전체 일정·가고 싶은 곳을 파일로 저장 (파일 위치는 사용자가 고른다) */
    fun exportBackup(uri: android.net.Uri) = f.busy {
        val ready = f.ready() ?: return@busy
        val events = f.container.events?.fetchAll(ready.couple.id).orEmpty()
        val wishList = f.container.wishes?.fetchAll(ready.couple.id).orEmpty()
        val reviewList = f.container.reviews?.fetchAll(ready.couple.id).orEmpty()
        val text = Backup.encode(
            events = events,
            wishes = wishList,
            startDate = ready.couple.startDate,
            countFromOne = ready.couple.countFromOne,
            names = ready.couple.names,
            exportedAt = java.time.LocalDateTime.now().withNano(0).toString(),
            reviews = reviewList,
        )
        BackupFiles.write(f.app, uri, text)
        f.message = "백업 파일을 저장했어요 (일정 ${events.size}개 · 가고 싶은 곳 ${wishList.size}곳 · 후기 ${reviewList.size}개)"
    }

    /** 백업 파일을 읽고, 지금 없는 것만 골라 확인 창을 띄운다 */
    fun readBackup(uri: android.net.Uri) = f.busy {
        val ready = f.ready() ?: return@busy
        val text = BackupFiles.read(f.app, uri)
        val data = Backup.decode(text)
        val eventIds = f.container.events?.fetchAll(ready.couple.id).orEmpty().map { it.id }.toSet()
        val wishIds = f.container.wishes?.fetchAll(ready.couple.id).orEmpty().map { it.id }.toSet()
        val reviewIds = f.container.reviews?.fetchAll(ready.couple.id).orEmpty().map { it.id }.toSet()
        _importPreview.value = Backup.preview(data, eventIds, wishIds, reviewIds)
    }

    fun cancelImport() {
        _importPreview.value = null
    }

    /** 지금 없는 일정·장소만 더한다 (만든 사람이 지금 커플이 아니면 나로) */
    fun confirmImport() {
        val preview = _importPreview.value ?: return
        _importPreview.value = null
        val ready = f.ready() ?: return
        val members = ready.couple.members.toSet()
        preview.newEvents.forEach { e ->
            val owner = e.createdBy.takeIf { it in members } ?: ready.myUid
            f.container.events?.restore(ready.couple.id, ready.myUid, e.copy(createdBy = owner)) { f.message = "일부 일정을 더하지 못했어요. 다시 불러와 주세요." }
        }
        preview.newWishes.forEach { w ->
            val owner = w.createdBy.takeIf { it in members } ?: ready.myUid
            f.container.wishes?.restore(ready.couple.id, ready.myUid, w, owner) { f.message = "일부 장소를 더하지 못했어요. 다시 불러와 주세요." }
        }
        preview.newReviews.forEach { r ->
            f.container.reviews?.restore(ready.couple.id, ready.myUid, r.copy(entries = r.entries.filterKeys { it in members })) { f.message = "일부 후기를 더하지 못했어요. 다시 불러와 주세요." }
        }
        f.message = "백업에서 일정 ${preview.newEvents.size}개 · 가고 싶은 곳 ${preview.newWishes.size}곳 · 후기 ${preview.newReviews.size}개를 더했어요"
    }

}

/** 링크로 설치한 앱의 자체 업데이트 */
class UpdateController internal constructor(private val f: FeatureScope) {

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** 새 버전 확인. manual = 메뉴에서 직접 눌렀을 때 (최신이면 안내) */
    fun checkForUpdate(manual: Boolean) = f.scope.launch {
        val updater = f.container.updater
        val latest = updater.fetchLatest()
        when {
            latest == null -> if (manual) f.message = "업데이트 정보를 확인하지 못했어요. 인터넷 연결을 확인해 주세요."
            updater.isNewer(latest) -> _update.value = UpdateState.Available(latest)
            manual -> f.message = "최신 버전이에요 (${BuildConfig.VERSION_NAME})"
        }
    }

    /** 업데이트 시작: 설치 허용 확인 → 내려받기 → 설치 화면 */
    fun startUpdate() {
        val release = when (val s = _update.value) {
            is UpdateState.Available -> s.release
            is UpdateState.NeedPermission -> s.release
            else -> return
        }
        val updater = f.container.updater
        if (!updater.canInstall()) {
            _update.value = UpdateState.NeedPermission(release)
            return
        }
        f.scope.launch {
            _update.value = UpdateState.Downloading(release, 0f)
            try {
                val apk = updater.download(release) { p -> _update.value = UpdateState.Downloading(release, p) }
                _update.value = UpdateState.Idle
                f.app.startActivity(updater.installIntent(apk))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.value = UpdateState.Available(release)
                f.message = f.errorText(e) ?: "업데이트를 내려받지 못했어요."
            }
        }
    }

    /** 설치 허용 화면 열기 */
    fun openInstallPermission() {
        f.app.startActivity(f.container.updater.installPermissionIntent())
    }

    /** 앱으로 돌아왔을 때: 설치를 허용했으면 바로 이어서 진행 */
    fun onResumeAfterPermission() {
        if (_update.value is UpdateState.NeedPermission && f.container.updater.canInstall()) startUpdate()
    }

    fun dismissUpdate() {
        if (_update.value !is UpdateState.Downloading) _update.value = UpdateState.Idle
    }

}
