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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.data.local.model.BoxSummary
import com.boxvault.app.data.local.model.BoxWithItemsAndPhotos
import com.boxvault.app.data.local.model.ItemWithBox
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
    onNavigateToBackupRestore: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                    onScanQrClick = onNavigateToQrScanner,
                    onPrintQrSheetClick = onNavigateToPdfGenerator,
                    onBackupRestoreClick = onNavigateToBackupRestore
                )
            }
        }
    }
}

@Composable
private fun DashboardHomeContent(
    uiState: DashboardUiState,
    onSelectLocation: (String?) -> Unit,
    onBoxClick: (String) -> Unit,
    onBulkEntryClick: (String) -> Unit,
    onCreateBoxClick: () -> Unit,
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

        if (uiState.filteredBoxes.isEmpty()) {
            item {
                EmptyDashboardState(
                    hasAnyBoxes = uiState.totalBoxes > 0,
                    selectedLocation = uiState.selectedLocation,
                    onCreateBoxClick = onCreateBoxClick,
                    onClearLocationFilter = { onSelectLocation(null) }
                )
            }
        } else {
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
        }
    }
}

@Composable
private fun SummaryStatsBanner(
    totalBoxes: Int,
    totalItemQuantity: Int,
    distinctItemCount: Int,
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
                StatColumn(
                    value = totalBoxes.toString(),
                    label = "STORAGE BOXES"
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
    selectedLocation: String?,
    onCreateBoxClick: () -> Unit,
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
                    text = "Create your first box, print its QR label, and use Fast Bulk Entry to catalog items in seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = onCreateBoxClick,
                    label = { Text("Create First Box") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    }
                )
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
