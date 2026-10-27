package com.uridul.app

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uridul.app.data.AuthRepository
import com.uridul.app.data.CoupleRepository
import com.uridul.app.data.EventRepository
import com.uridul.app.widget.MidnightScheduler

class UridulApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MidnightScheduler.schedule(this)
    }
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

    val events: EventRepository? by lazy {
        if (firebaseReady) EventRepository(FirebaseFirestore.getInstance()) else null
    }
}
