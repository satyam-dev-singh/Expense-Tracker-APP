package com.left.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.navigation.LeftNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. All UI is Jetpack Compose (Material 3) behind
 * [LeftTheme]; navigation is handled by [LeftNavHost].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeftTheme {
                LeftNavHost()
            }
        }
    }
}
