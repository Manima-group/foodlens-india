package com.manima.foodlens.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun nutrientName(key: String): String =
    Nutrients.labelRes(key)?.let { stringResource(it) } ?: Nutrients.fallbackLabel(key)
