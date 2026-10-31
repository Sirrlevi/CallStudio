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
    suspend fun save(id: Long, name: String, phone: String, countryIso: String, photoFile: String?): Long {
        val cleanPhone = phone.filter { it.isDigit() }.ifBlank { phone.trim() }
        val cleanName = name.trim().ifBlank { cleanPhone }
        val now = System.currentTimeMillis()
        return if (id > 0) {
            val old = dao.get(id)
            if (old != null) {
                dao.update(old.copy(name = cleanName, phone = cleanPhone, countryIso = countryIso, photoFile = photoFile))
                if (old.photoFile != null && old.photoFile != photoFile) photos.delete(old.photoFile)
                id
            } else {
                dao.insert(CallerEntity(name = cleanName, phone = cleanPhone, countryIso = countryIso, photoFile = photoFile, createdAt = now))
            }
        } else {
            dao.insert(CallerEntity(name = cleanName, phone = cleanPhone, countryIso = countryIso, photoFile = photoFile, createdAt = now))
        }
    }

    /**
     * Adds phonebook entries without duplicating numbers already saved in Phone.
     * Existing caller names/photos are never overwritten by an automatic sync.
     */
    suspend fun importContacts(imported: List<CallerEntity>): Int {
        val existing = dao.getAll()
        val exactKeys = existing.mapTo(mutableSetOf()) { key(it.countryIso, it.phone) }
        val legacyDigits = existing.filter { it.countryIso.isBlank() }
            .mapTo(mutableSetOf()) { it.phone.filter(Char::isDigit) }
        val seen = mutableSetOf<String>()
        var added = 0
        for (candidate in imported) {
            val digits = candidate.phone.filter(Char::isDigit)
            if (digits.isBlank()) {
                photos.delete(candidate.photoFile)
                continue
            }
            val normalizedKey = key(candidate.countryIso, digits)
            val duplicate = !seen.add(normalizedKey) || normalizedKey in exactKeys || digits in legacyDigits
            if (duplicate) {
                photos.delete(candidate.photoFile)
                continue
            }
            dao.insert(candidate.copy(phone = digits))
            exactKeys += normalizedKey
            added++
        }
        return added
    }

    private fun key(iso: String, phone: String): String =
        "${iso.uppercase()}:${phone.filter(Char::isDigit)}"

    /** Deleting a caller also cancels the calls still scheduled for them. */
    suspend fun delete(caller: CallerEntity) {
        coordinator.cancelForCaller(caller.id)
        dao.delete(caller.id)
        photos.delete(caller.photoFile)
    }

    suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())
}
