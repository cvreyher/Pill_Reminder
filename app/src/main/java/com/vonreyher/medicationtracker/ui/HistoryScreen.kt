package com.vonreyher.medicationtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.vonreyher.medicationtracker.Medication
import java.time.LocalDateTime
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
    All("Gesamt", null)
}

private val DayFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val ShortDateFormatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
private val MonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)

/** When which medication was taken and how often – as a list or as a month calendar. */
@Composable
fun HistoryScreen(
    history: List<IntakeEvent>,
    /** All medication including stopped ones, to know what was scheduled on each day. */
    medications: List<Medication>,
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
                val personMedications = medications.filter { personId == null || it.personId == personId }
                val from = range.days?.let { today.minusDays(it - 1) }
                    ?: listOfNotNull(
                        personMedications.mapNotNull { it.createdAt?.toLocalDate() }.minOrNull(),
                        personEvents.minOfOrNull { it.takenAt.toLocalDate() }
                    ).minOrNull()
                    ?: today
                val events = personEvents
                    .filter { !it.takenAt.toLocalDate().isBefore(from) }
                    .sortedByDescending { it.takenAt }
                val stats = adherence(personMedications, personEvents, from, today, LocalDateTime.now())
                item { AdherenceCard(stats, weekly = range == HistoryRange.All && stats.size > 31) }
                medicationAdherence(stats, persons, careMode)
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

/** One scheduled dose on one day, and whether it was confirmed. */
private data class DoseOutcome(val medication: Medication, val taken: Boolean)

private data class DayAdherence(val day: LocalDate, val doses: List<DoseOutcome>) {
    val scheduled get() = doses.size
    val taken get() = doses.count { it.taken }
}

/**
 * For each day from [from] to [to]: the doses that were scheduled back then and already due at
 * [now], and whether each was confirmed that day.
 */
private fun adherence(
    medications: List<Medication>,
    history: List<IntakeEvent>,
    from: LocalDate,
    to: LocalDate,
    now: LocalDateTime
): List<DayAdherence> {
    val takenByDay = history.filter { it.scheduledTime != null }
        .groupBy { it.takenAt.toLocalDate() }
        .mapValues { (_, events) -> events.map { it.medicationId }.toSet() }
    return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { day ->
        val taken = takenByDay[day].orEmpty()
        val doses = medications
            .filter { !it.asNeeded }
            .filter { medication ->
                val dueAt = day.atTime(medication.time)
                medication.wasScheduledAt(dueAt) && !dueAt.isAfter(now)
            }
            .map { DoseOutcome(it, it.id in taken) }
        DayAdherence(day, doses)
    }.toList()
}

/** Bar fill: the validated purple steps of the dataviz check (the theme's own purples read too grey). */
@Composable
private fun chartColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFA77FD6) else Color(0xFF7651A3)

/**
 * Share of confirmed doses as a hero number, with one bar per day (or per week for long ranges).
 * Tapping a bar shows its numbers.
 */
@Composable
private fun AdherenceCard(stats: List<DayAdherence>, weekly: Boolean) {
    val scheduled = stats.sumOf { it.scheduled }
    val taken = stats.sumOf { it.taken }
    val bars = if (weekly) {
        stats.groupBy { it.day.with(DayOfWeek.MONDAY) }.entries.toList().takeLast(26).map { (week, days) ->
            Bar(
                label = "KW ${week.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}",
                detail = "Woche ab ${week.format(ShortDateFormatter)}",
                scheduled = days.sumOf { it.scheduled },
                taken = days.sumOf { it.taken }
            )
        }
    } else {
        stats.map { day ->
            Bar(
                label = day.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2),
                detail = day.day.format(DayFormatter),
                scheduled = day.scheduled,
                taken = day.taken
            )
        }
    }
    var selected by remember(bars.size) { mutableStateOf<Int?>(null) }
    val showLabels = bars.size <= 8

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Eingenommen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (scheduled == 0) {
                Text(
                    "In diesem Zeitraum war noch nichts geplant.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${taken * 100 / scheduled} %", style = MaterialTheme.typography.displaySmallEmphasized)
                Text(
                    "· $taken von $scheduled",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            // The tapped bar's numbers, in text colors (the bar color only marks the data).
            Text(
                text = selected?.let { bars.getOrNull(it) }?.let { bar ->
                    if (bar.scheduled == 0) "${bar.detail}: nichts geplant"
                    else "${bar.detail}: ${bar.taken} von ${bar.scheduled} eingenommen"
                } ?: "Tippe auf einen Balken für Details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BarChart(bars, selected, onSelect = { selected = if (selected == it) null else it }, showLabels = showLabels)
            if (!showLabels && bars.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()) {
                    Text(bars.first().detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(bars.last().detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class Bar(val label: String, val detail: String, val scheduled: Int, val taken: Int)

private val ChartHeight = 112.dp

@Composable
private fun BarChart(bars: List<Bar>, selected: Int?, onSelect: (Int) -> Unit, showLabels: Boolean) {
    val fill = chartColor()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val gap = if (bars.size > 14) 2.dp else 8.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = bars.joinToString("; ") { "${it.detail}: ${it.taken} von ${it.scheduled}" }
            },
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        bars.forEachIndexed { index, bar ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = bar.detail) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val share = if (bar.scheduled == 0) 0f else bar.taken.toFloat() / bar.scheduled
                    val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                    // Recessive full-height track, the share in the data color on top.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(track, shape)
                    )
                    if (share > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(share)
                                .background(fill.copy(alpha = if (selected == null || selected == index) 1f else 0.45f), shape)
                        )
                    }
                }
                if (showLabels) {
                    Text(bar.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Per medication: confirmed of scheduled doses in the period – the table view of the chart. */
private fun LazyListScope.medicationAdherence(stats: List<DayAdherence>, persons: List<Person>, careMode: Boolean) {
    val rows = stats.flatMap { it.doses }
        .groupBy { it.medication.personId to it.medication.name }
        .map { (key, doses) -> Triple(key, doses.count { it.taken }, doses.size) }
        .sortedWith(compareBy({ it.second.toFloat() / it.third }, { it.first.second }))
    if (rows.isEmpty()) return
    items(rows, key = { "${it.first.first}/${it.first.second}" }) { (key, taken, scheduled) ->
        val person = persons.firstOrNull { it.id == key.first }.takeIf { careMode }
        val complete = taken == scheduled
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (complete) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (complete) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = "Vollständig")
                        } else {
                            Text("${taken * 100 / scheduled}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(key.second, style = MaterialTheme.typography.titleMedium)
                    person?.let {
                        Text(it.fullName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("$taken/$scheduled", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
