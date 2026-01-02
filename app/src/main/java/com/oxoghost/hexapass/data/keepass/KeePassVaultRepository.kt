package com.oxoghost.hexapass.data.keepass

import android.content.ContentResolver
import android.net.Uri
import com.oxoghost.hexapass.domain.model.EntryDraft
import com.oxoghost.hexapass.domain.model.VaultEntry
import com.oxoghost.hexapass.domain.repository.VaultRepository
import org.linguafranca.pwdb.kdbx.KdbxCreds
import org.linguafranca.pwdb.kdbx.simple.SimpleDatabase
import org.linguafranca.pwdb.kdbx.simple.SimpleEntry
import org.linguafranca.pwdb.kdbx.simple.SimpleGroup
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class KeePassVaultRepository(
    private val contentResolver: ContentResolver,
    private val cacheDir: File
) : VaultRepository {

    private var database: SimpleDatabase? = null

    override fun openVault(
        vaultUri: Uri,
        masterPassword: CharArray
    ): List<VaultEntry> {
        return contentResolver.openInputStream(vaultUri).use { inputStream ->
            requireNotNull(inputStream) { "Unable to open vault input stream" }
            val creds = KdbxCreds(String(masterPassword).toByteArray())
            val db = SimpleDatabase.load(creds, inputStream)
            database = db
            getEntries()
        }
    }

    override fun createVault(
        vaultUri: Uri,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val db = SimpleDatabase()
            db.rootGroup.name = "Root"
            
            saveDatabase(vaultUri, db, masterPassword)
            database = db
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    private fun getEntries(): List<VaultEntry> {
        val db = database ?: return emptyList()
        val allEntries = mutableListOf<SimpleEntry>()
        collectEntriesRecursively(db.rootGroup, allEntries)
        
        return allEntries.map { entry ->
            VaultEntry(
                id = entry.uuid.toString(),
                title = entry.title ?: "",
                username = entry.username ?: "",
                urls = entry.url?.let { listOf(it) } ?: emptyList(),
                notes = entry.notes ?: "",
                passwordLength = entry.password?.length ?: 0
            )
        }
    }

    private fun collectEntriesRecursively(group: SimpleGroup, result: MutableList<SimpleEntry>) {
        result.addAll(group.entries)
        for (subGroup in group.groups) {
            collectEntriesRecursively(subGroup, result)
        }
    }

    override fun updateEntry(
        vaultUri: Uri,
        entryId: String,
        draft: EntryDraft,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val db = reloadDatabase(vaultUri, masterPassword)
            val entry = findEntryById(db.rootGroup, UUID.fromString(entryId))
                ?: throw Exception("Entry not found")

            entry.title = draft.title
            entry.username = draft.username ?: ""
            entry.password = draft.password?.let { String(it) } ?: ""
            entry.url = draft.url
            entry.notes = draft.notes ?: ""

            saveDatabase(vaultUri, db, masterPassword)
            database = db
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    override fun addEntry(
        vaultUri: Uri,
        draft: EntryDraft,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val db = reloadDatabase(vaultUri, masterPassword)
            
            val targetGroup = if (db.rootGroup.groups.isNotEmpty()) db.rootGroup.groups[0] else db.rootGroup
            val entry = targetGroup.addEntry(db.newEntry())
            
            entry.title = draft.title
            entry.username = draft.username ?: ""
            entry.password = draft.password?.let { String(it) } ?: ""
            entry.url = draft.url
            entry.notes = draft.notes ?: ""

            saveDatabase(vaultUri, db, masterPassword)
            database = db
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    override fun deleteEntry(
        vaultUri: Uri,
        entryId: String,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val db = reloadDatabase(vaultUri, masterPassword)
            val uuid = UUID.fromString(entryId)
            val entry = findEntryById(db.rootGroup, uuid)
                ?: throw Exception("Entry not found")

            entry.parent?.removeEntry(entry)

            saveDatabase(vaultUri, db, masterPassword)
            database = db
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    override fun addGroup(
        vaultUri: Uri,
        parentGroupId: String?,
        name: String,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val db = reloadDatabase(vaultUri, masterPassword)
            val parent = if (parentGroupId != null) {
                findGroupById(db.rootGroup, UUID.fromString(parentGroupId))
                    ?: throw Exception("Parent group not found")
            } else {
                db.rootGroup
            }

            if (parent.groups.any { it.name == name }) {
                throw Exception("A group with this name already exists")
            }

            parent.addGroup(db.newGroup(name))

            saveDatabase(vaultUri, db, masterPassword)
            database = db
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    override fun deleteGroup(
        vaultUri: Uri,
        groupId: String,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val db = reloadDatabase(vaultUri, masterPassword)
            val uuid = UUID.fromString(groupId)
            val group = findGroupById(db.rootGroup, uuid)
                ?: throw Exception("Group not found")

            if (group.isRootGroup) {
                throw Exception("Cannot delete root group")
            }

            group.parent?.removeGroup(group)

            saveDatabase(vaultUri, db, masterPassword)
            database = db
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    override fun moveEntry(
        vaultUri: Uri,
        entryId: String,
        targetGroupId: String,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val db = reloadDatabase(vaultUri, masterPassword)
            val entry = findEntryById(db.rootGroup, UUID.fromString(entryId))
                ?: throw Exception("Entry not found")
            val targetGroup = findGroupById(db.rootGroup, UUID.fromString(targetGroupId))
                ?: throw Exception("Target group not found")

            targetGroup.addEntry(entry)

            saveDatabase(vaultUri, db, masterPassword)
            database = db
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    private fun reloadDatabase(vaultUri: Uri, masterPassword: CharArray): SimpleDatabase {
        return contentResolver.openInputStream(vaultUri).use { inputStream ->
            requireNotNull(inputStream)
            val creds = KdbxCreds(String(masterPassword).toByteArray())
            SimpleDatabase.load(creds, inputStream)
        }
    }

    private fun saveDatabase(vaultUri: Uri, db: SimpleDatabase, masterPassword: CharArray) {
        val creds = KdbxCreds(String(masterPassword).toByteArray())
        val tempFile = File(cacheDir, "vault_temp.kdbx")
        FileOutputStream(tempFile).use { fos ->
            db.save(creds, fos)
        }

        contentResolver.openOutputStream(vaultUri, "wt")?.use { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        } ?: throw Exception("Cannot open vault output stream")
        
        tempFile.delete()
    }

    private fun findEntryById(group: SimpleGroup, uuid: UUID): SimpleEntry? {
        for (entry in group.entries) {
            if (entry.uuid == uuid) return entry
        }
        for (subGroup in group.groups) {
            val found = findEntryById(subGroup, uuid)
            if (found != null) return found
        }
        return null
    }

    private fun findGroupById(group: SimpleGroup, uuid: UUID): SimpleGroup? {
        if (group.uuid == uuid) return group
        for (subGroup in group.groups) {
            val found = findGroupById(subGroup, uuid)
            if (found != null) return found
        }
        return null
    }

    override fun getPassword(entryId: String): CharArray? {
        val db = database ?: return null
        val entry = findEntryById(db.rootGroup, UUID.fromString(entryId))
            ?: return null
        return entry.password?.toCharArray()
    }

    override fun clearSensitiveData() {
        database = null
    }
}
