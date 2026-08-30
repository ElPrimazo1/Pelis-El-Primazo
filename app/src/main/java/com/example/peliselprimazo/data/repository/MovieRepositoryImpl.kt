package com.example.peliselprimazo.data.repository

import android.util.Log
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.peliselprimazo.BuildConfig
import com.example.peliselprimazo.data.local.dao.MovieDao
import com.example.peliselprimazo.data.local.dao.SearchHistoryDao
import com.example.peliselprimazo.data.local.entities.MovieEntity
import com.example.peliselprimazo.data.local.entities.SearchHistoryEntity
import com.example.peliselprimazo.data.remote.StreamtapeApi
import com.example.peliselprimazo.data.remote.TmdbApi
import com.example.peliselprimazo.domain.model.CastMember
import com.example.peliselprimazo.domain.model.ContentType
import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.model.ServerLink
import com.example.peliselprimazo.domain.model.User
import com.example.peliselprimazo.domain.repository.MovieRepository
import com.example.peliselprimazo.ui.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InternalFileInfo(
    val cleanTitle: String,
    val year: String?,
    val season: Int?,
    val episode: Int?,
    val isTv: Boolean,
    val isAnime: Boolean
)

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val streamtapeApi: StreamtapeApi,
    private val tmdbApi: TmdbApi,
    private val movieDao: MovieDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val notificationHelper: NotificationHelper
) : MovieRepository {

    private val streamtapeLogin = BuildConfig.STREAMTAPE_LOGIN
    private val streamtapeKey = BuildConfig.STREAMTAPE_KEY
    private val tmdbApiKey = BuildConfig.TMDB_API_KEY

    private val tmdbSemaphore = Semaphore(3)
    
    private val _currentUser = MutableStateFlow<User?>(null)
    private val _avatarOverride = MutableStateFlow<String?>(null)

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                _currentUser.value = User(
                    username = firebaseUser.displayName ?: "Usuario",
                    email = firebaseUser.email ?: "",
                    profilePictureUri = firebaseUser.photoUrl?.toString(),
                    isLoggedIn = true
                )
            } else {
                _currentUser.value = null
                // No limpiamos el override para que persista en la sesión si se seleccionó antes
            }
        }
    }

    override fun getAllContent(): Flow<List<Movie>> = movieDao.getAllMovies().map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getPagedContent(): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = 20, prefetchDistance = 2),
            pagingSourceFactory = { movieDao.getPagedMovies() }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    override fun getMovies(): Flow<List<Movie>> = movieDao.getMoviesByType(ContentType.MOVIE.name).map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getSeries(): Flow<List<Movie>> = movieDao.getMoviesByType(ContentType.TV.name).map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getTrends(): Flow<List<Movie>> = flow {
        try {
            val snapshot = firestore.collection("trends")
                .orderBy("count", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()
            
            val trendingIds = snapshot.documents.mapNotNull { it.id.toIntOrNull() }
            val movies = trendingIds.mapNotNull { id ->
                movieDao.getMovieById(id)?.toDomain()
            }
            emit(movies)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun refreshContent() {
        withContext(Dispatchers.IO) {
            val syncStartTime = System.currentTimeMillis()
            try {
                syncUserData()
                val allFiles = getStreamtapeFilesSafe()
                
                if (allFiles.isEmpty()) {
                    movieDao.clearAllMovies()
                    return@withContext
                }

                val parsedItems = allFiles.map { pair ->
                    val info = parseFileName(pair.first)
                    info to pair.second.copy(season = info.season, episode = info.episode)
                }

                val grouped = parsedItems.groupBy { pair ->
                    val info = pair.first
                    val key = info.cleanTitle.lowercase().trim()
                    if (info.isTv || info.isAnime) "tv|$key" else "movie|$key|${info.year ?: "unknown"}"
                }

                val deferredMovies = grouped.values.map { items ->
                    async {
                        val primaryInfo = items.first().first
                        val allLinks = items.map { it.second }.distinctBy { "${it.serverName}_${it.fileId}" }
                        val id = (primaryInfo.cleanTitle + (primaryInfo.year ?: "")).hashCode()

                        val existing = movieDao.getMovieById(id)
                        
                        val tmdbData = tmdbSemaphore.withPermit {
                            try {
                                if (tmdbApiKey.isNotBlank()) fetchTmdbData(primaryInfo) else null
                            } catch (_: Exception) { null }
                        }

                        val entity = MovieEntity(
                            id = id,
                            title = tmdbData?.title ?: primaryInfo.cleanTitle.capitalizeWords(),
                            overview = tmdbData?.overview ?: existing?.overview ?: "Contenido disponible.",
                            posterUrl = tmdbData?.posterUrl ?: existing?.posterUrl,
                            backdropUrl = tmdbData?.backdropUrl ?: existing?.backdropUrl,
                            releaseDate = tmdbData?.releaseDate ?: primaryInfo.year ?: "Desconocido",
                            rating = tmdbData?.rating ?: existing?.rating ?: 0.0,
                            serverLinks = allLinks,
                            contentType = when {
                                primaryInfo.isAnime -> ContentType.ANIME.name
                                primaryInfo.isTv -> ContentType.TV.name
                                else -> ContentType.MOVIE.name
                            },
                            genres = tmdbData?.genres ?: existing?.genres ?: emptyList(),
                            year = tmdbData?.releaseDate?.take(4) ?: primaryInfo.year,
                            trailerUrl = tmdbData?.trailerUrl ?: existing?.trailerUrl,
                            cast = tmdbData?.cast ?: existing?.cast ?: emptyList(),
                            season = primaryInfo.season,
                            episode = primaryInfo.episode,
                            duration = tmdbData?.duration ?: existing?.duration,
                            isLiked = existing?.isLiked ?: false,
                            isSaved = existing?.isSaved ?: false,
                            isWatchLater = existing?.isWatchLater ?: false,
                            isWatching = existing?.isWatching ?: false,
                            isFinished = existing?.isFinished ?: false,
                            lastPosition = existing?.lastPosition ?: 0L,
                            totalDuration = existing?.totalDuration ?: 0L,
                            timestamp = existing?.timestamp ?: System.currentTimeMillis(),
                            lastUpdated = syncStartTime
                        )
                        
                        if (existing != null && (existing.isLiked || existing.isWatching) && (entity.contentType == ContentType.TV.name || entity.contentType == ContentType.ANIME.name)) {
                            val oldMaxEp = existing.serverLinks.maxOfOrNull { it.episode ?: 0 } ?: 0
                            val newMaxEp = entity.serverLinks.maxOfOrNull { it.episode ?: 0 } ?: 0
                            
                            if (newMaxEp > oldMaxEp) {
                                val latestLink = entity.serverLinks.maxByOrNull { it.episode ?: 0 }
                                notificationHelper.showNewEpisodeNotification(
                                    movieTitle = entity.title,
                                    season = latestLink?.season,
                                    episode = latestLink?.episode,
                                    movieId = entity.id
                                )
                            }
                        }
                        
                        entity
                    }
                }

                val results = deferredMovies.awaitAll()

                if (results.isNotEmpty()) {
                    movieDao.insertMovies(results)
                    movieDao.deleteOldMovies(syncStartTime)
                }
            } catch (e: Exception) {
                Log.e("PrimazoSync", "Error en actualización: ${e.message}")
            }
        }
    }

    private suspend fun syncUserData() {
        val firebaseUser = firebaseAuth.currentUser ?: return
        try {
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (doc.exists()) {
                val liked = (doc.get("liked") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()
                val saved = (doc.get("saved") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()
                val watchLater = (doc.get("watchLater") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()
                val finished = (doc.get("finished") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()

                liked.forEach { movieDao.updateLiked(it, true) }
                saved.forEach { movieDao.updateSaved(it, true) }
                watchLater.forEach { movieDao.updateWatchLater(it, true) }
                finished.forEach { movieDao.updateFinished(it, true) }
            }
        } catch (e: Exception) {
            Log.e("PrimazoSync", "Error sincronizando datos de usuario: ${e.message}")
        }
    }

    private suspend fun fetchTmdbData(info: InternalFileInfo): TmdbData? {
        return if (info.isTv || info.isAnime) {
            val search = tmdbApi.searchTv(query = info.cleanTitle, year = info.year)
            val result = search.results.firstOrNull()
            if (result != null) {
                val detail = tmdbApi.getTvDetails(tvId = result.id)
                TmdbData(
                    title = result.name,
                    overview = result.overview,
                    posterUrl = result.posterPath?.let { "${TmdbApi.IMAGE_BASE_URL}$it" },
                    backdropUrl = result.backdropPath?.let { "${TmdbApi.IMAGE_BASE_URL}$it" },
                    releaseDate = result.firstAirDate,
                    rating = result.voteAverage ?: 0.0,
                    genres = detail.genres?.map { it.name } ?: emptyList(),
                    trailerUrl = detail.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.let { "https://www.youtube.com/watch?v=${it.key}" },
                    cast = detail.credits?.cast?.take(10)?.map { CastMember(it.name, it.character, it.profile_path?.let { p -> "${TmdbApi.IMAGE_BASE_URL}$p" }) } ?: emptyList(),
                    duration = detail.episodeRunTime?.firstOrNull()?.let { "$it min" }
                )
            } else null
        } else {
            val search = tmdbApi.searchMovie(query = info.cleanTitle, year = info.year)
            val result = search.results.firstOrNull()
            if (result != null) {
                val detail = tmdbApi.getMovieDetails(movieId = result.id)
                TmdbData(
                    title = result.title,
                    overview = result.overview,
                    posterUrl = result.posterPath?.let { "${TmdbApi.IMAGE_BASE_URL}$it" },
                    backdropUrl = result.backdropPath?.let { "${TmdbApi.IMAGE_BASE_URL}$it" },
                    releaseDate = result.releaseDate,
                    rating = result.voteAverage ?: 0.0,
                    genres = detail.genres?.map { it.name } ?: emptyList(),
                    trailerUrl = detail.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.let { "https://www.youtube.com/watch?v=${it.key}" },
                    cast = detail.credits?.cast?.take(10)?.map { CastMember(it.name, it.character, it.profile_path?.let { p -> "${TmdbApi.IMAGE_BASE_URL}$p" }) } ?: emptyList(),
                    duration = detail.runtime?.let { "$it min" }
                )
            } else null
        }
    }

    private data class TmdbData(
        val title: String,
        val overview: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
        val releaseDate: String?,
        val rating: Double,
        val genres: List<String>,
        val trailerUrl: String?,
        val cast: List<CastMember>,
        val duration: String? = null
    )

    private fun parseFileName(name: String): InternalFileInfo {
        val noExt = name.substringBeforeLast(".")
        val cleaned = noExt.replace(Regex("[._-]"), " ").trim()
        val yearMatch = Regex("\\b(19|20)\\d{2}\\b").find(cleaned)
        val year = yearMatch?.value
        val seMatch = Regex("(?i)S(\\d{1,2})\\s?[Ex ]?\\s?(\\d{1,3})|\\b(\\d{1,2})x(\\d{1,3})\\b").find(cleaned)
        var s: Int? = null
        var e: Int? = null
        if (seMatch != null) {
            s = (seMatch.groupValues[1].ifEmpty { seMatch.groupValues[3] }).toIntOrNull()
            e = (seMatch.groupValues[2].ifEmpty { seMatch.groupValues[4] }).toIntOrNull()
        }
        var title = cleaned
        year?.let { title = title.replace(it, "") }
        seMatch?.let { title = title.replace(it.value, "") }
        val junk = listOf("(?i)\\b(1080p|720p|480p|dual|latino|castellano|multi|sub|bluray|web-dl|hdrip|thumb|preview|sample|mp4|mkv|x264|x265|h264|h265)\\b", "[\\[\\(].*?[\\]\\)]", "(?i)^thumb\\s+")
        junk.forEach { pattern -> title = title.replace(Regex(pattern), " ") }
        val finalTitle = title.trim().replace(Regex("\\s+"), " ")
        return InternalFileInfo(if (finalTitle.length < 2) cleaned else finalTitle, year, s, e, s != null || cleaned.contains("Temporada", true), cleaned.contains("Anime", true))
    }

    override suspend fun getMovieById(id: Int): Movie? {
        return movieDao.getMovieById(id)?.toDomain()
    }

    private suspend fun getStreamtapeFilesSafe(): List<Pair<String, ServerLink>> {
        val results = mutableListOf<Pair<String, ServerLink>>()
        val folderQueue = mutableListOf<Pair<String?, String?>>()
        folderQueue.add(null to null)
        var iters = 0
        val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m3u8")

        while (folderQueue.isNotEmpty() && iters < 500) {
            val pair = folderQueue.removeAt(0)
            val currentId = pair.first
            val currentParent = pair.second
            try {
                val response = streamtapeApi.listFolder(streamtapeLogin, streamtapeKey, currentId)
                response.result?.let { res ->
                    res.files?.forEach { file ->
                        val fileName = file.name.lowercase()
                        if (videoExtensions.any { fileName.endsWith(it) }) {
                            val name = if (currentParent != null && file.name.length < 10) "$currentParent ${file.name}" else file.name
                            results.add(name to ServerLink("Streamtape", file.linkid, "HD"))
                        }
                    }
                    res.folders?.forEach { folder ->
                        val folderName = folder.name.lowercase()
                        if (!folderName.contains("thumbnail") && !folderName.startsWith(".")) {
                            folderQueue.add(folder.id to folder.name)
                        }
                    }
                }
                delay(100)
            } catch (_: Exception) { }
            iters++
        }
        return results
    }

    override suspend fun getDownloadUrl(serverName: String, fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            if (serverName.contains("Streamtape", true)) {
                val tResponse = streamtapeApi.getDownloadTicket(fileId, streamtapeLogin, streamtapeKey)
                val t = tResponse.result ?: return@withContext null
                delay(t.waitTime * 1000L + 200) 
                val urlResponse = streamtapeApi.getDownloadLink(fileId, t.ticket)
                val url = urlResponse.result?.url
                if (url != null) {
                    val finalUrl = if (url.startsWith("//")) "https:$url" else if (url.startsWith("/")) "https://streamtape.com$url" else url
                    return@withContext finalUrl
                }
            } else if (fileId.startsWith("http")) return@withContext fileId
            null
        } catch (_: Exception) { null }
    }

    override fun searchMovies(query: String): Flow<List<Movie>> = movieDao.searchMovies(query).map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getLikedContent(): Flow<List<Movie>> = movieDao.getAllMovies().map { list ->
        list.filter { it.isLiked }.map { it.toDomain() }
    }

    override fun getWatchLaterContent(): Flow<List<Movie>> = movieDao.getAllMovies().map { list ->
        list.filter { it.isWatchLater }.map { it.toDomain() }
    }

    override fun getWatchingContent(): Flow<List<Movie>> = movieDao.getAllMovies().map { list ->
        list.filter { it.isWatching }.sortedByDescending { it.timestamp }.map { it.toDomain() }
    }

    override fun getSavedContent(): Flow<List<Movie>> = movieDao.getAllMovies().map { list ->
        list.filter { it.isSaved }.map { it.toDomain() }
    }

    override fun getFinishedContent(): Flow<List<Movie>> = movieDao.getAllMovies().map { list ->
        list.filter { it.isFinished }.map { it.toDomain() }
    }

    override suspend fun toggleLike(movieId: Int) {
        val m = movieDao.getMovieById(movieId) ?: return
        val newValue = !m.isLiked
        movieDao.updateLiked(movieId, newValue)
        
        firebaseAuth.currentUser?.let { user ->
            val userRef = firestore.collection("users").document(user.uid)
            if (newValue) {
                userRef.update("liked", FieldValue.arrayUnion(movieId)).await()
                // Update trends
                firestore.collection("trends").document(movieId.toString())
                    .set(mapOf("count" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
            } else {
                userRef.update("liked", FieldValue.arrayRemove(movieId)).await()
                // Update trends
                firestore.collection("trends").document(movieId.toString())
                    .set(mapOf("count" to FieldValue.increment(-1)), com.google.firebase.firestore.SetOptions.merge())
            }
        }
    }

    override suspend fun toggleWatchLater(movieId: Int) {
        val m = movieDao.getMovieById(movieId) ?: return
        val newValue = !m.isWatchLater
        movieDao.updateWatchLater(movieId, newValue)

        firebaseAuth.currentUser?.let { user ->
            val userRef = firestore.collection("users").document(user.uid)
            if (newValue) {
                userRef.update("watchLater", FieldValue.arrayUnion(movieId)).await()
            } else {
                userRef.update("watchLater", FieldValue.arrayRemove(movieId)).await()
            }
        }
    }

    override suspend fun toggleSave(movieId: Int) {
        val m = movieDao.getMovieById(movieId) ?: return
        val newValue = !m.isSaved
        movieDao.updateSaved(movieId, newValue)

        firebaseAuth.currentUser?.let { user ->
            val userRef = firestore.collection("users").document(user.uid)
            if (newValue) {
                userRef.update("saved", FieldValue.arrayUnion(movieId)).await()
            } else {
                userRef.update("saved", FieldValue.arrayRemove(movieId)).await()
            }
        }
    }

    override suspend fun toggleFinished(movieId: Int) {
        val m = movieDao.getMovieById(movieId) ?: return
        val newValue = !m.isFinished
        movieDao.updateFinished(movieId, newValue)

        firebaseAuth.currentUser?.let { user ->
            val userRef = firestore.collection("users").document(user.uid)
            if (newValue) {
                userRef.update("finished", FieldValue.arrayUnion(movieId)).await()
            } else {
                userRef.update("finished", FieldValue.arrayRemove(movieId)).await()
            }
        }
    }

    override suspend fun addToWatching(movieId: Int) {
        movieDao.updateWatching(movieId, true, System.currentTimeMillis())
    }

    override suspend fun updatePlaybackProgress(movieId: Int, position: Long, duration: Long) {
        movieDao.updatePlaybackProgress(movieId, position, duration)
        
        if (duration > 0 && position.toDouble() / duration.toDouble() > 0.95) {
            val m = movieDao.getMovieById(movieId)
            if (m != null && !m.isFinished) {
                toggleFinished(movieId)
            }
        }
    }

    override suspend fun clearWatchingHistory() {}

    override suspend fun clearCache() {
        movieDao.clearAllMovies()
    }

    override fun isMovieLiked(movieId: Int): Flow<Boolean> = movieDao.getAllMovies().map { list ->
        list.any { it.id == movieId && it.isLiked }
    }

    override fun isMovieSaved(movieId: Int): Flow<Boolean> = movieDao.getAllMovies().map { list ->
        list.any { it.id == movieId && it.isSaved }
    }

    override fun isMovieInWatchLater(movieId: Int): Flow<Boolean> = movieDao.getAllMovies().map { list ->
        list.any { it.id == movieId && it.isWatchLater }
    }

    override fun isMovieFinished(movieId: Int): Flow<Boolean> = movieDao.getAllMovies().map { list ->
        list.any { it.id == movieId && it.isFinished }
    }

    override fun getSearchHistory(): Flow<List<String>> = searchHistoryDao.getSearchHistory()

    override suspend fun addSearchQuery(query: String) {
        searchHistoryDao.insertSearch(SearchHistoryEntity(query))
    }

    override suspend fun clearSearchHistory() {
        searchHistoryDao.clearHistory()
    }

    override suspend fun reportBrokenLink(movieId: Int, serverName: String, fileId: String) {
        val report = mapOf(
            "movieId" to movieId,
            "server" to serverName,
            "fileId" to fileId,
            "timestamp" to System.currentTimeMillis(),
            "userId" to (firebaseAuth.currentUser?.uid ?: "anonymous")
        )
        firestore.collection("reports").add(report).await()
    }

    override fun getUser(): Flow<User?> = combine(_currentUser.asStateFlow(), _avatarOverride.asStateFlow()) { user, override ->
        val baseUser = user ?: User(username = "Usuario", isLoggedIn = false)
        if (override != null) {
            baseUser.copy(profilePictureUri = override)
        } else {
            baseUser
        }
    }

    override suspend fun register(username: String, email: String, password: String) {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        result.user?.let { firebaseUser ->
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()
            
            val userData = mapOf(
                "username" to username,
                "email" to email,
                "createdAt" to System.currentTimeMillis(),
                "liked" to emptyList<Int>(),
                "saved" to emptyList<Int>(),
                "watchLater" to emptyList<Int>(),
                "finished" to emptyList<Int>()
            )
            firestore.collection("users").document(firebaseUser.uid).set(userData).await()
        }
    }

    override suspend fun login(email: String, password: String): Boolean {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            syncUserData()
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun logout() {
        firebaseAuth.signOut()
        _avatarOverride.value = null
    }

    override suspend fun updateAvatar(uri: String) {
        // Actualización inmediata para la UI
        _avatarOverride.value = uri

        try {
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(uri.toUri())
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
                
                firestore.collection("users").document(firebaseUser.uid)
                    .update("profilePictureUri", uri).await()
            }
        } catch (e: Exception) {
            Log.e("MovieRepository", "Error actualizando avatar: ${e.message}")
        }
    }

    private fun MovieEntity.toDomain() = Movie(
        id = id,
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        releaseDate = releaseDate,
        rating = rating,
        serverLinks = serverLinks,
        contentType = ContentType.valueOf(contentType),
        genres = genres,
        trailerUrl = trailerUrl,
        cast = cast,
        year = year,
        season = season,
        episode = episode,
        duration = duration,
        lastPosition = lastPosition,
        totalDuration = totalDuration,
        isLiked = isLiked,
        isWatchLater = isWatchLater,
        isWatching = isWatching,
        isSaved = isSaved,
        isFinished = isFinished
    )

    private fun String.capitalizeWords() = split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
