package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EditStatusDialog(
    initialStatus: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val choices: ImmutableList<StatusChoice> = remember {
        persistentListOf(
            StatusChoice(SManga.ONGOING.toLong(), MR.strings.ongoing),
            StatusChoice(SManga.COMPLETED.toLong(), MR.strings.completed),
            StatusChoice(SManga.LICENSED.toLong(), MR.strings.licensed),
            StatusChoice(SManga.PUBLISHING_FINISHED.toLong(), MR.strings.publishing_finished),
            StatusChoice(SManga.CANCELLED.toLong(), MR.strings.cancelled),
            StatusChoice(SManga.ON_HIATUS.toLong(), MR.strings.on_hiatus),
        )
    }
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = stringResource(
        choices.firstOrNull { it.value == initialStatus }?.label ?: MR.strings.unknown,
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        title = { Text(text = stringResource(MR.strings.edit_status_dialog_title)) },
        text = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = {
                        Text(text = stringResource(MR.strings.edit_status_dialog_message))
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                )
                ExposedDropdownMenu(
                    modifier = Modifier.exposedDropdownSize(),
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    choices.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(item.label)) },
                            onClick = {
                                expanded = false
                                onConfirm(item.value)
                                onDismissRequest()
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        },
    )
}

private data class StatusChoice(val value: Long, val label: StringResource)
