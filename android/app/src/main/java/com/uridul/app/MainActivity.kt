package com.uridul.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uridul.app.domain.InviteToken
import com.uridul.app.ui.HomeScreen
import com.uridul.app.ui.LoadingScreen
import com.uridul.app.ui.LoginScreen
import com.uridul.app.ui.NotConfiguredScreen
import com.uridul.app.ui.SetupScreen
import com.uridul.app.ui.shareInviteLink
import com.uridul.app.ui.theme.UridulTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            UridulTheme {
                UridulRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** https://<host>/invite/<토큰> 또는 uridul://invite/<토큰> 으로 열린 경우 */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.dataString ?: return
        InviteToken.parse(data)?.let(viewModel::onInviteReceived)
    }
}

@Composable
private fun UridulRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pendingInvite by viewModel.pendingInvite.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }

    when (val s = state) {
        AppState.NotConfigured -> NotConfiguredScreen()
        AppState.Loading -> LoadingScreen()
        AppState.SignedOut -> LoginScreen(
            hasPendingInvite = pendingInvite != null,
            busy = busy,
            snackbar = snackbar,
            onSignIn = { viewModel.signIn(context) },
        )
        is AppState.NoCouple -> SetupScreen(
            myName = s.myName,
            pendingInvite = pendingInvite,
            busy = busy,
            snackbar = snackbar,
            onCreate = viewModel::createCouple,
            onJoin = viewModel::join,
            onSignOut = { viewModel.signOut(context) },
        )
        is AppState.Ready -> HomeScreen(
            couple = s.couple,
            myUid = s.myUid,
            busy = busy,
            snackbar = snackbar,
            onInvite = { viewModel.createInviteLink { link -> shareInviteLink(context, link) } },
            onChangeStartDate = viewModel::updateStartDate,
            onSetCountFromOne = viewModel::setCountFromOne,
            onSignOut = { viewModel.signOut(context) },
        )
    }
}
