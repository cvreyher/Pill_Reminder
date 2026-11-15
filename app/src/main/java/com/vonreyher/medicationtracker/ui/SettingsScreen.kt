package com.vonreyher.medicationtracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import com.vonreyher.medicationtracker.data.DrugDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.AppMode
import com.vonreyher.medicationtracker.data.EmaCredentials
import com.vonreyher.medicationtracker.data.MedicationStore
import com.vonreyher.medicationtracker.data.Person

private sealed interface SettingsDialog {
    data object Name : SettingsDialog
    data object NewPerson : SettingsDialog
    data class EditPerson(val personId: String) : SettingsDialog
    data class EmaField(val field: EmaFieldType) : SettingsDialog
}

enum class EmaFieldType(val label: String, val secret: Boolean = false) {
    TokenUrl("Token-URL"),
    ClientId("Client-ID"),
    ClientSecret("Client-Secret", secret = true),
    Scope("Scope (optional)")
}

private fun EmaCredentials.valueOf(field: EmaFieldType) = when (field) {
    EmaFieldType.TokenUrl -> tokenUrl
    EmaFieldType.ClientId -> clientId
    EmaFieldType.ClientSecret -> clientSecret
    EmaFieldType.Scope -> scope
}

private fun EmaCredentials.with(field: EmaFieldType, value: String) = when (field) {
    EmaFieldType.TokenUrl -> copy(tokenUrl = value)
    EmaFieldType.ClientId -> copy(clientId = value)
    EmaFieldType.ClientSecret -> copy(clientSecret = value)
    EmaFieldType.Scope -> copy(scope = value)
}

