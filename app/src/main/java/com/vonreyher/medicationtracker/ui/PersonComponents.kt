package com.vonreyher.medicationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.Person

/** Initials of a person in an expressive cookie shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonAvatar(person: Person?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Surface(
        shape = MaterialShapes.Cookie4Sided.toShape(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(person?.initials ?: "?", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * "Alle" plus one button per person as an expressive connected button group; the selected button
 * morphs into a pill. Scrolls sideways when there are more people than fit on screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonFilterChips(
    persons: List<Person>,
    selectedPersonId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    includeAll: Boolean = true
) {
    val options: List<Person?> = (if (includeAll) listOf(null) else emptyList()) + persons
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween, Alignment.CenterHorizontally)
    ) {
        options.forEachIndexed { index, person ->
            val checked = selectedPersonId == person?.id
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(person?.id) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.semantics { role = Role.RadioButton }
            ) {
                if (checked) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(person?.let { it.firstName.ifBlank { it.fullName } } ?: "Alle", maxLines = 1)
            }
        }
    }
}
