package com.smartword.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.smartword.app.MainViewModel
import com.smartword.app.R
import com.smartword.app.data.FailureKind
import com.smartword.app.data.FileNames
import com.smartword.app.ui.document.DocumentScreen
import com.smartword.app.ui.home.HomeScreen

/** The whole app: the home screen, or the open document when there is one. */
@Composable
fun SmartWordApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri, persist = true)
    }

    val open = state.open
    if (open == null) {
        HomeScreen(
            recents = state.recents,
            loading = state.loading,
            onNew = viewModel::createNew,
            onOpenFile = { picker.launch(arrayOf(FileNames.DOCX_MIME)) },
            onOpenRecent = { viewModel.open(it.uri.toUri(), persist = false) },
        )
    } else {
        BackHandler(onBack = viewModel::close)
        DocumentScreen(document = open, onBack = viewModel::close)
    }

    val error = state.error
    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(stringResource(messageFor(error.kind))) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

private fun messageFor(kind: FailureKind): Int = when (kind) {
    FailureKind.ENCRYPTED -> R.string.error_encrypted
    FailureKind.TOO_LARGE -> R.string.error_too_large
    FailureKind.INVALID -> R.string.error_invalid
    FailureKind.NOT_FOUND -> R.string.error_not_found
    FailureKind.NO_ACCESS -> R.string.error_no_access
    FailureKind.OTHER -> R.string.error_other
}
