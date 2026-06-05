package com.schwabtrader.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.schwabtrader.app.data.security.SecureStorage
import com.schwabtrader.app.ui.auth.LoginScreen
import com.schwabtrader.app.ui.auth.LoginViewModel
import com.schwabtrader.app.ui.dashboard.DashboardScreen
import com.schwabtrader.app.ui.order.OrderScreen
import com.schwabtrader.app.ui.screener.ScreenerScreen
import com.schwabtrader.app.ui.schwabconnect.SchwabConnectScreen
import com.schwabtrader.app.ui.schwabconnect.SchwabConnectViewModel
import com.schwabtrader.app.ui.stockdetail.StockDetailScreen
import androidx.compose.material3.ExperimentalMaterial3Api
import com.schwabtrader.app.ui.theme.AccentBlue
import com.schwabtrader.app.ui.theme.CardBackground
import com.schwabtrader.app.ui.theme.TextSecondary

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SchwabConnect : Screen("schwab_connect")
    object Dashboard : Screen("dashboard")
    object Screener : Screen("screener")
    object Order : Screen("order/{symbol}/{accountHash}") {
        fun createRoute(symbol: String, accountHash: String) = "order/$symbol/$accountHash"
    }
    object StockDetail : Screen("stockdetail/{symbol}") {
        fun createRoute(symbol: String) = "stockdetail/$symbol"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    pendingOAuthCode: String?,
    pendingOAuthState: String?,
    onOAuthConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val firebaseAuth = FirebaseAuth.getInstance()

    val isLoggedIn = firebaseAuth.currentUser != null

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Dashboard, "Portfolio", Icons.Default.AccountBalance),
        BottomNavItem(Screen.Screener, "Screener", Icons.Default.Search)
    )

    val bottomNavRoutes = setOf(Screen.Dashboard.route, Screen.Screener.route)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = bottomNavRoutes.any { route ->
        currentDestination?.hierarchy?.any { it.route == route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = CardBackground
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentBlue,
                                selectedTextColor = AccentBlue,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = CardBackground
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) Screen.Dashboard.route else Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                val viewModel: LoginViewModel = hiltViewModel()
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.SchwabConnect.route) {
                val viewModel: SchwabConnectViewModel = hiltViewModel()

                // Handle pending OAuth code from deep link
                LaunchedEffect(pendingOAuthCode) {
                    if (pendingOAuthCode != null) {
                        viewModel.handleOAuthCallback(pendingOAuthCode)
                        onOAuthConsumed()
                    }
                }

                SchwabConnectScreen(
                    viewModel = viewModel,
                    onConnected = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.SchwabConnect.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onConnectSchwab = {
                        navController.navigate(Screen.SchwabConnect.route)
                    },
                    onNavigateToOrder = { symbol, accountHash ->
                        navController.navigate(Screen.Order.createRoute(symbol, accountHash))
                    },
                    onSignOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Screener.route) {
                ScreenerScreen(
                    onNavigateToOrder = { symbol, accountHash ->
                        navController.navigate(Screen.Order.createRoute(symbol, accountHash))
                    },
                    onNavigateToDetail = { symbol ->
                        navController.navigate(Screen.StockDetail.createRoute(symbol))
                    },
                    onConnectSchwab = {
                        navController.navigate(Screen.SchwabConnect.route)
                    }
                )
            }

            composable(
                route = Screen.StockDetail.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                StockDetailScreen(
                    symbol = symbol,
                    onNavigateToOrder = { sym, accountHash ->
                        navController.navigate(Screen.Order.createRoute(sym, accountHash))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Order.route,
                arguments = listOf(
                    navArgument("symbol") { type = NavType.StringType },
                    navArgument("accountHash") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                val accountHash = backStackEntry.arguments?.getString("accountHash") ?: ""
                OrderScreen(
                    symbol = symbol,
                    accountHash = accountHash,
                    onOrderPlaced = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
