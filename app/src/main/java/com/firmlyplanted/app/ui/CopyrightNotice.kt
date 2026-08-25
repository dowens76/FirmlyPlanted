package com.firmlyplanted.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firmlyplanted.app.domain.Translation

/** Resolves a Translation's copyright notice string by name (see DefaultTranslations / strings.xml). */
@Composable
fun resolveCopyrightNotice(translation: Translation): String {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier(translation.copyrightNoticeResName, "string", context.packageName)
    return if (resId != 0) stringResource(resId) else translation.licenseSummary
}

/** Required-notice footer. Render this on every screen that shows a translation's verse text. */
@Composable
fun CopyrightNotice(translation: Translation, modifier: Modifier = Modifier) {
    val notice = resolveCopyrightNotice(translation)
    Column(modifier = modifier.padding(12.dp)) {
        HorizontalDivider()
        Text(
            text = notice,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
