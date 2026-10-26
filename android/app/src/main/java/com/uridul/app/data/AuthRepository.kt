package com.uridul.app.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Google 계정 선택 창을 띄우고 Firebase 에 로그인한다. activityContext 는 Activity 여야 한다. */
    suspend fun signInWithGoogle(activityContext: Context) {
        val clientId = webClientId(activityContext)
            ?: throw UserFacingException("Firebase 설정에 Google 로그인이 켜져 있지 않아요. 설정 가이드를 확인해 주세요.")

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
            .credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        } else {
            throw UserFacingException("지원하지 않는 로그인 방식이에요.")
        }
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /**
     * google-services 플러그인이 만들어 주는 default_web_client_id 를 찾는다.
     * (Google 로그인을 켜기 전 받은 설정 파일에는 이 값이 없을 수 있어 이름으로 조회한다.)
     */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }
}
