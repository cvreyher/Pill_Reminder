package com.vonreyher.medicationtracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.vonreyher.medicationtracker.IntakeEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.Medication
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.Person
import com.vonreyher.medicationtracker.formatted
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.temporal.ChronoUnit

/** The current time, truncated to minutes and updated on every minute boundary. */
@Composable
fun rememberCurrentTime(): State<LocalTime> =
    produceState(LocalTime.now().truncatedTo(ChronoUnit.MINUTES)) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
        }
    }

private sealed interface TodayRow {
    data class Dose(val medication: Medication) : TodayRow
    data object Now : TodayRow
}

/** How many past days can be swiped back to. */
private const val DaysBack = 30

/**
 * Home screen (the greeting lives in the collapsing app bar): a day switcher on top; below, swipe left and right between today and
 * the past days, like in fitness apps. Past days show what was taken and what was missed.
 */
@Composable
fun TodayScreen(
    medications: List<Medication>,
    persons: List<Person>,
    careMode: Boolean,
    selectedPersonId: String?,
    onSelectPerson: (String?) -> Unit,
    intakesToday: (Medication) -> Int,
    history: List<IntakeEvent>,
    /** All medication including stopped ones, so past days show what was scheduled back then. */
    pastMedications: List<Medication>,
    now: LocalTime,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onMedicationClick: (Medication) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = DaysBack) { DaysBack + 1 }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    fun dayOf(page: Int): LocalDate = today.minusDays((DaysBack - page).toLong())

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        DaySwitcher(
            day = dayOf(pagerState.currentPage),
            today = today,
            canGoBack = pagerState.currentPage > 0,
            canGoForward = pagerState.currentPage < DaysBack,
            onBack = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            onForward = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            onToday = { scope.launch { pagerState.animateScrollToPage(DaysBack) } }
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { it }
        ) { page ->
            val pagePadding = PaddingValues(top = 8.dp, bottom = contentPadding.calculateBottomPadding())
            if (page == DaysBack) {
                TodayPage(
                    medications = medications,
                    persons = persons,
                    careMode = careMode,
                    selectedPersonId = selectedPersonId,
                    onSelectPerson = onSelectPerson,
                    intakesToday = intakesToday,
                    now = now,
                    listState = listState,
                    contentPadding = pagePadding,
                    onMedicationClick = onMedicationClick
                )
            } else {
                PastDayPage(
                    day = dayOf(page),
                    medications = pastMedications,
                    persons = persons,
                    careMode = careMode,
                    selectedPersonId = selectedPersonId,
                    onSelectPerson = onSelectPerson,
                    history = history,
                    contentPadding = pagePadding
                )
            }
        }
    }
}

/** "‹ Heute ›" with the full date below; tapping the label jumps back to today. */
@Composable
private fun DaySwitcher(
    day: LocalDate,
    today: LocalDate,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Vorheriger Tag")
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = day != today, onClickLabel = "Zu heute springen", onClick = onToday),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (day) {
                    today -> "Heute"
                    today.minusDays(1) -> "Gestern"
                    else -> day.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))
                },
                style = MaterialTheme.typography.titleMediumEmphasized
            )
            Text(
                text = day.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Nächster Tag")
        }
    }
}

/**
 * A past day: every dose scheduled back then, marked as taken (with the time) or missed, and what
 * was taken as needed. Doses added later don't count for that day.
 */
