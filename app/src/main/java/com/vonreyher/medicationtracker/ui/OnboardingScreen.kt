package com.vonreyher.medicationtracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.AppMode
import com.vonreyher.medicationtracker.data.Person
import java.util.UUID

/**
 * First start: who is the app for, the user's name and, in care mode, the first person cared for.
 * Everything can be changed later in the settings.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onDone: (mode: AppMode, firstName: String, lastName: String, firstPerson: Person?) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(AppMode.Self) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var personFirstName by rememberSaveable { mutableStateOf("") }
    var personLastName by rememberSaveable { mutableStateOf("") }
    var personBirthDate by rememberSaveable { mutableStateOf("") }
    val lastStep = if (mode == AppMode.Care) 2 else 1

    BackHandler(enabled = step > 0) { step-- }

    fun finish() {
        val person = if (mode == AppMode.Care && personFirstName.isNotBlank()) {
            Person(UUID.randomUUID().toString(), personFirstName.trim(), personLastName.trim(), personBirthDate.trim().ifBlank { null })
        } else {
            null
        }
        onDone(mode, firstName.trim(), lastName.trim(), person)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(24.dp)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * direction / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it * direction / 3 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboardingStep"
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(Modifier.height(32.dp))
                    when (current) {
                        0 -> {
                            Hero(R.drawable.ic_logo_mark)
                            Text("Willkommen", style = MaterialTheme.typography.displaySmallEmphasized)
                            Text(
                                "Für wen möchtest du die App nutzen?",
                                style = MaterialTheme.typography.titleLarge
                            )
                            ModeCard(
                                title = "Nur für mich",
                                description = "Ich verwalte meine eigenen Medikamente.",
                                icon = R.drawable.ic_person,
                                selected = mode == AppMode.Self,
                                onClick = { mode = AppMode.Self }
                            )
                            ModeCard(
                                title = "Für mehrere Personen",
                                description = "Ich kümmere mich als Pflegekraft oder Angehörige*r um die Medikamente anderer.",
                                icon = R.drawable.ic_group,
                                selected = mode == AppMode.Care,
                                onClick = { mode = AppMode.Care }
                            )
                        }
                        1 -> {
                            Hero(R.drawable.ic_person)
                            Text("Wie heißt du?", style = MaterialTheme.typography.displaySmallEmphasized)
                            Text(
                                if (mode == AppMode.Care) "Dein Name als Pflegekraft – für die Begrüßung."
                                else "Damit die App dich persönlich begrüßen kann.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text("Vorname") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it },
                                label = { Text("Nachname") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            Hero(R.drawable.ic_group)
                            Text("Erste Person", style = MaterialTheme.typography.displaySmallEmphasized)
                            Text(
                                "Um wen kümmerst du dich? Weitere Personen kannst du später in den Einstellungen " +
                                    "anlegen – oder sie entstehen automatisch beim Scannen eines Medikationsplans.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = personFirstName,
                                onValueChange = { personFirstName = it },
                                label = { Text("Vorname") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = personLastName,
                                onValueChange = { personLastName = it },
                                label = { Text("Nachname") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = personBirthDate,
                                onValueChange = { personBirthDate = it },
                                label = { Text("Geburtsdatum (optional)") },
                                placeholder = { Text("TT.MM.JJJJ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text("Zurück") }
                } else {
                    Spacer(Modifier)
                }
                val canContinue = when (step) {
                    1 -> firstName.isNotBlank()
                    else -> true
                }
                Button(
                    onClick = { if (step < lastStep) step++ else finish() },
                    enabled = canContinue,
                    modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                ) {
                    Text(
                        text = when {
                            step < lastStep -> "Weiter"
                            mode == AppMode.Care && personFirstName.isBlank() -> "Später anlegen"
                            else -> "Los geht's"
                        },
                        style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Hero(icon: Int) {
    Surface(
        shape = MaterialShapes.Cookie9Sided.toShape(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(96.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun ModeCard(title: String, description: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
