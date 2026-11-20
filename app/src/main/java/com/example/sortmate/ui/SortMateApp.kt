package com.example.sortmate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sortmate.data.prefs.UserSession
import com.example.sortmate.di.AppContainer
import com.example.sortmate.ui.bills.BillEditorScreen
import com.example.sortmate.ui.bills.BillEditorViewModel
import com.example.sortmate.ui.bills.BillsScreen
import com.example.sortmate.ui.bills.BillsViewModel
import com.example.sortmate.ui.chores.ChoresScreen
import com.example.sortmate.ui.chores.ChoresViewModel
import com.example.sortmate.ui.home.HomeScreen
import com.example.sortmate.ui.home.HomeViewModel
import com.example.sortmate.ui.household.HouseholdScreen
import com.example.sortmate.ui.household.HouseholdViewModel
import com.example.sortmate.ui.onboarding.OnboardingScreen
import com.example.sortmate.ui.onboarding.OnboardingViewModel
import com.example.sortmate.ui.theme.SortMateTheme

object SortMateRoutes {
    const val HOME = "home"
    const val BILLS = "bills"
    const val CHORES = "chores"
    const val HOUSEHOLD = "household"
    const val BILL_EDITOR = "bill-editor"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(SortMateRoutes.HOME, "Home", Icons.Rounded.Home),
    BottomDestination(SortMateRoutes.BILLS, "Bills", Icons.AutoMirrored.Rounded.ReceiptLong),
    BottomDestination(SortMateRoutes.CHORES, "Chores", Icons.Rounded.Checklist),
    BottomDestination(SortMateRoutes.HOUSEHOLD, "Household", Icons.Rounded.Groups),
)

@Composable
fun SortMateApp(container: AppContainer) {
    // The session lives in DataStore. A null value means we have not read it yet.
    val session by produceState<UserSession?>(initialValue = null) {
        container.userPreferences.session.collect { value = it }
    }

    SortMateTheme {
        when (val current = session) {
            null -> SplashScreen()
            else -> if (current.onboardingComplete) {
                MainApp(container)
            } else {
                val viewModel: OnboardingViewModel = viewModel(
                    factory = appViewModelFactory { c ->
                        OnboardingViewModel(c.householdRepository, c.userPreferences)
                    },
                )
                OnboardingScreen(viewModel)
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MainApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showBottomBar = currentRoute != SortMateRoutes.BILL_EDITOR
    val showScanAction = currentRoute == SortMateRoutes.HOME || currentRoute == SortMateRoutes.BILLS

    NotificationPermissionEffect()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showScanAction) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(SortMateRoutes.BILL_EDITOR) },
                    icon = { Icon(Icons.Rounded.DocumentScanner, contentDescription = null) },
                    text = { Text("Scan bill") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SortMateRoutes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(SortMateRoutes.HOME) {
                val viewModel: HomeViewModel = viewModel(
                    factory = appViewModelFactory { c ->
                        HomeViewModel(
                            householdRepository = c.householdRepository,
                            billRepository = c.billRepository,
                            choreRepository = c.choreRepository,
                            preferences = c.userPreferences,
                        )
                    },
                )
                HomeScreen(
                    viewModel = viewModel,
                    onOpenBills = { navController.navigate(SortMateRoutes.BILLS) },
                    onOpenChores = { navController.navigate(SortMateRoutes.CHORES) },
                    onScanBill = { navController.navigate(SortMateRoutes.BILL_EDITOR) },
                )
            }

            composable(SortMateRoutes.BILLS) {
                val viewModel: BillsViewModel = viewModel(
                    factory = appViewModelFactory { c ->
                        BillsViewModel(
                            householdRepository = c.householdRepository,
                            billRepository = c.billRepository,
                            preferences = c.userPreferences,
                        )
                    },
                )
                BillsScreen(viewModel = viewModel)
            }

            composable(SortMateRoutes.CHORES) {
                val viewModel: ChoresViewModel = viewModel(
                    factory = appViewModelFactory { c ->
                        ChoresViewModel(
                            householdRepository = c.householdRepository,
                            choreRepository = c.choreRepository,
                            preferences = c.userPreferences,
                        )
                    },
                )
                ChoresScreen(viewModel = viewModel)
            }

            composable(SortMateRoutes.HOUSEHOLD) {
                val viewModel: HouseholdViewModel = viewModel(
                    factory = appViewModelFactory { c -> HouseholdViewModel(c) },
                )
                HouseholdScreen(viewModel = viewModel)
            }

            composable(SortMateRoutes.BILL_EDITOR) {
                val viewModel: BillEditorViewModel = viewModel(
                    factory = appViewModelFactory { c -> BillEditorViewModel(c) },
                )
                val state by viewModel.state.collectAsStateWithLifecycle()
                BillEditorScreen(
                    viewModel = viewModel,
                    state = state,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
