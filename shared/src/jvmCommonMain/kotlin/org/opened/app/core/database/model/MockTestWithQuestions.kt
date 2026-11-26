package org.opened.app.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import kotlinx.serialization.Serializable
import org.opened.app.core.database.entity.MockTestEntity
import org.opened.app.core.database.entity.MockTestQuestionEntity

@Serializable
data class MockTestWithQuestions(
    @Embedded
    val test: MockTestEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "testId"
    )
    val questions: List<MockTestQuestionEntity>
)
