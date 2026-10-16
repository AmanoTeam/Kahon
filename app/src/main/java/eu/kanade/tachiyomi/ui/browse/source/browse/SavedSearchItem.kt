package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SavedSearchItem(
    savedSearches: List<SavedSearch>,
    onSavedSearch: (SavedSearch) -> Unit,
    onSavedSearchPress: (SavedSearch) -> Unit,
) {
    if (savedSearches.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
    ) {
        Text(
            text = stringResource(MR.strings.saved_searches_delete),
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            savedSearches.forEach {
                SavedSearchChip(
                    onClick = { onSavedSearch(it) },
                    onLongClick = { onSavedSearchPress(it) },
                    label = it.name,
                )
            }
        }
    }
}

@Composable
private fun SavedSearchChip(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    label: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
