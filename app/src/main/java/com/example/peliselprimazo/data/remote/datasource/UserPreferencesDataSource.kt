package com.example.peliselprimazo.data.remote.datasource

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteUserData(
    val liked: List<Int> = emptyList(),
    val saved: List<Int> = emptyList(),
    val watchLater: List<Int> = emptyList(),
    val finished: List<Int> = emptyList(),
    val watching: Map<Int, RemoteWatchingProgress> = emptyMap()
)

data class RemoteWatchingProgress(
    val position: Long,
    val duration: Long,
    val timestamp: Long
)

@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COLL_USERS = "users"
        private const val COLL_TRENDS = "trends"
        private const val COLL_REPORTS = "reports"
        private const val KEY_LIKED = "liked"
        private const val KEY_SAVED = "saved"
        private const val KEY_WATCH_LATER = "watchLater"
        private const val KEY_FINISHED = "finished"
        private const val KEY_WATCHING = "watching"
    }

    suspend fun getUserData(uid: String): RemoteUserData? = try {
        val doc = firestore.collection(COLL_USERS).document(uid).get().await()
        if (doc.exists()) {
            RemoteUserData(
                liked = doc.getIntList(KEY_LIKED),
                saved = doc.getIntList(KEY_SAVED),
                watchLater = doc.getIntList(KEY_WATCH_LATER),
                finished = doc.getIntList(KEY_FINISHED),
                watching = parseWatchingMap(doc.get(KEY_WATCHING) as? Map<*, *>)
            )
        } else null
    } catch (_: Exception) { null }

    suspend fun getTrendingIds(limit: Int = 10): List<Int> = try {
        val snapshot = firestore.collection(COLL_TRENDS)
            .orderBy("count", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
        snapshot.documents.mapNotNull { it.id.toIntOrNull() }
    } catch (_: Exception) { emptyList() }

    suspend fun updateList(uid: String, listKey: String, movieId: Int, add: Boolean) {
        val action = if (add) FieldValue.arrayUnion(movieId) else FieldValue.arrayRemove(movieId)
        firestore.collection(COLL_USERS).document(uid).update(listKey, action).await()
    }

    suspend fun updateTrend(movieId: Int, field: String, increment: Long) {
        firestore.collection(COLL_TRENDS).document(movieId.toString())
            .set(mapOf(field to FieldValue.increment(increment)), SetOptions.merge())
    }

    suspend fun updateWatchingProgress(uid: String, movieId: Int, position: Long, duration: Long, timestamp: Long) {
        val data = mapOf(
            "$KEY_WATCHING.$movieId.position" to position,
            "$KEY_WATCHING.$movieId.duration" to duration,
            "$KEY_WATCHING.$movieId.timestamp" to timestamp
        )
        firestore.collection(COLL_USERS).document(uid).update(data).await()
    }
    
    suspend fun updateWatchingTimestamp(uid: String, movieId: Int, timestamp: Long) {
        firestore.collection(COLL_USERS).document(uid).update("$KEY_WATCHING.$movieId.timestamp", timestamp).await()
    }

    suspend fun reportBrokenLink(report: Map<String, Any>) {
        firestore.collection(COLL_REPORTS).add(report).await()
    }
    
    suspend fun saveInitialUserData(uid: String, userData: Map<String, Any>) {
        firestore.collection(COLL_USERS).document(uid).set(userData).await()
    }

    private fun DocumentSnapshot.getIntList(f: String): List<Int> = 
        (get(f) as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()

    private fun parseWatchingMap(rawMap: Map<*, *>?): Map<Int, RemoteWatchingProgress> {
        return rawMap?.mapNotNull { (idStr, data) ->
            val id = idStr.toString().toIntOrNull() ?: return@mapNotNull null
            val m = data as? Map<*, *> ?: return@mapNotNull null
            id to RemoteWatchingProgress(
                position = (m["position"] as? Long) ?: 0L,
                duration = (m["duration"] as? Long) ?: 0L,
                timestamp = (m["timestamp"] as? Long) ?: System.currentTimeMillis()
            )
        }?.toMap() ?: emptyMap()
    }
}
