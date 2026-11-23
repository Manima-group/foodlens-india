package com.manima.foodlens.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** True when the UI is shown in Hindi, for API fields that come in both languages (e.g. `name_hi`). */
@Composable
fun isHindi(): Boolean = LocalConfiguration.current.locales[0]?.language == "hi"