@Composable
fun SettingsScreen(store: MedicationStore, contentPadding: PaddingValues) {
    val settings = store.settings
    val careMode = settings.mode == AppMode.Care
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        )
    ) {
        item { SettingsHeader("Profil") }
        item {
            ListItem(
                modifier = Modifier.clickable { dialog = SettingsDialog.Name },
                headlineContent = { Text(if (careMode) "Dein Name (Pflegekraft)" else "Name") },
                supportingContent = {
                    Text(listOfNotNull(settings.firstName, settings.lastName).joinToString(" ").ifBlank { "Nicht angegeben" })
                },
                leadingContent = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) }
            )
        }

        item { SettingsHeader("Nutzung") }
        item {
            ModeOption(
                title = "Nur für mich",
                description = "Deine eigenen Medikamente",
                selected = !careMode,
                onSelect = { store.updateSettings { it.copy(mode = AppMode.Self) } }
            )
        }
        item {
            ModeOption(
                title = "Für mehrere Personen",
                description = "Als Pflegekraft oder Angehörige*r die Medikamente mehrerer Personen verwalten",
                selected = careMode,
                onSelect = { store.updateSettings { it.copy(mode = AppMode.Care) } }
            )
        }

        item { SettingsHeader("Erinnerungen") }
        item { ReminderSettings(store) }

        if (careMode) {
            item { SettingsHeader("Personen") }
            items(store.persons, key = { it.id }) { person ->
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.EditPerson(person.id) },
                    headlineContent = { Text(person.fullName.ifBlank { "Ohne Namen" }) },
                    supportingContent = {
                        val count = store.activeMedications.count { it.personId == person.id }
                        Text(listOfNotNull(person.birthDate?.let { "geb. $it" }, "$count Medikamente").joinToString(" · "))
                    },
                    leadingContent = { PersonAvatar(person) }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.NewPerson },
                    headlineContent = { Text("Person hinzufügen") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) }
                )
            }
        }

        item { SettingsHeader("Arzneimitteldatenbank") }
        item { DrugDatabaseSettings(store) }

        item { SettingsHeader("EMA-Datenbank") }
        item {
            Text(
                text = if (settings.ema.isComplete) {
                    "Eingerichtet. Arzneimittelinfos werden bei der EMA abgefragt."
                } else {
                    "Für Arzneimittelinfos aus der EMA-Datenbank brauchst du Zugangsdaten, die die EMA nach " +
                        "einer Registrierung vergibt. Sie bleiben nur auf diesem Gerät."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(EmaFieldType.entries) { field ->
            val value = settings.ema.valueOf(field)
            ListItem(
                modifier = Modifier.clickable { dialog = SettingsDialog.EmaField(field) },
                headlineContent = { Text(field.label) },
                supportingContent = {
                    Text(
                        when {
                            value.isBlank() -> "Nicht gesetzt"
                            field.secret -> "••••••••"
                            else -> value
                        }
                    )
                }
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            Text(
                text = "Alle Daten bleiben auf diesem Gerät. Es werden keine Gesundheitsdaten an Server übertragen; " +
                    "an die EMA geht nur der Name des Medikaments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    when (val current = dialog) {
        null -> Unit
        SettingsDialog.Name -> PersonDialog(
            title = "Dein Name",
            initial = Person(Person.SELF_ID, settings.firstName.orEmpty(), settings.lastName.orEmpty()),
            showBirthDate = false,
            onDismiss = { dialog = null },
            onConfirm = { first, last, _ ->
                store.updateSettings { it.copy(firstName = first, lastName = last) }
                dialog = null
            }
        )
        SettingsDialog.NewPerson -> PersonDialog(
            title = "Person hinzufügen",
            initial = null,
            showBirthDate = true,
            onDismiss = { dialog = null },
            onConfirm = { first, last, birthDate ->
                store.addPerson(first, last, birthDate)
                dialog = null
            }
        )
        is SettingsDialog.EditPerson -> store.person(current.personId)?.let { person ->
            PersonDialog(
                title = "Person bearbeiten",
                initial = person,
                showBirthDate = true,
                onDismiss = { dialog = null },
                onConfirm = { first, last, birthDate ->
                    store.updatePerson(person.copy(firstName = first, lastName = last, birthDate = birthDate))
                    dialog = null
                },
                onDelete = if (person.id == Person.SELF_ID) null else {
                    {
                        store.deletePerson(person.id)
                        dialog = null
                    }
                }
            )
        } ?: run { dialog = null }
        is SettingsDialog.EmaField -> TextInputDialog(
            title = current.field.label,
            initial = settings.ema.valueOf(current.field),
            secret = current.field.secret,
            onDismiss = { dialog = null },
            onConfirm = { value ->
                store.updateSettings { it.copy(ema = it.ema.with(current.field, value.trim())) }
                dialog = null
            }
        )
    }
}

@Composable
private fun ReminderSettings(store: MedicationStore) {
    val context = LocalContext.current
    val settings = store.settings
    // Permissions are changed in the system settings; re-check whenever the screen comes back.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val notificationsAllowed = remember(checks) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactAllowed = remember(checks) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val fullScreenAllowed = remember(checks) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val packageUri = Uri.parse("package:${context.packageName}")

    Column {
        ListItem(
            modifier = Modifier.toggleable(
                value = settings.remindersEnabled,
                role = Role.Switch,
                onValueChange = { enabled -> store.updateSettings { it.copy(remindersEnabled = enabled) } }
            ),
            headlineContent = { Text("Erinnerungen") },
            supportingContent = { Text("Benachrichtigung zu jeder geplanten Einnahme") },
            trailingContent = { Switch(checked = settings.remindersEnabled, onCheckedChange = null) }
        )
        if (!settings.remindersEnabled) return@Column

        ListItem(
            modifier = Modifier.clickable(enabled = !notificationsAllowed) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            },
            headlineContent = { Text("Benachrichtigungen") },
            supportingContent = {
                Text(
                    if (notificationsAllowed) "Erlaubt"
                    else "Nicht erlaubt – tippen, um sie in den Systemeinstellungen zu erlauben",
                    color = if (notificationsAllowed) Color.Unspecified else MaterialTheme.colorScheme.error
                )
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                modifier = Modifier.clickable(enabled = !exactAllowed) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
                },
                headlineContent = { Text("Pünktliche Erinnerungen") },
                supportingContent = {
                    Text(
                        if (exactAllowed) "Erlaubt – Erinnerungen kommen auf die Minute genau"
                        else "Nicht erlaubt – Android kann Erinnerungen verzögern. Tippen zum Erlauben.",
                        color = if (exactAllowed) Color.Unspecified else MaterialTheme.colorScheme.error
                    )
                }
            )
        }
        ListItem(
            modifier = Modifier.toggleable(
                value = settings.soundWhenMuted,
                role = Role.Switch,
                onValueChange = { enabled -> store.updateSettings { it.copy(soundWhenMuted = enabled) } }
            ),
            headlineContent = { Text("Ton auch bei Stummschaltung") },
            supportingContent = { Text("Erinnerungen klingeln über die Wecker-Lautstärke, auch wenn das Handy stumm ist") },
            trailingContent = { Switch(checked = settings.soundWhenMuted, onCheckedChange = null) }
        )
        ListItem(
            modifier = Modifier.toggleable(
                value = settings.repeatReminders,
                role = Role.Switch,
                onValueChange = { enabled -> store.updateSettings { it.copy(repeatReminders = enabled) } }
            ),
            headlineContent = { Text("Bei Vergessen erneut erinnern") },
            supportingContent = {
                Text("Bis die Einnahme bestätigt ist, alle ${settings.repeatIntervalMinutes} Minuten")
            },
            trailingContent = { Switch(checked = settings.repeatReminders, onCheckedChange = null) }
        )
        if (settings.repeatReminders) {
            var minutes by remember(settings.repeatIntervalMinutes) {
                mutableFloatStateOf(settings.repeatIntervalMinutes.toFloat())
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Abstand: ${minutes.toInt()} Minuten", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    onValueChangeFinished = {
                        store.updateSettings { it.copy(repeatIntervalMinutes = minutes.toInt()) }
                    },
                    valueRange = 5f..60f,
                    steps = 10
                )
            }
        }
        ListItem(
            modifier = Modifier.toggleable(
                value = settings.lockScreenAlarm,
                role = Role.Switch,
                onValueChange = { enabled ->
                    store.updateSettings { it.copy(lockScreenAlarm = enabled) }
                    if (enabled && !fullScreenAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
                    }
                }
            ),
            headlineContent = { Text("Wie ein Wecker") },
            supportingContent = {
                Text(
                    when {
                        settings.lockScreenAlarm && !fullScreenAllowed ->
                            "Erlaubnis für Vollbild-Benachrichtigungen fehlt – aus- und wieder einschalten, um sie zu erteilen"
                        else -> "Bei gesperrtem Handy öffnet sich der Bestätigen-Bildschirm sofort im Vollbild"
                    }
                )
            },
            trailingContent = { Switch(checked = settings.lockScreenAlarm, onCheckedChange = null) }
        )
    }
}

