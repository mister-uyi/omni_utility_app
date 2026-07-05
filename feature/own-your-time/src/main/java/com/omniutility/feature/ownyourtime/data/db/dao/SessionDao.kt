package com.omniutility.feature.ownyourtime.data.db.dao

import androidx.room.*
import com.omniutility.feature.ownyourtime.data.db.entity.SessionEntity
import com.omniutility.feature.ownyourtime.data.db.entity.SessionTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM session_tasks WHERE sessionId = :sessionId")
    fun observeTasksForSession(sessionId: String): Flow<List<SessionTaskEntity>>

    @Query("SELECT * FROM session_tasks WHERE sessionId = :sessionId")
    suspend fun getTasksForSession(sessionId: String): List<SessionTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessionTask(sessionTask: SessionTaskEntity)

    @Query("UPDATE session_tasks SET completed = :completed, completedAt = :completedAt WHERE sessionId = :sessionId AND taskId = :taskId")
    suspend fun updateTaskCompletion(sessionId: String, taskId: String, completed: Boolean, completedAt: Long?)

    @Query("SELECT COUNT(*) FROM sessions WHERE startedAt >= :fromMs AND startedAt < :toMs")
    suspend fun countSessionsInRange(fromMs: Long, toMs: Long): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE startedAt >= :fromMs AND startedAt < :toMs")
    fun observeSessionCountInRange(fromMs: Long, toMs: Long): Flow<Int>

    @Query("SELECT SUM(actualDurationMs) FROM sessions WHERE startedAt >= :fromMs AND startedAt < :toMs")
    suspend fun sumDurationInRange(fromMs: Long, toMs: Long): Long?

    @Query("SELECT SUM(actualDurationMs) FROM sessions WHERE startedAt >= :fromMs AND startedAt < :toMs")
    fun observeSumDurationInRange(fromMs: Long, toMs: Long): Flow<Long?>

    @Query("""
        SELECT COUNT(*) FROM session_tasks st
        INNER JOIN sessions s ON st.sessionId = s.id
        WHERE st.completed = 1 AND s.startedAt >= :fromMs AND s.startedAt < :toMs
    """)
    suspend fun countCompletedTasksInRange(fromMs: Long, toMs: Long): Int

    @Query("""
        SELECT COUNT(*) FROM session_tasks st
        INNER JOIN sessions s ON st.sessionId = s.id
        WHERE st.completed = 1 AND s.startedAt >= :fromMs AND s.startedAt < :toMs
    """)
    fun observeCompletedTasksInRange(fromMs: Long, toMs: Long): Flow<Int>

    @Query("SELECT DISTINCT taskId FROM session_tasks WHERE completed = 1")
    fun observeCompletedTaskIds(): Flow<List<String>>
}
