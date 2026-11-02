package com.boxvault.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boxvault.app.R
import com.boxvault.app.data.storage.PhotoStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface LocalPhotoLoadState {
    data object Loading : LocalPhotoLoadState
    data class Loaded(val bitmap: ImageBitmap) : LocalPhotoLoadState
    data object Error : LocalPhotoLoadState
}

/**
 * 100% offline local photo thumbnail loader.
 * Reads photo bytes strictly from [PhotoStorageManager] on [Dispatchers.IO] and decodes
 * downsampled bitmaps via [BitmapFactory] without any external network libraries.
 */
@Composable
fun LocalPhotoThumbnail(
    relativePath: String,
    photoStorageManager: PhotoStorageManager,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    contentDescription: String? = stringResource(R.string.photo_attached_cd),
    onDeleteClick: (() -> Unit)? = null
) {
    val loadState by produceState<LocalPhotoLoadState>(
        initialValue = LocalPhotoLoadState.Loading,
        key1 = relativePath,
        key2 = photoStorageManager
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = photoStorageManager.readPhotoBytes(relativePath)
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                var sampleSize = 1
                val maxDimension = 512
                while (
                    boundsOptions.outWidth / sampleSize > maxDimension ||
                    boundsOptions.outHeight / sampleSize > maxDimension
                ) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                if (decoded != null) {
                    LocalPhotoLoadState.Loaded(decoded.asImageBitmap())
                } else {
                    LocalPhotoLoadState.Error
                }
            }.getOrElse {
                LocalPhotoLoadState.Error
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when (val state = loadState) {
            is LocalPhotoLoadState.Loading -> {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            is LocalPhotoLoadState.Loaded -> {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is LocalPhotoLoadState.Error -> {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.photo_missing),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (onDeleteClick != null) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.photo_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