/**
 * Local PZN → name database. The app ships without data; a CSV the user is licensed to use is
 * imported here and then names PZN-only medication everywhere in the app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DrugDatabaseSettings(store: MedicationStore) {
    val context = LocalContext.current
    val database = remember { DrugDatabase.get(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val count by produceState(0, refresh) { value = withContext(Dispatchers.IO) { database.count() } }
    val lastImport by produceState<String?>(null, refresh) { value = withContext(Dispatchers.IO) { database.lastImport() } }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        progress = 0
        message = null
        scope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        database.importCsv(input, today) { progress = it }
                    } ?: error("unreadable")
                }
            }
            importing = false
            refresh++
            message = result.fold(
                onSuccess = { imported ->
                    val renamed = store.applyKnownNames()
                    buildString {
                        append("${imported.imported} Medikamente importiert")
                        if (imported.skipped > 0) append(", ${imported.skipped} Zeilen ohne gültige PZN übersprungen")
                        if (renamed > 0) append(". $renamed gespeicherte Medikamente haben jetzt ihren Namen")
                        append(".")
                    }
                },
                onFailure = { "Die Datei konnte nicht gelesen werden." }
            )
        }
    }

    Column {
        Text(
            text = "Medikationspläne enthalten oft nur die PZN. Mit einer Liste aus PZN und Namen (CSV, z. B. ein " +
                "lizenzierter IFA-Export) ergänzt die App die Namen automatisch – komplett offline. Achte darauf, " +
                "dass du die Daten nutzen darfst.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        ListItem(
            headlineContent = { Text("Medikamente in der Datenbank") },
            supportingContent = {
                Text(if (count == 0) "Noch leer" else "$count · zuletzt importiert am ${lastImport ?: "–"}")
            }
        )
        if (importing) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                Text("$progress Einträge importiert …", style = MaterialTheme.typography.bodySmall)
            }
        }
        ListItem(
            modifier = Modifier.clickable(enabled = !importing) {
                picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*"))
            },
            headlineContent = { Text("CSV-Datei importieren") },
            supportingContent = { Text("Spalten mit PZN und Name; Hersteller, Darreichungsform und Stärke optional") },
            leadingContent = { Icon(painterResource(R.drawable.ic_description), contentDescription = null) }
        )
        if (count > 0 && !importing) {
            ListItem(
                modifier = Modifier.clickable { confirmClear = true },
                headlineContent = { Text("Datenbank leeren", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) }
            )
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Datenbank leeren?") },
            text = { Text("Alle importierten Medikamente werden entfernt. Namen, die du selbst eingetragen hast, bleiben erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { database.clear() }
                        refresh++
                        message = null
                    }
                }) { Text("Leeren", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun ModeOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { RadioButton(selected = selected, onClick = null) }
    )
}

/** Name (and optionally birth date) of a person. */
@Composable
fun PersonDialog(
    title: String,
    initial: Person?,
    showBirthDate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (firstName: String, lastName: String, birthDate: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var firstName by rememberSaveable { mutableStateOf(initial?.firstName.orEmpty()) }
    var lastName by rememberSaveable { mutableStateOf(initial?.lastName.orEmpty()) }
    var birthDate by rememberSaveable { mutableStateOf(initial?.birthDate.orEmpty()) }
    val birthDateValid = birthDate.isBlank() || Regex("""\d{2}\.\d{2}\.\d{4}""").matches(birthDate.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Vorname") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Nachname") },
                    singleLine = true
                )
                if (showBirthDate) {
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("Geburtsdatum (optional)") },
                        placeholder = { Text("TT.MM.JJJJ") },
                        isError = !birthDateValid,
                        supportingText = { Text("Hilft, gescannte Pläne richtig zuzuordnen.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Person und ihre Medikamente löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(firstName.trim(), lastName.trim(), birthDate.trim().ifBlank { null }) },
                enabled = firstName.isNotBlank() && birthDateValid
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    secret: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(title) },
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
