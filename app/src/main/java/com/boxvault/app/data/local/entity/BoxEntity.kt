package com.boxvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "boxes",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["shortCode"], unique = true),
        Index(value = ["name"]),
        Index(value = ["location"]),
        Index(value = ["category"]),
        Index(value = ["roomId"])
    ]
)
data class BoxEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val roomId: String? = null,
    val shortCode: String,
    val name: String,
    val location: String,
    val category: String = "General",
    val colorHex: String = "#F59E0B",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val qrPayload: String
        get() = "$QR_URI_PREFIX$id"

    companion object {
        const val QR_URI_PREFIX = "boxvault://box/"

        fun parseBoxIdFromQrPayload(rawPayload: String): String? {
            val trimmed = rawPayload.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith(QR_URI_PREFIX, ignoreCase = true)) {
                val extracted = trimmed.substring(QR_URI_PREFIX.length).trim()
                return extracted.takeIf { it.isNotEmpty() }
            }
            return try {
                UUID.fromString(trimmed).toString()
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
