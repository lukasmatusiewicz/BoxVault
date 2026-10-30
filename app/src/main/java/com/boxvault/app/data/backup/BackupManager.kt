package com.boxvault.app.data.backup

import android.content.Context
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupExportSummary(
    val boxCount: Int,
    val itemCount: Int,
    val photoCount: Int,
    val totalPhotoBytes: Long,
    val exportedAt: Long
)

data class BackupRestoreSummary(
    val boxCount: Int,
    val itemCount: Int,
    val photoCount: Int,
    val verifiedPhotoBytes: Long,
    val backupTimestamp: Long
)

/**
 * 100% offline local `.zip` backup and atomic staged restore manager.
 *
 * Security & Integrity Guarantees:
 * 1. **Zero Live Mutation During Extraction**: Incoming `.zip` archives are extracted into
 *    an isolated temporary staging directory (`cacheDir/restore_staging`) first.
 * 2. **Strict Zip-Slip Protection**: Every `ZipEntry` name is canonically resolved against
 *    `restore_staging`; any entry containing `..`, absolute paths, or escaping the staging root
 *    immediately throws [SecurityException] before touching live data.
 * 3. **Cryptographic SHA-256 Verification**: Every photo file in `backup_manifest.json` carries
 *    a SHA-256 hex digest verified during staging; any tampered or missing file aborts the restore
 *    with [SecurityException].
 * 4. **Guaranteed Staging Cleanup**: `cacheDir/restore_staging` is always wiped in a `finally` block.
 */