@Composable
private fun PastDayPage(
    day: LocalDate,
    medications: List<Medication>,
    persons: List<Person>,
    careMode: Boolean,
    selectedPersonId: String?,
    onSelectPerson: (String?) -> Unit,
    history: List<IntakeEvent>,
    contentPadding: PaddingValues
) {
    val forPerson = { personId: String -> selectedPersonId == null || personId == selectedPersonId }
    val events = history.filter { it.takenAt.toLocalDate() == day && forPerson(it.personId) }
    val takenAt = events.filter { it.scheduledTime != null }.groupBy { it.medicationId }
        .mapValues { (_, intakes) -> intakes.minOf { it.takenAt }.toLocalTime() }
    val scheduled = medications
        .filter { !it.asNeeded && forPerson(it.personId) }
        // Only doses that were scheduled at their time count: nothing before setup or after stopping is "missed".
        .filter { medication -> medication.wasScheduledAt(day.atTime(medication.time)) }
        .sortedBy { it.time }
    val done = scheduled.count { it.id in takenAt }
    val missed = scheduled.size - done
    val asNeededIntakes = events.filter { it.scheduledTime == null }.sortedBy { it.takenAt }
    val personOf = { id: String -> persons.firstOrNull { it.id == id }?.takeIf { careMode } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DayClock(
                title = day.format(DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)),
                completed = done,
                total = scheduled.size,
                footer = if (missed > 0) "$missed vergessen" else null,
                doneLabel = "eingenommen",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        }
        if (careMode && persons.size > 1) {
            item {
                PersonFilterChips(
                    persons = persons,
                    selectedPersonId = selectedPersonId,
                    onSelect = onSelectPerson,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        if (scheduled.isEmpty() && asNeededIntakes.isEmpty()) {
            item {
                Text(
                    "An diesem Tag war nichts geplant.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        if (scheduled.isNotEmpty()) {
            item { SectionHeader("Tagesplan", "$done/${scheduled.size}") }
            items(scheduled, key = { it.id }) { medication ->
                val time = takenAt[medication.id]
                DoseCard(
                    medication = medication.copy(takenAt = time),
                    person = personOf(medication.personId),
                    trailing = time?.formatted() ?: "Vergessen",
                    overdue = time == null,
                    isNext = false,
                    onClick = null,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        if (asNeededIntakes.isNotEmpty()) {
            item { SectionHeader("Bei Bedarf", "${asNeededIntakes.size}", Modifier.padding(top = 12.dp)) }
            items(asNeededIntakes, key = { it.id }) { event ->
                ListItem(
                    headlineContent = { Text(event.name) },
                    supportingContent = {
                        Text(listOfNotNull(personOf(event.personId)?.fullName, event.dosage).joinToString(" · "))
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
                    trailingContent = { Text(event.takenAt.toLocalTime().formatted(), style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

/**
 * Home screen: greeting, a clock showing today's progress, the day's scheduled doses in time order
 * with a line marking the current time, and medication without fixed times below.
 * In care mode every dose shows who it is for and chips filter by person.
 */
@Composable
private fun TodayPage(
    medications: List<Medication>,
    persons: List<Person>,
    careMode: Boolean,
    selectedPersonId: String?,
    onSelectPerson: (String?) -> Unit,
    intakesToday: (Medication) -> Int,
    now: LocalTime,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onMedicationClick: (Medication) -> Unit
) {
    val visible = medications.filter { selectedPersonId == null || it.personId == selectedPersonId }
    val scheduled = visible.filter { !it.asNeeded }.sortedBy { it.time }
    val asNeeded = visible.filter { it.asNeeded }
    val next = scheduled.firstOrNull { !it.taken }
    val personOf = { medication: Medication -> persons.firstOrNull { it.id == medication.personId } }

    val nowIndex = scheduled.indexOfFirst { it.time!!.isAfter(now) }.let { if (it < 0) scheduled.size else it }
    val rows = buildList<TodayRow> {
        scheduled.forEachIndexed { index, medication ->
            if (index == nowIndex) add(TodayRow.Now)
            add(TodayRow.Dose(medication))
        }
        if (nowIndex == scheduled.size && scheduled.isNotEmpty()) add(TodayRow.Now)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val nextPerson = next?.let(personOf)?.takeIf { careMode }
            DayClock(
                title = now.formatted(),
                completed = scheduled.count { it.taken },
                total = scheduled.size,
                footer = next?.let { "Als Nächstes: ${it.name}" + (nextPerson?.let { p -> " (${p.firstName})" } ?: "") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        }

        // Filter by person, right below the clock.
        if (careMode && persons.size > 1) {
            item {
                PersonFilterChips(
                    persons = persons,
                    selectedPersonId = selectedPersonId,
                    onSelect = onSelectPerson,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        if (visible.isEmpty()) {
            item { EmptyDay(Modifier.padding(horizontal = 24.dp)) }
        } else if (scheduled.isNotEmpty()) {
            item { SectionHeader("Tagesplan", "${scheduled.count { it.taken }}/${scheduled.size}") }
        }

        items(rows, key = { row -> if (row is TodayRow.Dose) row.medication.id else "now" }) { row ->
            when (row) {
                TodayRow.Now -> NowIndicator(now)
                is TodayRow.Dose -> DoseCard(
                    medication = row.medication,
                    person = personOf(row.medication).takeIf { careMode },
                    trailing = if (row.medication.taken) null else row.medication.time!!.formatted(),
                    overdue = !row.medication.taken && !row.medication.time!!.isAfter(now),
                    isNext = row.medication.id == next?.id,
                    onClick = { onMedicationClick(row.medication) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        if (asNeeded.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Bei Bedarf",
                    badge = null,
                    subtitle = "Ohne feste Uhrzeit – antippen und bestätigen, wenn du es nimmst.",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            items(asNeeded, key = { it.id }) { medication ->
                val count = intakesToday(medication)
                DoseCard(
                    medication = medication,
                    person = personOf(medication).takeIf { careMode },
                    trailing = if (count == 0) null else "Heute ${count}×",
                    overdue = false,
                    isNext = false,
                    onClick = { onMedicationClick(medication) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

/** A title (the time, or a past date) inside a cookie shape, wrapped by a wavy ring showing the day's progress. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayClock(
    title: String,
    completed: Int,
    total: Int,
    footer: String?,
    modifier: Modifier = Modifier,
    doneLabel: String = "erledigt"
) {
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else completed.toFloat() / total,
        label = "dayProgress"
    )
    val stroke = with(LocalDensity.current) { Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round) }

    Box(modifier, contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(272.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            stroke = stroke,
            trackStroke = stroke,
            wavelength = 36.dp
        )
        Surface(
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(216.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(title, style = MaterialTheme.typography.displayMediumEmphasized, maxLines = 1)
                Text(
                    text = when {
                        total == 0 -> "Keine festen Einnahmen"
                        completed == total -> "Alles $doneLabel"
                        else -> "$completed von $total $doneLabel"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                if (footer != null) {
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, badge: String?, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
            if (badge != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(badge, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shown when nothing is scheduled: an expressive shape with a short hint. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyDay(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialShapes.Sunny.toShape(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_medication), contentDescription = null)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Noch nichts geplant", style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    "Füge ein Medikament hinzu oder scanne einen Medikationsplan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One dose as a tonal card: shaped icon (or the person's avatar), name and dosage, and the time as
 * a pill. The next dose is highlighted, overdue ones show their time in the error color.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DoseCard(
    medication: Medication,
    person: Person?,
    trailing: String?,
    overdue: Boolean,
    isNext: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val container = when {
        isNext -> colorScheme.primaryContainer
        medication.taken -> colorScheme.surfaceContainerLow
        else -> colorScheme.surfaceContainerHigh
    }
    val content = if (isNext) colorScheme.onPrimaryContainer else colorScheme.onSurface

    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (medication.taken) 0.7f else 1f },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (person != null) {
                PersonAvatar(person, size = 48.dp)
            } else {
                Surface(
                    shape = (if (medication.taken) MaterialShapes.Circle else MaterialShapes.Cookie6Sided).toShape(),
                    color = if (isNext) colorScheme.primary else colorScheme.secondaryContainer,
                    contentColor = if (isNext) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (medication.taken) R.drawable.ic_check else R.drawable.ic_medication),
                            contentDescription = if (medication.taken) "Eingenommen" else null
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (isNext) {
                    Text("Als Nächstes", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                }
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (medication.taken) TextDecoration.LineThrough else null
                )
                Text(
                    text = listOfNotNull(person?.firstName?.ifBlank { null }, medication.dosage).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isNext) content else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        overdue -> colorScheme.errorContainer
                        isNext -> colorScheme.primary
                        else -> colorScheme.surfaceContainerHighest
                    },
                    contentColor = when {
                        overdue -> colorScheme.onErrorContainer
                        isNext -> colorScheme.onPrimary
                        else -> colorScheme.onSurfaceVariant
                    }
                ) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** Marks the current time between the doses: a pill with the time and a line. */
@Composable
private fun NowIndicator(now: LocalTime) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Jetzt, ${now.formatted()} Uhr" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = color, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Text(
                "Jetzt · ${now.formatted()}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(thickness = 2.dp, color = color, modifier = Modifier.weight(1f))
    }
}
