package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActivationCodeDoc
import com.example.data.AppConfigStore
import com.example.data.BuyerUserDoc
import com.example.data.CodeFilter
import com.example.data.DashboardTab
import com.example.data.LicenseRepository
import com.example.data.ManagedAppConfig
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardStats(
    val totalCodes: Int = 0,
    val availableCodes: Int = 0,
    val usedCodes: Int = 0,
    val activeBuyers: Int = 0
)

data class DashboardControlsState(
    val selectedTab: DashboardTab = DashboardTab.CODES,
    val selectedFilter: CodeFilter = CodeFilter.ALL,
    val codeSearchQuery: String = "",
    val buyerSearchQuery: String = "",
    val generateBatchCount: Int = 5,
    val isGenerating: Boolean = false,
    val busyCodeId: String? = null,
    val busyUserId: String? = null,
    val lastGeneratedCodes: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseDashboardViewModel(
    private val appConfigStore: AppConfigStore,
    private val repository: LicenseRepository,
    val currentUserId: String,
    val adminEmail: String
) : ViewModel() {

    private val refreshNonce = MutableStateFlow(0)

    val apps: StateFlow<List<ManagedAppConfig>> = appConfigStore.apps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val isDarkTheme: StateFlow<Boolean> = appConfigStore.isDarkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = true
    )

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            appConfigStore.setDarkTheme(enabled)
        }
    }

    val activeApp: StateFlow<ManagedAppConfig?> = combine(
        appConfigStore.apps,
        appConfigStore.selectedAppId
    ) { appList, selectedId ->
        appList.firstOrNull { it.id == selectedId } ?: appList.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    private val activeDatabaseKey = combine(
        activeApp.map { it?.databaseId.orEmpty() },
        refreshNonce
    ) { dbId, nonce -> dbId to nonce }
        .distinctUntilChanged()

    val codesState: StateFlow<UiState<List<ActivationCodeDoc>>> = activeDatabaseKey
        .flatMapLatest { (databaseId, _) ->
            repository.observeActivationCodes(databaseId)
                .map<List<ActivationCodeDoc>, UiState<List<ActivationCodeDoc>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing activation_codes on $databaseId", rootError)
                    emit(UiState.Error(rootError.message ?: "Gagal memuat activation_codes"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val buyersState: StateFlow<UiState<List<BuyerUserDoc>>> = activeDatabaseKey
        .flatMapLatest { (databaseId, _) ->
            repository.observeUsers(databaseId)
                .map<List<BuyerUserDoc>, UiState<List<BuyerUserDoc>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing users on $databaseId", rootError)
                    emit(UiState.Error(rootError.message ?: "Gagal memuat users"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val stats: StateFlow<DashboardStats> = combine(codesState, buyersState) { codes, buyers ->
        val codeList = (codes as? UiState.Success)?.data.orEmpty()
        val buyerList = (buyers as? UiState.Success)?.data.orEmpty()
        DashboardStats(
            totalCodes = codeList.size,
            availableCodes = codeList.count { it.isAvailableStatus },
            usedCodes = codeList.count { it.isUsedStatus },
            activeBuyers = buyerList.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardStats()
    )

    private val _controls = MutableStateFlow(DashboardControlsState())
    val controls: StateFlow<DashboardControlsState> = _controls.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    fun selectTab(tab: DashboardTab) {
        _controls.value = _controls.value.copy(selectedTab = tab)
    }

    fun selectFilter(filter: CodeFilter) {
        _controls.value = _controls.value.copy(selectedFilter = filter)
    }

    fun updateCodeSearch(query: String) {
        _controls.value = _controls.value.copy(codeSearchQuery = query)
    }

    fun updateBuyerSearch(query: String) {
        _controls.value = _controls.value.copy(buyerSearchQuery = query)
    }

    fun selectGenerateCount(count: Int) {
        if (count in LicenseRepository.ALLOWED_BATCH_COUNTS) {
            _controls.value = _controls.value.copy(generateBatchCount = count)
        }
    }

    fun dismissLastGeneratedBanner() {
        _controls.value = _controls.value.copy(lastGeneratedCodes = emptyList())
    }

    fun refreshData() {
        refreshNonce.value += 1
    }

    fun selectApp(appId: String) {
        viewModelScope.launch {
            appConfigStore.selectApp(appId)
            _controls.value = _controls.value.copy(lastGeneratedCodes = emptyList())
        }
    }

    fun saveInlineDatabaseId(appId: String, databaseId: String) {
        viewModelScope.launch {
            appConfigStore.updateDatabaseId(appId, databaseId)
            _snackbarEvents.tryEmit("Pengaturan database berhasil disimpan")
        }
    }

    fun addManagedApp(name: String, prefix: String, databaseId: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            appConfigStore.addApp(name, prefix, databaseId)
            _controls.value = _controls.value.copy(lastGeneratedCodes = emptyList())
            _snackbarEvents.tryEmit("Aplikasi ${name.trim()} berhasil ditambahkan")
        }
    }

    fun updateManagedApp(appId: String, name: String, prefix: String, databaseId: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            appConfigStore.updateApp(appId, name, prefix, databaseId)
            _snackbarEvents.tryEmit("Pengaturan aplikasi diperbarui")
        }
    }

    fun deleteManagedApp(appId: String) {
        viewModelScope.launch {
            appConfigStore.deleteApp(appId)
            _snackbarEvents.tryEmit("Aplikasi dihapus dari daftar")
        }
    }

    fun generateCodes() {
        val currentApp = activeApp.value ?: return
        val count = _controls.value.generateBatchCount
        if (_controls.value.isGenerating) return

        viewModelScope.launch {
            _controls.value = _controls.value.copy(isGenerating = true)
            val result = repository.generateActivationCodes(
                databaseId = currentApp.databaseId,
                prefix = currentApp.normalizedPrefix,
                count = count
            )
            _controls.value = _controls.value.copy(isGenerating = false)
            result.onSuccess { generated ->
                _controls.value = _controls.value.copy(lastGeneratedCodes = generated)
                _snackbarEvents.tryEmit("${generated.size} kode aktivasi berhasil dibuat")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(
                    "Gagal membuat kode: ${err.localizedMessage ?: "Periksa koneksi & izin database"}"
                )
            }
        }
    }

    fun deleteCode(code: ActivationCodeDoc) {
        val currentApp = activeApp.value ?: return
        if (!code.isAvailableStatus || _controls.value.busyCodeId != null) return

        viewModelScope.launch {
            _controls.value = _controls.value.copy(busyCodeId = code.id)
            val result = repository.deleteAvailableCode(currentApp.databaseId, code.id)
            _controls.value = _controls.value.copy(busyCodeId = null)
            result.onSuccess {
                _snackbarEvents.tryEmit("Kode aktivasi berhasil dihapus")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(
                    "Gagal menghapus kode: ${err.localizedMessage ?: "Operasi ditolak"}"
                )
            }
        }
    }

    fun resetBuyerDevice(buyer: BuyerUserDoc) {
        val currentApp = activeApp.value ?: return
        if (_controls.value.busyUserId != null) return

        viewModelScope.launch {
            _controls.value = _controls.value.copy(busyUserId = buyer.id)
            val result = repository.resetUserDevice(
                databaseId = currentApp.databaseId,
                userId = buyer.id,
                licenseCode = buyer.resolvedLicenseCode
            )
            _controls.value = _controls.value.copy(busyUserId = null)
            result.onSuccess {
                _snackbarEvents.tryEmit("Perangkat berhasil direset. Pembeli siap login di HP baru.")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(
                    "Gagal reset perangkat: ${err.localizedMessage ?: "Operasi ditolak"}"
                )
            }
        }
    }

    private companion object {
        const val TAG = "LicenseDashboardVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
