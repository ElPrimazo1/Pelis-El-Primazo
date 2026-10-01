package com.example.peliselprimazo.data.remote.datasource

import androidx.core.net.toUri
import com.example.peliselprimazo.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthDataSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun getAuthStateFlow(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            val user = firebaseUser?.let {
                User(
                    username = it.displayName ?: "Usuario",
                    email = it.email ?: "",
                    profilePictureUri = it.photoUrl?.toString(),
                    isLoggedIn = true
                )
            }
            trySend(user)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    suspend fun register(username: String, email: String, pass: String): String {
        val res = firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
        val fbUser = res.user ?: throw Exception("Error al crear usuario")
        fbUser.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(username).build()).await()
        return fbUser.uid
    }

    suspend fun login(email: String, pass: String): Boolean = try {
        firebaseAuth.signInWithEmailAndPassword(email, pass).await()
        true
    } catch (_: Exception) { false }

    suspend fun logout() {
        firebaseAuth.signOut()
    }

    suspend fun updateAvatar(uri: String) {
        val user = firebaseAuth.currentUser ?: return
        user.updateProfile(UserProfileChangeRequest.Builder().setPhotoUri(uri.toUri()).build()).await()
        firestore.collection("users").document(user.uid).update("profilePictureUri", uri).await()
    }

    suspend fun resetPassword(email: String) {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    fun getCurrentUserUid(): String? = firebaseAuth.currentUser?.uid
}
