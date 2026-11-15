package com.vonreyher.medicationtracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.Medication
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.EmaCredentials
import com.vonreyher.medicationtracker.data.Person
import com.vonreyher.medicationtracker.formatMinutes
import com.vonreyher.medicationtracker.formatted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime

/** How long the success animation stays on screen before it closes; the intake is saved before it starts. */
private const val CelebrationMillis = 1600L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationDetailScreen(
    medication: Medication,
    person: Person?,
    intakesToday: Int,
    emaCredentials: EmaCredentials,
    now: LocalTime,
    onClose: () -> Unit,
    onConfirmTaken: () -> Unit,
    onMarkNotTaken: () -> Unit,
    /** Null where the time can't be changed, e.g. when opened from a reminder. */
    onChangeTime: (() -> Unit)?,
    onEdit: (() -> Unit)? = null
) {
    BackHandler(onBack = onClose)

    val sound = ConfirmationSound.get(LocalContext.current)
    var showInfo by remember { mutableStateOf(false) }
    val iconRotation = remember { Animatable(0f) }
    val currentOnConfirmTaken by rememberUpdatedState(onConfirmTaken)
    val currentOnClose by rememberUpdatedState(onClose)
    var celebrating by remember { mutableStateOf(false) }
    val iconScale = remember { Animatable(1f) }
    val innerRing = remember { Animatable(0f) }
    val outerRing = remember { Animatable(0f) }

    LaunchedEffect(celebrating) {
        if (!celebrating) return@LaunchedEffect
        sound.play()
        launch {
            iconScale.snapTo(0.6f)
            iconScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
        }
        launch { iconRotation.animateTo(360f, tween(1200, easing = FastOutSlowInEasing)) }
        launch { innerRing.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch {
            delay(180)
            outerRing.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        delay(CelebrationMillis)
        currentOnClose()
    }

    val scheduledTime = medication.time
    val minutesUntil = scheduledTime?.let { Duration.between(now, it).toMinutes() } ?: 0L
    val takenAt = medication.takenAt
    val (headline, supporting) = when {
        celebrating -> (if (person != null) "Gegeben!" else "Eingenommen!") to null
        scheduledTime == null -> "Bei Bedarf" to when (intakesToday) {
            0 -> "Heute noch nicht eingenommen"
            else -> "Heute ${intakesToday}× eingenommen, zuletzt um ${takenAt?.formatted()} Uhr"
        }
        takenAt != null -> "Eingenommen" to "um ${takenAt.formatted()} Uhr"
        minutesUntil > 0 -> "Noch ${formatMinutes(minutesUntil)} bis zur Einnahme" to null
        minutesUntil < 0 -> "Jetzt einnehmen" to "${formatMinutes(-minutesUntil)} überfällig"
        else -> "Jetzt einnehmen" to null
    }
    val colorScheme = MaterialTheme.colorScheme
    val headlineColor by animateColorAsState(
        targetValue = when {
            celebrating -> colorScheme.onPrimaryContainer
            medication.taken || medication.asNeeded -> colorScheme.onSurfaceVariant
            minutesUntil <= 0 -> colorScheme.error
            else -> colorScheme.primary
        },
        label = "headlineColor"
    )
    val background by animateColorAsState(
        targetValue = if (celebrating) colorScheme.primaryContainer else colorScheme.surface,
        animationSpec = tween(500),
        label = "background"
    )
    val iconContainer by animateColorAsState(
        targetValue = if (celebrating) colorScheme.primary else colorScheme.primaryContainer,
        label = "iconContainer"
    )
    val iconContent by animateColorAsState(
        targetValue = if (celebrating) colorScheme.onPrimary else colorScheme.onPrimaryContainer,
        label = "iconContent"
    )

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Schließen")
                    }
                },
                actions = {
                    if (!celebrating) {
                        if (onEdit != null) {
                            IconButton(onClick = onEdit) {
                                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Bearbeiten")
                            }
                        }
                        IconButton(onClick = { showInfo = true }) {
                            Icon(painterResource(R.drawable.ic_info), contentDescription = "Arzneimittelinfo")
                        }
                        if (onChangeTime != null) {
                            IconButton(onClick = onChangeTime) {
                                Icon(
                                    painterResource(R.drawable.ic_schedule),
                                    contentDescription = if (medication.asNeeded) "Uhrzeit festlegen" else "Uhrzeit ändern"
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
          ) {

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                if (celebrating) {
                    ExpandingRing(progress = outerRing.value, maxScale = 2.8f)
                    ExpandingRing(progress = innerRing.value, maxScale = 2.0f)
                }
                Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = iconContainer,
                    contentColor = iconContent,
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                            rotationZ = iconRotation.value
                        }
                ) {
                    AnimatedContent(
                        targetState = celebrating,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith
                                (scaleOut() + fadeOut())
                        },
                        contentAlignment = Alignment.Center,
                        label = "icon"
                    ) { done ->
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(if (done) R.drawable.ic_check else R.drawable.ic_medication),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (person != null) {
                Text(
                    text = "Für ${person.fullName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = medication.name,
                style = MaterialTheme.typography.displaySmallEmphasized,
                textAlign = TextAlign.Center
            )
            medication.ingredient?.let {
                Text(
                    text = "Wirkstoff: $it",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = listOfNotNull(medication.dosage, medication.form).joinToString(" · "),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = scheduledTime?.let { "Geplant um ${it.formatted()} Uhr" } ?: "Keine feste Uhrzeit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = headline,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "headline"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = headlineColor,
                    textAlign = TextAlign.Center
                )
            }
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (medication.hasPlaceholderName && onEdit != null) {
                MissingNameCard(onEdit = onEdit, modifier = Modifier.padding(top = 16.dp))
            }
            if (medication.instructions != null || medication.reason != null) {
                InstructionsCard(
                    instructions = medication.instructions,
                    reason = medication.reason,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
          }

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = !celebrating,
                exit = fadeOut() + slideOutVertically { it }
            ) {
                if (medication.taken) {
                    OutlinedButton(onClick = onMarkNotTaken) {
                        Text("Als nicht eingenommen markieren")
                    }
                } else {
                    SlideToConfirm(
                        text = person?.let { "Gabe an ${it.firstName.ifBlank { it.fullName }} bestätigen" }
                            ?: "Zum Bestätigen schieben",
                        onConfirm = {
                            if (!celebrating) {
                                // Save right away: leaving during the animation must not lose the intake.
                                currentOnConfirmTaken()
                                celebrating = true
                            }
                        }
                    )
                }
            }
        }
    }

    if (showInfo) {
        DrugInfoSheet(
            medicationName = medication.name,
            credentials = emaCredentials,
            onDismiss = { showInfo = false }
        )
    }
}

/** Plans often carry only the PZN; asks to name the medication once, the app remembers it. */
@Composable
private fun MissingNameCard(onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Name fehlt", style = MaterialTheme.typography.titleMediumEmphasized)
            Text(
                "Der Medikationsplan enthält für dieses Medikament nur die PZN. Gib den Namen einmal ein oder " +
                    "importiere eine Arzneimitteldatenbank – die App merkt ihn sich für alle weiteren Pläne.",
                style = MaterialTheme.typography.bodyMedium
            )
            FilledTonalButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Namen ergänzen")
            }
        }
    }
}

/** The plan's instructions, shown prominently so they are read before taking the dose. */
@Composable
private fun InstructionsCard(instructions: String?, reason: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                instructions?.let {
                    Text("Hinweis", style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.titleMediumEmphasized)
                }
                reason?.let {
                    Text("Grund der Einnahme", style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** A ring that grows out of the icon and fades away. */
@Composable
private fun ExpandingRing(progress: Float, maxScale: Float) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                val scale = 1f + (maxScale - 1f) * progress
                scaleX = scale
                scaleY = scale
                alpha = 1f - progress
            }
    ) {}
}
