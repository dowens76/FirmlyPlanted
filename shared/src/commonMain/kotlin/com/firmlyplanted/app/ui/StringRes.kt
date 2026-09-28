package com.firmlyplanted.app.ui

import androidx.compose.runtime.Composable
import com.firmlyplanted.app.resources.Res
import com.firmlyplanted.app.resources.allStringResources
import org.jetbrains.compose.resources.stringResource

/** Resolves a string resource by its name (used for the license-message ids carried on ScopeCheck). */
@Composable
fun resolveStringByName(name: String): String =
    stringByNameOrNull(name) ?: name

/** Looks up a Compose string resource by its `strings.xml` name, or null if there isn't one. */
@Composable
internal fun stringByNameOrNull(name: String): String? =
    Res.allStringResources[name]?.let { stringResource(it) }
