package com.vonreyher.medicationtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.data.AppMode
import com.vonreyher.medicationtracker.data.BmpPage
import com.vonreyher.medicationtracker.data.DrugDatabase
import com.vonreyher.medicationtracker.data.MedicationStore
import com.vonreyher.medicationtracker.data.Person
import com.vonreyher.medicationtracker.ui.AddMedicationScreen
import com.vonreyher.medicationtracker.ui.ConfirmationSound
import com.vonreyher.medicationtracker.ui.TimeSelectionDialog
import com.vonreyher.medicationtracker.ui.HistoryScreen
import com.vonreyher.medicationtracker.ui.MedicationDetailScreen
import com.vonreyher.medicationtracker.ui.OnboardingScreen
import com.vonreyher.medicationtracker.ui.PlanDetailScreen
import com.vonreyher.medicationtracker.ui.PlanOwner
import com.vonreyher.medicationtracker.ui.PlansScreen
import com.vonreyher.medicationtracker.ui.ScanPreviewScreen
import com.vonreyher.medicationtracker.ui.ScannerScreen
import com.vonreyher.medicationtracker.ui.SettingsScreen
import com.vonreyher.medicationtracker.ui.TodayScreen
import com.vonreyher.medicationtracker.ui.rememberCurrentTime
import com.vonreyher.medicationtracker.ui.theme.MedicationTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

private enum class Destination(val label: String, val icon: Int) {
    Today("Heute", R.drawable.ic_home),
    Plans("Pläne", R.drawable.ic_description),
    History("Verlauf", R.drawable.ic_history),
    Settings("Einstellungen", R.drawable.ic_settings)
}

/** Content shown full screen on top of the main navigation. */
private sealed interface FullScreen {
    data class MedicationDetail(val medicationId: Int) : FullScreen
    data class PlanDetail(val planId: String) : FullScreen
    data object Scanner : FullScreen
    data object AddMedication : FullScreen
    data class EditMedication(val medicationId: Int) : FullScreen
    data object ScanPreview : FullScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val store = MedicationStore.get(this)
        // Load the confirmation chime early so it plays without delay.
        ConfirmationSound.get(this)

