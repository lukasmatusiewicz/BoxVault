package com.boxvault.app.ui.screens.boxdetail

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.local.model.ItemWithPhotos
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.ui.components.LocalPhotoThumbnail
import com.boxvault.app.ui.theme.parseBoxColor
import com.boxvault.app.util.image.PhotoCompressor
import com.boxvault.app.util.qr.QrCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxDetailScreen(
    viewModel: BoxDetailViewModel,
    photoStorageManager: PhotoStorageManager,
    onNavigateBack: () -> Unit,
    onNavigateToEditBox: (String) -> Unit,
    onNavigateToBulkEntry: (String) -> Unit,
    onNavigateToPrintQr: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddOrEditItemDialog by remember { mutableStateOf(false) }
    var itemBeingEdited by remember { mutableStateOf<ItemEntity?>(null) }
    var showDeleteBoxConfirm by remember { mutableStateOf(false) }
    var showQrPreviewDialog by remember { mutableStateOf(false) }
    var pendingPhotoTargetItemId by remember { mutableStateOf<String?>(null) }

    // Camera capture launcher (100% local in-memory bitmap -> downsampled 80% JPEG bytes)
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val bytes = PhotoCompressor.compressBitmapToJpeg(bitmap)
            val targetItemId = pendingPhotoTargetItemId
            if (targetItemId != null) {
                viewModel.attachPhotoToItem(targetItemId, bytes)
            } else {
                viewModel.attachPhotoToBox(bytes)
            }
        }
        pendingPhotoTargetItemId = null
    }

    // Local Gallery file picker launcher (100% local URI stream -> Exif normalized & downsampled)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val rawBytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                val compressedBytes = PhotoCompressor.compressAndNormalize(rawBytes)
                val targetItemId = pendingPhotoTargetItemId
                if (targetItemId != null) {
                    viewModel.attachPhotoToItem(targetItemId, compressedBytes)
                } else {
                    viewModel.attachPhotoToBox(compressedBytes)
                }
            }
        }
        pendingPhotoTargetItemId = null
    }

    val box = uiState.box

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = box?.shortCode ?: "Box Detail",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = box?.name ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (box != null) {
                        IconButton(onClick = { showQrPreviewDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "View QR Code"
                            )
                        }
                        IconButton(onClick = { onNavigateToPrintQr(box.id) }) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Print QR Label"
                            )
                        }
                        IconButton(onClick = { onNavigateToEditBox(box.id) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Box"
                            )
                        }
                        IconButton(onClick = { showDeleteBoxConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Box",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (box == null && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Box not found.",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (box != null) {
                // Box Metadata & QR Payload Header Card
                item {
                    BoxHeaderCard(
                        box = box,
                        distinctItemCount = uiState.distinctItemCount,
                        totalQuantity = uiState.totalQuantity,
                        photoCount = uiState.boxWithDetails?.photos?.size ?: 0,
                        onShowQrModal = { showQrPreviewDialog = true }
                    )
                }

                // Prominent Fast Bulk Item Entry CTA Card
                item {
                    FastBulkEntryLaunchCard(
                        boxShortCode = box.shortCode,
                        onLaunchBulkEntry = { onNavigateToBulkEntry(box.id) }
                    )
                }

                // Box Photos Section
                item {
                    BoxPhotosSection(
                        photos = uiState.boxLevelPhotos,
                        photoStorageManager = photoStorageManager,
                        onCapturePhoto = {
                            pendingPhotoTargetItemId = null
                            takePicturePreviewLauncher.launch(null)
                        },
                        onPickPhoto = {
                            pendingPhotoTargetItemId = null
                            pickImageLauncher.launch("image/*")
                        },
                        onDeletePhoto = viewModel::deletePhoto
                    )
                }

                // Items Section Header + Filter + Add Single Item
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ITEMS IN BOX (${uiState.totalQuantity} total)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            AssistChip(
                                onClick = {
                                    itemBeingEdited = null
                                    showAddOrEditItemDialog = true
                                },
                                label = { Text("Add 1 Item") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }

                        if (uiState.itemsWithPhotos.size > 2) {
                            OutlinedTextField(
                                value = uiState.itemFilterQuery,
                                onValueChange = viewModel::onItemFilterQueryChange,
                                placeholder = { Text("Filter items inside ${box.shortCode}...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                if (uiState.filteredItemsWithPhotos.isEmpty()) {
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
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (uiState.itemsWithPhotos.isEmpty()) {
                                        "This box is currently empty"
                                    } else {
                                        "No items match \"${uiState.itemFilterQuery}\""
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (uiState.itemsWithPhotos.isEmpty()) {
                                    Button(onClick = { onNavigateToBulkEntry(box.id) }) {
                                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Start Fast Bulk Entry")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.filteredItemsWithPhotos,
                        key = { it.item.id }
                    ) { itemWithPhotos ->
                        BoxItemRowCard(
                            itemWithPhotos = itemWithPhotos,
                            photoStorageManager = photoStorageManager,
                            onIncrement = { viewModel.incrementItemQuantity(itemWithPhotos.item) },
                            onDecrement = { viewModel.decrementItemQuantity(itemWithPhotos.item) },
                            onEdit = {
                                itemBeingEdited = itemWithPhotos.item
                                showAddOrEditItemDialog = true
                            },
                            onDelete = { viewModel.deleteItem(itemWithPhotos.item.id) },
                            onAttachPhoto = {
                                pendingPhotoTargetItemId = itemWithPhotos.item.id
                                takePicturePreviewLauncher.launch(null)
                            },
                            onDeletePhoto = viewModel::deletePhoto
                        )
                    }
                }
            }
        }
    }

    if (showAddOrEditItemDialog) {
        ItemEditDialog(
            initialItem = itemBeingEdited,
            onDismiss = {
                showAddOrEditItemDialog = false
                itemBeingEdited = null
            },
            onConfirm = { name, qty, category, notes ->
                viewModel.saveItem(
                    name = name,
                    quantity = qty,
                    category = category,
                    notes = notes,
                    existingItemId = itemBeingEdited?.id
                )
                showAddOrEditItemDialog = false
                itemBeingEdited = null
            }
        )
    }

    if (showQrPreviewDialog && box != null) {
        QrCodePreviewDialog(
            box = box,
            onDismiss = { showQrPreviewDialog = false },
            onPrintPdfSheet = {
                showQrPreviewDialog = false
                onNavigateToPrintQr(box.id)
            }
        )
    }

    if (showDeleteBoxConfirm && box != null) {
        AlertDialog(
            onDismissRequest = { showDeleteBoxConfirm = false },
            title = { Text("Delete ${box.shortCode} (${box.name})?") },
            text = {
                Text(
                    "This will permanently delete this box, all ${uiState.distinctItemCount} item entries inside it, and any attached local photos."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteBoxConfirm = false
                        viewModel.deleteBox(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Box")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBoxConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BoxHeaderCard(
    box: BoxEntity,
    distinctItemCount: Int,
    totalQuantity: Int,
    photoCount: Int,
    onShowQrModal: () -> Unit
) {
    val accentColor = parseBoxColor(box.colorHex)
    val qrBitmap = remember(box.id) {
        runCatching {
            QrCodeGenerator.generateBoxQrBitmap(
                boxId = box.id,
                sizePx = 192
            )
        }.getOrNull()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor)
                )
                Surface(
                    color = accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = box.shortCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (box.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = box.location,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Text(
                text = box.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = box.category,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "• $totalQuantity total qty ($distinctItemCount unique) • $photoCount photos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (box.notes.isNotBlank()) {
                Text(
                    text = box.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Interactive QR Thumbnail + Immutable QR URI badge
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowQrModal)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Tap to enlarge QR code",
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tap to Enlarge / Print QR Label",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = box.qrPayload,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCodePreviewDialog(
    box: BoxEntity,
    onDismiss: () -> Unit,
    onPrintPdfSheet: () -> Unit
) {
    val qrBitmap = remember(box.id) {
        runCatching {
            QrCodeGenerator.generateBoxQrBitmap(
                boxId = box.id,
                sizePx = 512,
                marginModules = 2
            )
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${box.shortCode} • ${box.name}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = box.qrPayload,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "High-contrast QR code for ${box.shortCode}",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    )
                }
                Text(
                    text = "This QR code encodes the immutable box UUID. You can rename or move ${box.shortCode} at any time without ever re-printing this sticker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onPrintPdfSheet) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print on PDF Sheet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FastBulkEntryLaunchCard(
    boxShortCode: String,
    onLaunchBulkEntry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fast Bulk Item Entry Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Rapidly catalog items into $boxShortCode without the keyboard ever closing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onLaunchBulkEntry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bulk Add")
            }
        }
    }
}

@Composable
private fun BoxPhotosSection(
    photos: List<PhotoEntity>,
    photoStorageManager: PhotoStorageManager,
    onCapturePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BOX PHOTOS (${photos.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onCapturePhoto,
                    label = { Text("Camera") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = onPickPhoto,
                    label = { Text("Gallery") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        if (photos.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = photos,
                    key = { it.id }
                ) { photo ->
                    LocalPhotoThumbnail(
                        relativePath = photo.relativePath,
                        photoStorageManager = photoStorageManager,
                        size = 96.dp,
                        onDeleteClick = { onDeletePhoto(photo.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxItemRowCard(
    itemWithPhotos: ItemWithPhotos,
    photoStorageManager: PhotoStorageManager,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttachPhoto: () -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    val item = itemWithPhotos.item

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (item.notes.isNotBlank()) {
                        Text(
                            text = item.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Inline Quantity Stepper (- / qty / +)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledIconButton(
                        onClick = onDecrement,
                        enabled = item.quantity > 1,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease quantity",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    FilledIconButton(
                        onClick = onIncrement,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase quantity",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (itemWithPhotos.photos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = itemWithPhotos.photos,
                        key = { it.id }
                    ) { photo ->
                        LocalPhotoThumbnail(
                            relativePath = photo.relativePath,
                            photoStorageManager = photoStorageManager,
                            size = 68.dp,
                            onDeleteClick = { onDeletePhoto(photo.id) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onAttachPhoto) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Photo")
                }
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ItemEditDialog(
    initialItem: ItemEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: Int, category: String, notes: String) -> Unit
) {
    var name by remember(initialItem) { mutableStateOf(initialItem?.name ?: "") }
    var quantityText by remember(initialItem) {
        mutableStateOf((initialItem?.quantity ?: 1).toString())
    }
    var category by remember(initialItem) { mutableStateOf(initialItem?.category ?: "General") }
    var notes by remember(initialItem) { mutableStateOf(initialItem?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialItem == null) "Add Item to Box" else "Edit Item")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { input ->
                        quantityText = input.filter { it.isDigit() }
                    },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    onConfirm(name, qty, category, notes)
                },
                enabled = name.trim().isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
