package com.patryksliwinski.quicknotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.patryksliwinski.quicknotes.R
import com.patryksliwinski.quicknotes.data.DAY_KEY_TODAY
import com.patryksliwinski.quicknotes.data.DAY_KEY_YESTERDAY
import com.patryksliwinski.quicknotes.data.NoteError
import com.patryksliwinski.quicknotes.data.NoteListRow
import com.patryksliwinski.quicknotes.data.NoteMeta
import com.patryksliwinski.quicknotes.data.NoteSortOrder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    onOpenNote: (NoteMeta) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val listRows by viewModel.listRows.collectAsState()
    val query by viewModel.query.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val confirmDelete by viewModel.confirmDelete.collectAsState()
    val folderRevoked by viewModel.folderRevoked.collectAsState()
    val storageName by viewModel.storageName.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NoteMeta?>(null) }
    val deletedMsg = stringResource(R.string.note_deleted)
    val deleteFailedMsg = stringResource(R.string.delete_failed)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(errorMessage) {
        val error = errorMessage ?: return@LaunchedEffect
        val message = when (error) {
            NoteError.CreateFailed -> context.getString(R.string.note_create_failed)
            NoteError.FolderUnavailable -> context.getString(R.string.folder_unavailable)
            NoteError.StorageError -> context.getString(R.string.note_io_failed)
            NoteError.FolderPermissionLost -> context.getString(R.string.folder_permission_not_persisted)
        }
        scope.launch {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
        viewModel.clearError()
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { deleted ->
            if (deleted != null) {
                val result = snackbarHostState.showSnackbar(
                    message = deletedMsg,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoDelete()
                }
            } else {
                snackbarHostState.showSnackbar(
                    message = deleteFailedMsg,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text(stringResource(R.string.confirm_delete_message, pendingDelete!!.title))
            },
            confirmButton = {
                TextButton(onClick = {
                    val note = pendingDelete
                    pendingDelete = null
                    if (note != null) viewModel.deleteNote(note)
                }) {
                    Text(stringResource(R.string.confirm_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.confirm_delete_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = pluralStringResource(R.plurals.note_count, notes.size, notes.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort_by))
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            NoteSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(sortOrderLabel(order)) },
                                    onClick = {
                                        sortMenuExpanded = false
                                        viewModel.onSortOrderChange(order)
                                    },
                                    trailingIcon = {
                                        if (order == sortOrder) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = {
                    viewModel.createNote { note -> onOpenNote(note) }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_notes)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            if (folderRevoked) {
                Text(
                    text = stringResource(R.string.folder_revoked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (notes.isEmpty()) {
                val emptyText = if (query.isBlank()) {
                    stringResource(R.string.empty_notes)
                } else {
                    stringResource(R.string.empty_search, query)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = listRows,
                        key = { row ->
                            when (row) {
                                is NoteListRow.Header -> "header-${row.dayKey}"
                                is NoteListRow.Note -> row.meta.uri
                            }
                        },
                    ) { row ->
                        when (row) {
                            is NoteListRow.Header -> DayHeader(dayKey = row.dayKey)
                            is NoteListRow.Note -> {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                            if (confirmDelete) {
                                                pendingDelete = row.meta
                                                false
                                            } else {
                                                viewModel.deleteNote(row.meta)
                                                true
                                            }
                                        } else false
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = {
                                        val direction = dismissState.dismissDirection
                                        if (direction != SwipeToDismissBoxValue.Settled) {
                                            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                                Alignment.CenterStart
                                            } else {
                                                Alignment.CenterEnd
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.errorContainer)
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = alignment,
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = stringResource(R.string.delete),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.note_deleted),
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    NoteRow(note = row.meta, onClick = { onOpenNote(row.meta) })
                                }
                            }
                        }
                    }
                }
            }
            Text(
                text = storageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun sortOrderLabel(order: NoteSortOrder): String = when (order) {
    NoteSortOrder.LAST_MODIFIED -> stringResource(R.string.sort_last_modified)
    NoteSortOrder.TITLE -> stringResource(R.string.sort_title)
    NoteSortOrder.FILENAME_DATE -> stringResource(R.string.sort_filename_date)
}

@Composable
private fun DayHeader(dayKey: String) {
    val label = when (dayKey) {
        DAY_KEY_TODAY -> stringResource(R.string.header_today)
        DAY_KEY_YESTERDAY -> stringResource(R.string.header_yesterday)
        else -> dayKey
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun NoteRow(note: NoteMeta, onClick: () -> Unit) {
    val date = remember(note.lastModified) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(note.lastModified))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = note.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (note.snippet.isNotEmpty()) {
            Text(
                text = note.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
