package com.boxvault.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.boxvault.app.data.preferences.ThemeMode
import com.boxvault.app.data.preferences.UserPreferencesManager
import com.boxvault.app.ui.components.ThemeSelectionDialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    userPreferencesManager: UserPreferencesManager? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolvedPrefsManager = remember(userPreferencesManager, context) {
        userPreferencesManager ?: UserPreferencesManager(context)
    }
    val currentThemeMode by resolvedPrefsManager.themeMode.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var editingRoom by remember { mutableStateOf<RoomEntity?>(null) }
    var deletingRoom by remember { mutableStateOf<RoomEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "BoxVault",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "100% Local Offline Storage Catalog",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Select Theme Mode"
                        )
                    }
                    IconButton(onClick = onNavigateToQrScanner) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Box QR Code"
                        )
                    }
                    IconButton(onClick = onNavigateToPdfGenerator) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print QR Label Sheet"
                        )
                    }
                    IconButton(onClick = onNavigateToBackupRestore) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup & Restore"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreateBox,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text("New Box") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Instant Global Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text("Search any item, box code, location, or note...")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Global Search"
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = viewModel::onClearSearch) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
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
            title = "Create New Room",
            confirmButtonText = "Create Room",
            onDismiss = { showCreateRoomDialog = false },
            onCreateRoom = { name, colorHex ->
                viewModel.createRoom(name, colorHex)
                showCreateRoomDialog = false
            }
        )
    }

    if (editingRoom != null) {
        val target = editingRoom!!
        QuickCreateRoomDialog(
            initialName = target.name,
            initialColorHex = target.colorHex,
            title = "Edit Room",
            confirmButtonText = "Save Changes",
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
            title = { Text("Delete Room?") },
            text = {
                Text(
                    "Are you sure you want to delete \"${target.name}\"? All child boxes will be safely preserved as unassigned boxes."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoom(target.id)
                        deletingRoom = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Room")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRoom = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentThemeMode = currentThemeMode,
            onThemeSelected = { mode ->
                resolvedPrefsManager.setThemeMode(mode)
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun DashboardHomeContent(
    uiState: DashboardUiState,
    onSelectLocation: (String?) -> Unit,
    onBoxClick: (String) -> Unit,
    onBulkEntryClick: (String) -> Unit,
    onCreateBoxClick: () -> Unit,
    onCreateRoomClick: () -> Unit,
    onEditRoom: (RoomEntity) -> Unit,
    onDeleteRoom: (RoomEntity) -> Unit,
    onScanQrClick: () -> Unit,
    onPrintQrSheetClick: () -> Unit,
    onBackupRestoreClick: () -> Unit
) {
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
                onBackupRestoreClick = onBackupRestoreClick,
                onCreateRoomClick = onCreateRoomClick
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
                    onQuickBulkEntryClick = { onBulkEntryClick(summary.box.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            // --- ROOM-GROUPED HIERARCHY (1+ rooms exist) ---
            uiState.roomSections.forEach { section ->
                item(key = "room_header_${section.room.id}") {
                    RoomHeaderCard(
                        room = section.room,
                        boxCount = section.boxCount,
                        totalItems = section.totalItems,
                        onEditRoom = { onEditRoom(section.room) },
                        onDeleteRoom = { onDeleteRoom(section.room) }
                    )
                }

                if (section.boxes.isEmpty()) {
                    item(key = "room_empty_${section.room.id}") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "No boxes assigned to this room yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                } else {
                    items(
                        items = section.boxes,
                        key = { "room_box_${section.room.id}_${it.box.id}" }
                    ) { summary ->
                        BoxSummaryCard(
                            summary = summary,
                            onClick = { onBoxClick(summary.box.id) },
                            onQuickBulkEntryClick = { onBulkEntryClick(summary.box.id) },
                            modifier = Modifier.padding(start = 24.dp, end = 16.dp)
                        )
                    }
                }
            }

            if (uiState.unassignedBoxes.isNotEmpty()) {
                item(key = "unassigned_header") {
                    UnassignedHeaderCard(
                        boxCount = uiState.unassignedBoxes.size,
                        totalItems = uiState.unassignedBoxes.sumOf { it.itemCount }
                    )
                }

                items(
                    items = uiState.unassignedBoxes,
                    key = { "unassigned_box_${it.box.id}" }
                ) { summary ->
                    BoxSummaryCard(
                        summary = summary,
                        onClick = { onBoxClick(summary.box.id) },
                        onQuickBulkEntryClick = { onBulkEntryClick(summary.box.id) },
                        modifier = Modifier.padding(start = 24.dp, end = 16.dp)
                    )
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
    onBackupRestoreClick: () -> Unit,
    onCreateRoomClick: () -> Unit
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
                        label = "ROOMS"
                    )
                }
                StatColumn(
                    value = totalBoxes.toString(),
                    label = "BOXES"
                )
                StatColumn(
                    value = totalItemQuantity.toString(),
                    label = "TOTAL ITEMS"
                )
                StatColumn(
                    value = distinctItemCount.toString(),
                    label = "UNIQUE ENTRIES"
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AssistChip(
                        onClick = onCreateRoomClick,
                        label = { Text("+ Room") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                item {
                    AssistChip(
                        onClick = onScanQrClick,
                        label = { Text("Scan QR") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                item {
                    AssistChip(
                        onClick = onPrintQrSheetClick,
                        label = { Text("Print QR Sheet") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                item {
                    AssistChip(
                        onClick = onBackupRestoreClick,
                        label = { Text("Backup ZIP") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomHeaderCard(
    room: RoomEntity,
    boxCount: Int,
    totalItems: Int,
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
                modifier = Modifier.weight(1f)
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
                        text = "$boxCount boxes • $totalItems items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = onEditRoom) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Room",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDeleteRoom) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Room",
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
    totalItems: Int
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MeetingRoom,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = "Unassigned / General",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$boxCount boxes • $totalItems items (no room assigned)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                label = { Text("All Locations") }
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

@Composable
private fun BoxSummaryCard(
    summary: BoxSummary,
    onClick: () -> Unit,
    onQuickBulkEntryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = parseBoxColor(summary.box.colorHex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                        label = { Text("Bulk +") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Fast Bulk Entry",
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
                            text = "${summary.totalQuantity} items (${summary.itemCount} types)",
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
                text = "Instant Search Results for \"$query\"",
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
                            text = "No matching items or boxes found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching by item name, box code (e.g. BOX-001), or shelf location.",
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
                    text = "MATCHING ITEMS (${matchingItems.size})",
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
                    text = "MATCHING BOXES (${matchingBoxes.size})",
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
                        text = "Qty: ${itemWithBox.item.quantity}",
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
                    text = "No Storage Boxes Cataloged Yet",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Create your first box, organize with rooms, print QR labels, and use Fast Bulk Entry to catalog items in seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onCreateBoxClick,
                        label = { Text("Create First Box") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        }
                    )
                    if (!hasAnyRooms) {
                        AssistChip(
                            onClick = onCreateRoomClick,
                            label = { Text("Create First Room") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.MeetingRoom, contentDescription = null)
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = "No boxes in \"$selectedLocation\"",
                    style = MaterialTheme.typography.titleLarge
                )
                AssistChip(
                    onClick = onClearLocationFilter,
                    label = { Text("Show All Locations") }
                )
            }
        }
    }
}
