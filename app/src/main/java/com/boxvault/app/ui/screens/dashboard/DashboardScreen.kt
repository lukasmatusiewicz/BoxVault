package com.boxvault.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.boxvault.app.util.sensory.SensoryFeedbackManager
import com.boxvault.app.data.preferences.ThemeMode
import com.boxvault.app.data.preferences.UserPreferencesManager
import com.boxvault.app.ui.components.BoxVaultHeader
import com.boxvault.app.ui.components.OnboardingDialog
import com.boxvault.app.ui.components.PrivacyPolicyDialog
import com.boxvault.app.ui.components.ThemeSelectionDialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxvault.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.data.local.entity.RoomEntity
import com.boxvault.app.data.local.model.BoxSummary
import com.boxvault.app.data.local.model.BoxWithItemsAndPhotos
import com.boxvault.app.data.local.model.ItemWithBox
import com.boxvault.app.ui.screens.boxedit.QuickCreateRoomDialog
import com.boxvault.app.ui.theme.parseBoxColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToCreateBox: () -> Unit,
    onNavigateToBoxDetail: (String) -> Unit,
    onNavigateToBulkEntry: (String) -> Unit,
    onNavigateToQrScanner: () -> Unit,
    onNavigateToPdfGenerator: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    userPreferencesManager: UserPreferencesManager? = null,
    sensoryFeedbackManager: SensoryFeedbackManager? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolvedPrefsManager = remember(userPreferencesManager, context) {
        userPreferencesManager ?: UserPreferencesManager(context)
    }
    val haptic = LocalHapticFeedback.current
    val sensoryManager = remember(sensoryFeedbackManager, context, resolvedPrefsManager) {
        sensoryFeedbackManager ?: SensoryFeedbackManager(context, resolvedPrefsManager)
    }
    val currentThemeMode by resolvedPrefsManager.themeMode.collectAsStateWithLifecycle()
    val soundEffectsEnabled by resolvedPrefsManager.soundEffectsEnabled.collectAsStateWithLifecycle()
    val hapticsEnabled by resolvedPrefsManager.hapticsEnabled.collectAsStateWithLifecycle()
    val hasCompletedOnboarding by resolvedPrefsManager.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var editingRoom by remember { mutableStateOf<RoomEntity?>(null) }
    var deletingRoom by remember { mutableStateOf<RoomEntity?>(null) }
    var isSpeedDialExpanded by remember { mutableStateOf(false) }
    var boxToMove by remember { mutableStateOf<BoxSummary?>(null) }
    var pendingMoveBoxId by remember { mutableStateOf<String?>(null) }

    val fabRotation by animateFloatAsState(
        targetValue = if (isSpeedDialExpanded) 45f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "fabRotation"
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = isSpeedDialExpanded,
                    enter = fadeIn(tween(150)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                isSpeedDialExpanded = false
                                showCreateRoomDialog = true
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.MeetingRoom,
                                    contentDescription = null
                                )
                            },
                            text = { Text(stringResource(R.string.dashboard_speed_dial_new_room)) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        ExtendedFloatingActionButton(
                            onClick = {
                                isSpeedDialExpanded = false
                                onNavigateToCreateBox()
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null
                                )
                            },
                            text = { Text(stringResource(R.string.dashboard_speed_dial_new_box)) },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { isSpeedDialExpanded = !isSpeedDialExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(
                            if (isSpeedDialExpanded) R.string.dashboard_speed_dial_close else R.string.dashboard_speed_dial_open
                        ),
                        modifier = Modifier.rotate(fabRotation)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Instant Global Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = {
                    Text(stringResource(R.string.dashboard_search_placeholder))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.dashboard_search_content_desc)
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = viewModel::onClearSearch) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.dashboard_search_clear)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            if (uiState.isSearchActive) {
                GlobalSearchResultsContent(
                    query = uiState.searchResult.query,
                    matchingBoxes = uiState.searchResult.matchingBoxes,
                    matchingItems = uiState.searchResult.matchingItems,
                    onBoxClick = onNavigateToBoxDetail,
                    onBulkEntryClick = onNavigateToBulkEntry
                )
            } else {
                DashboardHomeContent(
                    uiState = uiState,
                    onSelectLocation = viewModel::onSelectLocation,
                    onBoxClick = onNavigateToBoxDetail,
                    onBoxLongClick = { summary ->
                        sensoryManager.onActionSuccess(haptic)
                        boxToMove = summary
                    },
                    onBulkEntryClick = onNavigateToBulkEntry,
                    onCreateBoxClick = onNavigateToCreateBox,
                    onCreateRoomClick = { showCreateRoomDialog = true },
                    onEditRoom = { editingRoom = it },
                    onDeleteRoom = { deletingRoom = it },
                    onScanQrClick = onNavigateToQrScanner,
                    onPrintQrSheetClick = onNavigateToPdfGenerator,
                    onBackupRestoreClick = onNavigateToBackupRestore
                )
            }
        }
    }

    if (showCreateRoomDialog) {
        QuickCreateRoomDialog(
            title = stringResource(R.string.dashboard_room_create_title),
            confirmButtonText = stringResource(R.string.dashboard_room_create_button),
            onDismiss = {
                showCreateRoomDialog = false
                pendingMoveBoxId = null
            },
            onCreateRoom = { name, colorHex ->
                val targetBoxId = pendingMoveBoxId
                if (targetBoxId != null) {
                    viewModel.createRoom(name, colorHex) { newRoomId ->
                        viewModel.moveBoxToRoom(targetBoxId, newRoomId)
                    }
                    pendingMoveBoxId = null
                } else {
                    viewModel.createRoom(name, colorHex)
                }
                showCreateRoomDialog = false
            }
        )
    }

    if (boxToMove != null) {
        val targetBox = boxToMove!!
        ModalBottomSheet(
            onDismissRequest = { boxToMove = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.dashboard_move_to_room_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.dashboard_move_to_room_subtitle, targetBox.box.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Option: Unassigned / None
                val isUnassigned = targetBox.box.roomId == null
                Surface(
                    onClick = {
                        viewModel.moveBoxToRoom(targetBox.box.id, null)
                        sensoryManager.onActionSuccess(haptic)
                        boxToMove = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUnassigned) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MeetingRoom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.dashboard_move_to_room_unassigned),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isUnassigned) {
                            Icon(
                                imageVector = Icons.Default.CheckBox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // All active rooms
                uiState.rooms.forEach { room ->
                    val isCurrent = targetBox.box.roomId == room.id
                    val roomAccent = parseBoxColor(room.colorHex)
                    Surface(
                        onClick = {
                            viewModel.moveBoxToRoom(targetBox.box.id, room.id)
                            sensoryManager.onActionSuccess(haptic)
                            boxToMove = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(roomAccent)
                            )
                            Text(
                                text = room.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.CheckBox,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Inline + New Room Button
                Button(
                    onClick = {
                        val targetId = targetBox.box.id
                        boxToMove = null
                        pendingMoveBoxId = targetId
                        showCreateRoomDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.dashboard_move_to_room_new_room))
                }
            }
        }
    }

    if (editingRoom != null) {
        val target = editingRoom!!
        QuickCreateRoomDialog(
            initialName = target.name,
            initialColorHex = target.colorHex,
            title = stringResource(R.string.dashboard_room_edit_title),
            confirmButtonText = stringResource(R.string.dashboard_room_save_changes),
            onDismiss = { editingRoom = null },
            onCreateRoom = { name, colorHex ->
                viewModel.updateRoom(target.id, name, colorHex, target.iconName)
                editingRoom = null
            }
        )
    }

    if (deletingRoom != null) {
        val target = deletingRoom!!
        AlertDialog(
            onDismissRequest = { deletingRoom = null },
            title = { Text(stringResource(R.string.dashboard_room_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.dashboard_room_delete_message, target.name)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sensoryManager.onDeleteAction(haptic)
                        viewModel.deleteRoom(target.id)
                        deletingRoom = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.dashboard_room_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRoom = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (!hasCompletedOnboarding) {
        OnboardingDialog(
            onDismiss = {
                coroutineScope.launch {
                    resolvedPrefsManager.setCompletedOnboarding(true)
                }
            }
        )
    }
}

@Composable
private fun DashboardHomeContent(
    uiState: DashboardUiState,
    onSelectLocation: (String?) -> Unit,
    onBoxClick: (String) -> Unit,
    onBoxLongClick: (BoxSummary) -> Unit,
    onBulkEntryClick: (String) -> Unit,
    onCreateBoxClick: () -> Unit,
    onCreateRoomClick: () -> Unit,
    onEditRoom: (RoomEntity) -> Unit,
    onDeleteRoom: (RoomEntity) -> Unit,
    onScanQrClick: () -> Unit,
    onPrintQrSheetClick: () -> Unit,
    onBackupRestoreClick: () -> Unit
) {
    val collapsedRoomIds = remember { mutableStateMapOf<String, Boolean>() }
    var isUnassignedCollapsed by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Stats & Quick Actions Banner
        item {
            SummaryStatsBanner(
                totalBoxes = uiState.totalBoxes,
                totalItemQuantity = uiState.totalItemQuantity,
                distinctItemCount = uiState.distinctItemCount,
                totalRooms = uiState.totalRooms,
                onScanQrClick = onScanQrClick,
                onPrintQrSheetClick = onPrintQrSheetClick,
                onBackupRestoreClick = onBackupRestoreClick
            )
        }

        // Location Filter Chips
        if (uiState.locations.isNotEmpty()) {
            item {
                LocationFilterRow(
                    locations = uiState.locations,
                    selectedLocation = uiState.selectedLocation,
                    onSelectLocation = onSelectLocation
                )
            }
        }

        val hasAnyVisibleBoxes = uiState.filteredBoxes.isNotEmpty()

        if (!hasAnyVisibleBoxes && (!uiState.hasRooms || uiState.roomSections.all { it.boxes.isEmpty() })) {
            item {
                EmptyDashboardState(
                    hasAnyBoxes = uiState.totalBoxes > 0,
                    hasAnyRooms = uiState.hasRooms,
                    selectedLocation = uiState.selectedLocation,
                    onCreateBoxClick = onCreateBoxClick,
                    onCreateRoomClick = onCreateRoomClick,
                    onClearLocationFilter = { onSelectLocation(null) }
                )
            }
        } else if (!uiState.hasRooms) {
            // --- AUTOMATIC FLAT-LIST FALLBACK (0 rooms created) ---
            items(
                items = uiState.filteredBoxes,
                key = { it.box.id }
            ) { summary ->
                BoxSummaryCard(
                    summary = summary,
                    onClick = { onBoxClick(summary.box.id) },
                    onLongClick = { onBoxLongClick(summary) },
                    onQuickBulkEntryClick = { onBulkEntryClick(summary.box.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            // --- ROOM-GROUPED HIERARCHY (1+ rooms exist) ---
            uiState.roomSections.forEach { section ->
                val isExpanded = !(collapsedRoomIds[section.room.id] ?: false)
                item(key = "room_header_${section.room.id}") {
                    RoomHeaderCard(
                        room = section.room,
                        boxCount = section.boxCount,
                        totalItems = section.totalItems,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            collapsedRoomIds[section.room.id] = isExpanded
                        },
                        onEditRoom = { onEditRoom(section.room) },
                        onDeleteRoom = { onDeleteRoom(section.room) }
                    )
                }

                item(key = "room_content_${section.room.id}") {
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn(tween(200)) + expandVertically(),
                        exit = fadeOut(tween(150)) + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (section.boxes.isEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_room_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            } else {
                                section.boxes.forEach { summary ->
                                    BoxSummaryCard(
                                        summary = summary,
                                        onClick = { onBoxClick(summary.box.id) },
                                        onLongClick = { onBoxLongClick(summary) },
                                        onQuickBulkEntryClick = { onBulkEntryClick(summary.box.id) },
                                        modifier = Modifier.padding(start = 24.dp, end = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.unassignedBoxes.isNotEmpty()) {
                item(key = "unassigned_header") {
                    UnassignedHeaderCard(
                        boxCount = uiState.unassignedBoxes.size,
                        totalItems = uiState.unassignedBoxes.sumOf { it.itemCount },
                        isExpanded = !isUnassignedCollapsed,
                        onToggleExpand = { isUnassignedCollapsed = !isUnassignedCollapsed }
                    )
                }

                item(key = "unassigned_content") {
                    AnimatedVisibility(
                        visible = !isUnassignedCollapsed,
                        enter = fadeIn(tween(200)) + expandVertically(),
                        exit = fadeOut(tween(150)) + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.unassignedBoxes.forEach { summary ->
                                BoxSummaryCard(
                                    summary = summary,
                                    onClick = { onBoxClick(summary.box.id) },
                                    onLongClick = { onBoxLongClick(summary) },
                                    onQuickBulkEntryClick = { onBulkEntryClick(summary.box.id) },
                                    modifier = Modifier.padding(start = 24.dp, end = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsBanner(
    totalBoxes: Int,
    totalItemQuantity: Int,
    distinctItemCount: Int,
    totalRooms: Int,
    onScanQrClick: () -> Unit,
    onPrintQrSheetClick: () -> Unit,
    onBackupRestoreClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (totalRooms > 0) {
                    StatColumn(
                        value = totalRooms.toString(),
                        label = stringResource(R.string.dashboard_stat_rooms)
                    )
                }
                StatColumn(
                    value = totalBoxes.toString(),
                    label = stringResource(R.string.dashboard_stat_boxes)
                )
                StatColumn(
                    value = totalItemQuantity.toString(),
                    label = stringResource(R.string.dashboard_stat_total_items)
                )
                StatColumn(
                    value = distinctItemCount.toString(),
                    label = stringResource(R.string.dashboard_stat_unique_entries)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UtilityHubCard(
                    icon = Icons.Default.QrCodeScanner,
                    label = stringResource(R.string.dashboard_hub_scan_qr),
                    onClick = onScanQrClick,
                    modifier = Modifier.weight(1f)
                )
                UtilityHubCard(
                    icon = Icons.Default.Print,
                    label = stringResource(R.string.dashboard_hub_print_sheet),
                    onClick = onPrintQrSheetClick,
                    modifier = Modifier.weight(1f)
                )
                UtilityHubCard(
                    icon = Icons.Default.Backup,
                    label = stringResource(R.string.dashboard_hub_backup_zip),
                    onClick = onBackupRestoreClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UtilityHubCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RoomHeaderCard(
    room: RoomEntity,
    boxCount: Int,
    totalItems: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditRoom: () -> Unit,
    onDeleteRoom: () -> Unit
) {
    val accentColor = parseBoxColor(room.colorHex)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpand)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Column {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.dashboard_room_counts, boxCount, totalItems),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.dashboard_room_collapse else R.string.dashboard_room_expand
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onEditRoom) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.dashboard_room_edit),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDeleteRoom) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.dashboard_room_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UnassignedHeaderCard(
    boxCount: Int,
    totalItems: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpand)
            ) {
                Icon(
                    imageVector = Icons.Default.MeetingRoom,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_unassigned_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.dashboard_unassigned_counts, boxCount, totalItems),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.dashboard_unassigned_collapse else R.string.dashboard_unassigned_expand
                    ),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LocationFilterRow(
    locations: List<String>,
    selectedLocation: String?,
    onSelectLocation: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedLocation == null,
                onClick = { onSelectLocation(null) },
                label = { Text(stringResource(R.string.dashboard_filter_all_locations)) }
            )
        }
        items(locations) { location ->
            FilterChip(
                selected = selectedLocation.equals(location, ignoreCase = true),
                onClick = { onSelectLocation(location) },
                label = { Text(location) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxSummaryCard(
    summary: BoxSummary,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onQuickBulkEntryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = parseBoxColor(summary.box.colorHex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = accentColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = summary.box.shortCode,
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (summary.box.location.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = summary.box.location,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    AssistChip(
                        onClick = onQuickBulkEntryClick,
                        label = { Text(stringResource(R.string.dashboard_box_quick_bulk)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = stringResource(R.string.dashboard_box_fast_bulk_desc),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                Text(
                    text = summary.box.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.dashboard_box_items_count, summary.totalQuantity, summary.itemCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (summary.photoCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${summary.photoCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = summary.box.category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultsContent(
    query: String,
    matchingBoxes: List<BoxWithItemsAndPhotos>,
    matchingItems: List<ItemWithBox>,
    onBoxClick: (String) -> Unit,
    onBulkEntryClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.dashboard_search_results_title, query),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (matchingBoxes.isEmpty() && matchingItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_search_no_results),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dashboard_search_no_results_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (matchingItems.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_search_matching_items, matchingItems.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(
                items = matchingItems,
                key = { "item_${it.item.id}" }
            ) { itemWithBox ->
                SearchMatchingItemCard(
                    itemWithBox = itemWithBox,
                    onClick = { onBoxClick(itemWithBox.box.id) }
                )
            }
        }

        if (matchingBoxes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_search_matching_boxes, matchingBoxes.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(
                items = matchingBoxes,
                key = { "box_${it.box.id}" }
            ) { boxWithDetails ->
                BoxSummaryCard(
                    summary = BoxSummary(
                        box = boxWithDetails.box,
                        itemCount = boxWithDetails.distinctItemCount,
                        totalQuantity = boxWithDetails.totalItemQuantity,
                        photoCount = boxWithDetails.photos.size
                    ),
                    onClick = { onBoxClick(boxWithDetails.box.id) },
                    onQuickBulkEntryClick = { onBulkEntryClick(boxWithDetails.box.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchMatchingItemCard(
    itemWithBox: ItemWithBox,
    onClick: () -> Unit
) {
    val boxColor = parseBoxColor(itemWithBox.box.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = itemWithBox.item.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_item_qty, itemWithBox.item.quantity),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (itemWithBox.item.notes.isNotBlank()) {
                Text(
                    text = itemWithBox.item.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // High-Contrast Parent Box Locator Banner so user immediately knows which box holds the item
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(boxColor)
                        )
                        Text(
                            text = itemWithBox.box.shortCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = itemWithBox.box.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (itemWithBox.box.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = itemWithBox.box.location,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboardState(
    hasAnyBoxes: Boolean,
    hasAnyRooms: Boolean,
    selectedLocation: String?,
    onCreateBoxClick: () -> Unit,
    onCreateRoomClick: () -> Unit,
    onClearLocationFilter: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            if (!hasAnyBoxes) {
                Text(
                    text = stringResource(R.string.dashboard_empty_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.dashboard_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onCreateBoxClick,
                        label = { Text(stringResource(R.string.dashboard_empty_create_box)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        }
                    )
                    if (!hasAnyRooms) {
                        AssistChip(
                            onClick = onCreateRoomClick,
                            label = { Text(stringResource(R.string.dashboard_empty_create_room)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.MeetingRoom, contentDescription = null)
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.dashboard_empty_no_boxes_in_location, selectedLocation ?: ""),
                    style = MaterialTheme.typography.titleLarge
                )
                AssistChip(
                    onClick = onClearLocationFilter,
                    label = { Text(stringResource(R.string.dashboard_empty_show_all_locations)) }
                )
            }
        }
    }
}
