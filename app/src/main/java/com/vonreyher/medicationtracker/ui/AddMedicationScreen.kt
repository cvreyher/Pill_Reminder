package com.vonreyher.medicationtracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.Medication
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.Drug
import com.vonreyher.medicationtracker.data.PackageCodeParser
import com.vonreyher.medicationtracker.data.Person
import com.vonreyher.medicationtracker.data.PlanEntry
import com.vonreyher.medicationtracker.formatted
import kotlinx.coroutines.delay
import java.time.LocalTime

private enum class Field(val label: String) {
    Name("Handelsname"),
    Ingredient("Wirkstoff"),
    Form("Darreichungsform"),
    Dosage("Dosis"),
    Instructions("Hinweise"),
    Reason("Grund der Einnahme"),
    Pzn("PZN")
}

private val Slots = listOf(
    "Morgens" to PlanEntry.MorningTime,
    "Mittags" to PlanEntry.NoonTime,
    "Abends" to PlanEntry.EveningTime,
    "Nachts" to PlanEntry.NightTime
)

/**
 * Full-screen form for a new medication. The name can be picked from the local drug database while
 * typing; the PZN can be scanned from the package barcode and fills in the name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun AddMedicationScreen(
    persons: List<Person>,
    careMode: Boolean,
    initialPersonId: String,
    /** The medication to edit, or null to add a new one. */
    initial: Medication? = null,
    onSave: (template: Medication, times: List<LocalTime>) -> Unit,
    onClose: () -> Unit,
    /** Searches the local drug database by name or PZN. */
    searchDrugs: suspend (String) -> List<Drug> = { emptyList() },
    /** Looks a PZN up in the local drug database. */
    findDrug: suspend (String) -> Drug? = { null }
) {
    // A placeholder name ("PZN 1234567") is left empty so the real name can be entered.
    var name by rememberSaveable { mutableStateOf(initial?.takeUnless { it.hasPlaceholderName }?.name.orEmpty()) }
    var ingredient by rememberSaveable { mutableStateOf(initial?.ingredient.orEmpty()) }
    var form by rememberSaveable { mutableStateOf(initial?.form.orEmpty()) }
    var dosage by rememberSaveable { mutableStateOf(initial?.dosage.orEmpty()) }
    var instructions by rememberSaveable { mutableStateOf(initial?.instructions.orEmpty()) }
    var reason by rememberSaveable { mutableStateOf(initial?.reason.orEmpty()) }
    var pzn by rememberSaveable { mutableStateOf(initial?.pzn.orEmpty()) }
    var personId by rememberSaveable { mutableStateOf(initial?.personId ?: initialPersonId) }
    var asNeeded by rememberSaveable { mutableStateOf(initial?.asNeeded ?: false) }
    var times by remember {
        mutableStateOf(if (initial == null) setOf(PlanEntry.MorningTime) else setOfNotNull(initial.time))
    }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var scanning by remember { mutableStateOf<Field?>(null) }
    // Set when a suggestion was picked, so the list hides until the name is edited again.
    var pickedName by remember { mutableStateOf<String?>(null) }

    fun applyDrug(drug: Drug) {
        val fullName = listOfNotNull(drug.name, drug.strength?.takeIf { it !in drug.name }).joinToString(" ")
        name = fullName
        pickedName = fullName
        pzn = drug.pzn
        if (form.isBlank()) drug.form?.let { form = it }
    }

    // A complete PZN – typed or scanned – fills in the name from the database.
    LaunchedEffect(pzn) {
        if (pzn.length == 8 && name.isBlank()) findDrug(pzn)?.let(::applyDrug)
    }
    val suggestions by produceState(emptyList<Drug>(), name, pickedName) {
        value = if (name.trim().length < 3 || name == pickedName) {
            emptyList()
        } else {
            delay(250)
            searchDrugs(name).take(5)
        }
    }

    fun valueOf(field: Field) = when (field) {
        Field.Name -> name
        Field.Ingredient -> ingredient
        Field.Form -> form
        Field.Dosage -> dosage
        Field.Instructions -> instructions
        Field.Reason -> reason
        Field.Pzn -> pzn
    }

    fun set(field: Field, value: String) {
        when (field) {
            Field.Name -> name = value
            Field.Ingredient -> ingredient = value
            Field.Form -> form = value
            Field.Dosage -> dosage = value
            Field.Instructions -> instructions = value
            Field.Reason -> reason = value
            Field.Pzn -> pzn = value
        }
    }

    if (scanning == Field.Pzn) {
        PackageBarcodeScreen(
            onPzn = {
                pzn = it
                scanning = null
            },
            onClose = { scanning = null }
        )
        return
    }

    BackHandler(onBack = onClose)
    val pznValid = pzn.isBlank() || PackageCodeParser.isValidPzn(pzn.padStart(8, '0'))
    val canSave = name.isNotBlank() && pznValid && (asNeeded || times.isNotEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial == null) "Medikament hinzufügen" else "Medikament bearbeiten",
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Abbrechen")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = {
                        onSave(
                            Medication(
                                id = 0,
                                name = name.trim(),
                                dosage = dosage.trim().ifBlank { "Nach Anweisung" },
                                time = null,
                                personId = personId,
                                ingredient = ingredient.trim().ifBlank { null },
                                form = form.trim().ifBlank { null },
                                instructions = instructions.trim().ifBlank { null },
                                reason = reason.trim().ifBlank { null },
                                pzn = pzn.trim().ifBlank { null }?.padStart(8, '0')
                            ),
                            if (asNeeded) emptyList() else times.sorted()
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                ) {
                    Text("Speichern", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (careMode && persons.size > 1) {
                SectionLabel("Für")
                PersonFilterChips(
                    persons = persons,
                    selectedPersonId = personId,
                    onSelect = { id -> if (id != null) personId = id },
                    includeAll = false
                )
            }

            SectionLabel("Medikament")
            InputField(Field.Name, name, { name = it }, placeholder = "z. B. Ramipril HEXAL 5 mg")
            if (suggestions.isNotEmpty()) {
                OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    suggestions.forEach { drug ->
                        ListItem(
                            modifier = Modifier.clickable { applyDrug(drug) },
                            headlineContent = { Text(listOfNotNull(drug.name, drug.strength).joinToString(" ")) },
                            supportingContent = {
                                Text(listOfNotNull("PZN ${drug.pzn}", drug.form, drug.manufacturer).joinToString(" · "))
                            },
                            leadingContent = { Icon(painterResource(R.drawable.ic_medication), contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
            InputField(Field.Ingredient, ingredient, { ingredient = it }, placeholder = "z. B. Ramipril 5 mg")
            InputField(Field.Form, form, { form = it }, placeholder = "z. B. Tabletten")
            OutlinedTextField(
                value = pzn,
                onValueChange = { value -> pzn = value.filter(Char::isDigit).take(8) },
                label = { Text(Field.Pzn.label) },
                placeholder = { Text("8-stellig, steht auf der Packung") },
                isError = !pznValid,
                supportingText = if (!pznValid) ({ Text("Diese PZN ist ungültig.") }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    IconButton(onClick = { scanning = Field.Pzn }) {
                        Icon(painterResource(R.drawable.ic_qr_code_scanner), contentDescription = "PZN-Barcode scannen")
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("Einnahme")
            InputField(Field.Dosage, dosage, { dosage = it }, placeholder = "z. B. 1 Tablette")
            ListItem(
                modifier = Modifier.toggleable(value = asNeeded, role = Role.Switch, onValueChange = { asNeeded = it }),
                headlineContent = { Text("Bei Bedarf") },
                supportingContent = { Text("Ohne feste Uhrzeit, beliebig oft bestätigen") },
                trailingContent = { Switch(checked = asNeeded, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
            if (!asNeeded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    Slots.forEachIndexed { index, (label, time) ->
                        ToggleButton(
                            checked = time in times,
                            onCheckedChange = { checked -> times = if (checked) times + time else times - time },
                            modifier = Modifier.weight(1f),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                Slots.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                        ) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(label, maxLines = 1)
                                Text(time.formatted(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.filter { time -> Slots.none { it.second == time } }.sorted().forEach { time ->
                        InputChip(
                            selected = true,
                            onClick = { times = times - time },
                            label = { Text(time.formatted()) },
                            trailingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = "Entfernen",
                                    modifier = Modifier.size(InputChipDefaults.IconSize)
                                )
                            }
                        )
                    }
                    AssistChip(
                        onClick = { showTimePicker = true },
                        label = { Text("Andere Uhrzeit") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_schedule), contentDescription = null) }
                    )
                }
            }

            SectionLabel("Hinweise")
            InputField(
                Field.Instructions, instructions, { instructions = it },
                placeholder = "z. B. auf nüchternen Magen",
                singleLine = false
            )
            InputField(Field.Reason, reason, { reason = it }, placeholder = "z. B. Blutdruck")
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showTimePicker) {
        TimeSelectionDialog(
            initial = LocalTime.of(8, 0),
            onDismiss = { showTimePicker = false },
            onConfirm = {
                times = times + it
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun InputField(
    field: Field,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(field.label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    )
}
