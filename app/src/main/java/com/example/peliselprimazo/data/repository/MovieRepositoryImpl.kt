package com.example.peliselprimazo.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.peliselprimazo.data.local.dao.MovieDao
import com.example.peliselprimazo.data.local.dao.SearchHistoryDao
import com.example.peliselprimazo.data.local.entities.MovieEntity
import com.example.peliselprimazo.data.local.entities.SearchHistoryEntity
import com.example.peliselprimazo.data.remote.datasource.*
import com.example.peliselprimazo.domain.model.CastMember
import com.example.peliselprimazo.domain.model.ContentType
import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.model.ServerLink
import com.example.peliselprimazo.domain.model.User
import com.example.peliselprimazo.domain.repository.MovieRepository
import com.example.peliselprimazo.ui.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayDeque

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

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val authDataSource: AuthDataSource,
    private val remoteMovieDataSource: RemoteMovieDataSource,
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val movieDao: MovieDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val notificationHelper: NotificationHelper
) : MovieRepository {

    private val tmdbSemaphore = Semaphore(3)
    private val jikanSemaphore = Semaphore(1)
    
    private val _avatarOverride = MutableStateFlow<String?>(null)
    private val lastProgressUpdate = mutableMapOf<Int, Long>()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val JIKAN_DELAY = 1000L
        private const val STREAMTAPE_POLL_DELAY = 100L
        private const val SYNC_BATCH_SIZE = 500
        private const val PROGRESS_SYNC_THRESHOLD = 10000L
        
        private val EXTENSION_REGEX = Regex("\\.(mp4|mkv|avi|mov|wmv|flv|webm|m3u8)$", RegexOption.IGNORE_CASE)
        private val CLEAN_SYMBOL_REGEX = Regex("[._\\-/()\\[\\]{}]")
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b")
        private val SE_PATTERN = Pattern.compile("(?i)S(\\d{1,2})E(\\d{1,3})|(\\d{1,2})x(\\d{1,3})|Ep\\s?(\\d{1,3})")
        private val EP_END_PATTERN = Pattern.compile("\\s(\\d{1,4})$")
        
        private val NOISE_TAGS = listOf(
            "1080p", "720p", "480p", "h264", "h265", "x264", "x265", 
            "bluray", "webrip", "latino", "español", "castellano", "dual", 
            "sub", "subs", "subtitulado", "multi", "hdrip", "dvdrip", "remux", "streamtape",
            "ingles", "english", "frances", "french", "japones", "japanese", "anime"
        )
        private val NOISE_REGEX = Regex("(?i)\\b(${NOISE_TAGS.joinToString("|")})\\b")
        private val LANGUAGES = listOf("latino", "español", "castellano", "ingles", "english", "frances", "french", "japones", "japanese")
        private val LANGUAGE_REGEX = Regex("(?i)\\b(${LANGUAGES.joinToString("|")})\\b")
    }

    override fun getAllContent(): Flow<List<Movie>> = movieDao.getAllMovies().map { entities -> entities.map { it.toDomain() } }

    override fun getPagedContent(): Flow<PagingData<Movie>> = Pager(
        config = PagingConfig(pageSize = 20, prefetchDistance = 2),
        pagingSourceFactory = { movieDao.getPagedMovies() }
    ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun getMovies(): Flow<List<Movie>> = movieDao.getMoviesByType(ContentType.MOVIE.name).map { entities -> entities.map { it.toDomain() } }
    override fun getSeries(): Flow<List<Movie>> = movieDao.getMoviesByType(ContentType.TV.name).map { entities -> entities.map { it.toDomain() } }

    override fun getTrends(): Flow<List<Movie>> = flow {
        try {
            val trendingIds = userPreferencesDataSource.getTrendingIds()
            emit(trendingIds.mapNotNull { id -> movieDao.getMovieById(id)?.toDomain() })
        } catch (e: Exception) { emit(emptyList()) }
    }
    
    override suspend fun refreshContent() {
        withContext(Dispatchers.IO) {
            val syncStartTime = System.currentTimeMillis()
            try {
                val uid = authDataSource.getCurrentUserUid()
                val remoteData = uid?.let { userPreferencesDataSource.getUserData(it) }
                val allFiles = getStreamtapeFilesSafe()
                if (allFiles.isEmpty()) {
                    movieDao.clearAllMovies()
                    return@withContext
                }

                val parsedItems: List<Pair<InternalFileInfo, ServerLink>> = allFiles.map { (fileName, serverLink, category) ->
                    val info = parseFileName(fileName, category)
                    info to serverLink.copy(season = info.season, episode = info.episode, language = info.language)
                }

                val grouped: Map<String, List<Pair<InternalFileInfo, ServerLink>>> = parsedItems.groupBy { pair ->
                    val info = pair.first
                    val key = info.cleanTitle.lowercase().trim()
                    if (info.isTv || info.isAnime) "tv|$key" else "movie|$key|${info.year ?: "unknown"}"
                }

                val results = grouped.values.map { items ->
                    async {
                        val primaryInfo = items.first().first
                        val allLinks = items.map { it.second }.distinctBy { "${it.serverName}_${it.fileId}" }
                        val id = (primaryInfo.cleanTitle + (primaryInfo.year ?: "")).hashCode()

                        val existing = movieDao.getMovieById(id)
                        val metaData = resolveMetadata(primaryInfo, existing)

                        val isLiked = remoteData?.liked?.contains(id) ?: existing?.isLiked ?: false
                        val isSaved = remoteData?.saved?.contains(id) ?: existing?.isSaved ?: false
                        val isWatchLater = remoteData?.watchLater?.contains(id) ?: existing?.isWatchLater ?: false
                        val isFinished = remoteData?.finished?.contains(id) ?: existing?.isFinished ?: false
                        val remoteProgress = remoteData?.watching?.get(id)
                        
                        if (existing != null && (primaryInfo.isTv || primaryInfo.isAnime) && allLinks.size > existing.serverLinks.size && existing.isWatching) {
                            notificationHelper.showNewEpisodeNotification(metaData?.title ?: primaryInfo.cleanTitle.capitalizeWords(), primaryInfo.season, primaryInfo.episode, id)
                        }

                        MovieEntity(
                            id = id,
                            title = metaData?.title ?: primaryInfo.cleanTitle.capitalizeWords(),
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
                            isLiked = isLiked, isSaved = isSaved, isWatchLater = isWatchLater, isFinished = isFinished,
                            isWatching = remoteProgress != null || (existing?.isWatching ?: false),
                            lastPosition = remoteProgress?.position ?: existing?.lastPosition ?: 0L,
                            totalDuration = remoteProgress?.duration ?: existing?.totalDuration ?: 0L,
                            timestamp = remoteProgress?.timestamp ?: existing?.timestamp ?: System.currentTimeMillis(),
                            lastUpdated = syncStartTime,
                            createdAt = existing?.createdAt ?: syncStartTime
                        )
                    }
                }.awaitAll()

                if (results.isNotEmpty()) {
                    movieDao.insertMovies(results)
                    movieDao.deleteOldMovies(syncStartTime)
                }
            } catch (e: Exception) { Log.e("PrimazoSync", "Error en actualización: ${e.message}") }
        }
    }

    private suspend fun resolveMetadata(info: InternalFileInfo, existing: MovieEntity?): TmdbData? {
        val needsMetadata = existing == null || existing.posterUrl.isNullOrBlank() || existing.overview == "Contenido disponible."
        if (!needsMetadata) return null
        return if (info.isAnime || info.category == FolderCategory.ANIME) {
            jikanSemaphore.withPermit { fetchJikanData(info) } ?: tmdbSemaphore.withPermit { fetchTmdbData(info) }
        } else {
            tmdbSemaphore.withPermit { fetchTmdbData(info) }
        }
    }

    private fun parseFileName(fileName: String, folderCategory: FolderCategory): InternalFileInfo {
        val nameWithoutExt = fileName.replace(EXTENSION_REGEX, "")
        val isAnime = folderCategory == FolderCategory.ANIME || fileName.lowercase().contains("anime") || fileName.contains("[Anime]")
        var cleanTitle = nameWithoutExt.replace(CLEAN_SYMBOL_REGEX, " ").replace(MULTI_SPACE_REGEX, " ").trim()
        val foundLanguage = LANGUAGE_REGEX.find(cleanTitle)?.value?.replaceFirstChar { it.uppercase() }
        val yearMatcher = YEAR_PATTERN.matcher(cleanTitle)
        val year = if (yearMatcher.find()) yearMatcher.group() else null
        if (year != null) cleanTitle = cleanTitle.replace(year, "")
        val seMatcher = SE_PATTERN.matcher(cleanTitle)
        var season: Int? = null
        var episode: Int? = null
        var seMatch: String? = null
        if (seMatcher.find()) {
            seMatch = seMatcher.group()
            season = seMatcher.group(1)?.toIntOrNull() ?: seMatcher.group(3)?.toIntOrNull()
            episode = seMatcher.group(2)?.toIntOrNull() ?: seMatcher.group(4)?.toIntOrNull() ?: seMatcher.group(5)?.toIntOrNull()
        } else {
            val epMatcher = EP_END_PATTERN.matcher(cleanTitle)
            if (epMatcher.find()) {
                episode = epMatcher.group(1).toIntOrNull()
                seMatch = epMatcher.group()
            }
        }
        if (seMatch != null) {
            val idx = cleanTitle.indexOf(seMatch)
            if (idx != -1) cleanTitle = cleanTitle.substring(0, idx)
        }
        cleanTitle = cleanTitle.replace(NOISE_REGEX, "").replace(MULTI_SPACE_REGEX, " ").trim()
        if (cleanTitle.isEmpty()) cleanTitle = nameWithoutExt
        val isTv = folderCategory == FolderCategory.SERIES || season != null || fileName.lowercase().contains("tv") || isAnime
        return InternalFileInfo(cleanTitle, year, season, episode, isTv, isAnime, foundLanguage, folderCategory)
    }

    private suspend fun fetchJikanData(info: InternalFileInfo): TmdbData? = try {
        val query = info.cleanTitle.trim()
        if (query.length < 3) null else {
            delay(JIKAN_DELAY) 
            var res = remoteMovieDataSource.searchJikanAnime(query).data?.firstOrNull()
            if (res == null) {
                val cl = query.replace(Regex("\\s\\d+$"), "").trim()
                if (cl != query && cl.length >= 3) { delay(JIKAN_DELAY); res = remoteMovieDataSource.searchJikanAnime(cl).data?.firstOrNull() }
            }
            val id = res?.id ?: return null
            delay(JIKAN_DELAY)
            val d = remoteMovieDataSource.getJikanAnimeFull(id).data ?: res
            val p = d.images?.webp?.largeImageUrl ?: d.images?.webp?.imageUrl ?: d.images?.jpg?.largeImageUrl ?: d.images?.jpg?.imageUrl
            
            // Priorizamos el título en Inglés para el nombre principal y Romaji/Original para originalTitle
            val finalTitle = d.titleEnglish ?: d.title ?: info.cleanTitle
            val originalTitle = d.title ?: d.titleJapanese
            
            TmdbData(
                title = finalTitle,
                originalTitle = originalTitle,
                overview = d.synopsis ?: "Sinopsis no disponible.",
                posterUrl = p, 
                backdropUrl = p, 
                releaseDate = d.year?.toString() ?: info.year ?: "Desconocido",
                rating = d.score ?: 0.0,
                genres = d.genres?.mapNotNull { it.name } ?: emptyList(),
                trailerUrl = d.trailer?.url ?: d.trailer?.youtubeId?.let { "https://www.youtube.com/watch?v=$it" },
                cast = emptyList(), 
                duration = d.duration
            )
        }
    } catch (_: Exception) { null }

    private suspend fun fetchTmdbData(info: InternalFileInfo): TmdbData? = try {
        val isTv = info.isTv || info.category == FolderCategory.SERIES || info.category == FolderCategory.ANIME
        val id = if (isTv) remoteMovieDataSource.searchTmdbTv(info.cleanTitle, info.year).results.firstOrNull()?.id
                 else remoteMovieDataSource.searchTmdbMovie(info.cleanTitle, info.year).results.firstOrNull()?.id
        if (id == null) null else {
            if (isTv) {
                val d = remoteMovieDataSource.getTmdbTvDetails(id)
                TmdbData((if (info.isAnime) d.originalName else d.name) ?: info.cleanTitle, d.name, d.overview ?: "Sinopsis no disponible.", d.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }, d.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }, d.firstAirDate, d.voteAverage, d.genres?.map { it.name } ?: emptyList(), d.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.let { "https://www.youtube.com/watch?v=${it.key}" }, d.credits?.cast?.take(10)?.map { CastMember(it.name, it.character, it.profilePath?.let { p -> "https://image.tmdb.org/t/p/w185$p" }) } ?: emptyList(), d.episodeRunTime?.firstOrNull()?.let { "$it min" })
            } else {
                val d = remoteMovieDataSource.getTmdbMovieDetails(id)
                TmdbData(d.title ?: info.cleanTitle, d.originalTitle, d.overview ?: "Sinopsis no disponible.", d.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }, d.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }, d.releaseDate, d.voteAverage, d.genres?.map { it.name } ?: emptyList(), d.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.let { "https://www.youtube.com/watch?v=${it.key}" }, d.credits?.cast?.take(10)?.map { CastMember(it.name, it.character, it.profilePath?.let { p -> "https://image.tmdb.org/t/p/w185$p" }) } ?: emptyList(), d.runtime?.let { "$it min" })
            }
        }
    } catch (_: Exception) { null }

    private suspend fun getStreamtapeFilesSafe(): List<Triple<String, ServerLink, FolderCategory>> {
        val res = mutableListOf<Triple<String, ServerLink, FolderCategory>>()
        val q = ArrayDeque<Triple<String?, String?, FolderCategory>>().apply { add(Triple(null, null, FolderCategory.UNKNOWN)) }
        var it = 0
        while (q.isNotEmpty() && it++ < SYNC_BATCH_SIZE) {
            val (cId, cP, cC) = q.removeFirst()
            try {
                remoteMovieDataSource.listStreamtapeFolder(cId).result?.let { r ->
                    r.files?.forEach { f ->
                        var n = if (cP != null && f.name.length < 10) "$cP ${f.name}" else f.name
                        if (cC == FolderCategory.ANIME && !n.contains("[Anime]", true)) n = "[Anime] $n"
                        res.add(Triple(n, ServerLink("Streamtape", f.linkid, "HD"), cC))
                    }
                    r.folders?.forEach { folder ->
                        val fn = folder.name.lowercase()
                        if (!fn.contains("thumbnail") && !fn.startsWith(".")) {
                            val cat = when {
                                fn.contains("anime") -> FolderCategory.ANIME
                                fn.contains("pelicula") || fn.contains("movie") -> FolderCategory.MOVIES
                                fn.contains("serie") || fn.contains("tv") -> FolderCategory.SERIES
                                else -> cC
                            }
                            q.add(Triple(folder.id, folder.name, cat))
                        }
                    }
                }
                delay(STREAMTAPE_POLL_DELAY)
            } catch (_: Exception) { }
        }
        return res
    }

    override suspend fun getDownloadUrl(server: String, fId: String): String? = withContext(Dispatchers.IO) {
        try {
            if (server.contains("Streamtape", true)) {
                remoteMovieDataSource.hitStreamtapeEmbed(fId)
                val t = remoteMovieDataSource.getStreamtapeDownloadTicket(fId).result ?: return@withContext null
                delay(t.waitTime * 1000L + 200)
                remoteMovieDataSource.getStreamtapeDownloadLink(fId, t.ticket).result?.url?.let {
                    return@withContext when { it.startsWith("//") -> "https:$it"; it.startsWith("/") -> "https://streamtape.com$it"; else -> it }
                }
            } else if (fId.startsWith("http")) return@withContext fId
            null
        } catch (_: Exception) { null }
    }

    override suspend fun getMovieById(id: Int): Movie? = movieDao.getMovieById(id)?.toDomain()
    override fun searchMovies(q: String) = movieDao.searchMovies(q).map { l -> l.map { it.toDomain() } }
    override fun getLikedContent() = movieDao.getAllMovies().map { l -> l.filter { it.isLiked }.map { it.toDomain() } }
    override fun getWatchLaterContent() = movieDao.getAllMovies().map { l -> l.filter { it.isWatchLater }.map { it.toDomain() } }
    override fun getWatchingContent() = movieDao.getAllMovies().map { l -> l.filter { it.isWatching }.sortedByDescending { it.timestamp }.map { it.toDomain() } }
    override fun getSavedContent() = movieDao.getAllMovies().map { l -> l.filter { it.isSaved }.map { it.toDomain() } }
    override fun getFinishedContent() = movieDao.getAllMovies().map { l -> l.filter { it.isFinished }.map { it.toDomain() } }

    private fun remoteUpdate(id: Int, key: String, add: Boolean) = repositoryScope.launch {
        authDataSource.getCurrentUserUid()?.let { userPreferencesDataSource.updateList(it, key, id, add) }
    }

    override suspend fun toggleLike(id: Int) {
        movieDao.getMovieById(id)?.let { m ->
            val nv = !m.isLiked
            movieDao.updateLiked(id, nv)
            remoteUpdate(id, "liked", nv)
            repositoryScope.launch { userPreferencesDataSource.updateTrend(id, "count", if (nv) 1L else -1L) }
        }
    }

    override suspend fun toggleWatchLater(id: Int) { movieDao.getMovieById(id)?.let { movieDao.updateWatchLater(id, !it.isWatchLater); remoteUpdate(id, "watchLater", !it.isWatchLater) } }
    override suspend fun toggleSave(id: Int) { movieDao.getMovieById(id)?.let { movieDao.updateSaved(id, !it.isSaved); remoteUpdate(id, "saved", !it.isSaved) } }
    override suspend fun toggleFinished(id: Int) { movieDao.getMovieById(id)?.let { movieDao.updateFinished(id, !it.isFinished); remoteUpdate(id, "finished", !it.isFinished) } }

    override suspend fun addToWatching(id: Int) {
        val ts = System.currentTimeMillis()
        movieDao.updateWatching(id, true, ts)
        repositoryScope.launch {
            authDataSource.getCurrentUserUid()?.let { userPreferencesDataSource.updateWatchingTimestamp(it, id, ts) }
            userPreferencesDataSource.updateTrend(id, "views", 1L)
        }
    }

    override suspend fun updatePlaybackProgress(id: Int, pos: Long, dur: Long) {
        movieDao.updatePlaybackProgress(id, pos, dur)
        val now = System.currentTimeMillis()
        if (now - (lastProgressUpdate[id] ?: 0L) > PROGRESS_SYNC_THRESHOLD) {
            repositoryScope.launch {
                authDataSource.getCurrentUserUid()?.let { userPreferencesDataSource.updateWatchingProgress(it, id, pos, dur, now) }
                lastProgressUpdate[id] = now
            }
        }
        if (dur > 0 && pos.toDouble() / dur.toDouble() > 0.95) movieDao.getMovieById(id)?.let { if (!it.isFinished) toggleFinished(id) }
    }

    override suspend fun clearWatchingHistory() {}
    override suspend fun clearCache() = movieDao.clearAllMovies()
    override fun isMovieLiked(id: Int) = movieDao.getMovieFlowById(id).map { it?.isLiked ?: false }
    override fun isMovieSaved(id: Int) = movieDao.getMovieFlowById(id).map { it?.isSaved ?: false }
    override fun isMovieInWatchLater(id: Int) = movieDao.getMovieFlowById(id).map { it?.isWatchLater ?: false }
    override fun isMovieFinished(id: Int) = movieDao.getMovieFlowById(id).map { it?.isFinished ?: false }
    override fun isMovieWatching(id: Int) = movieDao.getMovieFlowById(id).map { it?.isWatching ?: false }
    override fun getSearchHistory() = searchHistoryDao.getSearchHistory()
    override suspend fun addSearchQuery(q: String) = searchHistoryDao.insertSearch(SearchHistoryEntity(q))
    override suspend fun clearSearchHistory() = searchHistoryDao.clearHistory()

    override suspend fun reportBrokenLink(id: Int, srv: String, fId: String) {
        userPreferencesDataSource.reportBrokenLink(mapOf("movieId" to id, "server" to srv, "fileId" to fId, "timestamp" to System.currentTimeMillis(), "userId" to (authDataSource.getCurrentUserUid() ?: "anonymous")))
    }

    override fun getUser(): Flow<User?> = combine(authDataSource.getAuthStateFlow(), _avatarOverride.asStateFlow()) { u, o -> u?.copy(profilePictureUri = o ?: u.profilePictureUri) }

    override suspend fun register(user: String, email: String, pass: String) {
        val uid = authDataSource.register(user, email, pass)
        _avatarOverride.value = null
        repositoryScope.launch {
            val ud = mapOf("username" to user, "email" to email, "createdAt" to System.currentTimeMillis(), "liked" to emptyList<Int>(), "saved" to emptyList<Int>(), "watchLater" to emptyList<Int>(), "finished" to emptyList<Int>(), "watching" to emptyMap<String, Any>())
            userPreferencesDataSource.saveInitialUserData(uid, ud)
        }
    }

    override suspend fun login(email: String, pass: String) = authDataSource.login(email, pass)
    override suspend fun logout() { authDataSource.logout(); _avatarOverride.value = null }
    override suspend fun updateAvatar(uri: String) { _avatarOverride.value = uri; repositoryScope.launch { authDataSource.updateAvatar(uri) } }
    override suspend fun resetPassword(email: String) { authDataSource.resetPassword(email) }

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
        subtitles = emptyList(),
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
