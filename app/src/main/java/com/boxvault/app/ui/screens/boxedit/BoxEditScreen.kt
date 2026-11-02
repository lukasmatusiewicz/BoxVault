package com.boxvault.app.ui.screens.boxedit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxvault.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.ui.theme.DefaultBoxColorOptions
import com.boxvault.app.ui.theme.parseBoxColor
import com.boxvault.app.util.qr.QrCodeGenerator

private val PresetBoxCategories = listOf(
    "General",
    "Tools & Hardware",
    "Winter Gear",
    "Holiday Decor",
    "Electronics",
    "Kitchen & Dining",
    "Documents",
    "Camping & Sports"
)

private val RoomColorPresets = listOf(
    "#3B82F6", // Blue
    "#10B981", // Emerald
    "#F59E0B", // Amber
    "#EF4444", // Red
    "#8B5CF6", // Purple
    "#EC4899", // Pink
    "#6366F1", // Indigo
    "#14B8A6"  // Teal
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxEditScreen(
    viewModel: BoxEditViewModel,
    onNavigateBack: () -> Unit,
    onBoxSaved: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateRoomDialog by remember { mutableStateOf(false) }

    val qrBitmap = remember(uiState.boxId) {
        runCatching {
            QrCodeGenerator.generateBoxQrBitmap(
                boxId = uiState.boxId,
                sizePx = 192
            )
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            uiState.isEditMode -> stringResource(R.string.box_edit_title_edit)
                            uiState.isPreAllocatedQrLabel -> stringResource(R.string.box_edit_title_assign_qr)
                            else -> stringResource(R.string.box_edit_title_new)
                        },
                        fontWeight = FontWeight.Bold
                    )
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
                    Button(
                        onClick = { viewModel.saveBox(onBoxSaved) },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.box_edit_save_box))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isPreAllocatedQrLabel) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.box_edit_preprinted_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.box_edit_preprinted_desc, uiState.boxId.take(8)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Immutable QR URI + Live ZXing QR Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.box_edit_qr_preview_cd),
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.box_edit_immutable_qr_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = uiState.qrPayloadPreview,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Short Code + Auto-Generate Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.shortCode,
                    onValueChange = viewModel::onShortCodeChange,
                    label = { Text(stringResource(R.string.box_edit_short_code_label)) },
                    placeholder = { Text(stringResource(R.string.box_edit_short_code_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = viewModel::regenerateShortCode,
                    label = { Text(stringResource(R.string.box_edit_auto_code)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.box_edit_auto_code_desc),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            // Box Name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.box_edit_name_label)) },
                placeholder = { Text(stringResource(R.string.box_edit_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Room Selector (with "+ New Room" quick inline action)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.box_edit_room_section),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { showCreateRoomDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.box_edit_new_room_btn))
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.roomId == null,
                            onClick = { viewModel.onRoomSelected(null) },
                            label = { Text(stringResource(R.string.box_edit_room_unassigned)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.MeetingRoom,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    items(uiState.availableRooms) { room ->
                        val roomColor = parseBoxColor(room.colorHex)
                        FilterChip(
                            selected = uiState.roomId == room.id,
                            onClick = { viewModel.onRoomSelected(room.id) },
                            label = { Text(room.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(roomColor)
                                )
                            }
                        )
                    }
                }
            }

            // Storage Location + Quick Location Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = uiState.location,
                    onValueChange = viewModel::onLocationChange,
                    label = { Text(stringResource(R.string.box_edit_location_label)) },
                    placeholder = { Text(stringResource(R.string.box_edit_location_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.existingLocations.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(uiState.existingLocations) { loc ->
                            FilterChip(
                                selected = uiState.location.equals(loc, ignoreCase = true),
                                onClick = { viewModel.onLocationChange(loc) },
                                label = { Text(loc) }
                            )
                        }
                    }
                }
            }

            // Category + Quick Category Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = uiState.category,
                    onValueChange = viewModel::onCategoryChange,
                    label = { Text(stringResource(R.string.box_edit_category_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(PresetBoxCategories) { cat ->
                        FilterChip(
                            selected = uiState.category.equals(cat, ignoreCase = true),
                            onClick = { viewModel.onCategoryChange(cat) },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            // Color Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.box_edit_color_tag),
                    style = MaterialTheme.typography.titleMedium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(DefaultBoxColorOptions) { option ->
                        val isSelected = uiState.colorHex.equals(option.hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                                .clickable { viewModel.onColorHexChange(option.hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = option.label,
                                    tint = Color.Black
                                )
                            }
                        }
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.box_edit_notes_label)) },
                placeholder = { Text(stringResource(R.string.box_edit_notes_placeholder)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.saveBox(onBoxSaved) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isEditMode) {
                        stringResource(R.string.box_edit_save_changes)
                    } else {
                        stringResource(R.string.box_edit_create_box)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (showCreateRoomDialog) {
        QuickCreateRoomDialog(
            onDismiss = { showCreateRoomDialog = false },
            onCreateRoom = { name, colorHex ->
                viewModel.createRoomInline(name, colorHex)
                showCreateRoomDialog = false
            }
        )
    }
}

@Composable
fun QuickCreateRoomDialog(
    initialName: String = "",
    initialColorHex: String = "#3B82F6",
    title: String? = null,
    confirmButtonText: String? = null,
    onDismiss: () -> Unit,
    onCreateRoom: (name: String, colorHex: String) -> Unit
) {
    var roomName by remember { mutableStateOf(initialName) }
    var selectedColorHex by remember { mutableStateOf(initialColorHex) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val resolvedTitle = title ?: stringResource(R.string.room_create_title_default)
    val resolvedConfirm = confirmButtonText ?: stringResource(R.string.room_create_btn_default)
    val requiredError = stringResource(R.string.room_create_error_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(resolvedTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = {
                        roomName = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.room_create_name_label)) },
                    placeholder = { Text(stringResource(R.string.room_create_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = stringResource(R.string.room_create_color_accent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(RoomColorPresets) { hex ->
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parseBoxColor(hex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = roomName.trim()
                    if (trimmed.isEmpty()) {
                        errorMessage = requiredError
                    } else {
                        onCreateRoom(trimmed, selectedColorHex)
                    }
                }
            ) {
                Text(resolvedConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
