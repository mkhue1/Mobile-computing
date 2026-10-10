package com.example.gamercalendar.ui.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.CalendarSession
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the Month view shows. */
internal data class MonthUiState(
    val selectedDate: LocalDate,
    val sessionsByDate: Map<LocalDate, List<CalendarSession>>, // grouped by the day each session starts
    val selectedDaySessions: List<CalendarSession>
)

/** Builds the Month view's state from the shared sessions and the selected day. */
internal class MonthViewModel(
    sessionsByDate: StateFlow<Map<LocalDate, List<CalendarSession>>>,
    private val selectedDate: StateFlow<LocalDate>
) : ViewModel() {

    /** The months the calendar can scroll through: a year either side of this month. */
    val startMonth: YearMonth = YearMonth.now().minusMonths(MONTHS_EACH_WAY)
    val endMonth: YearMonth = YearMonth.now().plusMonths(MONTHS_EACH_WAY)

    val uiState: StateFlow<MonthUiState> = combine(sessionsByDate, selectedDate) { sessions, selected ->
        MonthUiState(
            selectedDate = selected,
            sessionsByDate = sessions,
            selectedDaySessions = sessions[selected].orEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MonthUiState(
            selectedDate = selectedDate.value,
            sessionsByDate = sessionsByDate.value,
            selectedDaySessions = sessionsByDate.value[selectedDate.value].orEmpty()
        )
    )

    /** The month to open on: the selected day's month, kept inside the range the calendar covers. */
    fun monthToShow(): YearMonth = YearMonth.from(selectedDate.value).coerceIn(startMonth, endMonth)

    private companion object {
        const val MONTHS_EACH_WAY = 12L
    }
}

internal class MonthViewModelFactory(
    private val calendar: CalendarViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MonthViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return MonthViewModel(calendar.sessionsByDate, calendar.selectedDate) as T
    }
}