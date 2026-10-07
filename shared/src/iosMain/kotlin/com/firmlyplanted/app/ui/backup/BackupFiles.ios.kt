package com.firmlyplanted.app.ui.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

/**
 * Receives the Files picker's result. The picker holds its delegate weakly, so the composables
 * below keep this alive for as long as the picker might call back.
 */
private class PickerDelegate(
    private val onPicked: (NSURL?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

/** The view controller currently on top, to present the picker from. */
private fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

private class DelegateHolder {
    var delegate: PickerDelegate? = null
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberBackupSaver(
    produce: suspend () -> String,
    onResult: (Result<Boolean>) -> Unit,
): (fileName: String) -> Unit {
    val scope = rememberCoroutineScope()
    val currentProduce by rememberUpdatedState(produce)
    val currentOnResult by rememberUpdatedState(onResult)
    val holder = remember { DelegateHolder() }

    return remember {
        { fileName ->
            scope.launch {
                // The export picker copies an existing file, so write the backup to a temporary
                // file first, then let the user choose where the copy goes.
                val file = runCatching {
                    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
                    val written = NSString.create(string = currentProduce())
                        .writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
                    if (!written) error("Couldn't prepare the backup file.")
                    url
                }
                val url = file.getOrElse {
                    currentOnResult(Result.failure(it))
                    return@launch
                }
                val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
                val delegate = PickerDelegate { picked ->
                    holder.delegate = null
                    currentOnResult(Result.success(picked != null))
                }
                holder.delegate = delegate
                picker.delegate = delegate
                topViewController()?.presentViewController(picker, animated = true, completion = null)
                    ?: currentOnResult(Result.failure(IllegalStateException("Couldn't show the file picker.")))
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberBackupOpener(onResult: (Result<String?>) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val holder = remember { DelegateHolder() }

    return remember {
        {
            // asCopy = true hands back a copy inside the app's sandbox, so no security-scoped
            // access is needed to read it.
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON), asCopy = true)
            val delegate = PickerDelegate { picked ->
                holder.delegate = null
                if (picked == null) {
                    currentOnResult(Result.success(null))
                } else {
                    val text = NSString.create(contentsOfURL = picked, encoding = NSUTF8StringEncoding, error = null)
                    currentOnResult(
                        if (text != null) Result.success(text.toString()) else Result.failure(IllegalStateException("Couldn't read the chosen file.")),
                    )
                }
            }
            holder.delegate = delegate
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
                ?: currentOnResult(Result.failure(IllegalStateException("Couldn't show the file picker.")))
        }
    }
}
