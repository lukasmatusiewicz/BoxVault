package com.boxvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["id"],
            childColumns = ["boxId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["boxId"]),
        Index(value = ["name"]),
        Index(value = ["category"])
    ]
)
data class ItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val boxId: String,
    val name: String,
    val quantity: Int = 1,
    val category: String = "General",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

