package nl.muorg.android.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.muorg.android.ui.component.ProvideLibraryChrome
import nl.muorg.android.data.preferences.SearchHistoryEntry
import nl.muorg.android.ui.glass.glassField
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.ui.theme.MuorgShapes

/**
 * The library's search field and sort/filter row.
 *
 * Published into the bottom island rather than rendered in place — the web
 * client keeps these under the mini player, not at the top of the view — so
 * this composable emits nothing where it is called.
 *
 * Split out of [LibraryScreen], which was a single 551-line composable.
 */
@Composable
fun LibraryChrome(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    chromeAccent: Color,
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Local to the chrome: nothing outside the search field reads it.
    var recentSearches by remember { mutableStateOf<List<SearchHistoryEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        recentSearches = viewModel.getRecentSearches()
    }

    ProvideLibraryChrome {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    onSearchTextChange(it)
                    viewModel.onSearchQueryChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .glassField(MuorgShapes.pill),
                placeholder = {
                    Text(
                        "Search albums, artists…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("search")), contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchTextChange("")
                            viewModel.onSearchQueryChange("")
                            coroutineScope.launch { recentSearches = viewModel.getRecentSearches() }
                        }) {
                            Icon(
                        painter = painterResource(mageIconRes("multiply")), contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.submitSearch()
                    coroutineScope.launch { recentSearches = viewModel.getRecentSearches() }
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
                shape = MuorgShapes.pill,
                // The pill itself is the `glassField` material; the text field
                // must not draw a second outline on top of it.
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )

            // Search history chips — show when search field is empty and there's history
            if (searchText.isEmpty() && recentSearches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(mageIconRes("clock")),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            viewModel.clearSearchHistory()
                            recentSearches = emptyList()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("Clear all", style = MaterialTheme.typography.labelSmall)
                    }
                }
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    recentSearches.take(10).forEach { entry ->
                        AssistChip(
                            onClick = {
                                onSearchTextChange(entry.query)
                                viewModel.onSearchQueryChange(entry.query)
                                coroutineScope.launch { recentSearches = viewModel.getRecentSearches() }
                            },
                            label = { Text(entry.query, style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Icon(
                        painter = painterResource(mageIconRes("clock")),
                                    contentDescription = null,
                                    Modifier.size(14.dp),
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                            shape = RoundedCornerShape(20),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = uiState.artistFilter != null) {
                Row(modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                    AssistChip(
                        onClick = viewModel::clearArtistFilter,
                        label = { Text(uiState.artistFilter ?: "") },
                        leadingIcon = { Icon(
                        painter = painterResource(mageIconRes("user")), null, Modifier.size(16.dp)) },
                        trailingIcon = { Icon(
                        painter = painterResource(mageIconRes("multiply")), "Clear", Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = chromeAccent.copy(alpha = 0.18f),
                            labelColor = chromeAccent,
                            leadingIconContentColor = chromeAccent,
                            trailingIconContentColor = chromeAccent,
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = chromeAccent,
                        ),
                    )
                }
            }

            // Sort, genre, layout and shuffle-all live in the top bar now
            // (LibraryTopBar), as on the web client — not in the island.
        }
    }
}
