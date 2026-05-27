package com.familycheckin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.familycheckin.navigation.AppNavHost

@Composable
fun App() {
    MaterialTheme {
        AppNavHost()
    }
}
