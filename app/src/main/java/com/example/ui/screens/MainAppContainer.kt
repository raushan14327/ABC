package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.ChatViewModel

@Composable
fun MainAppContainer(viewModel: ChatViewModel) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()

    val targetScreen = if (!isAuthenticated) {
        when (currentScreen) {
            is AppScreen.Signup -> AppScreen.Signup
            else -> AppScreen.Login
        }
    } else {
        currentScreen
    }

    AnimatedContent(
        targetState = targetScreen,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "screen_navigation",
        modifier = Modifier.fillMaxSize()
    ) { screenState ->
        when (screenState) {
            is AppScreen.Onboarding -> OnboardingScreen(viewModel)
            is AppScreen.Login -> LoginScreen(
                viewModel = viewModel,
                onNavigateToSignup = { viewModel.navigateTo(AppScreen.Signup) },
                onLoginSuccess = { viewModel.navigateTo(AppScreen.Dashboard) }
            )
            is AppScreen.Signup -> SignupScreen(
                viewModel = viewModel,
                onNavigateToLogin = { viewModel.navigateTo(AppScreen.Login) },
                onSignupSuccess = { viewModel.navigateTo(AppScreen.Dashboard) }
            )
            is AppScreen.Profile -> ProfileScreen(
                viewModel = viewModel,
                onBackClick = { viewModel.navigateBack() }
            )
            is AppScreen.Dashboard -> MainDashboard(viewModel)
            is AppScreen.ChatDetail -> ChatScreen(
                viewModel = viewModel,
                chatId = screenState.chatId,
                chatName = screenState.chatName,
                isGroup = screenState.isGroup,
                onBackClick = { viewModel.navigateBack() }
            )
            is AppScreen.Calling -> CallingScreen(viewModel)
            is AppScreen.AdminPanel -> AdminPanelScreen(viewModel)
            is AppScreen.Settings -> SettingsScreen(viewModel)
            else -> MainDashboard(viewModel)
        }
    }
}
