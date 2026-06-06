package com.familycheckin

import androidx.compose.runtime.Composable
import com.familycheckin.navigation.AppNavHost
import com.familycheckin.ui.FamilyCheckinTheme

@Composable
fun App() {
    FamilyCheckinTheme {
        AppNavHost()
    }
}
