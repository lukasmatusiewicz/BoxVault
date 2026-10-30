package com.boxvault.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.ui.screens.boxdetail.BoxDetailScreen
import com.boxvault.app.ui.screens.boxdetail.BoxDetailViewModel
import com.boxvault.app.ui.screens.boxedit.BoxEditScreen
import com.boxvault.app.ui.screens.boxedit.BoxEditViewModel
import com.boxvault.app.ui.screens.bulkentry.BulkEntryScreen
import com.boxvault.app.ui.screens.bulkentry.BulkEntryViewModel
import com.boxvault.app.ui.screens.dashboard.DashboardScreen
import com.boxvault.app.ui.screens.dashboard.DashboardViewModel

@Composable
fun BoxVaultNavHost(
    repository: BoxVaultRepository,
    photoStorageManager: PhotoStorageManager,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    qrScannerContent: (@Composable (onNavigateBack: () -> Unit, onBoxScanned: (String) -> Unit) -> Unit)? = null,
    pdfGeneratorContent: (@Composable (initialBoxId: String?, onNavigateBack: () -> Unit) -> Unit)? = null,
    backupRestoreContent: (@Composable (onNavigateBack: () -> Unit) -> Unit)? = null
) {
    NavHost(
        navController = navController,
        startDestination = BoxVaultDestinations.DASHBOARD_ROUTE,
        modifier = modifier
    ) {
        composable(route = BoxVaultDestinations.DASHBOARD_ROUTE) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.Factory(repository)
            )
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToCreateBox = {
                    navController.navigate(BoxVaultDestinations.boxEditRoute(null))
                },
                onNavigateToBoxDetail = { boxId ->
                    navController.navigate(BoxVaultDestinations.boxDetailRoute(boxId))
                },
                onNavigateToBulkEntry = { boxId ->
                    navController.navigate(BoxVaultDestinations.bulkEntryRoute(boxId))
                },
                onNavigateToQrScanner = {
                    navController.navigate(BoxVaultDestinations.QR_SCANNER_ROUTE)
                },
                onNavigateToPdfGenerator = {
                    navController.navigate(BoxVaultDestinations.pdfGeneratorRoute(null))
                },
                onNavigateToBackupRestore = {
                    navController.navigate(BoxVaultDestinations.BACKUP_RESTORE_ROUTE)
                }
            )
        }

        composable(
            route = BoxVaultDestinations.BOX_EDIT_ROUTE,
            arguments = listOf(
                navArgument(BoxVaultDestinations.ARG_BOX_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val boxId = backStackEntry.arguments?.getString(BoxVaultDestinations.ARG_BOX_ID)
            val editViewModel: BoxEditViewModel = viewModel(
                factory = BoxEditViewModel.Factory(repository, boxId)
            )
            BoxEditScreen(
                viewModel = editViewModel,
                onNavigateBack = { navController.popBackStack() },
                onBoxSaved = { savedBoxId ->
                    navController.navigate(BoxVaultDestinations.boxDetailRoute(savedBoxId)) {
                        popUpTo(BoxVaultDestinations.DASHBOARD_ROUTE) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = BoxVaultDestinations.BOX_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(BoxVaultDestinations.ARG_BOX_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val boxId = backStackEntry.arguments?.getString(BoxVaultDestinations.ARG_BOX_ID).orEmpty()
            val detailViewModel: BoxDetailViewModel = viewModel(
                factory = BoxDetailViewModel.Factory(repository, boxId)
            )
            BoxDetailScreen(
                viewModel = detailViewModel,
                photoStorageManager = photoStorageManager,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditBox = { targetBoxId ->
                    navController.navigate(BoxVaultDestinations.boxEditRoute(targetBoxId))
                },
                onNavigateToBulkEntry = { targetBoxId ->
                    navController.navigate(BoxVaultDestinations.bulkEntryRoute(targetBoxId))
                },
                onNavigateToPrintQr = { targetBoxId ->
                    navController.navigate(BoxVaultDestinations.pdfGeneratorRoute(targetBoxId))
                }
            )
        }

        composable(
            route = BoxVaultDestinations.BULK_ENTRY_ROUTE,
            arguments = listOf(
                navArgument(BoxVaultDestinations.ARG_BOX_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val boxId = backStackEntry.arguments?.getString(BoxVaultDestinations.ARG_BOX_ID).orEmpty()
            val bulkViewModel: BulkEntryViewModel = viewModel(
                factory = BulkEntryViewModel.Factory(repository, boxId)
            )
            BulkEntryScreen(
                viewModel = bulkViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Milestone 4 & 5 Route Hooks
        composable(route = BoxVaultDestinations.QR_SCANNER_ROUTE) {
            if (qrScannerContent != null) {
                qrScannerContent(
                    { navController.popBackStack() },
                    { scannedBoxId ->
                        navController.navigate(BoxVaultDestinations.boxDetailRoute(scannedBoxId)) {
                            popUpTo(BoxVaultDestinations.DASHBOARD_ROUTE) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                MilestoneRouteHookPlaceholder(
                    title = "QR Code Scanner",
                    subtitle = "CameraX + Offline ML Kit Barcode Scanner Hook Ready",
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = BoxVaultDestinations.PDF_GENERATOR_ROUTE,
            arguments = listOf(
                navArgument(BoxVaultDestinations.ARG_BOX_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val boxId = backStackEntry.arguments?.getString(BoxVaultDestinations.ARG_BOX_ID)
            if (pdfGeneratorContent != null) {
                pdfGeneratorContent(boxId) { navController.popBackStack() }
            } else {
                MilestoneRouteHookPlaceholder(
                    title = "Printable A4 QR Grid Sheet",
                    subtitle = "ZXing + Android PdfDocument Generator Hook Ready",
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(route = BoxVaultDestinations.BACKUP_RESTORE_ROUTE) {
            if (backupRestoreContent != null) {
                backupRestoreContent { navController.popBackStack() }
            } else {
                MilestoneRouteHookPlaceholder(
                    title = "Local ZIP Backup & Restore",
                    subtitle = "100% Offline SAF Archive Export/Import Hook Ready",
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MilestoneRouteHookPlaceholder(
    title: String,
    subtitle: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onNavigateBack) {
                Text("Back to Dashboard")
            }
        }
    }
}
