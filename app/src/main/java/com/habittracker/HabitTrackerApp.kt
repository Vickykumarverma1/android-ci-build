package com.habittracker

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MAX_HABITS = 10

private val AppColors = lightColorScheme(
    primary = Color(0xFF2B6E4F),
    onPrimary = Color.White,
    secondary = Color(0xFFF5B971),
    background = Color(0xFFF6F1E7),
    surface = Color(0xFFFFFBF4),
    surfaceVariant = Color(0xFFE7E2D7),
    onSurface = Color(0xFF1E1B18),
    outline = Color(0xFFC7BFAF)
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "habit_completions", primaryKeys = ["habitId", "date"])
data class HabitCompletionEntity(
    val habitId: Int,
    val date: String
)

@Dao
interface HabitTrackerDao {
    @Query("SELECT * FROM habits ORDER BY id ASC")
    fun observeHabits(): kotlinx.coroutines.flow.Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_completions WHERE date LIKE :monthPrefix || '%'")
    fun observeCompletionsForMonth(monthPrefix: String): kotlinx.coroutines.flow.Flow<List<HabitCompletionEntity>>

    @Insert
    suspend fun insertHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabitById(habitId: Int)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId")
    suspend fun deleteCompletionsByHabit(habitId: Int)

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun getHabitCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(entry: HabitCompletionEntity)

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun findCompletion(habitId: Int, date: String): HabitCompletionEntity?

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCompletion(habitId: Int, date: String)
}

@Database(
    entities = [HabitEntity::class, HabitCompletionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HabitTrackerDatabase : RoomDatabase() {
    abstract fun dao(): HabitTrackerDao

    companion object {
        @Volatile
        private var instance: HabitTrackerDatabase? = null

        fun get(context: Context): HabitTrackerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitTrackerDatabase::class.java,
                    "habit-tracker.db"
                ).build().also { instance = it }
            }
        }
    }
}

data class HabitRowUi(
    val id: Int,
    val name: String,
    val completedDates: Set<LocalDate>
)

data class HabitTrackerUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val habitRows: List<HabitRowUi> = emptyList(),
    val dailyTotals: Map<LocalDate, Int> = emptyMap(),
    val canAddMore: Boolean = true
)

class HabitTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = HabitTrackerDatabase.get(application).dao()
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<HabitTrackerUiState> = combine(
        selectedMonth,
        dao.observeHabits(),
        selectedMonth.flatMapLatest { month ->
            dao.observeCompletionsForMonth(month.toString())
        }
    ) { month, habits, completions ->
        val completionsByHabit = completions
            .groupBy { it.habitId }
            .mapValues { (_, items) -> items.map { LocalDate.parse(it.date) }.toSet() }

        val dailyTotals = completions
            .map { LocalDate.parse(it.date) }
            .groupingBy { it }
            .eachCount()

        HabitTrackerUiState(
            selectedMonth = month,
            habitRows = habits.map { habit ->
                HabitRowUi(
                    id = habit.id,
                    name = habit.name,
                    completedDates = completionsByHabit[habit.id].orEmpty()
                )
            },
            dailyTotals = dailyTotals,
            canAddMore = habits.size < MAX_HABITS
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HabitTrackerUiState()
    )

    fun addHabit(name: String) {
        val cleanedName = name.trim()
        if (cleanedName.isBlank()) return

        viewModelScope.launch {
            if (dao.getHabitCount() >= MAX_HABITS) return@launch
            dao.insertHabit(HabitEntity(name = cleanedName))
        }
    }

    fun deleteHabit(habitId: Int) {
        viewModelScope.launch {
            dao.deleteCompletionsByHabit(habitId)
            dao.deleteHabitById(habitId)
        }
    }

    fun toggleHabit(habitId: Int, date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return

        viewModelScope.launch {
            val existing = dao.findCompletion(habitId, date.toString())
            if (existing == null) {
                dao.upsertCompletion(HabitCompletionEntity(habitId = habitId, date = date.toString()))
            } else {
                dao.deleteCompletion(habitId, date.toString())
            }
        }
    }

    fun showPreviousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        val currentMonth = YearMonth.now()
        if (selectedMonth.value < currentMonth) {
            selectedMonth.value = selectedMonth.value.plusMonths(1)
        }
    }
}

@Composable
fun HabitTrackerApp() {
    MaterialTheme(colorScheme = AppColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val trackerViewModel: HabitTrackerViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
                    androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
                )
            )
            val state by trackerViewModel.uiState.collectAsState()

            HabitTrackerScreen(
                state = state,
                onAddHabit = trackerViewModel::addHabit,
                onDeleteHabit = trackerViewModel::deleteHabit,
                onToggleHabit = trackerViewModel::toggleHabit,
                onPreviousMonth = trackerViewModel::showPreviousMonth,
                onNextMonth = trackerViewModel::showNextMonth
            )
        }
    }
}

