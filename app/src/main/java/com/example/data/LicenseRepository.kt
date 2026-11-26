package com.example.data

import com.example.auth.AdminAuthHelper
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class LicenseRepository(
    private val defaultDatabaseId: String,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val secureRandom = SecureRandom()

    private fun firestoreFor(databaseId: String): FirebaseFirestore {
        val targetDatabaseId = databaseId.trim().ifBlank { defaultDatabaseId }
        return FirebaseFirestore.getInstance(FirebaseApp.getInstance(), targetDatabaseId)
    }

    private fun requireAdminUid(): String {
        val user = auth.currentUser
        if (user == null || !AdminAuthHelper.isAuthorizedAdmin(user)) {
            throw IllegalStateException("Akses Ditolak: Akun ini tidak memiliki izin Admin.")
        }
        return user.uid
    }

    fun observeActivationCodes(databaseId: String): Flow<List<ActivationCodeDoc>> = flow {
        val uid = requireAdminUid()
        val db = firestoreFor(databaseId)
        val path = "activation_codes"
        emitAll(
            db.collection(COLLECTION_ACTIVATION_CODES)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ActivationCodeDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUsers(databaseId: String): Flow<List<BuyerUserDoc>> = flow {
        val uid = requireAdminUid()
        val db = firestoreFor(databaseId)
        val path = "users"
        emitAll(
            db.collection(COLLECTION_USERS)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BuyerUserDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun generateActivationCodes(
        databaseId: String,
        prefix: String,
        count: Int
    ): Result<List<String>> {
        val path = COLLECTION_ACTIVATION_CODES
        return try {
            val uid = requireAdminUid()
            require(count in ALLOWED_BATCH_COUNTS) {
                "Jumlah kode harus salah satu dari: $ALLOWED_BATCH_COUNTS"
            }
            val cleanPrefix = AppConfigStore.sanitizePrefix(prefix)
            val db = firestoreFor(databaseId)
            val generatedCodes = buildSet {
                while (size < count) {
                    add("$cleanPrefix-${randomSegment(4)}-${randomSegment(4)}")
                }
            }.toList()

            val batch = db.batch()
            generatedCodes.forEach { code ->
                val docRef = db.collection(COLLECTION_ACTIVATION_CODES).document(code)
                val payload = mapOf(
                    "status" to "available",
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(generatedCodes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAvailableCode(databaseId: String, codeId: String): Result<Unit> {
        val path = "$COLLECTION_ACTIVATION_CODES/$codeId"
        return try {
            requireAdminUid()
            require(codeId.isNotBlank() && VALID_ID_REGEX.matches(codeId)) {
                "ID kode aktivasi tidak valid"
            }
            val db = firestoreFor(databaseId)
            db.collection(COLLECTION_ACTIVATION_CODES)
                .document(codeId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun resetUserDevice(
        databaseId: String,
        userId: String,
        licenseCode: String
    ): Result<Unit> {
        val path = "$COLLECTION_USERS/$userId"
        return try {
            requireAdminUid()
            require(userId.isNotBlank() && VALID_ID_REGEX.matches(userId)) {
                "ID pengguna tidak valid"
            }
            val db = firestoreFor(databaseId)
            val userRef = db.collection(COLLECTION_USERS).document(userId)
            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "registeredDeviceId" to "",
                    "registeredDeviceIds" to emptyList<String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val trimmedCode = licenseCode.trim()
            if (trimmedCode.isNotBlank() && VALID_ID_REGEX.matches(trimmedCode)) {
                val codeRef = db.collection(COLLECTION_ACTIVATION_CODES).document(trimmedCode)
                val codeSnapshot = codeRef.get().await()
                if (codeSnapshot.exists()) {
                    batch.update(
                        codeRef,
                        mapOf(
                            "registeredDeviceId" to "",
                            "registeredDeviceIds" to emptyList<String>()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun randomSegment(length: Int): String = buildString(length) {
        repeat(length) {
            append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
        }
    }

    companion object {
        const val COLLECTION_ACTIVATION_CODES = "activation_codes"
        const val COLLECTION_USERS = "users"
        val ALLOWED_BATCH_COUNTS = listOf(1, 5, 10, 20)
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
