package com.manima.foodlens.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.manima.foodlens.R
import com.manima.foodlens.core.ui.MessageState

/** Blocking screen for 426 APP_VERSION_UNSUPPORTED (SPEC §10.8). */
@Composable
fun UpdateRequiredScreen() {
    val context = LocalContext.current
    MessageState(
        title = stringResource(R.string.update_title),
        body = stringResource(R.string.update_body),
        icon = Icons.Filled.Refresh,
        primaryAction = stringResource(R.string.update_action) to {
            val id = context.packageName.removeSuffix(".debug").removeSuffix(".staging")
            val market = Intent(Intent.ACTION_VIEW, "market://details?id=$id".toUri())
            try {
                context.startActivity(market)
            } catch (_: ActivityNotFoundException) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$id".toUri())
                )
            }
        },
        modifier = Modifier.testTag("update_required")
    )
}
