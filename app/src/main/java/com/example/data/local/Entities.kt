package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val packageName: String,
    val appDisplayName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val videoTimestamp: String = "",
    val tags: String = "General"
)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appDisplayName: String,
    val startTime: Long,
    val durationSeconds: Long,
    val actionsTriggered: Int = 0,
    val notesCount: Int = 0
)

@Entity(tableName = "traversal_logs")
data class TraversalLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val actionType: String,
    val success: Boolean,
    val nodesScanned: Int,
    val durationMs: Long,
    val details: String
)
