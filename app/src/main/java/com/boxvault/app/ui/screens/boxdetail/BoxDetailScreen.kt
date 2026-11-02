package com.boxvault.app.ui.screens.boxdetail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalHapticFeedback
import com.boxvault.app.data.preferences.UserPreferencesManager
import com.boxvault.app.util.sensory.SensoryFeedbackManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boxvault.app.R
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.ui.components.findActivity
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.local.model.ItemWithPhotos
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.ui.components.LocalPhotoThumbnail
import com.boxvault.app.ui.components.PhotoSlotCard
import com.boxvault.app.ui.components.PhotoSourceSelectionDialog
import com.boxvault.app.ui.theme.parseBoxColor
import com.boxvault.app.util.qr.QrCodeGenerator
import java.io.ByteArrayOutputStream

private const val DRAFT_ITEM_PHOTO_TARGET = "__DRAFT_NEW_ITEM_PHOTO__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxDetailScreen(
    viewModel: BoxDetailViewModel,
    photoStorageManager: PhotoStorageManager,
    onNavigateBack: () -> Unit,
    onNavigateToEditBox: (String) -> Unit,
    onNavigateToBulkEntry: (String) -> Unit,
    onNavigateToPrintQr: (String) -> Unit,
    sensoryFeedbackManager: SensoryFeedbackManager? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sensoryManager = remember(sensoryFeedbackManager, context) {
        sensoryFeedbackManager ?: SensoryFeedbackManager(context, UserPreferencesManager(context))
    }

    var showAddOrEditItemDialog by remember { mutableStateOf(false) }
    var itemBeingEdited by remember { mutableStateOf<ItemEntity?>(null) }
    var showDeleteBoxConfirm by remember { mutableStateOf(false) }
    var showQrPreviewDialog by remember { mutableStateOf(false) }
    var pendingPhotoTargetItemId by remember { mutableStateOf<String?>(null) }

    // Draft photo state for ItemEditDialog (when adding a brand-new item)
    var draftItemPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var draftItemPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Unified Photo Source Selection Dialog state
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    val defaultPhotoSourceTitle = stringResource(R.string.photo_source_dialog_title)
    var photoSourceTitle by remember { mutableStateOf("") }
    var photoSourceTargetItemId by remember { mutableStateOf<String?>(null) }
    var photoSourceHasExistingPhoto by remember { mutableStateOf(false) }
    var photoSourceOnRemovePhoto by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showCameraRationaleDialog by remember { mutableStateOf(false) }

    // Camera capture launcher (100% local photo -> background IO processing in ViewModel)
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            if (pendingPhotoTargetItemId == DRAFT_ITEM_PHOTO_TARGET) {
                draftItemPhotoBitmap = bitmap
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                draftItemPhotoBytes = stream.toByteArray()
            } else {
                viewModel.attachPhotoFromBitmap(bitmap, pendingPhotoTargetItemId)
            }
        }
        pendingPhotoTargetItemId = null
    }

    // Local Gallery file picker launcher (100% local URI -> background IO processing in ViewModel)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (pendingPhotoTargetItemId == DRAFT_ITEM_PHOTO_TARGET) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        draftItemPhotoBytes = bytes
                        draftItemPhotoBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            } else {
                viewModel.attachPhotoFromUri(context.contentResolver, uri, pendingPhotoTargetItemId)
            }
        }
        pendingPhotoTargetItemId = null
    }

    val activity = remember(context) { context.findActivity() }
    var isRequestingCameraPermission by remember { mutableStateOf(false) }
    var hasRequestedCameraOnce by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isRequestingCameraPermission = false
        hasRequestedCameraOnce = true
        if (isGranted) {
            showCameraRationaleDialog = false
            takePicturePreviewLauncher.launch(null)
        } else {
            showCameraRationaleDialog = true
        }
    }

    val isCameraPermanentlyDenied = remember(showCameraRationaleDialog, hasRequestedCameraOnce) {
        val notGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED
        val cannotShowRationale = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.CAMERA
        )
        notGranted && (cannotShowRationale && hasRequestedCameraOnce)
    }

    val openAppSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isNowGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (isNowGranted && showCameraRationaleDialog) {
                    showCameraRationaleDialog = false
                    takePicturePreviewLauncher.launch(null)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launchCameraWithPermissionCheck: (String?) -> Unit = { targetItemId ->
        pendingPhotoTargetItemId = targetItemId
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            takePicturePreviewLauncher.launch(null)
        } else if (!isRequestingCameraPermission) {
            isRequestingCameraPermission = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val openBoxPhotoSelection = {
        photoSourceTargetItemId = null
        photoSourceTitle = context.getString(R.string.box_detail_select_box_photo)
        val firstPhoto = uiState.boxLevelPhotos.firstOrNull()
        photoSourceHasExistingPhoto = firstPhoto != null
        photoSourceOnRemovePhoto = firstPhoto?.let { p -> { viewModel.deletePhoto(p.id) } }
        showPhotoSourceDialog = true
    }

    val openItemPhotoSelection: (ItemWithPhotos) -> Unit = { itemWithPhotos ->
        val firstPhoto = itemWithPhotos.photos.firstOrNull()
        photoSourceTargetItemId = itemWithPhotos.item.id
        photoSourceTitle = context.getString(R.string.box_detail_select_photo_for_item, itemWithPhotos.item.name)
        photoSourceHasExistingPhoto = firstPhoto != null
        photoSourceOnRemovePhoto = firstPhoto?.let { p -> { viewModel.deletePhoto(p.id) } }
        showPhotoSourceDialog = true
    }

    val openItemEditPhotoSelection = {
        if (itemBeingEdited != null) {
            val existingPhotos = uiState.itemsWithPhotos
                .firstOrNull { it.item.id == itemBeingEdited?.id }
                ?.photos.orEmpty()
            val firstPhoto = existingPhotos.firstOrNull()
            photoSourceTargetItemId = itemBeingEdited?.id
            photoSourceTitle = context.getString(R.string.box_detail_item_photo)
            photoSourceHasExistingPhoto = firstPhoto != null || draftItemPhotoBytes != null
            photoSourceOnRemovePhoto = {
                draftItemPhotoBitmap = null
                draftItemPhotoBytes = null
                firstPhoto?.let { viewModel.deletePhoto(it.id) }
            }
            showPhotoSourceDialog = true
        } else {
            photoSourceTargetItemId = DRAFT_ITEM_PHOTO_TARGET
            photoSourceTitle = context.getString(R.string.box_detail_item_photo)
            photoSourceHasExistingPhoto = draftItemPhotoBytes != null
            photoSourceOnRemovePhoto = {
                draftItemPhotoBitmap = null
                draftItemPhotoBytes = null
            }
            showPhotoSourceDialog = true
        }
    }

    val box = uiState.box

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = box?.shortCode ?: stringResource(R.string.box_detail_title_fallback),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = box?.name ?: stringResource(R.string.box_detail_loading),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (box != null) {
                        IconButton(onClick = { showQrPreviewDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = stringResource(R.string.box_detail_view_qr)
                            )
                        }
                        IconButton(onClick = { onNavigateToPrintQr(box.id) }) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = stringResource(R.string.box_detail_print_qr)
                            )
                        }
                        IconButton(onClick = { onNavigateToEditBox(box.id) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.box_detail_edit_box)
                            )
                        }
                        IconButton(onClick = { showDeleteBoxConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.box_detail_delete_box),
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
                    text = stringResource(R.string.box_detail_not_found),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (box != null) {
                // Box Metadata & QR Payload Header Card with prominent Photo Slot
                item {
                    BoxHeaderCard(
                        box = box,
                        primaryPhoto = uiState.boxLevelPhotos.firstOrNull(),
                        photoStorageManager = photoStorageManager,
                        distinctItemCount = uiState.distinctItemCount,
                        totalQuantity = uiState.totalQuantity,
                        photoCount = uiState.boxWithDetails?.photos?.size ?: 0,
                        onPhotoSlotClick = openBoxPhotoSelection,
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
                        onCapturePhoto = { launchCameraWithPermissionCheck(null) },
                        onPickPhoto = {
                            pendingPhotoTargetItemId = null
                            pickImageLauncher.launch("image/*")
                        },
                        onDeletePhoto = { photoId ->
                                sensoryManager.onDeleteAction(haptic)
                                viewModel.deletePhoto(photoId)
                            }
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
                                text = stringResource(R.string.box_detail_items_in_box_header, uiState.totalQuantity),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            AssistChip(
                                onClick = {
                                    itemBeingEdited = null
                                    draftItemPhotoBitmap = null
                                    draftItemPhotoBytes = null
                                    showAddOrEditItemDialog = true
                                },
                                label = { Text(stringResource(R.string.box_detail_add_single_item)) },
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
                                placeholder = { Text(stringResource(R.string.box_detail_filter_items_placeholder, box.shortCode)) },
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
                                        stringResource(R.string.box_detail_empty_box)
                                    } else {
                                        stringResource(R.string.box_detail_no_items_match, uiState.itemFilterQuery)
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (uiState.itemsWithPhotos.isEmpty()) {
                                    Button(onClick = { onNavigateToBulkEntry(box.id) }) {
                                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.box_detail_start_fast_bulk))
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
                            onIncrement = {
                                sensoryManager.onQuantityStep(haptic)
                                viewModel.incrementItemQuantity(itemWithPhotos.item)
                            },
                            onDecrement = {
                                sensoryManager.onQuantityStep(haptic)
                                viewModel.decrementItemQuantity(itemWithPhotos.item)
                            },
                            onEdit = {
                                itemBeingEdited = itemWithPhotos.item
                                draftItemPhotoBitmap = null
                                draftItemPhotoBytes = null
                                showAddOrEditItemDialog = true
                            },
                            onDelete = {
                                sensoryManager.onDeleteAction(haptic)
                                viewModel.deleteItem(itemWithPhotos.item.id)
                            },
                            onPhotoSlotClick = { openItemPhotoSelection(itemWithPhotos) },
                            onDeletePhoto = { photoId ->
                                sensoryManager.onDeleteAction(haptic)
                                viewModel.deletePhoto(photoId)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddOrEditItemDialog) {
        val existingPhotos = if (itemBeingEdited != null) {
            uiState.itemsWithPhotos
                .firstOrNull { it.item.id == itemBeingEdited?.id }
                ?.photos.orEmpty()
        } else {
            emptyList()
        }

        ItemEditDialog(
            initialItem = itemBeingEdited,
            existingPhotos = existingPhotos,
            photoStorageManager = photoStorageManager,
            draftBitmap = draftItemPhotoBitmap,
            draftPhotoBytes = draftItemPhotoBytes,
            onDismiss = {
                showAddOrEditItemDialog = false
                itemBeingEdited = null
                draftItemPhotoBitmap = null
                draftItemPhotoBytes = null
            },
            onPhotoSlotClick = openItemEditPhotoSelection,
            onConfirm = { name, qty, category, notes, draftBytes ->
                sensoryManager.onBulkEntrySubmit(haptic)
                viewModel.saveItem(
                    name = name,
                    quantity = qty,
                    category = category,
                    notes = notes,
                    existingItemId = itemBeingEdited?.id,
                    draftPhotoBytes = draftBytes
                )
                showAddOrEditItemDialog = false
                itemBeingEdited = null
                draftItemPhotoBitmap = null
                draftItemPhotoBytes = null
            }
        )
    }

    if (showPhotoSourceDialog) {
        PhotoSourceSelectionDialog(
            title = if (photoSourceTitle.isBlank()) defaultPhotoSourceTitle else photoSourceTitle,
            hasExistingPhoto = photoSourceHasExistingPhoto,
            onDismiss = { showPhotoSourceDialog = false },
            onTakePhoto = {
                pendingPhotoTargetItemId = photoSourceTargetItemId
                takePicturePreviewLauncher.launch(null)
            },
            onChooseFromGallery = {
                pendingPhotoTargetItemId = photoSourceTargetItemId
                pickImageLauncher.launch("image/*")
            },
            onRemovePhoto = photoSourceOnRemovePhoto
        )
    }

    if (showCameraRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showCameraRationaleDialog = false },
            title = {
                Text(
                    text = if (isCameraPermanentlyDenied) {
                        stringResource(R.string.photo_camera_perm_dialog_title)
                    } else {
                        stringResource(R.string.box_detail_camera_perm_title)
                    }
                )
            },
            text = {
                Text(
                    text = if (isCameraPermanentlyDenied) {
                        stringResource(R.string.photo_camera_perm_settings_desc)
                    } else {
                        stringResource(R.string.box_detail_camera_perm_desc)
                    }
                )
            },
            confirmButton = {
                if (isCameraPermanentlyDenied) {
                    Button(
                        onClick = {
                            showCameraRationaleDialog = false
                            openAppSettings()
                        }
                    ) {
                        Text(stringResource(R.string.photo_camera_perm_open_settings))
                    }
                } else {
                    Button(
                        onClick = {
                            showCameraRationaleDialog = false
                            pendingPhotoTargetItemId = photoSourceTargetItemId
                            pickImageLauncher.launch("image/*")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.box_detail_camera_perm_gallery))
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isCameraPermanentlyDenied) {
                        TextButton(
                            onClick = {
                                showCameraRationaleDialog = false
                                pendingPhotoTargetItemId = photoSourceTargetItemId
                                pickImageLauncher.launch("image/*")
                            }
                        ) {
                            Text(stringResource(R.string.photo_camera_perm_gallery))
                        }
                    }
                    TextButton(onClick = { showCameraRationaleDialog = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
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
            title = { Text(stringResource(R.string.box_detail_delete_box_title, box.shortCode, box.name)) },
            text = {
                Text(
                    stringResource(R.string.box_detail_delete_box_message, uiState.distinctItemCount)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteBoxConfirm = false
                        sensoryManager.onDeleteAction(haptic)
                        viewModel.deleteBox(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.box_detail_delete_box_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBoxConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun BoxHeaderCard(
    box: BoxEntity,
    primaryPhoto: PhotoEntity?,
    photoStorageManager: PhotoStorageManager,
    distinctItemCount: Int,
    totalQuantity: Int,
    photoCount: Int,
    onPhotoSlotClick: () -> Unit,
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

            // Dedicated prominent Photo Slot + Box Title & Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                PhotoSlotCard(
                    photoRelativePath = primaryPhoto?.relativePath,
                    photoStorageManager = photoStorageManager,
                    label = if (primaryPhoto == null) stringResource(R.string.box_detail_add_box_photo) else box.name,
                    size = 80.dp,
                    onClick = onPhotoSlotClick
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                            text = stringResource(R.string.box_detail_total_qty_unique, totalQuantity, distinctItemCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                            contentDescription = stringResource(R.string.box_detail_qr_tap_enlarge_cd),
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
                            text = stringResource(R.string.box_detail_qr_tap_enlarge),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.box_detail_qr_code_for, box.shortCode),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.box_detail_qr_permanent_info, box.shortCode),
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
                Text(stringResource(R.string.box_detail_print_on_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
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
                    text = stringResource(R.string.bulk_entry_fast_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.bulk_entry_fast_mode_desc, boxShortCode),
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
                Text(stringResource(R.string.bulk_entry_bulk_add_btn))
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
                text = stringResource(R.string.box_detail_box_photos_header, photos.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onCapturePhoto,
                    label = { Text(stringResource(R.string.box_detail_photo_camera)) },
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
                    label = { Text(stringResource(R.string.box_detail_photo_gallery)) },
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
    onPhotoSlotClick: () -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Item photo slot thumbnail or placeholder
                PhotoSlotCard(
                    photoRelativePath = itemWithPhotos.photos.firstOrNull()?.relativePath,
                    photoStorageManager = photoStorageManager,
                    label = item.name,
                    size = 60.dp,
                    onClick = onPhotoSlotClick
                )

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
                            contentDescription = stringResource(R.string.box_detail_decrease_qty),
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
                            contentDescription = stringResource(R.string.box_detail_increase_qty),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (itemWithPhotos.photos.size > 1) {
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
                            size = 56.dp,
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
                TextButton(onClick = onPhotoSlotClick) {
                    Icon(
                        imageVector = if (itemWithPhotos.photos.isNotEmpty()) Icons.Default.Image else Icons.Default.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (itemWithPhotos.photos.isNotEmpty()) {
                            stringResource(R.string.box_detail_item_photo_count, itemWithPhotos.photos.size)
                        } else {
                            stringResource(R.string.box_detail_item_photo_btn)
                        }
                    )
                }
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.common_edit))
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ItemEditDialog(
    initialItem: ItemEntity?,
    existingPhotos: List<PhotoEntity>,
    photoStorageManager: PhotoStorageManager,
    draftBitmap: Bitmap?,
    draftPhotoBytes: ByteArray?,
    onDismiss: () -> Unit,
    onPhotoSlotClick: () -> Unit,
    onConfirm: (name: String, quantity: Int, category: String, notes: String, draftPhotoBytes: ByteArray?) -> Unit
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
            Text(if (initialItem == null) stringResource(R.string.box_detail_add_item_title) else stringResource(R.string.box_detail_edit_item_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dedicated prominent Photo Slot for Item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    PhotoSlotCard(
                        photoRelativePath = existingPhotos.firstOrNull()?.relativePath,
                        photoStorageManager = photoStorageManager,
                        draftBitmap = draftBitmap,
                        size = 84.dp,
                        label = stringResource(R.string.box_detail_item_photo),
                        onClick = onPhotoSlotClick
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.box_detail_item_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { input ->
                        quantityText = input.filter { it.isDigit() }
                    },
                    label = { Text(stringResource(R.string.box_detail_item_qty_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.box_detail_item_cat_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.box_detail_item_notes_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    onConfirm(name, qty, category, notes, draftPhotoBytes)
                },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
