package com.example.gamercalendar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SessionCreate
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.data.model.SessionVisibility
import com.example.gamercalendar.data.model.UserGroup
import com.example.gamercalendar.data.repository.GroupRepository
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class CreateSessionForm(
    val gameId: String? = null,
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
    val games: List<Game> = emptyList(),
    val isLoadingGames: Boolean = false,
    val gamesError: String? = null,
    val groups: List<UserGroup> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val groupsError: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val createdSession: GamingSession? = null
)

class CreateSessionViewModel : ViewModel() {

    private val repository = SessionRepository()
    private val groupRepository = GroupRepository()

    private val _uiState = MutableStateFlow(CreateSessionUiState())
    val uiState: StateFlow<CreateSessionUiState> = _uiState.asStateFlow()

    init {
        loadGames()
        loadGroups()
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

    fun loadGames() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGames = true, gamesError = null) }
            try {
                val games = repository.getGames()
                _uiState.update { it.copy(games = games, isLoadingGames = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingGames = false,
                        gamesError = apiErrorDetail(e) ?: e.message ?: "Couldn't load games"
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
                val session = repository.createSession(request)
                _uiState.update { it.copy(isSubmitting = false, createdSession = session) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't create session"
                    )
                }
            }
        }
    }

    private fun validate(form: CreateSessionForm): String? {
        if (form.gameId == null) {
            return "Choose a game"
        }
        if (form.startEpochMillis <= System.currentTimeMillis()) {
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
