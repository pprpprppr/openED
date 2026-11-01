package org.opened.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "modules",
    foreignKeys = [
        ForeignKey(
            entity = StudyPathEntity::class,
            parentColumns = ["id"],
            childColumns = ["pathId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pathId"]),
        Index(value = ["pathId", "orderIndex"])
    ]
)
data class ModuleEntity(
    @PrimaryKey
    val id: String,
    val pathId: String,
    val title: String,
    val orderIndex: Int,
    val summary: String
)
