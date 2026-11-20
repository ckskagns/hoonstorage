package com.uridul.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// 연결 끊김 안내 줄: 인터넷이 끊기면 "연결이 끊겼어요 · 연결되면 자동으로 올라가요".
// 저장(Firestore)은 끊겨 있어도 폰에 먼저 적혀 있다가 다시 연결되면 저절로 올라가므로, 그걸 알려 준다.
// (선호도 조사 5번 "동기화 믿음": 올렸는데 상대에게 안 보인다는 불만)

/** 지금 인터넷에 연결돼 있는지 (휴대폰 연결 상태가 바뀌면 바로 다시 그린다) */
@Composable
fun rememberOnline(): Boolean {
    val context = LocalContext.current.applicationContext
    var online by remember { mutableStateOf(NetworkState.isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = NetworkState.isOnline(context) }
            override fun onLost(network: Network) { online = NetworkState.isOnline(context) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = NetworkState.isOnline(context)
            }
        }
        val registered = runCatching { cm?.registerDefaultNetworkCallback(callback) }.isSuccess && cm != null
        // 등록하는 사이에 바뀌었을 수 있으니 한 번 더
        online = NetworkState.isOnline(context)
        onDispose { if (registered) runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}

object NetworkState {
    /** 기본 연결이 인터넷을 쓸 수 있는지. 알 수 없으면 연결된 것으로 (괜히 경고하지 않게) */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        return runCatching {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val online = rememberOnline()
    AnimatedVisibility(!online, enter = expandVertically(), exit = shrinkVertically()) {
        Text(
            "📡 연결이 끊겼어요 · 연결되면 자동으로 올라가요",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
