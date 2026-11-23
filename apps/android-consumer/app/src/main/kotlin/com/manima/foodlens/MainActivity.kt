package com.manima.foodlens

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.designsystem.FoodLensTheme
import com.manima.foodlens.navigation.AaharApp
import com.manima.foodlens.navigation.UpdateRequiredScreen
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so AppCompatDelegate.setApplicationLocales works below API 33 (SPEC §10.10). */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FoodLensTheme {
                val launch by viewModel.launch.collectAsStateWithLifecycle()
                val updateRequired by viewModel.updateRequired.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when {
                        updateRequired -> UpdateRequiredScreen()
                        launch is LaunchState.Ready -> AaharApp(
                            onboardingDone = (launch as LaunchState.Ready).onboardingDone
                        )
                    }
                }
            }
        }
    }
}
