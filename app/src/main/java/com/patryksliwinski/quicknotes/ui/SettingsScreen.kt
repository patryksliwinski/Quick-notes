package com.patryksliwinski.quicknotes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patryksliwinski.quicknotes.R
import com.patryksliwinski.quicknotes.data.FolderPrefs
import com.patryksliwinski.quicknotes.data.NoteSortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val storageName by viewModel.storageName.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val confirmDelete by viewModel.confirmDelete.collectAsState()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
        if (uri != null) {
            FolderPrefs.saveFolderUri(context, uri)
            val persisted = FolderPrefs.takePermission(context, uri)
            if (!persisted) {
                viewModel.reportFolderPermissionLost()
            }
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.storage_folder)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.storage_folder_summary))
                        Text(
                            storageName.ifEmpty { stringResource(R.string.folder_default) }
                        )
                    }
                },
                trailingContent = {
                    Text(
                        text = stringResource(R.string.pick_folder),
                        modifier = Modifier.padding(8.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
                        picker.launch(intent)
                    },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_sort_order)) },
                supportingContent = {
                    Text(
                        when (sortOrder) {
                            NoteSortOrder.LAST_MODIFIED -> stringResource(R.string.sort_last_modified)
                            NoteSortOrder.TITLE -> stringResource(R.string.sort_title)
                            NoteSortOrder.FILENAME_DATE -> stringResource(R.string.sort_filename_date)
                        }
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_confirm_delete)) },
                supportingContent = { Text(stringResource(R.string.settings_confirm_delete_summary)) },
                trailingContent = {
                    Switch(
                        checked = confirmDelete,
                        onCheckedChange = viewModel::onConfirmDeleteChange,
                    )
                },
            )
        }
    }
}
