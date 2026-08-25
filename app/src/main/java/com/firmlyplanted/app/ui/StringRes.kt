package com.firmlyplanted.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/** Resolves a string resource by its name (used for the license-message ids carried on ScopeCheck). */
@Composable
fun resolveStringByName(name: String): String {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier(name, "string", context.packageName)
    return if (resId != 0) stringResource(resId) else name
}
