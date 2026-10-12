package com.example.gamercalendar.ui.feature.map

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsMapUiState(
    val sessions: List<GamingSession> = emptyList(),
    val markerBitmaps: Map<String, Bitmap> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class SessionsMapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository()

    private val _uiState = MutableStateFlow(SessionsMapUiState())
    val uiState: StateFlow<SessionsMapUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val sessions = repository.getPublicInPersonSessions()
                _uiState.update { it.copy(sessions = sessions, isLoading = false) }
                val bitmaps = markerBitmaps(sessions)
                _uiState.update { it.copy(markerBitmaps = bitmaps) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't load sessions"
                    )
                }
            }
        }
    }

    private suspend fun markerBitmaps(sessions: List<GamingSession>): Map<String, Bitmap> {
        val density = getApplication<Application>().resources.displayMetrics.density
        val covers = loadCovers(sessions.mapNotNull { it.game.cover_url }.distinct())
        return sessions.mapNotNull { session ->
            val cover = covers[session.game.cover_url] ?: return@mapNotNull null
            session.id to sessionMarkerBitmap(cover, density)
        }.toMap()
    }

    private suspend fun loadCovers(urls: List<String>): Map<String, Bitmap> = coroutineScope {
        val context = getApplication<Application>()
        val loader = SingletonImageLoader.get(context)
        val size = (128 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

        urls.map { url ->
            async {
                val bitmap = try {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(size)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    (result as? SuccessResult)?.image?.toBitmap()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                url to bitmap
            }
        }.awaitAll().mapNotNull { (url, bitmap) -> bitmap?.let { url to it } }.toMap()
    }
}
