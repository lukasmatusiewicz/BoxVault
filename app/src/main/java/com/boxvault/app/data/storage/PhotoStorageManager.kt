package com.boxvault.app.data.storage

import android.content.Context
import com.boxvault.app.util.image.PhotoCompressor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Manages strictly local, sandboxed photo files inside `context.filesDir/photos/`.
 * Never writes to shared external storage and enforces canonical path validation
 * to prevent path traversal outside the app-private `photos` directory.
 */
class PhotoStorageManager(
    baseFilesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(context.filesDir, ioDispatcher)

    val photosDirectory: File = File(baseFilesDir, PHOTOS_DIR_NAME).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    /**
     * Resolves a relative path inside the `photos` directory and strictly verifies
     * that the resulting canonical path stays inside [photosDirectory].
     * Maintains full backward compatibility for resolving existing `.jpg` files if requested
     * with `.webp` and vice versa.
     *
     * @throws SecurityException if [relativePath] is blank or attempts path traversal.
     */
    fun resolvePhotoFile(relativePath: String): File {
        val sanitizedInput = relativePath.trim()
        if (sanitizedInput.isEmpty()) {
            throw SecurityException("Photo relative path must not be blank.")
        }
        if (sanitizedInput.startsWith("/") || sanitizedInput.startsWith("\\") || sanitizedInput.contains("..")) {
            throw SecurityException("Path traversal or absolute path rejected: $relativePath")
        }

        if (!photosDirectory.exists()) {
            photosDirectory.mkdirs()
        }

        val rootCanonical = photosDirectory.canonicalFile
        val rootPrefix = rootCanonical.path + File.separator
        val targetCanonical = resolveCanonicalChild(rootCanonical, sanitizedInput, relativePath)

        // Backward compatibility: If the file does not exist at targetCanonical,
        // check whether an existing legacy file exists with swapped extension (.jpg <-> .webp)
        if (!targetCanonical.exists()) {
            if (sanitizedInput.endsWith(".webp", ignoreCase = true)) {
                val legacyJpg = File(rootCanonical, sanitizedInput.substringBeforeLast('.') + ".jpg").canonicalFile
                if (legacyJpg.exists() && legacyJpg.path.startsWith(rootPrefix)) {
                    return legacyJpg
                }
                val legacyJpeg = File(rootCanonical, sanitizedInput.substringBeforeLast('.') + ".jpeg").canonicalFile
                if (legacyJpeg.exists() && legacyJpeg.path.startsWith(rootPrefix)) {
                    return legacyJpeg
                }
            } else if (sanitizedInput.endsWith(".jpg", ignoreCase = true) || sanitizedInput.endsWith(".jpeg", ignoreCase = true)) {
                val modernWebp = File(rootCanonical, sanitizedInput.substringBeforeLast('.') + ".webp").canonicalFile
                if (modernWebp.exists() && modernWebp.path.startsWith(rootPrefix)) {
                    return modernWebp
                }
            }
        }

        return targetCanonical
    }

    suspend fun savePhotoBytes(
        bytes: ByteArray,
        prefix: String = "photo",
        extension: String = DEFAULT_PHOTO_EXTENSION,
        compressIfBitmap: Boolean = true
    ): String = withContext(ioDispatcher) {
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "").ifEmpty { "photo" }
        val safeExtension = extension.replace(Regex("[^a-zA-Z0-9]"), "").ifEmpty { DEFAULT_PHOTO_EXTENSION }
        val fileName = "${safePrefix}_${UUID.randomUUID()}.$safeExtension"
        val destFile = resolvePhotoFile(fileName)
        destFile.parentFile?.mkdirs()
        val finalBytes = if (compressIfBitmap) {
            PhotoCompressor.compressAndNormalize(bytes)
        } else {
            bytes
        }
        destFile.writeBytes(finalBytes)
        fileName
    }

    suspend fun savePhotoStream(
        inputStream: InputStream,
        prefix: String = "photo",
        extension: String = DEFAULT_PHOTO_EXTENSION
    ): String = withContext(ioDispatcher) {
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "").ifEmpty { "photo" }
        val safeExtension = extension.replace(Regex("[^a-zA-Z0-9]"), "").ifEmpty { DEFAULT_PHOTO_EXTENSION }
        val fileName = "${safePrefix}_${UUID.randomUUID()}.$safeExtension"
        val destFile = resolvePhotoFile(fileName)
        destFile.parentFile?.mkdirs()
        destFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        fileName
    }

    suspend fun writePhotoBytesAtRelativePath(
        relativePath: String,
        bytes: ByteArray
    ): File = withContext(ioDispatcher) {
        val destFile = resolvePhotoFile(relativePath)
        destFile.parentFile?.mkdirs()
        destFile.writeBytes(bytes)
        destFile
    }

    suspend fun clearAllPhotoFiles(): Int = withContext(ioDispatcher) {
        if (!photosDirectory.exists()) return@withContext 0
        var deletedCount = 0
        photosDirectory.listFiles()?.forEach { file ->
            if (file.isFile && file.delete()) {
                deletedCount++
            }
        }
        deletedCount
    }

    suspend fun readPhotoBytes(relativePath: String): ByteArray = withContext(ioDispatcher) {
        val file = resolvePhotoFile(relativePath)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("Photo file does not exist: $relativePath")
        }
        file.readBytes()
    }

    fun photoExists(relativePath: String): Boolean {
        return try {
            val file = resolvePhotoFile(relativePath)
            file.exists() && file.isFile
        } catch (_: SecurityException) {
            false
        }
    }

    suspend fun deletePhotoFile(relativePath: String): Boolean = withContext(ioDispatcher) {
        val file = resolvePhotoFile(relativePath)
        if (file.exists() && file.isFile) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun deleteAllOrphanedFiles(activeRelativePaths: Set<String>): Int = withContext(ioDispatcher) {
        if (!photosDirectory.exists()) return@withContext 0
        val canonicalActiveFiles = activeRelativePaths.mapNotNull { path ->
            runCatching { resolvePhotoFile(path) }.getOrNull()
        }.toSet()

        var deletedCount = 0
        photosDirectory.listFiles()?.forEach { file ->
            if (file.isFile && file.canonicalFile !in canonicalActiveFiles) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        deletedCount
    }

    private fun resolveCanonicalChild(
        rootDir: File,
        sanitizedRelativePath: String,
        rawRelativePath: String = sanitizedRelativePath
    ): File {
        val rootCanonical = rootDir.canonicalFile
        val targetCanonical = File(rootCanonical, sanitizedRelativePath).canonicalFile
        val rootPrefix = rootCanonical.path + File.separator
        if (!targetCanonical.path.startsWith(rootPrefix)) {
            throw SecurityException("Resolved path escapes target directory: $rawRelativePath")
        }
        return targetCanonical
    }

    fun openPhotoInputStream(relativePath: String): InputStream {
        val file = resolvePhotoFile(relativePath)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("Photo file does not exist: $relativePath")
        }
        return file.inputStream().buffered()
    }

    /**
     * Atomically replaces all files in [photosDirectory] with [stagedPhotos] (`relativePath -> stagedFile`)
     * using a reversible directory swap inside [photosDirectory]'s parent directory.
     *
     * If copying staged files fails, [photosDirectory] is untouched.
     * If [onPhotosSwapped] (e.g., database replacement) throws, the previous [photosDirectory]
     * is restored from the rollback directory before rethrowing.
     */
    suspend fun replaceAllPhotosAtomically(
        stagedPhotos: List<Pair<String, File>>,
        onPhotosSwapped: suspend () -> Unit
    ) {
        val baseDir = photosDirectory.parentFile ?: photosDirectory
        val incomingDir = File(baseDir, INCOMING_PHOTOS_DIR_NAME)
        val rollbackDir = File(baseDir, ROLLBACK_PHOTOS_DIR_NAME)

        withContext(ioDispatcher) {
            if (incomingDir.exists()) incomingDir.deleteRecursively()
            if (rollbackDir.exists()) rollbackDir.deleteRecursively()
            incomingDir.mkdirs()

            stagedPhotos.forEach { (relativePath, stagedFile) ->
                // Validate relativePath with standard traversal rules first
                resolvePhotoFile(relativePath)
                val sanitized = relativePath.trim()
                val destFile = resolveCanonicalChild(incomingDir, sanitized, relativePath)
                destFile.parentFile?.mkdirs()
                stagedFile.inputStream().buffered().use { input ->
                    destFile.outputStream().buffered().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
            }

            if (photosDirectory.exists()) {
                moveDirectory(photosDirectory, rollbackDir)
            }
            moveDirectory(incomingDir, photosDirectory)
        }

        try {
            onPhotosSwapped()
            withContext(ioDispatcher) {
                if (rollbackDir.exists()) {
                    rollbackDir.deleteRecursively()
                }
            }
        } catch (t: Throwable) {
            withContext(ioDispatcher) {
                if (photosDirectory.exists()) {
                    photosDirectory.deleteRecursively()
                }
                if (rollbackDir.exists()) {
                    moveDirectory(rollbackDir, photosDirectory)
                }
            }
            throw t
        } finally {
            withContext(ioDispatcher) {
                if (incomingDir.exists()) {
                    incomingDir.deleteRecursively()
                }
            }
        }
    }

    private fun moveDirectory(source: File, target: File) {
        if (target.exists()) {
            target.deleteRecursively()
        }
        if (source.renameTo(target)) {
            return
        }
        // Fallback if renameTo fails (e.g. open file handles or platform quirk)
        source.copyRecursively(target, overwrite = true)
        source.deleteRecursively()
    }

    companion object {
        const val PHOTOS_DIR_NAME = "photos"
        const val INCOMING_PHOTOS_DIR_NAME = "photos_restore_incoming"
        const val ROLLBACK_PHOTOS_DIR_NAME = "photos_restore_rollback"
        const val DEFAULT_PHOTO_EXTENSION = "webp"
    }
}
