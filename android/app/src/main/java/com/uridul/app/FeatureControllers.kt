package com.uridul.app

import android.app.Application
import com.uridul.app.data.Backup
import com.uridul.app.data.BackupFiles
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.ImportPreview
import com.uridul.app.data.PhotoMeta
import com.uridul.app.data.PhotoProcessor
import com.uridul.app.data.DayPhoto
import com.uridul.app.data.PhotoMonthCache
import com.uridul.app.data.HeroAction
import com.uridul.app.data.HeroPhoto
import com.uridul.app.data.HeroPhotoStore
import com.uridul.app.data.HeroState
import com.uridul.app.domain.HeroPick
import com.uridul.app.domain.Memories
import com.uridul.app.domain.PhotoDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import com.uridul.app.domain.PhotoQuota
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.uridul.app.data.Couple
import com.uridul.app.data.Place
import com.uridul.app.data.WishPlace
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
    /** 지금 연결된 커플 공간 ID (바뀔 때마다) */
    val coupleId: Flow<String?>,
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
        // 여러 사진첩이 한꺼번에 불러도(일기장 목록 등) 잠깐 모았다가 한 번만 센다
        weekCountJob?.cancel()
        weekCountJob = f.scope.launch {
            kotlinx.coroutines.delay(400)
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
                if (done > 0) {
                    loadMonthPhotos(java.time.YearMonth.from(event.date), force = true)
                    com.uridul.app.ui.Celebrate.fire()
                }
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
                    loadMonthPhotos(java.time.YearMonth.from(photo.date), force = true)
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

    private val _dayPhotos = MutableStateFlow<Map<LocalDate, DayPhoto>>(emptyMap())
    /** 사진 달력: 날짜 → 그날 대표 사진·사진 수 (불러온 달만) */
    val dayPhotos: StateFlow<Map<LocalDate, DayPhoto>> = _dayPhotos.asStateFlow()

    /** 달마다 마지막으로 서버에 사진 수를 물은 시각 (같은 달을 오갈 때 매번 묻지 않게) */
    private val monthCheckedAt = java.util.concurrent.ConcurrentHashMap<java.time.YearMonth, Long>()

    /**
     * 사진 달력에 보일 달의 대표 사진들: 폰에 저장해 둔 것을 먼저 보여주고,
     * 서버에 그 달 사진 "수"만 물어 같으면 끝, 다르면(또는 처음이면) 그 달 사진을 받아 다시 저장한다.
     * @param force 내가 올리거나 지운 직후: 수를 묻지 않고 바로 다시 받는다
     */
    fun loadMonthPhotos(month: java.time.YearMonth, force: Boolean = false) {
        val ready = f.ready() ?: return
        val repo = f.container.photos ?: return
        val now = System.currentTimeMillis()
        if (!PhotoDays.mayHavePhotos(month, LocalDate.now())) return
        val last = monthCheckedAt[month]
        if (!force && last != null && now - last < PhotoDays.RECHECK_MS) return
        monthCheckedAt[month] = now
        val coupleId = ready.couple.id
        f.scope.launch {
            try {
                val from = month.atDay(1)
                val to = month.atEndOfMonth()
                val saved = withContext(Dispatchers.IO) { PhotoMonthCache.read(f.app, coupleId, month) }
                if (saved != null) publishMonth(month, saved.days)
                if (!force) {
                    val count = repo.countRange(coupleId, from, to)
                    if (PhotoDays.cacheValid(saved?.count, saved?.savedAt ?: 0, count, now)) return@launch
                    if (count == 0) {
                        withContext(Dispatchers.IO) { PhotoMonthCache.write(f.app, coupleId, month, 0, emptyMap(), now) }
                        publishMonth(month, emptyMap())
                        return@launch
                    }
                }
                val photos = repo.fetchRange(coupleId, from, to)
                val days = PhotoDays.covers(photos).mapValues { (_, c) -> DayPhoto(c.photo.id, c.photo.thumb, c.count, c.photo.eventId) }
                withContext(Dispatchers.IO) { PhotoMonthCache.write(f.app, coupleId, month, photos.size, days, now) }
                publishMonth(month, days)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 못 물었으면(인터넷 없음 등) 저장해 둔 것만 보여주고, 다음에 다시 묻는다
                monthCheckedAt.remove(month)
            }
        }
    }

    private fun publishMonth(month: java.time.YearMonth, days: Map<LocalDate, DayPhoto>) {
        _dayPhotos.update { all -> all.filterKeys { java.time.YearMonth.from(it) != month } + days }
        heroToday?.let { recomputeHero(it) }
    }

    private val _partnerLatest = MutableStateFlow<PhotoMeta?>(null)
    /** 상대가 가장 최근에 올린 사진 (일기장 새 소식 점, "상대 최근 사진" 위젯) */
    val partnerLatest: StateFlow<PhotoMeta?> = _partnerLatest.asStateFlow()
    private var partnerCheckedAt = 0L

    /** 상대 최근 사진 다시 확인 (5분에 한 번까지, 사진은 최근 5장의 작은 사진만) */
    fun refreshPartnerLatest(force: Boolean = false) {
        val ready = f.ready() ?: return
        val repo = f.container.photos ?: return
        val now = System.currentTimeMillis()
        if (!force && now - partnerCheckedAt < PhotoDays.RECHECK_MS) return
        partnerCheckedAt = now
        f.scope.launch {
            runCatching { repo.fetchLatest(ready.couple.id, 5) }
                .onSuccess { list ->
                    val latest = list.firstOrNull { it.uploadedBy != ready.myUid }
                    _partnerLatest.value = latest
                    com.uridul.app.widget.PartnerPhotoStore.update(f.app, latest, ready.couple.partnerName(ready.myUid))
                }
                .onFailure { partnerCheckedAt = 0L }
        }
    }

    private val _hero = MutableStateFlow(HeroState())
    /** 디데이 카드에 깔 오늘의 사진 */
    val hero: StateFlow<HeroState> = _hero.asStateFlow()

    /** 홈이 보인 날 (그 뒤로 사진 달력 달이 들어오면 카드 사진을 다시 고른다) */
    private var heroToday: LocalDate? = null
    private var heroJob: Job? = null
    private var heroSavedLoaded = false

    /**
     * 홈을 열 때: 폰에 저장해 둔 달들을 먼저 후보로 올리고,
     * 이번 달·지난달·몇 년 전 이번 달은 사진 달력처럼 서버에 사진 수를 물어 새 사진도 후보에 넣는다.
     */
    fun refreshHero(today: LocalDate) {
        val ready = f.ready() ?: return
        heroToday = today
        // 홈을 열 때 상대 최근 사진도 (일기장 새 소식 점, 상대 사진 위젯)
        refreshPartnerLatest()
        val coupleId = ready.couple.id
        f.scope.launch {
            if (!heroSavedLoaded) {
                heroSavedLoaded = true
                val saved = withContext(Dispatchers.IO) {
                    PhotoMonthCache.months(f.app, coupleId).mapNotNull { m -> PhotoMonthCache.read(f.app, coupleId, m)?.let { m to it.days } }
                }
                saved.forEach { (m, days) -> if (_dayPhotos.value.keys.none { java.time.YearMonth.from(it) == m }) _dayPhotos.update { it + days } }
            }
            recomputeHero(today)
            val month = java.time.YearMonth.from(today)
            val years = ready.couple.startDate.let { java.time.Period.between(it, today).years }.coerceIn(0, 10)
            (listOf(month, month.minusMonths(1)) + (1..years).map { month.minusYears(it.toLong()) }).forEach { loadMonthPhotos(it) }
        }
    }

    /** 카드 메뉴: 다른 사진 / 이 사진 고정 / 매일 바뀌게 / 끄기·켜기 */
    fun heroAction(action: HeroAction) {
        val today = heroToday ?: LocalDate.now()
        when (action) {
            HeroAction.NEXT -> {
                HeroPhotoStore.nextSkip(f.app, today)
                // 고정해 둔 상태에서 "다른 사진" 이면 고정을 풀고 매일 바뀌는 쪽으로
                HeroPhotoStore.setMode(f.app, HeroPhotoStore.Mode.DAILY)
            }
            HeroAction.PIN -> _hero.value.photo?.let { p ->
                HeroPhotoStore.setMode(f.app, HeroPhotoStore.Mode.FIXED, HeroPhotoStore.Fixed(p.id, p.date, p.eventId))
            }
            HeroAction.DAILY, HeroAction.ON -> HeroPhotoStore.setMode(f.app, HeroPhotoStore.Mode.DAILY)
            HeroAction.OFF -> HeroPhotoStore.setMode(f.app, HeroPhotoStore.Mode.OFF)
        }
        recomputeHero(today, immediate = true)
    }

    private fun recomputeHero(today: LocalDate, immediate: Boolean = false) {
        val ready = f.ready() ?: return
        val repo = f.container.photos ?: return
        heroJob?.cancel()
        heroJob = f.scope.launch {
            // 달이 연달아 들어올 때 잠깐 모았다가 한 번만 고른다 (중간 후보의 큰 사진을 받지 않게)
            if (!immediate) kotlinx.coroutines.delay(300)
            val settings = HeroPhotoStore.read(f.app, today)
            val days = _dayPhotos.value
            val candidates = days.map { (d, p) -> HeroPick.Candidate(d, p.id, p.eventId) }
            val available = candidates.any { it.date <= today }
            if (settings.mode == HeroPhotoStore.Mode.OFF) {
                _hero.value = HeroState(null, settings.mode, available)
                return@launch
            }
            val chosen = settings.fixed?.takeIf { settings.mode == HeroPhotoStore.Mode.FIXED }
                // 일정 ID 를 모르고 고정했으면(예전 저장본) 사진 달력에서 채운다
                ?.let { HeroPick.Candidate(it.date, it.id, it.eventId.ifEmpty { days[it.date]?.takeIf { p -> p.id == it.id }?.eventId.orEmpty() }) }
                ?: HeroPick.pick(candidates, today, settings.skip)
            if (chosen == null) {
                _hero.value = HeroState(null, settings.mode, available)
                return@launch
            }
            val current = _hero.value.photo
            if (current?.id == chosen.id && current.sharp) {
                _hero.value = HeroState(current, settings.mode, available)
                return@launch
            }
            // 먼저 폰에 있는 사진(큰 사진 → 작은 사진)으로 보여주고, 큰 사진이 없으면 받아서 바꾼다
            val local = withContext(Dispatchers.IO) { HeroPhotoStore.readPhoto(f.app, chosen.id) }
            val thumb = days[chosen.date]?.takeIf { it.id == chosen.id }?.thumb
            val first = local ?: thumb
            if (first != null) _hero.value = HeroState(HeroPhoto(chosen.id, chosen.date, chosen.eventId, first, local != null), settings.mode, available)
            if (local != null) return@launch
            val big = runCatching { repo.fetchPhoto(ready.couple.id, chosen.id) }.getOrNull()
            if (big != null) {
                withContext(Dispatchers.IO) { HeroPhotoStore.savePhoto(f.app, chosen.id, big) }
                _hero.value = HeroState(HeroPhoto(chosen.id, chosen.date, chosen.eventId, big, true), settings.mode, available)
            } else if (first == null) {
                // 고정한 사진이 지워졌으면 고정을 풀고 다시 고른다
                if (settings.mode == HeroPhotoStore.Mode.FIXED) {
                    HeroPhotoStore.setMode(f.app, HeroPhotoStore.Mode.DAILY)
                    recomputeHero(today, immediate = true)
                } else {
                    _hero.value = HeroState(null, settings.mode, available)
                }
            }
        }
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

/** 가고 싶은 곳 목록 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WishController internal constructor(private val f: FeatureScope) {

    /** 가고 싶은 곳 목록 (최근에 담은 것부터) */
    val wishes: StateFlow<List<WishPlace>> = run {
        val repo = f.container.wishes
        if (repo == null) {
            MutableStateFlow(emptyList())
        } else {
            f.coupleId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observe(id) }
                .stateIn(f.scope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
    }

    fun addWish(place: Place) {
        val ready = f.ready() ?: return
        val repo = f.container.wishes ?: return
        if (wishes.value.any { it.place.name == place.name && !it.visited }) {
            f.message = "📍 ${place.name} 은(는) 이미 가고 싶은 곳에 있어요"
            return
        }
        if (wishes.value.size >= WishPlace.MAX_ITEMS) {
            f.message = "가고 싶은 곳은 ${WishPlace.MAX_ITEMS}개까지 담을 수 있어요"
            return
        }
        repo.add(ready.couple.id, ready.myUid, place) { e -> f.message = f.errorText(e) ?: "담지 못했어요." }
        f.message = "📍 ${place.name} 을(를) 가고 싶은 곳에 담았어요 💕"
    }

    fun setWishVisited(id: String, visited: Boolean) {
        val ready = f.ready() ?: return
        f.container.wishes?.setVisited(ready.couple.id, ready.myUid, id, visited) { e ->
            f.message = f.errorText(e) ?: "바꾸지 못했어요."
        }
    }

    fun setWishMemo(id: String, memo: String) {
        val ready = f.ready() ?: return
        f.container.wishes?.setMemo(ready.couple.id, ready.myUid, id, memo) { e ->
            f.message = f.errorText(e) ?: "메모를 저장하지 못했어요."
        }
    }

    fun deleteWish(id: String) {
        val ready = f.ready() ?: return
        f.container.wishes?.delete(ready.couple.id, id) { e -> f.message = f.errorText(e) ?: "지우지 못했어요." }
    }

}

/** 일정 색: 두 사람이 같은 색으로 보도록 커플 공간(역할별)과 이 폰 설정을 맞춘다 */
class ColorController internal constructor(private val f: FeatureScope) {

    /**
     * 일정 색 바꾸기: 이 폰 설정 + 서버(역할별) 에 함께 저장 → 상대 폰도 같은 색으로 보인다.
     * [mine] 은 내 일정 색, [partner] 는 상대 일정 색.
     */
    fun setEventColors(mine: com.uridul.app.data.EventColor, partner: com.uridul.app.data.EventColor) {
        com.uridul.app.data.AppSettings.update(f.app) { it.copy(mine = mine, partner = partner) }
        val ready = f.ready() ?: return
        uploadColors(ready.couple, ready.myUid, mine, partner)
    }

    private fun uploadColors(couple: Couple, myUid: String, mine: com.uridul.app.data.EventColor, partner: com.uridul.app.data.EventColor) {
        val repo = f.container.couples ?: return
        val map = if (couple.isCreator(myUid)) mapOf("creator" to mine.key, "partner" to partner.key)
        else mapOf("creator" to partner.key, "partner" to mine.key)
        if (couple.colors == map) return
        f.scope.launch {
            runCatching { repo.updateColors(couple.id, map) }
                .onFailure { f.message = (it as? Exception)?.let(f.errorText) ?: "일정 색을 저장하지 못했어요." }
        }
    }

    /** 서버의 일정 색을 이 폰 설정에 맞춘다. 서버에 아직 없으면 이 폰에서 고른 색을 올린다 (처음 한 번) */
    internal fun syncColors(couple: Couple, myUid: String) {
        val settings = com.uridul.app.data.AppSettings.state.value
        val (mineKey, partnerKey) = couple.colorKeys(myUid)
        if (mineKey == null && partnerKey == null) {
            uploadColors(couple, myUid, settings.mine, settings.partner)
            return
        }
        val mine = com.uridul.app.data.EventColor.from(mineKey, settings.mine)
        val partner = com.uridul.app.data.EventColor.from(partnerKey, settings.partner)
        if (mine != settings.mine || partner != settings.partner) {
            com.uridul.app.data.AppSettings.update(f.app) { it.copy(mine = mine, partner = partner) }
        }
    }

}
