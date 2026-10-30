package com.boxvault.app.ui.screens.bulkentry

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.data.local.entity.ItemEntity

private val BulkQuickCategories = listOf(
    "General",
    "Tools",
    "Hardware",
    "Electronics",
    "Kitchen",
    "Winter Gear",
    "Holiday",
    "Camping"
)

private val QuickQuantityPresets = listOf(1, 2, 3, 4, 5, 10)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkEntryScreen(
    viewModel: BulkEntryViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val triggerSubmitAndMaintainFocus = {
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
        topBar = {
            TopAppBar(
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
                                text = "Fast Bulk Entry • ${uiState.box?.shortCode ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "${uiState.box?.name ?: "Box"} (${uiState.totalBoxQuantity} items total)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Done"
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
                        Text("Done")
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
                            label = { Text("Type item name & press Next / Enter") },
                            placeholder = { Text("e.g., Cordless Impact Driver") },
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
                                contentDescription = "Add Item"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
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
                                text = "Qty:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            FilledIconButton(
                                onClick = {
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
                                    contentDescription = "Decrease quantity",
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
                                    contentDescription = "Increase quantity",
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
                            label = { Text("Note") },
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
                            label = { Text("Optional item note (resets after each add)") },
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
                    text = "ADDED THIS SESSION (${uiState.sessionAddedItems.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (uiState.lastAddedItemName != null) {
                    Text(
                        text = "Last saved: ${uiState.lastAddedItemName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                                text = "Type an item name above and hit Next/Enter on your keyboard. The keyboard stays open so you can add dozens of items in seconds.",
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
                            onBumpQuantity = {
                                viewModel.bumpSessionItemQuantity(sessionItem.id)
                                runCatching { focusRequester.requestFocus() }
                            },
                            onUndo = {
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
    onBumpQuantity: () -> Unit,
    onUndo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = "Qty: ${item.quantity} • ${item.category}",
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
                    label = { Text("+1") }
                )
                TextButton(onClick = onUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo item",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