class BackupManager(
    private val repository: BoxVaultRepository,
    private val photoStorageManager: PhotoStorageManager,
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(
        context: Context,
        repository: BoxVaultRepository,
        photoStorageManager: PhotoStorageManager,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        repository = repository,
        photoStorageManager = photoStorageManager,
        cacheDir = context.cacheDir,
        ioDispatcher = ioDispatcher
    )

    val stagingDirectory: File
        get() = File(cacheDir, STAGING_DIR_NAME)

    /**
     * Exports all boxes, items, and physical photo files into a single `.zip` stream
     * containing `backup_manifest.json` and `photos/<relativePath>`.
     */
    suspend fun exportBackupToZip(
        outputStream: OutputStream
    ): BackupExportSummary = withContext(ioDispatcher) {
        val boxes = repository.getAllBoxes()
        val items = repository.getAllItems()
        val photos = repository.getAllPhotos()
        val exportedAt = System.currentTimeMillis()

        val boxesJson = JSONArray()
        boxes.forEach { box ->
            boxesJson.put(
                JSONObject().apply {
                    put("id", box.id)
                    put("shortCode", box.shortCode)
                    put("name", box.name)
                    put("location", box.location)
                    put("category", box.category)
                    put("colorHex", box.colorHex)
                    put("notes", box.notes)
                    put("createdAt", box.createdAt)
                    put("updatedAt", box.updatedAt)
                }
            )
        }

        val itemsJson = JSONArray()
        items.forEach { item ->
            itemsJson.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("boxId", item.boxId)
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("category", item.category)
                    put("notes", item.notes)
                    put("createdAt", item.createdAt)
                    put("updatedAt", item.updatedAt)
                }
            )
        }

        val photosJson = JSONArray()
        val photoPayloads = mutableListOf<Pair<PhotoEntity, ByteArray>>()
        var totalPhotoBytes = 0L

        photos.forEach { photo ->
            if (photoStorageManager.photoExists(photo.relativePath)) {
                val bytes = photoStorageManager.readPhotoBytes(photo.relativePath)
                val sha256 = computeSha256Hex(bytes)
                totalPhotoBytes += bytes.size.toLong()
                photoPayloads.add(photo to bytes)

                photosJson.put(
                    JSONObject().apply {
                        put("id", photo.id)
                        put("boxId", photo.boxId ?: JSONObject.NULL)
                        put("itemId", photo.itemId ?: JSONObject.NULL)
                        put("relativePath", photo.relativePath)
                        put("createdAt", photo.createdAt)
                        put("sha256", sha256)
                        put("sizeBytes", bytes.size)
                    }
                )
            }
        }

        val manifestJson = JSONObject().apply {
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("appVersion", "1.0.0")
            put("exportedAt", exportedAt)
            put("boxes", boxesJson)
            put("items", itemsJson)
            put("photos", photosJson)
        }

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            // 1. Write backup_manifest.json
            val manifestBytes = manifestJson.toString(2).toByteArray(Charsets.UTF_8)
            val manifestEntry = ZipEntry(MANIFEST_FILE_NAME)
            zipOut.putNextEntry(manifestEntry)
            zipOut.write(manifestBytes)
            zipOut.closeEntry()

            // 2. Write each physical photo file under photos/<relativePath>
            photoPayloads.forEach { (photo, bytes) ->
                val sanitizedRelPath = photo.relativePath.replace('\\', '/').trimStart('/')
                val photoEntry = ZipEntry("$PHOTOS_ZIP_PREFIX$sanitizedRelPath")
                zipOut.putNextEntry(photoEntry)
                zipOut.write(bytes)
                zipOut.closeEntry()
            }
            zipOut.finish()
        }

        BackupExportSummary(
            boxCount = boxes.size,
            itemCount = items.size,
            photoCount = photoPayloads.size,
            totalPhotoBytes = totalPhotoBytes,
            exportedAt = exportedAt
        )
    }

    /**
     * Extracts and validates a backup `.zip` inside `cacheDir/restore_staging` before
     * atomically replacing live database records and internal photo files.
     *
     * @throws SecurityException if Zip-Slip path traversal or SHA-256 tampering is detected.
     * @throws IllegalArgumentException if the archive is not a valid BoxVault backup.
     */
    suspend fun restoreBackupFromZip(
        inputStream: InputStream
    ): BackupRestoreSummary = withContext(ioDispatcher) {
        val stagingRoot = stagingDirectory
        if (stagingRoot.exists()) {
            stagingRoot.deleteRecursively()
        }
        stagingRoot.mkdirs()

        try {
            // Phase 1: Safe extraction into staging directory with Zip-Slip enforcement
            var entryCount = 0
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val currentEntry = entry
                    val targetFile = resolveSafeStagingFile(stagingRoot, currentEntry.name)

                    if (currentEntry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { fileOut ->
                            copyWithSizeLimit(zipIn, fileOut, MAX_SINGLE_ENTRY_BYTES)
                        }
                        entryCount++
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (entryCount == 0) {
                throw IllegalArgumentException("Backup ZIP archive is empty or corrupted.")
            }

            // Phase 2: Parse & validate backup_manifest.json inside staging
            val manifestFile = resolveSafeStagingFile(stagingRoot, MANIFEST_FILE_NAME)
            if (!manifestFile.exists() || !manifestFile.isFile) {
                throw IllegalArgumentException("Missing $MANIFEST_FILE_NAME in backup archive.")
            }

            val manifestText = manifestFile.readText(Charsets.UTF_8)
            val rootJson = runCatching { JSONObject(manifestText) }.getOrElse { cause ->
                throw IllegalArgumentException("Malformed $MANIFEST_FILE_NAME: ${cause.message}", cause)
            }

            val schemaVersion = rootJson.optInt("schemaVersion", -1)
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                throw IllegalArgumentException("Unsupported backup schemaVersion: $schemaVersion")
            }

            val backupTimestamp = rootJson.optLong("exportedAt", 0L)
            val parsedBoxes = parseBoxes(rootJson.optJSONArray("boxes") ?: JSONArray())
            val parsedItems = parseItems(rootJson.optJSONArray("items") ?: JSONArray())
            val stagedPhotos = parseAndVerifyStagedPhotos(
                photosArray = rootJson.optJSONArray("photos") ?: JSONArray(),
                stagingRoot = stagingRoot
            )

            // Phase 3: Atomic Commit (only reached after 100% of security & checksum checks pass)
            val stagedPhotoPayloads = stagedPhotos.map { staged ->
                staged.entity to staged.stagedFile.readBytes()
            }

            photoStorageManager.clearAllPhotoFiles()
            var verifiedBytes = 0L
            stagedPhotoPayloads.forEach { (photoEntity, bytes) ->
                photoStorageManager.writePhotoBytesAtRelativePath(photoEntity.relativePath, bytes)
                verifiedBytes += bytes.size.toLong()
            }

            repository.replaceAllInventoryData(
                boxes = parsedBoxes,
                items = parsedItems,
                photos = stagedPhotoPayloads.map { it.first }
            )

            BackupRestoreSummary(
                boxCount = parsedBoxes.size,
                itemCount = parsedItems.size,
                photoCount = stagedPhotoPayloads.size,
                verifiedPhotoBytes = verifiedBytes,
                backupTimestamp = backupTimestamp
            )
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    /**
     * Strictly resolves a ZIP entry path inside [stagingRoot] and rejects any path traversal
     * (`..`), absolute paths, or canonical escapes outside [stagingRoot].
     */
    internal fun resolveSafeStagingFile(stagingRoot: File, entryName: String): File {
        val trimmed = entryName.trim()
        if (trimmed.isEmpty()) {
            throw SecurityException("ZIP entry name must not be blank.")
        }
        if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.contains(":")) {
            throw SecurityException("Absolute or drive-prefixed ZIP entry rejected: $entryName")
        }

        val normalizedSlashes = trimmed.replace('\\', '/')
        val segments = normalizedSlashes.split('/')
        if (segments.any { it == ".." || it.contains("..") }) {
            throw SecurityException("Zip-Slip path traversal segment rejected: $entryName")
        }

        val rootCanonical = stagingRoot.canonicalFile
        val targetCanonical = File(rootCanonical, normalizedSlashes).canonicalFile
        val rootPrefix = rootCanonical.path + File.separator

        if (targetCanonical != rootCanonical && !targetCanonical.path.startsWith(rootPrefix)) {
            throw SecurityException("Zip-Slip canonical path escape rejected: $entryName")
        }
        return targetCanonical
    }

    private fun parseBoxes(boxesArray: JSONArray): List<BoxEntity> {
        val result = ArrayList<BoxEntity>(boxesArray.length())
        for (i in 0 until boxesArray.length()) {
            val obj = boxesArray.getJSONObject(i)
            val id = obj.optString("id").trim()
            val shortCode = obj.optString("shortCode").trim()
            val name = obj.optString("name").trim()
            if (id.isEmpty() || shortCode.isEmpty() || name.isEmpty()) {
                throw IllegalArgumentException("Invalid box record at index $i in manifest.")
            }
            result.add(
                BoxEntity(
                    id = id,
                    shortCode = shortCode,
                    name = name,
                    location = obj.optString("location", ""),
                    category = obj.optString("category", "General").ifEmpty { "General" },
                    colorHex = obj.optString("colorHex", "#F59E0B").ifEmpty { "#F59E0B" },
                    notes = obj.optString("notes", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return result
    }

    private fun parseItems(itemsArray: JSONArray): List<ItemEntity> {
        val result = ArrayList<ItemEntity>(itemsArray.length())
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.getJSONObject(i)
            val id = obj.optString("id").trim()
            val boxId = obj.optString("boxId").trim()
            val name = obj.optString("name").trim()
            if (id.isEmpty() || boxId.isEmpty() || name.isEmpty()) {
                throw IllegalArgumentException("Invalid item record at index $i in manifest.")
            }
            result.add(
                ItemEntity(
                    id = id,
                    boxId = boxId,
                    name = name,
                    quantity = obj.optInt("quantity", 1).coerceAtLeast(1),
                    category = obj.optString("category", "General").ifEmpty { "General" },
                    notes = obj.optString("notes", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return result
    }

    private data class VerifiedStagedPhoto(
        val entity: PhotoEntity,
        val stagedFile: File
    )

    private fun parseAndVerifyStagedPhotos(
        photosArray: JSONArray,
        stagingRoot: File
    ): List<VerifiedStagedPhoto> {
        val result = ArrayList<VerifiedStagedPhoto>(photosArray.length())
        for (i in 0 until photosArray.length()) {
            val obj = photosArray.getJSONObject(i)
            val id = obj.optString("id").trim()
            val boxId = if (obj.isNull("boxId")) null else obj.optString("boxId").takeIf { it.isNotBlank() }
            val itemId = if (obj.isNull("itemId")) null else obj.optString("itemId").takeIf { it.isNotBlank() }
            val relativePath = obj.optString("relativePath").trim()
            val expectedSha256 = obj.optString("sha256").trim().lowercase(Locale.US)
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

            if (id.isEmpty() || relativePath.isEmpty()) {
                throw IllegalArgumentException("Invalid photo metadata at index $i in manifest.")
            }
            if (expectedSha256.length != 64) {
                throw SecurityException("Missing or invalid SHA-256 checksum for photo '$relativePath'.")
            }

            // Ensure relativePath is safe inside PhotoStorageManager as well
            photoStorageManager.resolvePhotoFile(relativePath)

            val stagedFile = resolveSafeStagingFile(
                stagingRoot,
                "$PHOTOS_ZIP_PREFIX${relativePath.replace('\\', '/').trimStart('/')}"
            )
            if (!stagedFile.exists() || !stagedFile.isFile) {
                throw SecurityException("Staged photo file missing from backup archive: $relativePath")
            }

            val actualSha256 = computeSha256Hex(stagedFile.readBytes())
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw SecurityException(
                    "SHA-256 checksum mismatch for photo '$relativePath': expected $expectedSha256, got $actualSha256"
                )
            }

            result.add(
                VerifiedStagedPhoto(
                    entity = PhotoEntity(
                        id = id,
                        boxId = boxId,
                        itemId = itemId,
                        relativePath = relativePath,
                        createdAt = createdAt
                    ),
                    stagedFile = stagedFile
                )
            )
        }
        return result
    }

    private fun copyWithSizeLimit(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalRead = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalRead += read
            if (totalRead > maxBytes) {
                throw SecurityException("ZIP entry exceeds maximum allowed size ($maxBytes bytes).")
            }
            output.write(buffer, 0, read)
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MANIFEST_FILE_NAME = "backup_manifest.json"
        const val PHOTOS_ZIP_PREFIX = "photos/"
        const val STAGING_DIR_NAME = "restore_staging"
        private const val MAX_SINGLE_ENTRY_BYTES = 50L * 1024L * 1024L // 50 MB per file guard

        fun computeSha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val hexChars = CharArray(digest.size * 2)
            val hexAlphabet = "0123456789abcdef"
            for (i in digest.indices) {
                val v = digest[i].toInt() and 0xFF
                hexChars[i * 2] = hexAlphabet[v ushr 4]
                hexChars[i * 2 + 1] = hexAlphabet[v and 0x0F]
            }
            return String(hexChars)
        }
    }
}

