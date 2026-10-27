package com.ritm.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ritm.app.ai.AiClient
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Состояние интерфейса, которое живёт, пока открыто приложение:
 * выбранные вкладки, даты, результаты ИИ. Не сбрасывается при переходе между вкладками.
 */
data class CheckDraft(
    val id: Long?,
    val date: LocalDate,
    val weight: String,
    val waist: String,
    val note: String,
    val photos: List<String>,
    val originalPhotos: List<String>,
)

object UiState {
    var today by mutableStateOf(LocalDate.now())

    var mainTab by mutableStateOf(MainTab.Plans)
    var plansSub by mutableIntStateOf(0)
    var workoutSub by mutableIntStateOf(0)

    var tasksDate by mutableStateOf(LocalDate.now())
    var foodDate by mutableStateOf(LocalDate.now())
    var programDay by mutableIntStateOf(LocalDate.now().dayOfWeek.value)
    /** Какой день программы выполняется сегодня (по умолчанию — текущий день недели). */
    var todayProgramDay by mutableIntStateOf(LocalDate.now().dayOfWeek.value)
    var workoutAgain by mutableStateOf(false)

    var plannerItems by mutableStateOf<List<AiClient.PlanItem>>(emptyList())
    var plannerLoading by mutableStateOf(false)
    var plannerError by mutableStateOf<String?>(null)

    /** Когда стартовал запрос ИИ (для таймера на кнопке). */
    var aiStartedAt by mutableStateOf(mapOf<String, Long>())

    var nutritionLoading by mutableStateOf(false)
    var nutritionError by mutableStateOf<String?>(null)
    var nutritionComment by mutableStateOf<String?>(null)

    var programAiOpen by mutableStateOf(false)
    var programResult by mutableStateOf<AiClient.ProgramResult?>(null)
    var programLoading by mutableStateOf(false)
    var programStep by mutableStateOf<String?>(null)
    var programError by mutableStateOf<String?>(null)

    /** Прогресс: редактор замера, сравнение, просмотр. */
    var progressNew by mutableStateOf(false)
    var progressEditor by mutableStateOf<CheckDraft?>(null)
    var progressCompare by mutableStateOf(false)
    var compareA by mutableStateOf<Long?>(null)
    var compareB by mutableStateOf<Long?>(null)

    /** Открытый приём пищи (редактор КБЖУ). */
    var foodEditId by mutableStateOf<Long?>(null)

    /** Открыть камеру для еды (из уведомления). */
    var openFoodCamera by mutableStateOf(false)

    /** Понедельник, от которого считаются страницы календаря. */
    val baseMonday: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)
    const val WEEK_PAGES = 10_000
    const val WEEK_CENTER = 5_000

    fun refreshDay() {
        val now = LocalDate.now()
        if (now != today) {
            if (tasksDate == today) tasksDate = now
            if (foodDate == today) foodDate = now
            todayProgramDay = now.dayOfWeek.value
            workoutAgain = false
            today = now
        }
    }
}
