package com.boxvault.app.ui.screens.pdfgenerator

import android.content.Context
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.R
import com.boxvault.app.util.pdf.BoxVaultPrintDocumentAdapter
import com.boxvault.app.util.pdf.PdfPageSize
import com.boxvault.app.util.pdf.QrGridPreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun QrGridPreset.titleRes(): Int = when (this) {
    QrGridPreset.LARGE_2X4 -> R.string.pdf_preset_large_title
    QrGridPreset.MEDIUM_3X7 -> R.string.pdf_preset_medium_title
    QrGridPreset.SMALL_4X10 -> R.string.pdf_preset_small_title
}

private fun QrGridPreset.subtitleRes(): Int = when (this) {
    QrGridPreset.LARGE_2X4 -> R.string.pdf_preset_large_desc
    QrGridPreset.MEDIUM_3X7 -> R.string.pdf_preset_medium_desc
    QrGridPreset.SMALL_4X10 -> R.string.pdf_preset_small_desc
}

private fun PdfPageSize.displayNameRes(): Int = when (this) {
    PdfPageSize.A4 -> R.string.pdf_page_size_a4
    PdfPageSize.US_LETTER -> R.string.pdf_page_size_us_letter
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfGeneratorScreen(
    viewModel: PdfGeneratorViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val createPdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val stream = runCatching {
                context.contentResolver.openOutputStream(uri)
            }.getOrNull()
            if (stream != null) {
                viewModel.exportPdfToStream(stream)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.pdf_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.pdf_subtitle,
                                uiState.activeLabelSpecs.size,
                                uiState.estimatedPageCount,
                                uiState.pageSize.name
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    IconButton(
                        onClick = {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            val adapter = BoxVaultPrintDocumentAdapter(
                                labels = uiState.activeLabelSpecs,
                                preset = uiState.gridPreset,
                                pageSize = uiState.pageSize,
                                drawCutGuides = uiState.drawCutGuides,
                                jobName = context.getString(R.string.pdf_print_job_title)
                            )
                            printManager.print(
                                context.getString(R.string.pdf_print_job_title),
                                adapter,
                                null
                            )
                        },
                        enabled = uiState.activeLabelSpecs.isNotEmpty() && !uiState.isExporting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = stringResource(R.string.pdf_direct_print_btn)
                        )
                    }
                    IconButton(
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            createPdfDocumentLauncher.launch("boxvault_qr_labels_$timestamp.pdf")
                        },
                        enabled = uiState.activeLabelSpecs.isNotEmpty() && !uiState.isExporting
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = stringResource(R.string.pdf_save_btn)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.statusMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.statusMessage ?: "",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                item {
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
            }

            // 1. Source Mode Selector (Existing Boxes vs Pre-Allocated Blank Sticker Sheet)
            item {
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
                        Text(
                            text = stringResource(R.string.pdf_section_mode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = uiState.sourceMode == PdfLabelSourceMode.EXISTING_BOXES,
                                onClick = { viewModel.onSourceModeChange(PdfLabelSourceMode.EXISTING_BOXES) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.pdf_mode_existing_boxes, uiState.allBoxes.size),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = uiState.sourceMode == PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS,
                                onClick = { viewModel.onSourceModeChange(PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.pdf_mode_blank_stickers),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = if (uiState.sourceMode == PdfLabelSourceMode.EXISTING_BOXES) {
                                stringResource(R.string.pdf_mode_existing_desc)
                            } else {
                                stringResource(R.string.pdf_mode_blank_desc)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Grid Preset & Page Size Configuration
            item {
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
                        Text(
                            text = stringResource(R.string.pdf_section_layout),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        QrGridPreset.entries.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onGridPresetChange(preset) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.gridPreset == preset,
                                    onClick = { viewModel.onGridPresetChange(preset) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(preset.titleRes()),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(preset.subtitleRes()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_paper_size_label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfPageSize.entries.forEach { pageSize ->
                                    FilterChip(
                                        selected = uiState.pageSize == pageSize,
                                        onClick = { viewModel.onPageSizeChange(pageSize) },
                                        label = {
                                            Text(
                                                text = stringResource(pageSize.displayNameRes()),
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.pdf_cut_guides_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.pdf_cut_guides_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.drawCutGuides,
                                onCheckedChange = viewModel::onDrawCutGuidesChange
                            )
                        }
                    }
                }
            }

            // 3. Mode-Specific Content (Box Selection OR Pre-Allocated Count)
            if (uiState.sourceMode == PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_section_blank_qty),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                AssistChip(
                                    onClick = viewModel::regenerateBlankLabelUuids,
                                    label = {
                                        Text(
                                            text = stringResource(R.string.pdf_new_uuids_btn),
                                            maxLines = 1
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }

                            val perPage = uiState.gridPreset.labelsPerPage
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(perPage, perPage * 2, perPage * 3).forEach { countOption ->
                                    val pages = countOption / perPage
                                    FilterChip(
                                        selected = uiState.blankLabelCount == countOption,
                                        onClick = { viewModel.onBlankLabelCountChange(countOption) },
                                        label = {
                                            Text(
                                                text = stringResource(R.string.pdf_blank_count_option, countOption, pages),
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            val firstCode = uiState.preallocatedBlankSpecs.firstOrNull()?.shortCode ?: "BOX-001"
                            val lastCode = uiState.preallocatedBlankSpecs.lastOrNull()?.shortCode ?: firstCode
                            Text(
                                text = stringResource(
                                    R.string.pdf_blank_range_desc,
                                    firstCode,
                                    lastCode,
                                    uiState.preallocatedBlankSpecs.size
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.pdf_section_select_boxes,
                                uiState.selectedBoxIds.size,
                                uiState.allBoxes.size
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = viewModel::selectAllBoxes) {
                                Icon(
                                    imageVector = Icons.Default.CheckBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.pdf_select_all))
                            }
                            TextButton(onClick = viewModel::clearBoxSelection) {
                                Icon(
                                    imageVector = Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.pdf_select_none))
                            }
                        }
                    }
                }

                if (uiState.allBoxes.isEmpty()) {
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
                                    text = stringResource(R.string.pdf_no_boxes),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedButton(
                                    onClick = {
                                        viewModel.onSourceModeChange(PdfLabelSourceMode.PRE_ALLOCATED_BLANK_LABELS)
                                    }
                                ) {
                                    Text(
                                        text = stringResource(R.string.pdf_switch_to_blank),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.allBoxes,
                        key = { it.id }
                    ) { box ->
                        val isSelected = box.id in uiState.selectedBoxIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleBoxSelection(box.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleBoxSelection(box.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${box.shortCode} • ${box.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = box.location.ifBlank { stringResource(R.string.pdf_no_location) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            val adapter = BoxVaultPrintDocumentAdapter(
                                labels = uiState.activeLabelSpecs,
                                preset = uiState.gridPreset,
                                pageSize = uiState.pageSize,
                                drawCutGuides = uiState.drawCutGuides,
                                jobName = context.getString(R.string.pdf_print_job_title)
                            )
                            printManager.print(
                                context.getString(R.string.pdf_print_job_title),
                                adapter,
                                null
                            )
                        },
                        enabled = uiState.activeLabelSpecs.isNotEmpty() && !uiState.isExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pdf_direct_print_btn),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            createPdfDocumentLauncher.launch("boxvault_qr_labels_$timestamp.pdf")
                        },
                        enabled = uiState.activeLabelSpecs.isNotEmpty() && !uiState.isExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pdf_export_btn, uiState.activeLabelSpecs.size),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
