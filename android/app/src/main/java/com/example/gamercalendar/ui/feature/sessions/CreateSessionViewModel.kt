package com.example.gamercalendar.ui.feature.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SessionCreate
import com.example.gamercalendar.data.model.SessionPlace
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.data.model.SessionVisibility
import com.example.gamercalendar.data.model.UserGroup
import com.example.gamercalendar.data.model.GameSearchResult
import com.example.gamercalendar.data.model.place
import com.example.gamercalendar.data.repository.GroupRepository
import com.example.gamercalendar.data.repository.PlaceRepository
import com.example.gamercalendar.data.repository.PlaceSuggestion
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.ui.navigation.Routes
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

private const val GAME_SEARCH_DEBOUNCE_MS = 250L
private const val PLACE_SEARCH_DEBOUNCE_MS = 250L

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
    /** What's typed in the location field. Saved as a plain name if no place is picked. */
    val locationQuery: String = "",
    val place: SessionPlace? = null,
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
    val canSearchPlaces: Boolean = false,
    val placeResults: List<PlaceSuggestion> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val isLoadingPlace: Boolean = false,
    val placeSearchError: String? = null,
    val groups: List<UserGroup> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val groupsError: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val savedSession: GamingSession? = null,

    val isEditing: Boolean = false,
    val isLoadingSession: Boolean = false,
    val loadError: String? = null,
    val originalStartEpochMillis: Long? = null,
    val originalVisibility: SessionVisibility? = null
) {
    /** A session's group is fixed once created, so editing can't move it into or out of a group. */
    fun canSelectVisibility(visibility: SessionVisibility): Boolean = when {
        !isEditing -> true
        originalVisibility == SessionVisibility.GROUP -> false
        else -> visibility != SessionVisibility.GROUP
    }

    val canChangeGroup: Boolean
        get() = !isEditing
}

class CreateSessionViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    /** Set when editing an existing session; null when creating a new one. */
    private val sessionId: String? = savedStateHandle[Routes.ARG_SESSION_ID]

    private val repository = SessionRepository()
    private val groupRepository = GroupRepository()
    private val placeRepository = PlaceRepository.createOrNull(application)

    private val _uiState = MutableStateFlow(
        CreateSessionUiState(isEditing = sessionId != null, canSearchPlaces = placeRepository != null)
    )
    val uiState: StateFlow<CreateSessionUiState> = _uiState.asStateFlow()

    private var gameSearchJob: Job? = null
    private var gameSelectJob: Job? = null
    private var placeSearchJob: Job? = null
    private var placeSelectJob: Job? = null

    init {
        loadGroups()
        loadExistingSession()
    }

    fun loadExistingSession() {
        val sessionId = sessionId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSession = true, loadError = null) }
            try {
                val session = repository.getSession(sessionId)
                val start = SessionTime.parseIso(session.start_at)
                    ?: throw IllegalStateException("Invalid start time")
                val end = SessionTime.parseIso(session.end_at)
                    ?: throw IllegalStateException("Invalid end time")
                val startLocal = Calendar.getInstance().apply { timeInMillis = start }
                val endLocal = Calendar.getInstance().apply { timeInMillis = end }

                val form = CreateSessionForm(
                    gameId = session.game_id,
                    gameQuery = session.game.name,
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
                    locationQuery = session.location_name.orEmpty(),
                    place = session.place,
                    playerLimit = session.player_limit?.toString().orEmpty()
                )

                _uiState.update {
                    it.copy(
                        form = form,
                        isLoadingSession = false,
                        originalStartEpochMillis = start,
                        originalVisibility = session.visibility
                    )
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

    fun onLocationQueryChange(query: String) {
        updateForm { it.copy(locationQuery = query, place = null) }
        placeSearchJob?.cancel()
        placeSelectJob?.cancel()
        _uiState.update { it.copy(isLoadingPlace = false) }

        val places = placeRepository ?: return
        if (query.isBlank()) {
            _uiState.update {
                it.copy(placeResults = emptyList(), isSearchingPlaces = false, placeSearchError = null)
            }
            return
        }

        _uiState.update { it.copy(isSearchingPlaces = true, placeSearchError = null) }
        placeSearchJob = viewModelScope.launch {
            delay(PLACE_SEARCH_DEBOUNCE_MS)
            try {
                val results = places.search(query.trim())
                _uiState.update { it.copy(placeResults = results, isSearchingPlaces = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        placeSearchError = e.message ?: "Couldn't search locations"
                    )
                }
            }
        }
    }

    fun selectPlace(suggestion: PlaceSuggestion) {
        val places = placeRepository ?: return
        placeSearchJob?.cancel()
        placeSelectJob?.cancel()
        updateForm { it.copy(locationQuery = suggestion.primaryText, place = null) }
        _uiState.update {
            it.copy(isSearchingPlaces = false, placeResults = emptyList(), isLoadingPlace = true)
        }

        placeSelectJob = viewModelScope.launch {
            try {
                val place = places.getPlace(suggestion.placeId)
                updateForm { it.copy(locationQuery = place.name, place = place) }
                _uiState.update { it.copy(isLoadingPlace = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingPlace = false,
                        placeSearchError = e.message ?: "Couldn't load that location"
                    )
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

        val isInPerson = form.sessionType == SessionType.IN_PERSON
        val place = form.place.takeIf { isInPerson }
        val typedLocation = form.locationQuery.trim().takeIf { isInPerson && it.isNotBlank() }

        val request = SessionCreate(
            game_id = form.gameId!!,
            group_id = form.groupId.takeIf { form.visibility == SessionVisibility.GROUP },
            title = form.title.trim().ifBlank { null },
            description = form.description.trim().ifBlank { null },
            start_at = SessionTime.toIsoUtc(form.startEpochMillis),
            end_at = SessionTime.toIsoUtc(form.endEpochMillis),
            session_type = form.sessionType,
            visibility = form.visibility,
            location_name = place?.name ?: typedLocation,
            location_address = place?.address,
            location_place_id = place?.placeId,
            location_lat = place?.lat,
            location_lng = place?.lng,
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
        if (form.sessionType == SessionType.IN_PERSON) {
            if (form.locationQuery.isBlank()) {
                return "Enter a location for an in-person session"
            }
            if (_uiState.value.isLoadingPlace) {
                return "Still loading the location, try again in a moment"
            }
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
