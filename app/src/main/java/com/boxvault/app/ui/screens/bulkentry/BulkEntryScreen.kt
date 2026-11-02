package com.boxvault.app.ui.screens.bulkentry

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.boxvault.app.data.preferences.UserPreferencesManager
import com.boxvault.app.util.sensory.SensoryFeedbackManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.boxvault.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.data.local.entity.ItemEntity

private val QuickQuantityPresets = listOf(1, 2, 5, 10)
private val BulkQuickCategories = listOf(
    "General",
    "Hardware",
    "Tools",
    "Cables",
    "Decor",
    "Winter",
    "Safety",
    "Office"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkEntryScreen(
    viewModel: BulkEntryViewModel,
    onNavigateBack: () -> Unit,
    sensoryFeedbackManager: SensoryFeedbackManager? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sensoryManager = remember(sensoryFeedbackManager, context) {
        sensoryFeedbackManager ?: SensoryFeedbackManager(context, UserPreferencesManager(context))
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val triggerSubmitAndMaintainFocus = {
        sensoryManager.onBulkEntrySubmit(haptic)
        viewModel.submitCurrentItem()
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    // Lock focus and open keyboard immediately upon entering Bulk Entry Mode
    // and re-assert focus on every rapid item submission so keyboard never closes.
    LaunchedEffect(uiState.submissionCount) {
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.bulk_entry_title, uiState.box?.shortCode ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = stringResource(R.string.bulk_entry_subtitle, uiState.box?.name ?: "Box", uiState.totalBoxQuantity),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_done)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.common_done))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Persistent Rapid Entry Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Primary Always-Focused Item Input + Inline Add Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.itemNameInput,
                            onValueChange = viewModel::onItemNameChange,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            label = { Text(stringResource(R.string.bulk_entry_input_label)) },
                            placeholder = { Text(stringResource(R.string.bulk_entry_input_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { triggerSubmitAndMaintainFocus() },
                                onDone = { triggerSubmitAndMaintainFocus() }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = { triggerSubmitAndMaintainFocus() },
                            enabled = uiState.itemNameInput.trim().isNotEmpty(),
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.bulk_entry_add_item_desc)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.bulk_entry_add_btn))
                        }
                    }

                    // Quick Quantity Selector Row (keeps keyboard open)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.bulk_entry_qty_label),
                                style = MaterialTheme.typography.labelLarge
                            )
                            FilledIconButton(
                                onClick = {
                                    sensoryManager.onQuantityStep(haptic)
                                    viewModel.decrementDraftQuantity()
                                    runCatching { focusRequester.requestFocus() }
                                },
                                enabled = uiState.quantityInput > 1,
                                modifier = Modifier.size(30.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = stringResource(R.string.box_detail_decrease_qty),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = uiState.quantityInput.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            FilledIconButton(
                                onClick = {
                                    sensoryManager.onQuantityStep(haptic)
                                    viewModel.incrementDraftQuantity()
                                    runCatching { focusRequester.requestFocus() }
                                },
                                modifier = Modifier.size(30.dp),
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

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(QuickQuantityPresets) { preset ->
                                FilterChip(
                                    selected = uiState.quantityInput == preset,
                                    onClick = {
                                        sensoryManager.onQuantityStep(haptic)
                                        viewModel.onQuantityChange(preset)
                                        runCatching { focusRequester.requestFocus() }
                                    },
                                    label = { Text("×$preset") }
                                )
                            }
                        }
                    }

                    // Sticky Category Chips + Optional Notes Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(BulkQuickCategories) { category ->
                                FilterChip(
                                    selected = uiState.categoryInput.equals(category, ignoreCase = true),
                                    onClick = {
                                        viewModel.onCategoryChange(category)
                                        runCatching { focusRequester.requestFocus() }
                                    },
                                    label = { Text(category) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        AssistChip(
                            onClick = { viewModel.toggleNotesField() },
                            label = { Text(stringResource(R.string.bulk_entry_note_chip)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    if (uiState.showNotesField) {
                        OutlinedTextField(
                            value = uiState.notesInput,
                            onValueChange = viewModel::onNotesChange,
                            label = { Text(stringResource(R.string.bulk_entry_note_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { triggerSubmitAndMaintainFocus() }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Live "Recently Added in This Session" Feed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bulk_entry_session_header, uiState.sessionAddedItems.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (uiState.lastAddedItemName != null) {
                    Text(
                        text = stringResource(R.string.bulk_entry_last_saved, uiState.lastAddedItemName ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.sessionAddedItems.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.bulk_entry_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(
                        items = uiState.sessionAddedItems,
                        key = { it.id }
                    ) { sessionItem ->
                        SessionAddedItemRow(
                            item = sessionItem,
                            modifier = Modifier.animateItem(),
                            onBumpQuantity = {
                                sensoryManager.onQuantityStep(haptic)
                                viewModel.bumpSessionItemQuantity(sessionItem.id)
                                runCatching { focusRequester.requestFocus() }
                            },
                            onUndo = {
                                sensoryManager.onDeleteAction(haptic)
                                viewModel.undoSessionItem(sessionItem.id)
                                runCatching { focusRequester.requestFocus() }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionAddedItemRow(
    item: ItemEntity,
    modifier: Modifier = Modifier,
    onBumpQuantity: () -> Unit,
    onUndo: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.bulk_entry_item_qty_cat, item.quantity, item.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = onBumpQuantity,
                    label = { Text(stringResource(R.string.bulk_entry_bump_qty)) }
                )
                TextButton(onClick = onUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.bulk_entry_undo_desc),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.bulk_entry_undo_btn), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
