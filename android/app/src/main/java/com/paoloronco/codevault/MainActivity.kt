package com.paoloronco.codevault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.Screen
import com.paoloronco.codevault.ui.screens.*
import com.paoloronco.codevault.ui.theme.CodeVaultTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots and app switcher previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()
        setContent {
            CodeVaultTheme {
                CodeVaultApp(viewModel)
            }
        }
    }

    /** Reset auto-lock timer on any user interaction. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.resetAutoLockTimer()
    }

    /** Lock when app goes to background. */
    override fun onStop() {
        super.onStop()
        viewModel.lock()
    }
}

@Composable
private fun CodeVaultApp(viewModel: MainViewModel) {
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    var onboardingComplete by remember { mutableStateOf(viewModel.isOnboardingComplete()) }
    val navController = rememberNavController()

    AnimatedContent(
        targetState = isUnlocked,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "lock_transition"
    ) { unlocked ->
        if (!unlocked) {
            when {
                !onboardingComplete -> OnboardingFlow(
                    onComplete = {
                        // Save to SharedPrefs — Activity will recreate and re-read this value
                        viewModel.completeOnboarding()
                    }
                )
                !viewModel.isPasscodeSet() -> SetupScreen(
                    viewModel       = viewModel,
                    onSetupComplete = {}
                )
                else -> LockScreen(
                    viewModel  = viewModel,
                    onUnlocked = {}
                )
            }
        } else {
            // Unlocked → main navigation
            NavHost(
                navController    = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel       = viewModel,
                        onAddClick      = { type ->
                            navController.navigate(Screen.AddEdit.forNew(type))
                        },
                        onItemClick     = { id -> navController.navigate(Screen.Detail.withId(id)) },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) }
                    )
                }

                composable(Screen.AddEdit.route) { backStack ->
                    val rawId    = backStack.arguments?.getString("id")?.toLongOrNull()
                    val accountId = if (rawId != null && rawId > 0) rawId else null
                    val typeArg  = backStack.arguments?.getString("type") ?: "account"
                    AddEditScreen(
                        viewModel = viewModel,
                        accountId = accountId,
                        entryType = typeArg,
                        onSaved   = { navController.popBackStack() },
                        onBack    = { navController.popBackStack() }
                    )
                }

                composable(Screen.Detail.route) { backStack ->
                    val id = backStack.arguments?.getString("id")?.toLong() ?: return@composable
                    AccountDetailScreen(
                        viewModel = viewModel,
                        accountId = id,
                        onEdit    = {
                            navController.navigate(Screen.AddEdit.forEdit(id)) {
                                popUpTo(Screen.Detail.withId(id))
                            }
                        },
                        onDeleted = { navController.popBackStack() },
                        onBack    = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack    = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
