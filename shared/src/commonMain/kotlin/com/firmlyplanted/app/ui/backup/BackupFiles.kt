package com.firmlyplanted.app.ui.backup

import androidx.compose.runtime.Composable

/**
 * Returns a function that asks the user where to save a backup named `fileName` — using the
 * system picker, so on-device storage, Google Drive, iCloud Drive and any other installed storage
 * app all work — then writes the text from [produce] there. [onResult] gets true once saved,
 * false if the user cancelled, or the failure.
 */
@Composable
expect fun rememberBackupSaver(
    produce: suspend () -> String,
    onResult: (Result<Boolean>) -> Unit,
): (fileName: String) -> Unit

/**
 * Returns a function that lets the user pick a backup file with the system picker. [onResult]
 * gets the file's text, null if the user cancelled, or the failure.
 */
@Composable
expect fun rememberBackupOpener(onResult: (Result<String?>) -> Unit): () -> Unit
