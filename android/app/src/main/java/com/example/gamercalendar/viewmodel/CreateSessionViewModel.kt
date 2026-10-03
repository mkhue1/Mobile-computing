package com.example.gamercalendar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SessionCreate
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.data.model.SessionVisibility
import com.example.gamercalendar.data.model.UserGroup
import com.example.gamercalendar.data.model.GameSearchResult
import com.example.gamercalendar.data.repository.GroupRepository
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.ui.navigation.Routes
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

private const val GAME_SEARCH_DEBOUNCE_MS = 250L

data class CreateSessionForm(
    val gameId: String? = null,
    val gameQuery: String = "",
    val groupId: String? = null,
    val title: String = "",
    val description: String = "",
    val dateUtcMillis: Long,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val sessionType: SessionType = SessionType.ONLINE,
    val visibility: SessionVisibility = SessionVisibility.PRIVATE,
    val locationName: String = "",
    val playerLimit: String = ""
) {
    /** An end time at or before the start time is treated as running past midnight. */
    val endsNextDay: Boolean
        get() = endHour * 60 + endMinute <= startHour * 60 + startMinute

    val startEpochMillis: Long
        get() = SessionTime.toEpochMillis(dateUtcMillis, startHour, startMinute)

    val endEpochMillis: Long
        get() = SessionTime.toEpochMillis(
            dateUtcMillis,
            endHour,
            endMinute,
            plusDays = if (endsNextDay) 1 else 0
        )

    companion object {
        /** Starts at the next full hour and runs for two hours. */
        fun default(): CreateSessionForm {
            val start = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
            val startHour = start.get(Calendar.HOUR_OF_DAY)
            return CreateSessionForm(
                dateUtcMillis = SessionTime.localDateToUtcMillis(
                    start.get(Calendar.YEAR),
                    start.get(Calendar.MONTH),
                    start.get(Calendar.DAY_OF_MONTH)
                ),
                startHour = startHour,
                startMinute = 0,
                endHour = (startHour + 2) % 24,
                endMinute = 0
            )
        }
    }
}

data class CreateSessionUiState(
    val form: CreateSessionForm = CreateSessionForm.default(),
    val gameResults: List<GameSearchResult> = emptyList(),
    val isSearchingGames: Boolean = false,
    val gameSearchError: String? = null,
    val groups: List<UserGroup> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val groupsError: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val savedSession: GamingSession? = null,

    val isEditing: Boolean = false,
    val isLoadingSession: Boolean = false,
    val loadError: String? = null,
    val originalStartEpochMillis: Long? = null
)

class CreateSessionViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Set when editing an existing session; null when creating a new one. */
    private val sessionId: String? = savedStateHandle[Routes.ARG_SESSION_ID]

    private val repository = SessionRepository()
    private val groupRepository = GroupRepository()

    private val _uiState = MutableStateFlow(CreateSessionUiState(isEditing = sessionId != null))
    val uiState: StateFlow<CreateSessionUiState> = _uiState.asStateFlow()

    private var gameSearchJob: Job? = null
    private var gameSelectJob: Job? = null

    init {
        loadGroups()
        loadExistingSession()
    }

    fun loadExistingSession() {
        val sessionId = sessionId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSession = true, loadError = null) }
            try {
                val (session, games) = coroutineScope {
                    val session = async { repository.getSession(sessionId) }
                    val games = async { repository.getGames() }
                    session.await() to games.await()
                }
                val start = SessionTime.parseIso(session.start_at)
                    ?: throw IllegalStateException("Invalid start time")
                val end = SessionTime.parseIso(session.end_at)
                    ?: throw IllegalStateException("Invalid end time")
                val startLocal = Calendar.getInstance().apply { timeInMillis = start }
                val endLocal = Calendar.getInstance().apply { timeInMillis = end }

                val form = CreateSessionForm(
                    gameId = session.game_id,
                    gameQuery = games.firstOrNull { it.id == session.game_id }?.name.orEmpty(),
                    groupId = session.group_id,
                    title = session.title.orEmpty(),
                    description = session.description.orEmpty(),
                    dateUtcMillis = SessionTime.localDateToUtcMillis(
                        startLocal.get(Calendar.YEAR),
                        startLocal.get(Calendar.MONTH),
                        startLocal.get(Calendar.DAY_OF_MONTH)
                    ),
                    startHour = startLocal.get(Calendar.HOUR_OF_DAY),
                    startMinute = startLocal.get(Calendar.MINUTE),
                    endHour = endLocal.get(Calendar.HOUR_OF_DAY),
                    endMinute = endLocal.get(Calendar.MINUTE),
                    sessionType = session.session_type,
                    visibility = session.visibility,
                    locationName = session.location_name.orEmpty(),
                    playerLimit = session.player_limit?.toString().orEmpty()
                )

                _uiState.update {
                    it.copy(form = form, isLoadingSession = false, originalStartEpochMillis = start)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingSession = false,
                        loadError = apiErrorDetail(e) ?: e.message ?: "Couldn't load session"
                    )
                }
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGroups = true, groupsError = null) }
            try {
                val groups = groupRepository.getGroups()
                _uiState.update { it.copy(groups = groups, isLoadingGroups = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingGroups = false,
                        groupsError = apiErrorDetail(e) ?: e.message ?: "Couldn't load groups"
                    )
                }
            }
        }
    }

    fun onGameQueryChange(query: String) {
        updateForm { it.copy(gameQuery = query, gameId = null) }
        gameSearchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(gameResults = emptyList(), isSearchingGames = false, gameSearchError = null)
            }
            return
        }

        _uiState.update { it.copy(isSearchingGames = true, gameSearchError = null) }
        gameSearchJob = viewModelScope.launch {
            delay(GAME_SEARCH_DEBOUNCE_MS)
            try {
                val results = repository.searchGames(query.trim())
                _uiState.update { it.copy(gameResults = results, isSearchingGames = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearchingGames = false,
                        gameSearchError = apiErrorDetail(e) ?: e.message ?: "Couldn't search games"
                    )
                }
            }
        }
    }

    fun selectGame(result: GameSearchResult) {
        gameSearchJob?.cancel()
        gameSelectJob?.cancel()
        updateForm { it.copy(gameId = null, gameQuery = result.name) }
        _uiState.update { it.copy(isSearchingGames = false, gameResults = emptyList()) }

        gameSelectJob = viewModelScope.launch {
            try {
                val game = repository.getGameByIgdbId(result.igdb_id)
                updateForm { it.copy(gameId = game.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(gameSearchError = apiErrorDetail(e) ?: e.message ?: "Couldn't select game")
                }
            }
        }
    }

    fun updateForm(transform: (CreateSessionForm) -> CreateSessionForm) {
        _uiState.update { it.copy(form = transform(it.form), error = null) }
    }

    fun submit() {
        val form = _uiState.value.form
        val validationError = validate(form)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        val request = SessionCreate(
            game_id = form.gameId!!,
            group_id = form.groupId.takeIf { form.visibility == SessionVisibility.GROUP },
            title = form.title.trim().ifBlank { null },
            description = form.description.trim().ifBlank { null },
            start_at = SessionTime.toIsoUtc(form.startEpochMillis),
            end_at = SessionTime.toIsoUtc(form.endEpochMillis),
            session_type = form.sessionType,
            visibility = form.visibility,
            location_name = form.locationName.trim()
                .takeIf { form.sessionType == SessionType.IN_PERSON && it.isNotBlank() },
            player_limit = form.playerLimit.toIntOrNull()
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val session = if (sessionId != null) {
                    repository.updateSession(sessionId, request)
                } else {
                    repository.createSession(request)
                }
                _uiState.update { it.copy(isSubmitting = false, savedSession = session) }
            } catch (e: Exception) {
                val fallback = if (sessionId != null) "Couldn't save changes" else "Couldn't create session"
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = apiErrorDetail(e) ?: e.message ?: fallback
                    )
                }
            }
        }
    }

    private fun validate(form: CreateSessionForm): String? {
        if (form.gameId == null) {
            return "Choose a game"
        }
        // An in-progress session can still be edited as long as its start time isn't moved.
        val startChanged = form.startEpochMillis != _uiState.value.originalStartEpochMillis
        if (startChanged && form.startEpochMillis <= System.currentTimeMillis()) {
            return "Start time must be in the future"
        }
        if (form.sessionType == SessionType.IN_PERSON && form.locationName.isBlank()) {
            return "Enter a location for an in-person session"
        }
        if (form.visibility == SessionVisibility.GROUP && form.groupId == null) {
            return "Choose a group"
        }
        if (form.playerLimit.isNotBlank()) {
            val limit = form.playerLimit.toIntOrNull()
            if (limit == null || limit < 2) {
                return "Player limit must be at least 2"
            }
        }
        return null
    }
}
