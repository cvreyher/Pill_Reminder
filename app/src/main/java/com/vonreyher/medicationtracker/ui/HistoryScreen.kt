package com.vonreyher.medicationtracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.IntakeEvent
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.Person
import com.vonreyher.medicationtracker.formatted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class HistoryView(val label: String) { List("Liste"), Calendar("Kalender") }

private enum class HistoryRange(val label: String, val days: Long?) {
    Week("7 Tage", 7),
    Month("30 Tage", 30),
    All("Alle", null)
}

private val DayFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val ShortDateFormatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
private val MonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)

/** When which medication was taken and how often – as a list or as a month calendar. */
@Composable
fun HistoryScreen(
    history: List<IntakeEvent>,
    persons: List<Person>,
    careMode: Boolean,
    contentPadding: PaddingValues
) {
    var view by rememberSaveable { mutableStateOf(HistoryView.List) }
    var range by rememberSaveable { mutableStateOf(HistoryRange.Week) }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now()) }

    val today = LocalDate.now()
    val personEvents = history.filter { personId == null || it.personId == personId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ConnectedChoice(
                options = HistoryView.entries,
                selected = view,
                label = { it.label },
                onSelect = { view = it }
            )
        }
        if (careMode && persons.size > 1) {
            item { PersonFilterChips(persons, personId, { personId = it }) }
        }

        when (view) {
            HistoryView.List -> {
                item {
                    ConnectedChoice(
                        options = HistoryRange.entries,
                        selected = range,
                        label = { it.label },
                        onSelect = { range = it }
                    )
                }
                val from = range.days?.let { today.minusDays(it - 1) }
                val events = personEvents
                    .filter { from == null || !it.takenAt.toLocalDate().isBefore(from) }
                    .sortedByDescending { it.takenAt }
                historyList(events, persons, careMode, today)
            }
            HistoryView.Calendar -> {
                item {
                    MonthCalendar(
                        month = month,
                        selectedDay = selectedDay,
                        today = today,
                        intakesPerDay = personEvents.groupingBy { it.takenAt.toLocalDate() }.eachCount(),
                        onMonthChange = { month = it },
                        onSelectDay = { selectedDay = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item {
                    SectionTitle(
                        when (selectedDay) {
                            today -> "Heute"
                            today.minusDays(1) -> "Gestern"
                            else -> selectedDay.format(DayFormatter)
                        }
                    )
                }
                val dayEvents = personEvents
                    .filter { it.takenAt.toLocalDate() == selectedDay }
                    .sortedBy { it.takenAt }
                if (dayEvents.isEmpty()) {
                    item { EmptyText("An diesem Tag wurde nichts eingenommen.") }
                } else {
                    items(dayEvents, key = { it.id }) { event -> IntakeRow(event, persons, careMode) }
                }
            }
        }
    }
}

/** An expressive connected button group with one option selected. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = selected == option,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Text(label(option))
            }
        }
    }
}

private fun LazyListScope.historyList(
    events: List<IntakeEvent>,
    persons: List<Person>,
    careMode: Boolean,
    today: LocalDate
) {
    if (events.isEmpty()) {
        item { EmptyText("In diesem Zeitraum wurde noch nichts eingenommen.") }
        return
    }

    item { SectionTitle("Wie oft") }
    item {
        val frequency = events
            .groupBy { it.personId to it.name }
            .entries
            .sortedByDescending { it.value.size }
        OutlinedCard(
            modifier = Modifier.padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            frequency.forEachIndexed { index, (key, intakes) ->
                if (index > 0) HorizontalDivider()
                val last = intakes.maxOf { it.takenAt }
                val person = persons.firstOrNull { it.id == key.first }.takeIf { careMode }
                ListItem(
                    headlineContent = { Text(key.second) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                person?.fullName,
                                "zuletzt am ${last.format(ShortDateFormatter)} um ${last.toLocalTime().formatted()} Uhr"
                            ).joinToString(" · ")
                        )
                    },
                    leadingContent = person?.let { { PersonAvatar(it) } },
                    trailingContent = {
                        Text("${intakes.size}×", style = MaterialTheme.typography.titleLargeEmphasized)
                    }
                )
            }
        }
    }

    item { SectionTitle("Verlauf") }
    events.groupBy { it.takenAt.toLocalDate() }.forEach { (date, dayEvents) ->
        item(key = date.toString()) {
            Text(
                text = when (date) {
                    today -> "Heute"
                    today.minusDays(1) -> "Gestern"
                    else -> date.format(DayFormatter)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
        items(dayEvents, key = { it.id }) { event -> IntakeRow(event, persons, careMode) }
    }
}

@Composable
private fun IntakeRow(event: IntakeEvent, persons: List<Person>, careMode: Boolean) {
    val person = persons.firstOrNull { it.id == event.personId }.takeIf { careMode }
    ListItem(
        headlineContent = { Text(event.name) },
        supportingContent = {
            Column {
                Text(listOfNotNull(person?.fullName, event.dosage).joinToString(" · "))
                Text(event.scheduledTime?.let { "geplant für ${it.formatted()} Uhr" } ?: "bei Bedarf")
            }
        },
        leadingContent = {
            if (person != null) PersonAvatar(person)
            else Icon(painterResource(R.drawable.ic_check), contentDescription = null)
        },
        trailingContent = {
            Text(event.takenAt.toLocalTime().formatted(), style = MaterialTheme.typography.labelLarge)
        }
    )
}

/**
 * Month grid starting on Monday. Days with intakes get a filled marker with their count, today is
 * outlined, the selected day is highlighted.
 */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    selectedDay: LocalDate,
    today: LocalDate,
    intakesPerDay: Map<LocalDate, Int>,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Vorheriger Monat")
                }
                Text(
                    text = month.format(MonthFormatter),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onMonthChange(month.plusMonths(1)) },
                    enabled = month.isBefore(YearMonth.from(today))
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Nächster Monat")
                }
            }

            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.size(4.dp))

            val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
            val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map(month::atDay)
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                CalendarDay(
                                    date = date,
                                    count = intakesPerDay[date] ?: 0,
                                    isToday = date == today,
                                    isSelected = date == selectedDay,
                                    isFuture = date.isAfter(today),
                                    onClick = { onSelectDay(date) }
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val container = when {
        isSelected -> colorScheme.primary
        count > 0 -> colorScheme.primaryContainer
        else -> colorScheme.surfaceContainerLow
    }
    val content = when {
        isSelected -> colorScheme.onPrimary
        count > 0 -> colorScheme.onPrimaryContainer
        isFuture -> colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = !isFuture,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (isToday && !isSelected) BorderStroke(2.dp, colorScheme.primary) else null,
        modifier = Modifier
            .padding(3.dp)
            .fillMaxSize()
            .semantics {
                contentDescription = "${date.format(DayFormatter)}: " +
                    if (count == 0) "keine Einnahmen" else "$count Einnahmen"
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
            if (count > 0) {
                Text("$count×", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp)
    )
}
