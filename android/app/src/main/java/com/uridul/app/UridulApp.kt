package com.uridul.app

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uridul.app.data.AppUpdater
import com.uridul.app.data.AuthRepository
import com.uridul.app.data.CoupleRepository
import com.uridul.app.data.EventRepository
import com.uridul.app.notify.Reminders
import com.uridul.app.widget.MidnightScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class UridulApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MidnightScheduler.schedule(this)
        // 화면 모드·일정 색 설정을 미리 읽어 둔다 (첫 화면부터 고른 모드로)
        com.uridul.app.data.AppSettings.load(this)
        // 화면 모드·일정 색을 바꾸면 (어느 화면에서 바꾸든) 홈 화면 위젯도 같은 색으로 다시 그린다
        appScope.launch {
            com.uridul.app.data.AppSettings.state
                .drop(1)
                .distinctUntilChanged()
                .collect { com.uridul.app.widget.Widgets.updateAll(this@UridulApp) }
        }
        Reminders.ensureChannel(this)
        Reminders.scheduleDaily(this)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** 간단한 수동 의존성 주입. google-services.json 이 없으면 Firebase 객체를 만들지 않는다. */
class AppContainer(context: Context) {
    val firebaseReady: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    val auth: AuthRepository? by lazy {
        if (firebaseReady) AuthRepository(FirebaseAuth.getInstance()) else null
    }

    val couples: CoupleRepository? by lazy {
        if (firebaseReady) CoupleRepository(FirebaseFirestore.getInstance()) else null
    }

    /** 링크로 설치한 앱의 자체 업데이트 (Firebase 설정과 무관하게 동작) */
    val updater: AppUpdater = AppUpdater(context.applicationContext)

    val events: EventRepository? by lazy {
        if (firebaseReady) EventRepository(FirebaseFirestore.getInstance()) else null
    }
}