@Composable
private fun HabitTrackerScreen(
    state: HabitTrackerUiState,
    onAddHabit: (String) -> Unit,
    onDeleteHabit: (Int) -> Unit,
    onToggleHabit: (Int, LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val days = remember(state.selectedMonth) { daysInMonth(state.selectedMonth) }
    val today = LocalDate.now()
    val todayDoneCount = state.dailyTotals[today] ?: 0
    var habitName by rememberSaveable { mutableStateOf("") }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderCard(
                selectedMonth = state.selectedMonth,
                todayDoneCount = todayDoneCount,
                habitCount = state.habitRows.size,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )

            AddHabitCard(
                habitName = habitName,
                onHabitNameChange = { habitName = it },
                onAddHabit = {
                    onAddHabit(habitName)
                    habitName = ""
                },
                canAddMore = state.canAddMore
            )

            MonthTrackerCard(
                days = days,
                habitRows = state.habitRows,
                onDeleteHabit = onDeleteHabit,
                onToggleHabit = onToggleHabit
            )

            ProgressGraphCard(
                days = days,
                dailyTotals = state.dailyTotals,
                todayDoneCount = todayDoneCount
            )
        }
    }
}

@Composable
private fun HeaderCard(
    selectedMonth: YearMonth,
    todayDoneCount: Int,
    habitCount: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Habit Pulse",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Track up to 10 habits, tick what you completed today, and watch the graph grow automatically.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthButton(label = "Prev", onClick = onPreviousMonth)
                Text(
                    text = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                MonthButton(label = "Next", onClick = onNextMonth)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBadge(title = "Today's ticks", value = "$todayDoneCount / $MAX_HABITS")
                MetricBadge(title = "Active habits", value = "$habitCount / $MAX_HABITS")
            }
        }
    }
}

@Composable
private fun MonthButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text = label)
    }
}

@Composable
private fun MetricBadge(title: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Text(text = value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AddHabitCard(
    habitName: String,
    onHabitNameChange: (String) -> Unit,
    onAddHabit: () -> Unit,
    canAddMore: Boolean
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add habit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = habitName,
                onValueChange = onHabitNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = canAddMore,
                singleLine = true,
                label = { Text("Habit name") },
                placeholder = { Text("Reading, workout, water...") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (canAddMore) {
                        "You can save up to 10 habits."
                    } else {
                        "Habit limit reached. Remove one to add another."
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onAddHabit, enabled = canAddMore && habitName.isNotBlank()) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun MonthTrackerCard(
    days: List<LocalDate>,
    habitRows: List<HabitRowUi>,
    onDeleteHabit: (Int) -> Unit,
    onToggleHabit: (Int, LocalDate) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Monthly tracker",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (habitRows.isEmpty()) {
                Text(
                    text = "Add your first habit to start ticking the month tracker.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            } else {
                val horizontalState = rememberScrollState()
                val cellSize = 38.dp

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Habit",
                        modifier = Modifier.width(132.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(modifier = Modifier.horizontalScroll(horizontalState)) {
                        days.forEach { day ->
                            DayHeaderCell(day = day, cellSize = cellSize)
                        }
                    }
                }

                habitRows.forEach { habit ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HabitNameCell(
                            name = habit.name,
                            onDelete = { onDeleteHabit(habit.id) }
                        )
                        Row(modifier = Modifier.horizontalScroll(horizontalState)) {
                            days.forEach { day ->
                                TrackerDayCell(
                                    cellSize = cellSize,
                                    isDone = habit.completedDates.contains(day),
                                    isFuture = day.isAfter(LocalDate.now()),
                                    onClick = { onToggleHabit(habit.id, day) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitNameCell(name: String, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = name,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )
        TextButton(
            onClick = onDelete,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text("Remove")
        }
    }
}

@Composable
private fun DayHeaderCell(day: LocalDate, cellSize: Dp) {
    Column(
        modifier = Modifier.width(cellSize),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = day.dayOfWeek.name.take(1),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun TrackerDayCell(
    cellSize: Dp,
    isDone: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        isDone -> MaterialTheme.colorScheme.primary
        isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else -> Color.Transparent
    }

    val contentColor = when {
        isDone -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    }

    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(cellSize)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = 1.dp,
                color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !isFuture, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isDone) "✓" else "",
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun ProgressGraphCard(
    days: List<LocalDate>,
    dailyTotals: Map<LocalDate, Int>,
    todayDoneCount: Int
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Progress graph",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "The graph always reads from 0 to 10. If today has 5 ticks, today's progress shows as 5.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
            MetricBadge(title = "Today", value = "$todayDoneCount / $MAX_HABITS")
            DailyProgressGraph(days = days, dailyTotals = dailyTotals)
        }
    }
}

@Composable
private fun DailyProgressGraph(days: List<LocalDate>, dailyTotals: Map<LocalDate, Int>) {
    val today = LocalDate.now()
    val chartHeight = 180.dp
    val plottedDays = remember(days) { days.filter { !it.isAfter(today) } }

    if (plottedDays.isEmpty()) {
        Text(
            text = "No progress to show yet for this month.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier.height(chartHeight),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text("10", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text("5", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text("0", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            plottedDays.forEach { day ->
                val value = dailyTotals[day] ?: 0
                DayProgressBar(
                    day = day,
                    value = value,
                    chartHeight = chartHeight
                )
            }
        }
    }
}

@Composable
private fun DayProgressBar(day: LocalDate, value: Int, chartHeight: Dp) {
    val barHeight = remember(value) {
        ((value.coerceIn(0, MAX_HABITS) / MAX_HABITS.toFloat()) * 120f).dp
    }

    Column(
        modifier = Modifier.widthIn(min = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier.height(chartHeight - 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(
                        if (value > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
        Text(
            text = day.dayOfMonth.toString(),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}

private fun daysInMonth(month: YearMonth): List<LocalDate> {
    return (1..month.lengthOfMonth()).map(month::atDay)
}