        setContent {
            MedicationTrackerTheme {
                if (store.settings.onboardingDone) {
                    MedicationTrackerApp(store)
                } else {
                    OnboardingScreen(onDone = store::completeOnboarding)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationTrackerApp(store: MedicationStore) {
    val settings = store.settings
    val careMode = settings.mode == AppMode.Care
    // In self mode only the user's own medication is shown, even if people were added in care mode.
    val persons = if (careMode) store.persons.toList() else store.persons.filter { it.id == Person.SELF_ID }
    val medications = store.activeMedications.filter { careMode || it.personId == Person.SELF_ID }
    val now by rememberCurrentTime()

    var destination by rememberSaveable { mutableStateOf(Destination.Today) }
    var fullScreen by remember { mutableStateOf<FullScreen?>(null) }
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var timePickerFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var scannedPages by remember { mutableStateOf<Map<Int, BmpPage>>(emptyMap()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val todayListState = rememberLazyListState()
    val todayScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Reminders need the notification permission from Android 13 on; ask once the app is set up.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = LocalContext.current
    val drugDatabase = remember { DrugDatabase.get(context) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun confirmIntake(medication: Medication) {
        val event = store.confirmIntake(medication)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "${medication.name} als eingenommen markiert",
                actionLabel = "Rückgängig",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) store.undoIntake(event)
        }
    }

    fun scan() {
        fullScreen = FullScreen.Scanner
    }

    /** Adds a scanned page to the current scan and returns an error message if it doesn't fit. */
    fun addScannedPage(page: BmpPage): String? {
        val existing = scannedPages.values.firstOrNull()
        if (existing != null && existing.instanceId != page.instanceId) {
            return "Diese Seite gehört zu einem anderen Medikationsplan."
        }
        // Fill in names the user already gave to PZN-only entries.
        scannedPages = scannedPages + (page.page to store.withKnownNames(page))
        fullScreen = FullScreen.ScanPreview
        return null
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (destination == Destination.Today) {
                Modifier.nestedScroll(todayScrollBehavior.nestedScrollConnection)
            } else {
                Modifier
            },
            topBar = {
                if (destination == Destination.Today) {
                    TodayTopBar(greeting(settings.firstName), todayScrollBehavior)
                } else {
                    MainTopBar(destination)
                }
            },
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            },
            floatingActionButton = {
                when (destination) {
                    Destination.Today -> ExtendedFloatingActionButton(
                        onClick = { fullScreen = FullScreen.AddMedication },
                        expanded = todayListState.firstVisibleItemIndex == 0,
                        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        text = { Text("Medikament hinzufügen") }
                    )
                    Destination.Plans -> ExtendedFloatingActionButton(
                        onClick = {
                            scannedPages = emptyMap()
                            scan()
                        },
                        icon = { Icon(painterResource(R.drawable.ic_qr_code_scanner), contentDescription = null) },
                        text = { Text("Plan scannen") }
                    )
                    else -> Unit
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            when (destination) {
                Destination.Today -> TodayScreen(
                    medications = medications,
                    persons = persons,
                    careMode = careMode,
                    selectedPersonId = selectedPersonId?.takeIf { id -> persons.any { it.id == id } },
                    onSelectPerson = { selectedPersonId = it },
                    intakesToday = { store.intakesToday(it.id).size },
                    history = store.history.filter { careMode || it.personId == Person.SELF_ID },
                    scheduledSince = store::scheduledSince,
                    now = now,
                    listState = todayListState,
                    contentPadding = padding,
                    onMedicationClick = { fullScreen = FullScreen.MedicationDetail(it.id) }
                )
                Destination.Plans -> PlansScreen(
                    plans = store.plans.filter { careMode || it.personId == Person.SELF_ID },
                    persons = persons,
                    careMode = careMode,
                    contentPadding = padding,
                    onPlanClick = { fullScreen = FullScreen.PlanDetail(it.id) }
                )
                Destination.History -> HistoryScreen(
                    history = store.history.filter { careMode || it.personId == Person.SELF_ID },
                    persons = persons,
                    careMode = careMode,
                    contentPadding = padding
                )
                Destination.Settings -> SettingsScreen(store = store, contentPadding = padding)
            }
        }

        AnimatedContent(
            targetState = fullScreen,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                    scaleIn(spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.92f) +
                    fadeIn(tween(220))) togetherWith
                    (scaleOut(tween(220), targetScale = 0.96f) + fadeOut(tween(220))) using null
            },
            label = "fullScreen"
        ) { target ->
            when (target) {
                null -> Unit
                is FullScreen.MedicationDetail -> {
                    val medication = store.medications.firstOrNull { it.id == target.medicationId }
                    if (medication != null) {
                        MedicationDetailScreen(
                            medication = medication,
                            person = store.person(medication.personId).takeIf { careMode },
                            intakesToday = store.intakesToday(medication.id).size,
                            emaCredentials = settings.ema,
                            now = now,
                            onClose = { fullScreen = null },
                            onConfirmTaken = { confirmIntake(medication) },
                            onMarkNotTaken = { store.undoLatestIntakeToday(medication) },
                            onChangeTime = { timePickerFor = medication.id },
                            onEdit = { fullScreen = FullScreen.EditMedication(medication.id) }
                        )
                    }
                }
                is FullScreen.PlanDetail -> {
                    val plan = store.plans.firstOrNull { it.id == target.planId }
                    if (plan != null) {
                        PlanDetailScreen(
                            plan = plan,
                            predecessor = store.predecessorOf(plan),
                            successor = plan.replacedBy?.let { id -> store.plans.firstOrNull { it.id == id } },
                            otherActivePlans = store.activePlansOf(plan.personId).filter { it.id != plan.id },
                            emaCredentials = settings.ema,
                            onClose = { fullScreen = null },
                            onSetActive = { active, deactivateOthers ->
                                store.setPlanActive(plan.id, active, deactivateOthers)
                                showMessage(if (active) "Plan aktiviert" else "Plan deaktiviert – er bleibt im Planverlauf")
                            },
                            onOpenPlan = { fullScreen = FullScreen.PlanDetail(it.id) },
                            onDelete = {
                                fullScreen = null
                                store.deletePlan(plan.id)
                                showMessage("Plan gelöscht")
                            }
                        )
                    }
                }
                FullScreen.AddMedication -> AddMedicationScreen(
                    persons = persons,
                    careMode = careMode,
                    initialPersonId = selectedPersonId?.takeIf { id -> persons.any { it.id == id } }
                        ?: persons.firstOrNull()?.id
                        ?: Person.SELF_ID,
                    onSave = { template, times ->
                        store.addMedication(template, times)
                        fullScreen = null
                        showMessage("${template.name} hinzugefügt")
                    },
                    onClose = { fullScreen = null },
                    searchDrugs = { query -> withContext(Dispatchers.IO) { drugDatabase.search(query) } },
                    findDrug = { pzn -> withContext(Dispatchers.IO) { drugDatabase.find(pzn) } }
                )
                is FullScreen.EditMedication -> {
                    val medication = store.medications.firstOrNull { it.id == target.medicationId }
                    if (medication != null) {
                        AddMedicationScreen(
                            persons = persons,
                            careMode = careMode,
                            initialPersonId = medication.personId,
                            initial = medication,
                            onSave = { edited, times ->
                                store.editMedication(medication, edited.copy(planId = medication.planId), times)
                                fullScreen = FullScreen.MedicationDetail(medication.id)
                                showMessage("Gespeichert")
                            },
                            onClose = { fullScreen = FullScreen.MedicationDetail(medication.id) },
                            searchDrugs = { query -> withContext(Dispatchers.IO) { drugDatabase.search(query) } },
                            findDrug = { pzn -> withContext(Dispatchers.IO) { drugDatabase.find(pzn) } }
                        )
                    }
                }
                FullScreen.Scanner -> ScannerScreen(
                    onPage = ::addScannedPage,
                    onClose = {
                        fullScreen = if (scannedPages.isEmpty()) null else FullScreen.ScanPreview
                    }
                )
                FullScreen.ScanPreview -> if (scannedPages.isNotEmpty()) {
                    val firstPage = scannedPages.values.minBy { it.page }
                    ScanPreviewScreen(
                        pages = scannedPages,
                        careMode = careMode,
                        persons = persons,
                        suggestedPersonId = store.matchPerson(firstPage)?.id?.takeIf { careMode },
                        activePlansOf = store::activePlansOf,
                        onScanNextPage = ::scan,
                        onSave = { owner, replaces ->
                            val personId = when (owner) {
                                is PlanOwner.Existing -> owner.personId
                                is PlanOwner.New -> store.addPerson(
                                    owner.person.firstName,
                                    owner.person.lastName,
                                    owner.person.birthDate
                                ).id
                            }
                            store.savePlan(scannedPages.values, personId, replaces)
                            scannedPages = emptyMap()
                            fullScreen = null
                            destination = Destination.Today
                            showMessage(
                                if (replaces.isEmpty()) "Plan gespeichert und in den Tagesplan übernommen"
                                else "Plan ersetzt – der bisherige liegt im Planverlauf"
                            )
                        },
                        onDiscard = {
                            scannedPages = emptyMap()
                            fullScreen = null
                        }
                    )
                }
            }
        }
    }

    timePickerFor?.let { id ->
        val medication = store.medications.firstOrNull { it.id == id }
        if (medication == null) {
            timePickerFor = null
        } else {
            TimeSelectionDialog(
                initial = medication.time ?: LocalTime.of(8, 0),
                onDismiss = { timePickerFor = null },
                onConfirm = { time ->
                    store.updateMedication(medication.copy(time = time))
                    timePickerFor = null
                }
            )
        }
    }
}

/** Large Material You header with the greeting that shrinks into a small bar while scrolling. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TodayTopBar(greeting: String, scrollBehavior: TopAppBarScrollBehavior) {
    LargeFlexibleTopAppBar(
        title = { Text(greeting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(destination: Destination) {
    TopAppBar(title = { Text(destination.label, style = MaterialTheme.typography.titleLargeEmphasized) })
}

@Composable
private fun greeting(name: String?): String {
    val hour = remember { LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> "Guten Morgen"
        in 12..17 -> "Guten Tag"
        else -> "Guten Abend"
    }
    return if (name.isNullOrBlank()) greeting else "$greeting, $name"
}
