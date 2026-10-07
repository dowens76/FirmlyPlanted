package com.firmlyplanted.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberBackupSaver(
    produce: suspend () -> String,
    onResult: (Result<Boolean>) -> Unit,
): (fileName: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentProduce by rememberUpdatedState(produce)
    val currentOnResult by rememberUpdatedState(onResult)
    // The backup is produced only after a destination is chosen, so nothing has to be held
    // across the picker (which may outlive this activity instance).
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) {
            currentOnResult(Result.success(false))
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                val text = currentProduce()
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "w")
                        ?: error("Couldn't open the chosen location for writing.")
                    stream.use { it.write(text.encodeToByteArray()) }
                }
                true
            }
            currentOnResult(result)
        }
    }
    return remember(launcher) { { fileName -> launcher.launch(fileName) } }
}

@Composable
actual fun rememberBackupOpener(onResult: (Result<String?>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            currentOnResult(Result.success(null))
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("Couldn't open the chosen file.")
                    stream.use { it.readBytes().decodeToString() }
                }
            }
            currentOnResult(result)
        }
    }
    // Any type: cloud providers don't always label .json files as application/json. The
    // contents are validated on restore.
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}
