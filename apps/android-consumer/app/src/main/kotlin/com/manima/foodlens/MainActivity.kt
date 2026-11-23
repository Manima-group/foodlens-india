package com.manima.foodlens

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.manima.foodlens.core.designsystem.FoodLensTheme
import com.manima.foodlens.navigation.FoodLensNavHost
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so AppCompatDelegate.setApplicationLocales works below API 33 (SPEC §10.10). */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FoodLensTheme { FoodLensNavHost() } }
    }
}
