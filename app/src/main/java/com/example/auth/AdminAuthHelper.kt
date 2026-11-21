package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object AdminAuthHelper {
    private const val TAG = "Auth"
    const val ADMIN_EMAIL = "farahrifa58@gmail.com"
    const val ACCESS_DENIED_MESSAGE = "Akses Ditolak: Akun ini tidak memiliki izin Admin."

    fun isAuthorizedAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        val email = user.email?.trim() ?: return false
        return email.equals(ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthorized: (FirebaseUser) -> Unit,
        onAccessDenied: (String) -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        val existing = Firebase.auth.currentUser
        if (existing != null) {
            if (isAuthorizedAdmin(existing)) {
                onAuthorized(existing)
            } else {
                signOutAdmin(credentialManager, scope) {
                    onAccessDenied(ACCESS_DENIED_MESSAGE)
                }
            }
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val credential = credentialManager.getCredential(context, request).credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val result = Firebase.auth
                        .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                        .await()
                    val signedInUser = result.user
                    if (isAuthorizedAdmin(signedInUser)) {
                        onAuthorized(signedInUser!!)
                    } else {
                        performNonCancellableSignOut(credentialManager)
                        onAccessDenied(ACCESS_DENIED_MESSAGE)
                    }
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthorized: (FirebaseUser) -> Unit,
        onAccessDenied: (String) -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError(context.getString(R.string.auth_error_config_missing))
            return
        }

        val activityContext = context as? Activity ?: run {
            onAuthError(context.getString(R.string.auth_error_generic))
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val credential = credentialManager.getCredential(activityContext, request).credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authResult = Firebase.auth
                        .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                        .await()
                    val user = authResult.user
                    if (isAuthorizedAdmin(user)) {
                        onAuthorized(user!!)
                    } else {
                        performNonCancellableSignOut(credentialManager)
                        onAccessDenied(ACCESS_DENIED_MESSAGE)
                    }
                } else {
                    onAuthError(context.getString(R.string.auth_error_generic))
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: context.getString(R.string.auth_error_generic))
            }
        }
    }

    fun signOutAdmin(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            performNonCancellableSignOut(credentialManager)
            onComplete()
        }
    }

    private suspend fun performNonCancellableSignOut(credentialManager: CredentialManager) {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
            }
        }
    }
}
