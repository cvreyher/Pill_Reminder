package com.vonreyher.medicationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.data.EmaClient
import com.vonreyher.medicationtracker.data.EmaCredentials
import com.vonreyher.medicationtracker.data.EmaException
import com.vonreyher.medicationtracker.data.EmaProduct

private sealed interface DrugInfoState {
    data object Loading : DrugInfoState
    data object NotConfigured : DrugInfoState
    data class Loaded(val products: List<EmaProduct>) : DrugInfoState
    data class Failed(val message: String) : DrugInfoState
}

/** Looks the medication up in the EMA Product Management Service and shows what was found. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DrugInfoSheet(
    medicationName: String,
    credentials: EmaCredentials,
    onDismiss: () -> Unit
) {
    val state by produceState<DrugInfoState>(DrugInfoState.Loading, medicationName, credentials) {
        value = if (!credentials.isComplete) {
            DrugInfoState.NotConfigured
        } else {
            try {
                DrugInfoState.Loaded(EmaClient(credentials).searchProducts(medicationName))
            } catch (e: EmaException) {
                DrugInfoState.Failed(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = medicationName,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "Arzneimittelinformationen der Europäischen Arzneimittel-Agentur (EMA)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            when (val current = state) {
                DrugInfoState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
                DrugInfoState.NotConfigured -> Message(
                    "Der EMA-Zugang ist noch nicht eingerichtet. Trage die Zugangsdaten, die du bei der " +
                        "Registrierung von der EMA erhalten hast, unter Einstellungen → EMA-Datenbank ein."
                )
                is DrugInfoState.Failed -> Message(current.message)
                is DrugInfoState.Loaded -> if (current.products.isEmpty()) {
                    Message("Zu diesem Namen wurde in der EMA-Datenbank nichts gefunden.")
                } else {
                    current.products.forEachIndexed { index, product ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                        ListItem(
                            headlineContent = { Text(product.name) },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (product.ingredients.isNotEmpty()) {
                                        Text("Wirkstoffe: ${product.ingredients.joinToString()}")
                                    }
                                    product.doseForm?.let { Text("Darreichungsform: $it") }
                                    product.classification?.let { Text("Klassifikation: $it") }
                                    product.legalStatus?.let { Text("Abgabe: $it") }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(24.dp)
    )
}
