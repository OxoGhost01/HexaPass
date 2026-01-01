package com.oxoghost.hexapass.domain.repository

import android.net.Uri
import com.oxoghost.hexapass.domain.model.EntryDraft
import com.oxoghost.hexapass.domain.model.VaultEntry

interface VaultRepository {

    fun openVault(
        vaultUri: Uri,
        masterPassword: CharArray
    ): List<VaultEntry>

    fun updateEntry(
        vaultUri: Uri,
        entryId: String,
        draft: EntryDraft,
        masterPassword: CharArray
    ): Result<Unit>

    fun addEntry(
        vaultUri: Uri,
        draft: EntryDraft,
        masterPassword: CharArray
    ): Result<Unit>

    fun deleteEntry(
        vaultUri: Uri,
        entryId: String,
        masterPassword: CharArray
    ): Result<Unit>

    fun clearSensitiveData()

    fun getPassword(entryId: String): CharArray?
}
