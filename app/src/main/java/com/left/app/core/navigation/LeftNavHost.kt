package com.left.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.left.app.R
import com.left.app.core.designsystem.LeftIcons
import com.left.app.feature.analytics.AnalyticsScreen
import com.left.app.feature.budgets.BudgetScreen
import com.left.app.feature.dashboard.DashboardScreen
import com.left.app.feature.onboarding.OnboardingScreen
import com.left.app.feature.settings.SettingsScreen
import com.left.app.feature.splash.SplashRoute
import com.left.app.feature.splash.SplashScreen
import com.left.app.feature.transactions.AddTransactionScreen
import com.left.app.feature.transactions.TransactionDetailScreen
import com.left.app.feature.transactions.TransactionsScreen

/**
 * All app routes. Phase 3 wires Dashboard, Transactions, AddTransaction and
 * TransactionDetail for real; Phase 4 adds Budgets; Analytics/Settings remain
 * phased placeholders.
 */
sealed class LeftDestination(val route: String) {
    data object Splash : LeftDestination("splash")
    data object Onboarding : LeftDestination("onboarding")
    data object Home : LeftDestination("home")
    data object Transactions : LeftDestination("transactions")
    data object AddTransaction : LeftDestination("transactions/add")
    data object TransactionDetail : LeftDestination("transactions/detail/{transactionId}") {
        fun routeFor(id: String): String = "transactions/detail/$id"
    }
    data object Budgets : LeftDestination("budgets")
    data object Analytics : LeftDestination("analytics")
    data object Settings : LeftDestination("settings")
}

/** Bottom-bar destinations (UX/UI Spec §2: Home | Transactions | Analytics | Settings + centered Add). */
private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(LeftDestination.Home.route, "Home", LeftIcons.Home),
    TRANSACTIONS(LeftDestination.Transactions.route, "Transactions", LeftIcons.Transactions),
    ANALYTICS(LeftDestination.Analytics.route, "Analytics", LeftIcons.Analytics),
    SETTINGS(LeftDestination.Settings.route, "Settings", LeftIcons.Settings),
}

/**
 * Navigation framework. Renders the bottom navigation bar and the prominent
 * centered Add action only on top-level destinations; Splash, Onboarding,
 * AddTransaction, TransactionDetail and Budgets display without chrome.
 */
@Composable
fun LeftNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTopLevelChrome = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showTopLevelChrome) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(LeftDestination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showTopLevelChrome) {
                FloatingActionButton(
                    onClick = { navController.navigate(LeftDestination.AddTransaction.route) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = LeftIcons.Add,
                        contentDescription = stringResource(R.string.content_description_add_transaction),
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LeftDestination.Splash.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(LeftDestination.Splash.route) {
                SplashScreen(
                    onNavigate = { route ->
                        val target = when (route) {
                            SplashRoute.ONBOARDING -> LeftDestination.Onboarding.route
                            SplashRoute.HOME -> LeftDestination.Home.route
                        }
                        navController.navigate(target) {
                            popUpTo(LeftDestination.Splash.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(LeftDestination.Onboarding.route) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(LeftDestination.Home.route) {
                            popUpTo(LeftDestination.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(LeftDestination.Home.route) {
                DashboardScreen(
                    onAddTransaction = { navController.navigate(LeftDestination.AddTransaction.route) },
                    onTransactionClick = { id ->
                        navController.navigate(LeftDestination.TransactionDetail.routeFor(id))
                    },
                    onManageBudgets = { navController.navigate(LeftDestination.Budgets.route) },
                )
            }
            composable(LeftDestination.Transactions.route) {
                TransactionsScreen(
                    onTransactionClick = { id ->
                        navController.navigate(LeftDestination.TransactionDetail.routeFor(id))
                    },
                )
            }
            composable(
                route = LeftDestination.TransactionDetail.route,
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
            ) { entry ->
                TransactionDetailScreen(
                    transactionId = entry.arguments?.getString("transactionId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(LeftDestination.AddTransaction.route) {
                AddTransactionScreen(onBack = { navController.popBackStack() })
            }
            composable(LeftDestination.Budgets.route) {
                BudgetScreen(onBack = { navController.popBackStack() })
            }
            composable(LeftDestination.Analytics.route) {
                AnalyticsScreen()
            }
            composable(LeftDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
