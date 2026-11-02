package com.boxvault.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.boxvault.app.data.backup.BackupManager
import com.boxvault.app.data.preferences.UserPreferencesManager
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.ui.components.BoxVaultHeader
import com.boxvault.app.ui.screens.backup.BackupRestoreScreen
import com.boxvault.app.ui.screens.backup.BackupRestoreViewModel
import com.boxvault.app.ui.screens.boxdetail.BoxDetailScreen
import com.boxvault.app.ui.screens.boxdetail.BoxDetailViewModel
import com.boxvault.app.ui.screens.boxedit.BoxEditScreen
import com.boxvault.app.ui.screens.boxedit.BoxEditViewModel
import com.boxvault.app.ui.screens.bulkentry.BulkEntryScreen
import com.boxvault.app.ui.screens.bulkentry.BulkEntryViewModel
import com.boxvault.app.ui.screens.dashboard.DashboardScreen
import com.boxvault.app.ui.screens.dashboard.DashboardViewModel
import com.boxvault.app.ui.screens.pdfgenerator.PdfGeneratorScreen
import com.boxvault.app.ui.screens.pdfgenerator.PdfGeneratorViewModel
import com.boxvault.app.ui.screens.scanner.QrScannerScreen
import com.boxvault.app.ui.screens.scanner.QrScannerViewModel

@Composable
fun BoxVaultNavHost(
    repository: BoxVaultRepository,
    photoStorageManager: PhotoStorageManager,
    modifier: Modifier = Modifier,
    backupManager: BackupManager? = null,
    userPreferencesManager: UserPreferencesManager? = null,
    navController: NavHostController = rememberNavController(),
    qrScannerContent: (@Composable (onNavigateBack: () -> Unit, onBoxScanned: (String) -> Unit) -> Unit)? = null,
    pdfGeneratorContent: (@Composable (initialBoxId: String?, onNavigateBack: () -> Unit) -> Unit)? = null,
    backupRestoreContent: (@Composable (onNavigateBack: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedBackupManager = remember(backupManager, repository, photoStorageManager) {
        backupManager ?: BackupManager(
            context = context,
            repository = repository,
            photoStorageManager = photoStorageManager
        )
    }
    val resolvedUserPreferencesManager = remember(userPreferencesManager, context) {
        userPreferencesManager ?: UserPreferencesManager(context)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDashboard = currentRoute == BoxVaultDestinations.DASHBOARD_ROUTE

    Column(modifier = modifier.fillMaxSize()) {
        BoxVaultHeader(
            onHeaderClick = if (!isDashboard) {
                {
                    navController.navigate(BoxVaultDestinations.DASHBOARD_ROUTE) {
                        popUpTo(BoxVaultDestinations.DASHBOARD_ROUTE) {
                            inclusive = false
                        }
                    }
                }
            } else null
        )

        NavHost(
            navController = navController,
            startDestination = BoxVaultDestinations.DASHBOARD_ROUTE,
            modifier = Modifier.weight(1f)
        ) {
        composable(route = BoxVaultDestinations.DASHBOARD_ROUTE) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.Factory(repository)
            )
            DashboardScreen(
                viewModel = dashboardViewModel,
                userPreferencesManager = resolvedUserPreferencesManager,
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
                },
                navArgument(BoxVaultDestinations.ARG_PRESET_BOX_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val boxId = backStackEntry.arguments?.getString(BoxVaultDestinations.ARG_BOX_ID)
            val presetBoxId = backStackEntry.arguments?.getString(BoxVaultDestinations.ARG_PRESET_BOX_ID)
            val editViewModel: BoxEditViewModel = viewModel(
                factory = BoxEditViewModel.Factory(
                    repository = repository,
                    boxId = boxId,
                    presetBoxId = presetBoxId
                )
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
                    navController.navigate(BoxVaultDestinations.boxEditRoute(boxId = targetBoxId))
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

        // Milestone 4: Offline CameraX + Bundled ML Kit QR Scanner
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
                val scannerViewModel: QrScannerViewModel = viewModel(
                    factory = QrScannerViewModel.Factory(repository)
                )
                QrScannerScreen(
                    viewModel = scannerViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onExistingBoxScanned = { existingBoxId ->
                        navController.navigate(BoxVaultDestinations.boxDetailRoute(existingBoxId)) {
                            popUpTo(BoxVaultDestinations.DASHBOARD_ROUTE) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onUnassignedQrScanned = { presetBoxId ->
                        navController.navigate(
                            BoxVaultDestinations.boxEditRoute(presetBoxId = presetBoxId)
                        ) {
                            popUpTo(BoxVaultDestinations.DASHBOARD_ROUTE) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        // Milestone 4: Native PdfDocument Printable QR Label Sheet Generator
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
                val pdfViewModel: PdfGeneratorViewModel = viewModel(
                    factory = PdfGeneratorViewModel.Factory(
                        repository = repository,
                        initialBoxId = boxId
                    )
                )
                PdfGeneratorScreen(
                    viewModel = pdfViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Milestone 5: SAF Local .ZIP Backup & Atomic Staged Restore
        composable(route = BoxVaultDestinations.BACKUP_RESTORE_ROUTE) {
            if (backupRestoreContent != null) {
                backupRestoreContent { navController.popBackStack() }
            } else {
                val backupViewModel: BackupRestoreViewModel = viewModel(
                    factory = BackupRestoreViewModel.Factory(
                        repository = repository,
                        backupManager = resolvedBackupManager
                    )
                )
                BackupRestoreScreen(
                    viewModel = backupViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
}
