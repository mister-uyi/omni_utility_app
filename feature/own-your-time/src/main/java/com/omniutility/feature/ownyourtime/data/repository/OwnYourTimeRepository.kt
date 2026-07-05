package com.omniutility.feature.ownyourtime.data.repository

import com.omniutility.feature.ownyourtime.data.db.dao.*
import com.omniutility.feature.ownyourtime.data.db.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OwnYourTimeRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val sessionDao: SessionDao,
    private val appConfigDao: AppConfigDao,
    private val userConfigDao: UserConfigDao
) {
    fun observeTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()
    suspend fun getTask(id: String): TaskEntity? = taskDao.getById(id)
    suspend fun saveTask(task: TaskEntity) = taskDao.upsert(task)
    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    fun observeRecentSessions(limit: Int = 10): Flow<List<SessionEntity>> = sessionDao.observeRecent(limit)
    fun observeAllSessions(): Flow<List<SessionEntity>> = sessionDao.observeAll()
    fun observeSession(id: String): Flow<SessionEntity?> = sessionDao.observeById(id)
    suspend fun getSession(id: String): SessionEntity? = sessionDao.getById(id)
    suspend fun saveSession(session: SessionEntity) = sessionDao.upsert(session)
    fun observeSessionTasks(sessionId: String): Flow<List<SessionTaskEntity>> = sessionDao.observeTasksForSession(sessionId)
    suspend fun getSessionTasks(sessionId: String): List<SessionTaskEntity> = sessionDao.getTasksForSession(sessionId)
    suspend fun saveSessionTask(sessionTask: SessionTaskEntity) = sessionDao.upsertSessionTask(sessionTask)
    suspend fun updateTaskCompletion(sessionId: String, taskId: String, completed: Boolean, completedAt: Long?) =
        sessionDao.updateTaskCompletion(sessionId, taskId, completed, completedAt)

    suspend fun getSessionCount(fromMs: Long, toMs: Long): Int = sessionDao.countSessionsInRange(fromMs, toMs)
    suspend fun getTotalDurationMs(fromMs: Long, toMs: Long): Long = sessionDao.sumDurationInRange(fromMs, toMs) ?: 0L
    suspend fun getCompletedTaskCount(fromMs: Long, toMs: Long): Int = sessionDao.countCompletedTasksInRange(fromMs, toMs)
    fun observeSessionCountInRange(fromMs: Long, toMs: Long): Flow<Int> = sessionDao.observeSessionCountInRange(fromMs, toMs)
    fun observeTotalDurationMsInRange(fromMs: Long, toMs: Long): Flow<Long> = sessionDao.observeSumDurationInRange(fromMs, toMs).map { it ?: 0L }
    fun observeCompletedTaskCountInRange(fromMs: Long, toMs: Long): Flow<Int> = sessionDao.observeCompletedTasksInRange(fromMs, toMs)
    fun observeCompletedTaskIds(): Flow<List<String>> = sessionDao.observeCompletedTaskIds()

    fun observeAppConfigs(): Flow<List<AppConfigEntity>> = appConfigDao.observeAll()
    fun observeAppsByCategory(category: AppCategory): Flow<List<AppConfigEntity>> = appConfigDao.observeByCategory(category)
    suspend fun getAppsByCategory(category: AppCategory): List<AppConfigEntity> = appConfigDao.getByCategory(category)
    suspend fun saveAppConfig(appConfig: AppConfigEntity) = appConfigDao.upsert(appConfig)
    suspend fun saveAllAppConfigs(appConfigs: List<AppConfigEntity>) = appConfigDao.upsertAll(appConfigs)
    suspend fun removeAppConfig(packageName: String) = appConfigDao.delete(packageName)

    fun observeUserConfig(): Flow<UserConfigEntity?> = userConfigDao.observe()
    suspend fun getUserConfig(): UserConfigEntity = userConfigDao.get() ?: UserConfigEntity()
    suspend fun saveUserConfig(config: UserConfigEntity) = userConfigDao.upsert(config)
}
