package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AdminAuthHelper
import com.example.data.AppConfigStore
import com.example.data.LicenseRepository
import com.example.ui.AuthScreen
import com.example.ui.LicenseDashboardScreen
import com.example.ui.theme.LicenseAdminTheme
import com.example.viewmodel.LicenseDashboardViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val defaultDatabaseId = remember { getString(R.string.firestore_database_id) }
            val appConfigStore = remember { AppConfigStore(applicationContext, defaultDatabaseId) }
            val isDarkTheme by appConfigStore.isDarkTheme.collectAsStateWithLifecycle(initialValue = true)
            val themeScope = rememberCoroutineScope()

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            LicenseAdminTheme(darkTheme = isDarkTheme) {
                AppContent(
                    appConfigStore = appConfigStore,
                    defaultDatabaseId = defaultDatabaseId,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { enabled ->
                        themeScope.launch { appConfigStore.setDarkTheme(enabled) }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    appConfigStore: AppConfigStore,
    defaultDatabaseId: String,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isCheckingSession by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null && !AdminAuthHelper.isAuthorizedAdmin(user)) {
                currentUser = null
                authError = AdminAuthHelper.ACCESS_DENIED_MESSAGE
                AdminAuthHelper.signOutAdmin(credentialManager, scope)
            } else {
                currentUser = user
                if (user != null) {
                    authError = null
                }
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        AdminAuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthorized = { user ->
                currentUser = user
                authError = null
                isCheckingSession = false
            },
            onAccessDenied = { message ->
                currentUser = null
                authError = message
                isCheckingSession = false
            },
            onUnauthenticated = {
                isCheckingSession = false
            }
        )
    }

    val authorizedUser = currentUser?.takeIf { AdminAuthHelper.isAuthorizedAdmin(it) }

    if (authorizedUser == null) {
        AuthScreen(
            errorMessage = authError,
            isCheckingSession = isCheckingSession,
            onAuthorized = { user ->
                currentUser = user
                authError = null
            },
            onError = { message ->
                authError = message
            }
        )
    } else {
        val uid = authorizedUser.uid
        val adminEmail = authorizedUser.email ?: AdminAuthHelper.ADMIN_EMAIL

        key(uid) {
            val viewModel: LicenseDashboardViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val repository = LicenseRepository(defaultDatabaseId = defaultDatabaseId)
                        LicenseDashboardViewModel(
                            appConfigStore = appConfigStore,
                            repository = repository,
                            currentUserId = uid,
                            adminEmail = adminEmail
                        )
                    }
                }
            )

            LicenseDashboardScreen(
                viewModel = viewModel,
                defaultDatabaseId = defaultDatabaseId,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onSignOutClick = {
                    AdminAuthHelper.signOutAdmin(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}
