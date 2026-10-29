package com.callstudio.app.data

import com.callstudio.app.scheduling.ScheduleCoordinator
import kotlinx.coroutines.flow.Flow

class CallerRepository(
    private val dao: CallerDao,
    private val photos: PhotoStore,
    private val coordinator: ScheduleCoordinator,
) {
    val all: Flow<List<CallerEntity>> = dao.observeAll()

    suspend fun get(id: Long): CallerEntity? = dao.get(id)

    suspend fun firstOrNull(): CallerEntity? = dao.mostRecentlyUsed()

    /** Creates or updates a caller. Blank names fall back to the phone number. */
    suspend fun save(id: Long, name: String, phone: String, photoFile: String?): Long {
        val cleanPhone = phone.trim()
        val cleanName = name.trim().ifBlank { cleanPhone }
        val now = System.currentTimeMillis()
        return if (id > 0) {
            val old = dao.get(id)
            if (old != null) {
                dao.update(old.copy(name = cleanName, phone = cleanPhone, photoFile = photoFile))
                if (old.photoFile != null && old.photoFile != photoFile) photos.delete(old.photoFile)
                id
            } else {
                dao.insert(CallerEntity(name = cleanName, phone = cleanPhone, photoFile = photoFile, createdAt = now))
            }
        } else {
            dao.insert(CallerEntity(name = cleanName, phone = cleanPhone, photoFile = photoFile, createdAt = now))
        }
    }

    /** Deleting a caller also cancels the calls still scheduled for them. */
    suspend fun delete(caller: CallerEntity) {
        coordinator.cancelForCaller(caller.id)
        dao.delete(caller.id)
        photos.delete(caller.photoFile)
    }

    suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())
}
