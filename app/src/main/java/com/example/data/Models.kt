package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

data class ManagedAppConfig(
    val id: String,
    val name: String,
    val codePrefix: String,
    val databaseId: String,
    val isDefault: Boolean = false
) {
    val normalizedPrefix: String
        get() = codePrefix.trim().uppercase().trimEnd('-').ifBlank { "APP" }
}

@Keep
@IgnoreExtraProperties
data class ActivationCodeDoc(
    @DocumentId val id: String = "",
    val status: String = "available",
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val usedByUid: String? = null,
    val usedByEmail: String? = null,
    val buyerEmail: String? = null,
    val email: String? = null,
    val businessName: String? = null,
    val namaUsaha: String? = null,
    val shopName: String? = null,
    val usedByBusinessName: String? = null,
    val registeredDeviceId: String = "",
    val registeredDeviceIds: List<String> = emptyList(),
    @ServerTimestamp val usedAt: Timestamp? = null
) {
    val isAvailableStatus: Boolean
        get() = status.equals("available", ignoreCase = true)

    val isUsedStatus: Boolean
        get() = status.equals("used", ignoreCase = true)

    val resolvedBusinessName: String?
        get() = listOf(businessName, usedByBusinessName, namaUsaha, shopName)
            .firstOrNull { !it.isNullOrBlank() }

    val resolvedBuyerEmail: String?
        get() = listOf(usedByEmail, buyerEmail, email)
            .firstOrNull { !it.isNullOrBlank() }
}

@Keep
@IgnoreExtraProperties
data class BuyerUserDoc(
    @DocumentId val id: String = "",
    val businessName: String? = null,
    val namaUsaha: String? = null,
    val shopName: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val activationCode: String? = null,
    val licenseCode: String? = null,
    val usedCode: String? = null,
    val registeredDeviceId: String = "",
    val registeredDeviceIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedBusinessName: String
        get() = listOf(businessName, namaUsaha, shopName, displayName)
            .firstOrNull { !it.isNullOrBlank() }
            ?: ""

    val resolvedEmail: String
        get() = email.orEmpty()

    val resolvedLicenseCode: String
        get() = listOf(activationCode, licenseCode, usedCode)
            .firstOrNull { !it.isNullOrBlank() }
            ?: ""

    val activeDeviceIds: List<String>
        get() = registeredDeviceIds.filter { it.isNotBlank() }

    val connectedDeviceCount: Int
        get() = when {
            activeDeviceIds.isNotEmpty() -> activeDeviceIds.size
            registeredDeviceId.isNotBlank() -> 1
            else -> 0
        }

    val deviceBound: Boolean
        get() = connectedDeviceCount > 0
}

enum class CodeFilter {
    ALL,
    AVAILABLE,
    USED
}

enum class DashboardTab {
    CODES,
    BUYERS
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
