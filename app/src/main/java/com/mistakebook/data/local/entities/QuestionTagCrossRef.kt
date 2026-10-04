package com.mistakebook.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "question_tags",
    primaryKeys = ["questionId", "tagId"],
    indices = [Index("tagId")]
)
data class QuestionTagCrossRef(
    val questionId: Long,
    val tagId: Long
)
