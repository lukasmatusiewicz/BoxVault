package com.boxvault.app.data.storage

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class PhotoStorageManagerTest {

    private lateinit var tempBaseDir: File
    private lateinit var storageManager: PhotoStorageManager

    @Before
    fun setUp() {
        tempBaseDir = Files.createTempDirectory("boxvault_photo_test").toFile()
        storageManager = PhotoStorageManager(baseFilesDir = tempBaseDir)
    }

    @After
    fun tearDown() {
        tempBaseDir.deleteRecursively()
    }

    @Test
    fun `savePhotoBytes writes inside photos directory and reads back identically`() = runTest {
        val payload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val relativePath = storageManager.savePhotoBytes(payload, prefix = "box", extension = "png")

        assertTrue(relativePath.startsWith("box_"))
        assertTrue(relativePath.endsWith(".png"))
        assertTrue(storageManager.photoExists(relativePath))

        val resolvedFile = storageManager.resolvePhotoFile(relativePath)
        assertEquals(storageManager.photosDirectory.canonicalFile, resolvedFile.parentFile?.canonicalFile)

        val readBack = storageManager.readPhotoBytes(relativePath)
        assertArrayEquals(payload, readBack)
    }

    @Test
    fun `savePhotoStream writes stream contents and deletePhotoFile removes file`() = runTest {
        val content = "sample-jpeg-binary-stream".encodeToByteArray()
        val relativePath = storageManager.savePhotoStream(
            inputStream = ByteArrayInputStream(content),
            prefix = "item",
            extension = "jpg"
        )

        assertTrue(storageManager.photoExists(relativePath))
        assertArrayEquals(content, storageManager.readPhotoBytes(relativePath))

        val deleted = storageManager.deletePhotoFile(relativePath)
        assertTrue(deleted)
        assertFalse(storageManager.photoExists(relativePath))
    }

    @Test
    fun `deleteAllOrphanedFiles deletes only unreferenced photo files`() = runTest {
        val activePhoto = storageManager.savePhotoBytes(byteArrayOf(1, 2, 3), prefix = "active")
        val orphanPhoto1 = storageManager.savePhotoBytes(byteArrayOf(4, 5, 6), prefix = "orphan1")
        val orphanPhoto2 = storageManager.savePhotoBytes(byteArrayOf(7, 8, 9), prefix = "orphan2")

        val deletedCount = storageManager.deleteAllOrphanedFiles(setOf(activePhoto))
        assertEquals(2, deletedCount)
        assertTrue(storageManager.photoExists(activePhoto))
        assertFalse(storageManager.photoExists(orphanPhoto1))
        assertFalse(storageManager.photoExists(orphanPhoto2))
    }

    @Test(expected = SecurityException::class)
    fun `resolvePhotoFile rejects parent directory traversal`() {
        storageManager.resolvePhotoFile("../secret_database.db")
    }

    @Test(expected = SecurityException::class)
    fun `resolvePhotoFile rejects nested parent traversal`() {
        storageManager.resolvePhotoFile("nested/../../escape.jpg")
    }

    @Test(expected = SecurityException::class)
    fun `resolvePhotoFile rejects unix absolute path`() {
        storageManager.resolvePhotoFile("/etc/passwd")
    }

    @Test(expected = SecurityException::class)
    fun `resolvePhotoFile rejects windows backslash root path`() {
        storageManager.resolvePhotoFile("\\Windows\\System32\\cmd.exe")
    }

    @Test(expected = SecurityException::class)
    fun `resolvePhotoFile rejects blank path`() {
        storageManager.resolvePhotoFile("   ")
    }
}
