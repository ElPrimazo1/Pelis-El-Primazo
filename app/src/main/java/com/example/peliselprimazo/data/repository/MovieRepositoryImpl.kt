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
import com.example.peliselprimazo.data.remote.JikanApi
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

enum class FolderCategory { MOVIES, SERIES, ANIME, UNKNOWN }

data class InternalFileInfo(
    val cleanTitle: String,
    val year: String?,
    val season: Int?,
    val episode: Int?,
    val isTv: Boolean,
    val isAnime: Boolean,
    val language: String? = null,
    val category: FolderCategory = FolderCategory.UNKNOWN
)

data class TmdbData(
    val title: String,
    val originalTitle: String?,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val rating: Double,
    val genres: List<String>,
    val trailerUrl: String?,
    val cast: List<CastMember>,
    val duration: String?
)

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
class MovieRepositoryImpl @Inject constructor(
    private val streamtapeApi: StreamtapeApi,
    private val tmdbApi: TmdbApi,
    private val jikanApi: JikanApi,
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
    private val jikanSemaphore = Semaphore(1) // Más restrictivo para Jikan (3 req/seg max)
    
    private val _currentUser = MutableStateFlow<User?>(null)
    private val _avatarOverride = MutableStateFlow<String?>(null)

    private val lastProgressUpdate = mutableMapOf<Int, Long>()

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
                val remoteData = fetchRemoteUserData()
                val allFiles = getStreamtapeFilesSafe()
                
                if (allFiles.isEmpty()) {
                    movieDao.clearAllMovies()
                    return@withContext
                }

                val parsedItems = allFiles.map { (fileName, serverLink, category) ->
                    val info = parseFileName(fileName, category)
                    info to serverLink.copy(
                        season = info.season, 
                        episode = info.episode,
                        language = info.language
                    )
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
                        
                        // Búsqueda de metadata con fallback y optimización
                        var metaData: TmdbData? = null
                        val needsMetadata = existing == null || existing.posterUrl.isNullOrBlank() || existing.overview == "Contenido disponible."

                        if (needsMetadata) {
                            if (primaryInfo.isAnime || primaryInfo.category == FolderCategory.ANIME) {
                                metaData = jikanSemaphore.withPermit {
                                    try { fetchJikanData(primaryInfo) } catch (e: Exception) { 
                                        Log.e("Sync", "Jikan error for ${primaryInfo.cleanTitle}: ${e.message}")
                                        null 
                                    }
                                }
                            }

                            // Si Jikan no encontró nada (o no es anime), probamos con TMDB
                            if (metaData == null) {
                                metaData = tmdbSemaphore.withPermit {
                                    try { if (tmdbApiKey.isNotBlank()) fetchTmdbData(primaryInfo) else null } catch (_: Exception) { null }
                                }
                            }
                        }

                        val isLiked = if (remoteData != null) remoteData.liked.contains(id) else existing?.isLiked ?: false
                        val isSaved = if (remoteData != null) remoteData.saved.contains(id) else existing?.isSaved ?: false
                        val isWatchLater = if (remoteData != null) remoteData.watchLater.contains(id) else existing?.isWatchLater ?: false
                        val isFinished = if (remoteData != null) remoteData.finished.contains(id) else existing?.isFinished ?: false
                        
                        val remoteProgress = remoteData?.watching?.get(id)
                        val isWatching = remoteProgress != null || (existing?.isWatching ?: false)
                        val lastPos = remoteProgress?.position ?: existing?.lastPosition ?: 0L
                        val totalDur = remoteProgress?.duration ?: existing?.totalDuration ?: 0L
                        val timestamp = remoteProgress?.timestamp ?: existing?.timestamp ?: System.currentTimeMillis()

                        if (existing != null && (primaryInfo.isTv || primaryInfo.isAnime) && allLinks.size > existing.serverLinks.size && existing.isWatching) {
                            notificationHelper.showNewEpisodeNotification(
                                metaData?.title ?: primaryInfo.cleanTitle.capitalizeWords(),
                                primaryInfo.season,
                                primaryInfo.episode,
                                id
                            )
                        }

                        val createdAt = if (existing == null) syncStartTime else existing.createdAt
                        val lastUpdated = syncStartTime

                        val finalTitle = when {
                            metaData != null -> metaData.title
                            else -> primaryInfo.cleanTitle.capitalizeWords()
                        }

                        val entity = MovieEntity(
                            id = id,
                            title = finalTitle,
                            originalTitle = metaData?.originalTitle,
                            overview = metaData?.overview ?: existing?.overview ?: "Contenido disponible.",
                            posterUrl = metaData?.posterUrl ?: existing?.posterUrl,
                            backdropUrl = metaData?.backdropUrl ?: existing?.backdropUrl,
                            releaseDate = metaData?.releaseDate ?: primaryInfo.year ?: "Desconocido",
                            rating = metaData?.rating ?: existing?.rating ?: 0.0,
                            serverLinks = allLinks,
                            contentType = when {
                                primaryInfo.isAnime -> ContentType.ANIME.name
                                primaryInfo.isTv -> ContentType.TV.name
                                else -> ContentType.MOVIE.name
                            },
                            genres = metaData?.genres ?: existing?.genres ?: emptyList(),
                            year = metaData?.releaseDate?.take(4) ?: primaryInfo.year,
                            trailerUrl = metaData?.trailerUrl ?: existing?.trailerUrl,
                            cast = metaData?.cast ?: existing?.cast ?: emptyList(),
                            season = primaryInfo.season,
                            episode = primaryInfo.episode,
                            duration = metaData?.duration ?: existing?.duration,
                            isLiked = isLiked,
                            isSaved = isSaved,
                            isWatchLater = isWatchLater,
                            isWatching = isWatching,
                            isFinished = isFinished,
                            lastPosition = lastPos,
                            totalDuration = totalDur,
                            timestamp = timestamp,
                            lastUpdated = lastUpdated,
                            createdAt = createdAt
                        )
                        
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
                if (e is java.net.UnknownHostException || e is java.net.ConnectException) throw e
            }
        }
    }

    private fun parseFileName(fileName: String, folderCategory: FolderCategory): InternalFileInfo {
        val nameWithoutExt = fileName.replace(Regex("\\.(mp4|mkv|avi|mov|wmv|flv|webm|m3u8)$", RegexOption.IGNORE_CASE), "")
        val isAnime = folderCategory == FolderCategory.ANIME || fileName.lowercase().contains("anime") || fileName.contains("[Anime]")

        var cleanTitle = nameWithoutExt.replace(Regex("[._\\-/()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val languages = listOf("latino", "español", "castellano", "ingles", "english", "frances", "french", "japones", "japanese")
        var foundLanguage: String? = null
        languages.forEach { lang ->
            if (cleanTitle.lowercase().contains(Regex("\\b$lang\\b"))) {
                foundLanguage = lang.replaceFirstChar { it.uppercase() }
            }
        }

        val yearPattern = Pattern.compile("\\b(19|20)\\d{2}\\b")
        val yearMatcher = yearPattern.matcher(cleanTitle)
        val year = if (yearMatcher.find()) yearMatcher.group() else null
        if (year != null) cleanTitle = cleanTitle.replace(year, "")

        val sePattern = Pattern.compile("(?i)S(\\d{1,2})E(\\d{1,3})|(\\d{1,2})x(\\d{1,3})|Ep\\s?(\\d{1,3})")
        val seMatcher = sePattern.matcher(cleanTitle)
        var season: Int? = null
        var episode: Int? = null
        var seMatch: String? = null
        
        if (seMatcher.find()) {
            seMatch = seMatcher.group()
            season = seMatcher.group(1)?.toIntOrNull() ?: seMatcher.group(3)?.toIntOrNull()
            episode = seMatcher.group(2)?.toIntOrNull() ?: seMatcher.group(4)?.toIntOrNull() ?: seMatcher.group(5)?.toIntOrNull()
        }

        if (episode == null) {
            val epPattern = Pattern.compile("\\s(\\d{1,4})$")
            val epMatcher = epPattern.matcher(cleanTitle)
            if (epMatcher.find()) {
                episode = epMatcher.group(1).toIntOrNull()
                seMatch = epMatcher.group()
            }
        }

        if (seMatch != null) {
            val idx = cleanTitle.indexOf(seMatch)
            if (idx != -1) cleanTitle = cleanTitle.substring(0, idx)
        }

        val noiseTags = listOf(
            "1080p", "720p", "480p", "h264", "h265", "x264", "x265", 
            "bluray", "webrip", "latino", "español", "castellano", "dual", 
            "sub", "subs", "subtitulado", "multi", "hdrip", "dvdrip", "remux", "streamtape",
            "ingles", "english", "frances", "french", "japones", "japanese", "anime"
        )
        noiseTags.forEach { tag ->
            cleanTitle = cleanTitle.replace(Regex("(?i)\\b$tag\\b"), "")
        }

        cleanTitle = cleanTitle.replace(Regex("\\s+"), " ").trim()
        if (cleanTitle.isEmpty()) cleanTitle = nameWithoutExt

        val isTv = folderCategory == FolderCategory.SERIES || season != null || fileName.lowercase().contains("tv") || isAnime

        return InternalFileInfo(cleanTitle, year, season, episode, isTv, isAnime, foundLanguage, folderCategory)
    }

    private suspend fun fetchJikanData(info: InternalFileInfo): TmdbData? {
        return try {
            var query = info.cleanTitle.trim()
            if (query.length < 3) return null
            
            // Jikan rate limit: 3 requests per second max. Usamos delay de 1s para seguridad.
            delay(1000) 
            
            Log.d("Jikan", "Buscando metadata en Jikan para: $query")
            var response = jikanApi.searchAnime(query)
            var searchResult = response.data?.firstOrNull()

            // Si no hay resultados, intentar una búsqueda más limpia (sin números al final)
            if (searchResult == null) {
                val cleanedQuery = query.replace(Regex("\\s\\d+$"), "").trim()
                if (cleanedQuery != query && cleanedQuery.length >= 3) {
                    delay(1000)
                    Log.d("Jikan", "Reintentando Jikan con query limpia: $cleanedQuery")
                    response = jikanApi.searchAnime(cleanedQuery)
                    searchResult = response.data?.firstOrNull()
                }
            }
            
            if (searchResult == null || searchResult.id == null) {
                Log.w("Jikan", "No se encontró metadata para: $query")
                return null
            }

            // Obtener datos completos para asegurar portada y sinopsis según doc v4
            delay(1000)
            val fullResponse = jikanApi.getAnimeFull(searchResult.id)
            val data = fullResponse.data ?: searchResult

            Log.d("Jikan", "Datos encontrados: ${data.title} (ID: ${data.id})")
            
            // Mapeo exhaustivo de imágenes según doc v4
            val poster = data.images?.webp?.largeImageUrl 
                ?: data.images?.webp?.imageUrl 
                ?: data.images?.jpg?.largeImageUrl 
                ?: data.images?.jpg?.imageUrl

            TmdbData(
                title = data.title ?: data.titleEnglish ?: info.cleanTitle, // Prioridad al nombre original/romaji
                originalTitle = data.titleEnglish ?: data.titleJapanese, // Guardamos el ingles como secundario
                overview = data.synopsis ?: "Sinopsis no disponible en Jikan.",
                posterUrl = poster,
                backdropUrl = poster, 
                releaseDate = data.year?.toString() ?: info.year ?: "Desconocido",
                rating = data.score ?: 0.0,
                genres = data.genres?.mapNotNull { it.name } ?: emptyList(),
                trailerUrl = data.trailer?.url ?: data.trailer?.youtubeId?.let { "https://www.youtube.com/watch?v=$it" },
                cast = emptyList(),
                duration = data.duration
            )
        } catch (e: Exception) {
            Log.e("Jikan", "Error crítico en búsqueda Jikan para ${info.cleanTitle}: ${e.message}")
            null
        }
    }

    private suspend fun fetchTmdbData(info: InternalFileInfo): TmdbData? {
        return try {
            // Si tiene temporada/episodio o es anime/serie, buscar como TV en TMDB
            val isTvSearch = info.isTv || info.category == FolderCategory.SERIES || info.category == FolderCategory.ANIME
            
            val id = if (isTvSearch) {
                tmdbApi.searchTv(info.cleanTitle, info.year).results.firstOrNull()?.id
            } else {
                tmdbApi.searchMovie(info.cleanTitle, info.year).results.firstOrNull()?.id
            } ?: return null
            
            if (isTvSearch) {
                val details = tmdbApi.getTvDetails(id)
                val isAnime = info.isAnime || info.category == FolderCategory.ANIME
                
                TmdbData(
                    title = (if (isAnime) details.originalName else details.name) ?: info.cleanTitle,
                    originalTitle = details.name,
                    overview = details.overview ?: "Sinopsis no disponible.",
                    posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                    releaseDate = details.firstAirDate,
                    rating = details.voteAverage,
                    genres = details.genres?.map { it.name } ?: emptyList(),
                    trailerUrl = details.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.let { "https://www.youtube.com/watch?v=${it.key}" },
                    cast = details.credits?.cast?.take(10)?.map { CastMember(it.name, it.character, it.profilePath?.let { p -> "https://image.tmdb.org/t/p/w185$p" }) } ?: emptyList(),
                    duration = details.episodeRunTime?.firstOrNull()?.let { "$it min" }
                )
            } else {
                val details = tmdbApi.getMovieDetails(id)
                TmdbData(
                    title = details.title ?: info.cleanTitle,
                    originalTitle = details.originalTitle,
                    overview = details.overview ?: "Sinopsis no disponible.",
                    posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                    releaseDate = details.releaseDate,
                    rating = details.voteAverage,
                    genres = details.genres?.map { it.name } ?: emptyList(),
                    trailerUrl = details.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.let { "https://www.youtube.com/watch?v=${it.key}" },
                    cast = details.credits?.cast?.take(10)?.map { CastMember(it.name, it.character, it.profilePath?.let { p -> "https://image.tmdb.org/t/p/w185$p" }) } ?: emptyList(),
                    duration = details.runtime?.let { "$it min" }
                )
            }
        } catch (e: Exception) {
            Log.e("TMDB", "Error en búsqueda TMDB para ${info.cleanTitle}: ${e.message}")
            null
        }
    }

    private suspend fun fetchRemoteUserData(): RemoteUserData? {
        val firebaseUser = firebaseAuth.currentUser ?: return null
        return try {
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (doc.exists()) {
                val liked = (doc.get("liked") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()
                val saved = (doc.get("saved") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()
                val watchLater = (doc.get("watchLater") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()
                val finished = (doc.get("finished") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()
                
                val watchingMap = doc.get("watching") as? Map<String, Map<String, Any>> ?: emptyMap()
                val watching = watchingMap.mapNotNull { (idStr, data) ->
                    val id = idStr.toIntOrNull() ?: return@mapNotNull null
                    id to RemoteWatchingProgress(
                        position = (data["position"] as? Long) ?: 0L,
                        duration = (data["duration"] as? Long) ?: 0L,
                        timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                    )
                }.toMap()

                RemoteUserData(liked, saved, watchLater, finished, watching)
            } else null
        } catch (e: Exception) {
            Log.e("PrimazoSync", "Error fetching remote data: ${e.message}")
            null
        }
    }

    override suspend fun getMovieById(id: Int): Movie? {
        return movieDao.getMovieById(id)?.toDomain()
    }

    private suspend fun getStreamtapeFilesSafe(): List<Triple<String, ServerLink, FolderCategory>> {
        val results = mutableListOf<Triple<String, ServerLink, FolderCategory>>()
        val folderQueue = mutableListOf<Triple<String?, String?, FolderCategory>>() 
        folderQueue.add(Triple(null, null, FolderCategory.UNKNOWN))
        var iters = 0
        
        while (folderQueue.isNotEmpty() && iters < 500) {
            val (currentId, currentParent, currentCategory) = folderQueue.removeAt(0)
            try {
                val response = streamtapeApi.listFolder(streamtapeLogin, streamtapeKey, currentId)
                response.result?.let { res ->
                    res.files?.forEach { file ->
                        var name = if (currentParent != null && file.name.length < 10) "$currentParent ${file.name}" else file.name
                        if (currentCategory == FolderCategory.ANIME && !name.contains("[Anime]", ignoreCase = true)) {
                            name = "[Anime] $name"
                        }
                        results.add(Triple(name, ServerLink("Streamtape", file.linkid, "HD"), currentCategory))
                    }
                    res.folders?.forEach { folder ->
                        val folderName = folder.name.lowercase()
                        if (!folderName.contains("thumbnail") && !folderName.startsWith(".")) {
                            val category = when {
                                folderName.contains("anime") -> FolderCategory.ANIME
                                folderName.contains("pelicula") || folderName.contains("movie") -> FolderCategory.MOVIES
                                folderName.contains("serie") || folderName.contains("tv") -> FolderCategory.SERIES
                                else -> currentCategory
                            }
                            folderQueue.add(Triple(folder.id, folder.name, category))
                        }
                    }
                }
                delay(100)
            } catch (e: Exception) {
                if (e is java.net.UnknownHostException || e is java.net.ConnectException) throw e
            }
            iters++
        }
        return results
    }

    override suspend fun getDownloadUrl(serverName: String, fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            if (serverName.contains("Streamtape", true)) {
                try {
                    val embedUrl = "https://streamtape.com/e/$fileId"
                    val connection = URL(embedUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.responseCode 
                    connection.disconnect()
                } catch (e: Exception) {
                    Log.e("StreamtapeHit", "Error al registrar vista: ${e.message}")
                }

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
                firestore.collection("trends").document(movieId.toString())
                    .set(mapOf("count" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
            } else {
                userRef.update("liked", FieldValue.arrayRemove(movieId)).await()
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
        val timestamp = System.currentTimeMillis()
        movieDao.updateWatching(movieId, true, timestamp)
        
        firebaseAuth.currentUser?.let { user ->
            val userRef = firestore.collection("users").document(user.uid)
            userRef.update("watching.$movieId.timestamp", timestamp).await()
            
            firestore.collection("trends").document(movieId.toString())
                .set(mapOf("views" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
        }
    }

    override suspend fun updatePlaybackProgress(movieId: Int, position: Long, duration: Long) {
        movieDao.updatePlaybackProgress(movieId, position, duration)
        
        val now = System.currentTimeMillis()
        val lastUpdate = lastProgressUpdate[movieId] ?: 0L
        
        if (now - lastUpdate > 10000) {
            firebaseAuth.currentUser?.let { user ->
                val userRef = firestore.collection("users").document(user.uid)
                val data = mapOf(
                    "watching.$movieId.position" to position,
                    "watching.$movieId.duration" to duration,
                    "watching.$movieId.timestamp" to now
                )
                userRef.update(data)
                lastProgressUpdate[movieId] = now
            }
        }

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

    override fun isMovieLiked(movieId: Int): Flow<Boolean> = movieDao.getMovieFlowById(movieId).map { it?.isLiked ?: false }

    override fun isMovieSaved(movieId: Int): Flow<Boolean> = movieDao.getMovieFlowById(movieId).map { it?.isSaved ?: false }

    override fun isMovieInWatchLater(movieId: Int): Flow<Boolean> = movieDao.getMovieFlowById(movieId).map { it?.isWatchLater ?: false }

    override fun isMovieFinished(movieId: Int): Flow<Boolean> = movieDao.getMovieFlowById(movieId).map { it?.isFinished ?: false }
    
    override fun isMovieWatching(movieId: Int): Flow<Boolean> = movieDao.getMovieFlowById(movieId).map { it?.isWatching ?: false }

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
        val firebaseUser = result.user ?: throw Exception("Error al crear usuario")
        
        _currentUser.value = User(username, email, null, true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
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
                    "finished" to emptyList<Int>(),
                    "watching" to emptyMap<String, Any>()
                )
                firestore.collection("users").document(firebaseUser.uid).set(userData).await()
            } catch (e: Exception) {
                Log.e("AuthBackground", "Error en tareas secundarias de registro: ${e.message}")
            }
        }
    }

    override suspend fun login(email: String, password: String): Boolean {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
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

    override suspend fun resetPassword(email: String) {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    private fun MovieEntity.toDomain() = Movie(
        id = id,
        title = title,
        originalTitle = originalTitle,
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
        isFinished = isFinished,
        createdAt = createdAt
    )

    private fun String.capitalizeWords() = split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
