package com.oxoghost.hexapass.data.keepass

import android.content.ContentResolver
import android.net.Uri
import com.oxoghost.hexapass.domain.model.EntryDraft
import com.oxoghost.hexapass.domain.model.VaultEntry
import com.oxoghost.hexapass.domain.repository.VaultRepository
import de.slackspace.openkeepass.KeePassDatabase
import de.slackspace.openkeepass.domain.EntryBuilder
import de.slackspace.openkeepass.domain.Group
import de.slackspace.openkeepass.domain.KeePassFile
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class KeePassVaultRepository(
    private val contentResolver: ContentResolver,
    private val cacheDir: File
) : VaultRepository {

    private var keepassFile: KeePassFile? = null

    override fun openVault(
        vaultUri: Uri,
        masterPassword: CharArray
    ): List<VaultEntry> {
        contentResolver.openInputStream(vaultUri).use { inputStream ->
            requireNotNull(inputStream) { "Unable to open vault input stream" }
            keepassFile = KeePassDatabase
                .getInstance(inputStream)
                .openDatabase(String(masterPassword))

            return getEntries()
        }
    }

    private fun getEntries(): List<VaultEntry> {
        val file = keepassFile ?: return emptyList()
        return collectEntries(file.root).map { entry ->
            VaultEntry(
                id = entry.uuid.toString(),
                title = entry.title ?: "",
                username = entry.username,
                urls = entry.url?.let { listOf(it) } ?: emptyList(),
                notes = entry.notes,
                passwordLength = entry.password?.length ?: 0
            )
        }
    }

    override fun updateEntry(
        vaultUri: Uri,
        entryId: String,
        draft: EntryDraft,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val passwordString = String(masterPassword)

            val currentFile = contentResolver.openInputStream(vaultUri).use { input ->
                requireNotNull(input)
                KeePassDatabase.getInstance(input).openDatabase(passwordString)
            }

            val uuid = UUID.fromString(entryId)
            updateGroupRecursively(currentFile.root, uuid, draft)

            val tempFile = File(cacheDir, "vault_temp.kdbx")
            FileOutputStream(tempFile).use { fos ->
                KeePassDatabase.write(currentFile, passwordString, fos)
            }

            contentResolver.openOutputStream(vaultUri, "wt")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot open vault output stream")

            tempFile.delete()
            keepassFile = currentFile

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun addEntry(
        vaultUri: Uri,
        draft: EntryDraft,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val passwordString = String(masterPassword)

            val currentFile = contentResolver.openInputStream(vaultUri).use { input ->
                requireNotNull(input)
                KeePassDatabase.getInstance(input).openDatabase(passwordString)
            }

            val newEntry = EntryBuilder(draft.title)
                .username(draft.username)
                .password(draft.password?.let { String(it) })
                .url(draft.url)
                .notes(draft.notes)
                .build()

            // Find the proper target group (prefer the first subgroup of root)
            val root = currentFile.root
            val targetGroup = if (root.groups.isNotEmpty()) root.groups[0] else root

            // Add the entry using the same list modification pattern that works for updates
            val updatedEntries = targetGroup.entries.toMutableList()
            updatedEntries.add(newEntry)
            
            targetGroup.entries.clear()
            targetGroup.entries.addAll(updatedEntries)

            val tempFile = File(cacheDir, "vault_temp.kdbx")
            FileOutputStream(tempFile).use { fos ->
                KeePassDatabase.write(currentFile, passwordString, fos)
            }

            contentResolver.openOutputStream(vaultUri, "wt")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot open vault output stream")

            tempFile.delete()
            keepassFile = currentFile

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteEntry(
        vaultUri: Uri,
        entryId: String,
        masterPassword: CharArray
    ): Result<Unit> {
        return try {
            val passwordString = String(masterPassword)

            val currentFile = contentResolver.openInputStream(vaultUri).use { input ->
                requireNotNull(input)
                KeePassDatabase.getInstance(input).openDatabase(passwordString)
            }

            val uuid = UUID.fromString(entryId)
            deleteEntryFromGroupRecursively(currentFile.root, uuid)

            val tempFile = File(cacheDir, "vault_temp.kdbx")
            FileOutputStream(tempFile).use { fos ->
                KeePassDatabase.write(currentFile, passwordString, fos)
            }

            contentResolver.openOutputStream(vaultUri, "wt")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot open vault output stream")

            tempFile.delete()
            keepassFile = currentFile

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    private fun deleteEntryFromGroupRecursively(group: Group, uuid: UUID) {
        val updatedEntries = group.entries.filter { it.uuid != uuid }
        group.entries.clear()
        group.entries.addAll(updatedEntries)

        group.groups.forEach { subGroup ->
            deleteEntryFromGroupRecursively(subGroup, uuid)
        }
    }

    private fun updateGroupRecursively(
        group: Group,
        uuid: UUID,
        draft: EntryDraft
    ) {
        val updatedEntries = group.entries.map { entry ->
            if (entry.uuid == uuid) {
                EntryBuilder(entry)
                    .title(draft.title)
                    .username(draft.username)
                    .password(draft.password?.let { String(it) })
                    .url(draft.url)
                    .notes(draft.notes)
                    .build()
            } else {
                entry
            }
        }

        group.entries.clear()
        group.entries.addAll(updatedEntries)

        group.groups.forEach { subGroup ->
            updateGroupRecursively(subGroup, uuid, draft)
        }
    }

    override fun getPassword(entryId: String): CharArray? {
        val file = keepassFile ?: return null
        val entry = collectEntries(file.root)
            .firstOrNull { it.uuid.toString() == entryId }
            ?: return null
        return entry.password?.toCharArray()
    }

    private fun collectEntries(group: Group): List<de.slackspace.openkeepass.domain.Entry> {
        val result = mutableListOf<de.slackspace.openkeepass.domain.Entry>()
        result.addAll(group.entries)
        for (subGroup in group.groups) {
            result.addAll(collectEntries(subGroup))
        }
        return result
    }

    override fun clearSensitiveData() {
        keepassFile = null
    }
}
