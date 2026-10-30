package com.patryksliwinski.quicknotes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patryksliwinski.quicknotes.R
import com.patryksliwinski.quicknotes.data.AppTheme
import com.patryksliwinski.quicknotes.data.EditorPrefs
import com.patryksliwinski.quicknotes.data.FolderPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit = {},
) {
    val context = LocalContext.current
    val storageName by viewModel.storageName.collectAsState()

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

    var fontSizeSp by remember { mutableStateOf(EditorPrefs.DEFAULT_FONT_SIZE_SP) }
    var debounceMs by remember { mutableStateOf(EditorPrefs.DEFAULT_DEBOUNCE_MS) }
    var theme by remember { mutableStateOf(AppTheme.SYSTEM) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                EditorPrefs.loadFontSizeSp(context),
                EditorPrefs.loadDebounceMs(context),
                EditorPrefs.loadTheme(context),
            )
        }
        fontSizeSp = loaded.first
        debounceMs = loaded.second
        theme = loaded.third
    }

    var fontDialog by remember { mutableStateOf(false) }
    var debounceDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.storage_folder)) },
                supportingContent = {
                    Text(storageName.ifEmpty { stringResource(R.string.folder_default) })
                },
                trailingContent = {
                    Text(
                        text = stringResource(R.string.change),
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
                headlineContent = { Text(stringResource(R.string.editor_font_size)) },
                supportingContent = {
                    Text(stringResource(R.string.editor_font_size_value, fontSizeSp))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fontDialog = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.autosave_debounce)) },
                supportingContent = {
                    Text(stringResource(R.string.autosave_debounce_value, debounceMs))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { debounceDialog = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme)) },
                supportingContent = {
                    Text(
                        when (theme) {
                            AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                            AppTheme.LIGHT -> stringResource(R.string.theme_light)
                            AppTheme.DARK -> stringResource(R.string.theme_dark)
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { themeDialog = true },
            )
        }
    }

    if (fontDialog) {
        ChoiceDialog(
            title = stringResource(R.string.editor_font_size),
            options = EditorPrefs.fontSizeOptions,
            selected = fontSizeSp,
            label = { stringResource(R.string.editor_font_size_value, it) },
            onSelected = {
                fontSizeSp = it
                EditorPrefs.saveFontSizeSp(context, it)
            },
            onDismiss = { fontDialog = false },
        )
    }
    if (debounceDialog) {
        ChoiceDialog(
            title = stringResource(R.string.autosave_debounce),
            options = EditorPrefs.debounceOptions,
            selected = debounceMs,
            label = { stringResource(R.string.autosave_debounce_value, it) },
            onSelected = {
                debounceMs = it
                EditorPrefs.saveDebounceMs(context, it)
            },
            onDismiss = { debounceDialog = false },
        )
    }
    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.theme),
            options = EditorPrefs.themeOptions,
            selected = theme,
            label = {
                when (it) {
                    AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                    AppTheme.LIGHT -> stringResource(R.string.theme_light)
                    AppTheme.DARK -> stringResource(R.string.theme_dark)
                }
            },
            onSelected = {
                theme = it
                EditorPrefs.saveTheme(context, it)
                onThemeChanged(it)
            },
            onDismiss = { themeDialog = false },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    TextButton(onClick = {
                        onSelected(option)
                        onDismiss()
                    }) {
                        Text(
                            text = if (option == selected) "● ${label(option)}" else label(option)
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}
