package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ActivationCodeDoc
import com.example.data.BuyerUserDoc
import com.example.data.CodeFilter
import com.example.data.DashboardTab
import com.example.data.ManagedAppConfig
import com.example.data.UiState
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoSmallStyle
import com.example.viewmodel.LicenseDashboardViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseDashboardScreen(
    viewModel: LicenseDashboardViewModel,
    defaultDatabaseId: String,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val activeApp by viewModel.activeApp.collectAsStateWithLifecycle()
    val codesState by viewModel.codesState.collectAsStateWithLifecycle()
    val buyersState by viewModel.buyersState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val controls by viewModel.controls.collectAsStateWithLifecycle()

    var showAddAppDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<ManagedAppConfig?>(null) }
    var pendingDeleteApp by remember { mutableStateOf<ManagedAppConfig?>(null) }
    var pendingDeleteCode by remember { mutableStateOf<ActivationCodeDoc?>(null) }
    var pendingResetBuyer by remember { mutableStateOf<BuyerUserDoc?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val copiedMessage = stringResource(R.string.snackbar_code_copied)

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch {
            snackbarHostState.showSnackbar(copiedMessage)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = semantic.topBarContainer,
                        titleContentColor = semantic.onTopBar,
                        actionIconContentColor = semantic.onTopBar
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_license_admin_icon),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.dashboard_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = viewModel.adminEmail,
                                    style = MonoSmallStyle,
                                    color = semantic.onTopBar.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshData() },
                            modifier = Modifier.testTag("refresh_dashboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.dashboard_refresh)
                            )
                        }
                        IconButton(
                            onClick = onSignOutClick,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.dashboard_sign_out)
                            )
                        }
                        // Top-right Settings button (Light / Dark Theme)
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.dashboard_settings)
                            )
                        }
                    }
                )
                HorizontalDivider(color = semantic.cardBorder, thickness = 1.dp)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Compact Multi-App Pill Switcher + Connected Badge (no raw DB input shown)
                item(key = "switcher") {
                    MultiAppSwitcherCard(
                        apps = apps,
                        activeApp = activeApp,
                        onSelectApp = viewModel::selectApp,
                        onAddAppClick = { showAddAppDialog = true },
                        onEditAppClick = { app -> editingApp = app },
                        onDeleteAppRequest = { app -> pendingDeleteApp = app }
                    )
                }

                // 2. Compact 4-Column Mini KPI Summary Bar
                item(key = "stats") {
                    StatsOverviewSection(stats = stats)
                }

                // 3. Prominent Activation Code Generator Card
                item(key = "generator") {
                    CodeGeneratorCard(
                        prefix = activeApp?.normalizedPrefix ?: "JUALAN",
                        selectedCount = controls.generateBatchCount,
                        isGenerating = controls.isGenerating,
                        lastGeneratedCodes = controls.lastGeneratedCodes,
                        onSelectCount = viewModel::selectGenerateCount,
                        onGenerateClick = viewModel::generateCodes,
                        onCopyText = ::copyToClipboard,
                        onDismissGenerated = viewModel::dismissLastGeneratedBanner
                    )
                }

                // 4. Modern Segmented Control with Item Count Badges
                item(key = "tabs") {
                    ModernSegmentedTabs(
                        selectedTab = controls.selectedTab,
                        codesCount = stats.totalCodes,
                        buyersCount = stats.activeBuyers,
                        onSelectTab = viewModel::selectTab
                    )
                }

                // 5. Tab Content
                if (controls.selectedTab == DashboardTab.CODES) {
                    item(key = "codes_controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = controls.codeSearchQuery,
                                onValueChange = viewModel::updateCodeSearch,
                                placeholder = { Text(stringResource(R.string.search_codes_hint)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = if (controls.codeSearchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { viewModel.updateCodeSearch("") }) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Hapus pencarian"
                                            )
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_codes_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = controls.selectedFilter == CodeFilter.ALL,
                                    onClick = { viewModel.selectFilter(CodeFilter.ALL) },
                                    label = { Text(stringResource(R.string.filter_all)) },
                                    modifier = Modifier.testTag("filter_all")
                                )
                                FilterChip(
                                    selected = controls.selectedFilter == CodeFilter.AVAILABLE,
                                    onClick = { viewModel.selectFilter(CodeFilter.AVAILABLE) },
                                    label = { Text(stringResource(R.string.filter_available)) },
                                    modifier = Modifier.testTag("filter_available")
                                )
                                FilterChip(
                                    selected = controls.selectedFilter == CodeFilter.USED,
                                    onClick = { viewModel.selectFilter(CodeFilter.USED) },
                                    label = { Text(stringResource(R.string.filter_used)) },
                                    modifier = Modifier.testTag("filter_used")
                                )
                            }
                        }
                    }

                    when (val state = codesState) {
                        is UiState.Loading -> {
                            item(key = "codes_loading") { LoadingCard() }
                        }
                        is UiState.Error -> {
                            item(key = "codes_error") {
                                ErrorStateCard(
                                    databaseId = activeApp?.databaseId.orEmpty(),
                                    detail = state.message,
                                    onRetry = viewModel::refreshData
                                )
                            }
                        }
                        is UiState.Success -> {
                            val query = controls.codeSearchQuery.trim().lowercase()
                            val filtered = state.data.filter { code ->
                                val matchesFilter = when (controls.selectedFilter) {
                                    CodeFilter.ALL -> true
                                    CodeFilter.AVAILABLE -> code.isAvailableStatus
                                    CodeFilter.USED -> code.isUsedStatus
                                }
                                val matchesQuery = query.isEmpty() ||
                                    code.id.lowercase().contains(query) ||
                                    (code.resolvedBusinessName?.lowercase()?.contains(query) == true) ||
                                    (code.resolvedBuyerEmail?.lowercase()?.contains(query) == true)
                                matchesFilter && matchesQuery
                            }

                            if (filtered.isEmpty()) {
                                item(key = "codes_empty") {
                                    EmptyStateCard(
                                        title = if (state.data.isEmpty()) {
                                            stringResource(R.string.empty_codes_title)
                                        } else {
                                            stringResource(R.string.empty_search_title)
                                        },
                                        subtitle = if (state.data.isEmpty()) {
                                            stringResource(R.string.empty_codes_subtitle)
                                        } else {
                                            stringResource(R.string.empty_search_subtitle)
                                        }
                                    )
                                }
                            } else {
                                items(filtered, key = { it.id }) { code ->
                                    ActivationCodeCard(
                                        code = code,
                                        isBusy = controls.busyCodeId == code.id,
                                        onCopy = { copyToClipboard("Activation Code", code.id) },
                                        onDeleteRequest = { pendingDeleteCode = code }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "buyers_search") {
                        OutlinedTextField(
                            value = controls.buyerSearchQuery,
                            onValueChange = viewModel::updateBuyerSearch,
                            placeholder = { Text(stringResource(R.string.search_buyers_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = if (controls.buyerSearchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { viewModel.updateBuyerSearch("") }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Hapus pencarian"
                                        )
                                    }
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_buyers_input")
                        )
                    }

                    when (val state = buyersState) {
                        is UiState.Loading -> {
                            item(key = "buyers_loading") { LoadingCard() }
                        }
                        is UiState.Error -> {
                            item(key = "buyers_error") {
                                ErrorStateCard(
                                    databaseId = activeApp?.databaseId.orEmpty(),
                                    detail = state.message,
                                    onRetry = viewModel::refreshData
                                )
                            }
                        }
                        is UiState.Success -> {
                            val query = controls.buyerSearchQuery.trim().lowercase()
                            val filtered = state.data.filter { buyer ->
                                query.isEmpty() ||
                                    buyer.resolvedBusinessName.lowercase().contains(query) ||
                                    buyer.resolvedEmail.lowercase().contains(query) ||
                                    buyer.resolvedLicenseCode.lowercase().contains(query) ||
                                    buyer.id.lowercase().contains(query)
                            }

                            if (filtered.isEmpty()) {
                                item(key = "buyers_empty") {
                                    EmptyStateCard(
                                        title = if (state.data.isEmpty()) {
                                            stringResource(R.string.empty_buyers_title)
                                        } else {
                                            stringResource(R.string.empty_search_title)
                                        },
                                        subtitle = if (state.data.isEmpty()) {
                                            stringResource(R.string.empty_buyers_subtitle)
                                        } else {
                                            stringResource(R.string.empty_search_subtitle)
                                        }
                                    )
                                }
                            } else {
                                items(filtered, key = { it.id }) { buyer ->
                                    BuyerDeviceCard(
                                        buyer = buyer,
                                        isBusy = controls.busyUserId == buyer.id,
                                        onResetDeviceRequest = { pendingResetBuyer = buyer }
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // Dialogs
    if (showSettingsDialog) {
        SettingsThemeDialog(
            isDarkTheme = isDarkTheme,
            onSelectTheme = { dark -> onToggleTheme(dark) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showAddAppDialog) {
        AppConfigDialog(
            initialApp = null,
            defaultDatabaseId = defaultDatabaseId,
            onDismiss = { showAddAppDialog = false },
            onSave = { name, prefix, dbId ->
                viewModel.addManagedApp(name, prefix, dbId)
                showAddAppDialog = false
            }
        )
    }

    editingApp?.let { app ->
        AppConfigDialog(
            initialApp = app,
            defaultDatabaseId = defaultDatabaseId,
            onDismiss = { editingApp = null },
            onSave = { name, prefix, dbId ->
                viewModel.updateManagedApp(app.id, name, prefix, dbId)
                editingApp = null
            },
            onDeleteRequest = { appToDelete ->
                editingApp = null
                pendingDeleteApp = appToDelete
            }
        )
    }

    pendingDeleteApp?.let { app ->
        DeleteAppConfirmDialog(
            app = app,
            onDismiss = { pendingDeleteApp = null },
            onConfirm = {
                viewModel.deleteManagedApp(app.id)
                pendingDeleteApp = null
            }
        )
    }

    pendingDeleteCode?.let { code ->
        DeleteCodeConfirmDialog(
            code = code,
            databaseId = activeApp?.databaseId.orEmpty(),
            onDismiss = { pendingDeleteCode = null },
            onConfirm = {
                viewModel.deleteCode(code)
                pendingDeleteCode = null
            }
        )
    }

    pendingResetBuyer?.let { buyer ->
        ResetDeviceConfirmDialog(
            buyer = buyer,
            onDismiss = { pendingResetBuyer = null },
            onConfirm = {
                viewModel.resetBuyerDevice(buyer)
                pendingResetBuyer = null
            }
        )
    }
}

@Composable
private fun LoadingCard() {
    val semantic = LocalSemanticColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ErrorStateCard(
    databaseId: String,
    detail: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.error_firestore_load, databaseId),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("error_retry_button")
            ) {
                Text(stringResource(R.string.error_retry))
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String
) {
    val semantic = LocalSemanticColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(semantic.subtleSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
