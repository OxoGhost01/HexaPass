package com.oxoghost.hexapass.ui

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oxoghost.hexapass.domain.model.EntryDraft
import com.oxoghost.hexapass.domain.model.VaultEntry
import com.oxoghost.hexapass.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VaultState {
    LOCKED,
    UNLOCKED
}

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Success : SaveState()
    data class Error(val theMessage: String) : SaveState()
}

class MainViewModel(
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _entries = MutableLiveData<List<VaultEntry>>(emptyList())
    val entries: LiveData<List<VaultEntry>> = _entries

    private val _vaultState = MutableLiveData(VaultState.LOCKED)
    val vaultState: LiveData<VaultState> = _vaultState

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    var autoLockTimeoutMs: Long = 30 * 1000 // 30 seconds
    var currentVaultUri: Uri? = null
        private set

    private var masterPasswordBuffer: CharArray? = null
    private var visiblePassword: CharArray? = null

    fun openVault(vaultUri: Uri, password: CharArray) {
        currentVaultUri = vaultUri
        
        if (password !== masterPasswordBuffer) {
            masterPasswordBuffer?.fill('\u0000')
            masterPasswordBuffer = password.copyOf()
        }

        val result = vaultRepository.openVault(vaultUri, masterPasswordBuffer!!)
        
        if (password !== masterPasswordBuffer) {
            password.fill('\u0000')
        }

        _entries.value = result
        _vaultState.value = VaultState.UNLOCKED
    }

    fun lockVault() {
        clearVisiblePassword()
        masterPasswordBuffer?.fill('\u0000')
        masterPasswordBuffer = null
        currentVaultUri = null
        vaultRepository.clearSensitiveData()
        _entries.value = emptyList()
        _vaultState.value = VaultState.LOCKED
        _saveState.value = SaveState.Idle
    }

    fun isUnlocked(): Boolean =
        _vaultState.value == VaultState.UNLOCKED

    fun copyPassword(entryId: String, onReady: (CharArray) -> Unit) {
        if (!isUnlocked()) return

        val pwd = vaultRepository.getPassword(entryId) ?: return
        onReady(pwd)
        pwd.fill('\u0000')
    }

    fun requestVisiblePassword(entryId: String): CharArray? {
        if (!isUnlocked()) return null

        visiblePassword?.fill('\u0000')
        visiblePassword = vaultRepository.getPassword(entryId)
        return visiblePassword
    }

    fun clearVisiblePassword() {
        visiblePassword?.fill('\u0000')
        visiblePassword = null
    }

    fun saveEntry(entryId: String, draft: EntryDraft) {
        val uri = currentVaultUri ?: return
        val masterPwd = masterPasswordBuffer ?: return

        _saveState.value = SaveState.Saving

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                vaultRepository.updateEntry(uri, entryId, draft, masterPwd)
            }

            result.fold(
                onSuccess = {
                    openVault(uri, masterPwd)
                    _saveState.value = SaveState.Success
                },
                onFailure = { error ->
                    _saveState.value = SaveState.Error(error.message ?: "Unknown error occurred")
                    lockVault()
                }
            )
        }
    }

    fun addEntry(draft: EntryDraft) {
        val uri = currentVaultUri ?: return
        val masterPwd = masterPasswordBuffer ?: return

        _saveState.value = SaveState.Saving

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                vaultRepository.addEntry(uri, draft, masterPwd)
            }

            result.fold(
                onSuccess = {
                    openVault(uri, masterPwd)
                    _saveState.value = SaveState.Success
                },
                onFailure = { error ->
                    _saveState.value = SaveState.Error(error.message ?: "Unknown error occurred")
                    lockVault()
                }
            )
        }
    }

    fun deleteEntry(entryId: String) {
        val currentList = _entries.value ?: return
        _entries.value = currentList.filter { it.id != entryId }
        clearVisiblePassword()
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
