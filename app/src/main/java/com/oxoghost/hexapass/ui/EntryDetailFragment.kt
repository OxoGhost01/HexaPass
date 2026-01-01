package com.oxoghost.hexapass.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.oxoghost.hexapass.R
import com.oxoghost.hexapass.domain.model.EntryDraft
import com.oxoghost.hexapass.domain.model.VaultEntry
import java.util.UUID

class EntryDetailFragment : Fragment(R.layout.fragment_entry_detail) {

    private lateinit var viewModel: MainViewModel
    private var vaultEntry: VaultEntry? = null
    private var entryDraft: EntryDraft? = null
    
    private var isPasswordVisible = false
    private var passwordBuffer: CharArray? = null
    private var isEditMode = false
    private var isCreateMode = false

    private val handler = Handler(Looper.getMainLooper())
    private val hidePasswordRunnable = Runnable {
        view?.let { v ->
            val passwordTextView = v.findViewById<TextView>(R.id.passwordTextView)
            val toggleButton = v.findViewById<ImageButton>(R.id.togglePasswordButton)
            vaultEntry?.let { entry ->
                hidePassword(entry, passwordTextView, toggleButton)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val entryId = arguments?.getString("entryId")
        isCreateMode = entryId == null

        setupToolbar(view)
        setupObservers(view, entryId)
        setupActionButtons(view)

        if (isCreateMode) {
            enterCreateMode(view)
        }
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = if (isCreateMode) "New Entry" else "Entry Details"
        toolbar.setNavigationOnClickListener {
            if (isEditMode || isCreateMode) {
                exitEditMode(view)
            } else {
                findNavController().navigateUp()
            }
        }
        
        toolbar.inflateMenu(R.menu.menu_entry_detail)
        
        // Only show Delete menu item when viewing an existing entry (not creating, not editing)
        toolbar.menu.findItem(R.id.action_delete).isVisible = !isCreateMode && !isEditMode

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    enterEditMode(view)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }
                else -> false
            }
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Entry")
            .setMessage("Delete this entry? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                vaultEntry?.let { 
                    viewModel.deleteEntry(it.id)
                    findNavController().navigateUp()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupObservers(view: View, entryId: String?) {
        viewModel.vaultState.observe(viewLifecycleOwner) { state ->
            if (state == VaultState.LOCKED) {
                findNavController().popBackStack(R.id.entryListFragment, false)
            }
        }

        if (entryId != null) {
            viewModel.entries.observe(viewLifecycleOwner) { entries ->
                val entry = entries.firstOrNull { it.id == entryId } ?: return@observe
                vaultEntry = entry
                if (!isEditMode && !isCreateMode) {
                    bindEntryViewMode(view, entry)
                }
            }
        }

        viewModel.saveState.observe(viewLifecycleOwner) { state ->
            val saveButton = view?.findViewById<Button>(R.id.saveButton)
            val cancelButton = view?.findViewById<Button>(R.id.cancelButton)

            when (state) {
                is SaveState.Saving -> {
                    saveButton?.isEnabled = false
                    cancelButton?.isEnabled = false
                }
                is SaveState.Success -> {
                    saveButton?.isEnabled = true
                    cancelButton?.isEnabled = true
                    exitEditMode(view ?: return@observe)
                    Toast.makeText(requireContext(), "Changes saved", Toast.LENGTH_SHORT).show()
                    viewModel.resetSaveState()
                }
                is SaveState.Error -> {
                    saveButton?.isEnabled = true
                    cancelButton?.isEnabled = true
                    Toast.makeText(requireContext(), "Save failed: ${state.theMessage}", Toast.LENGTH_LONG).show()
                    viewModel.resetSaveState()
                }
                is SaveState.Idle -> {
                    saveButton?.isEnabled = true
                    cancelButton?.isEnabled = true
                }
            }
        }
    }

    private fun bindEntryViewMode(view: View, entry: VaultEntry) {
        view.findViewById<TextView>(R.id.titleTextView).text = entry.title
        
        val usernameTv = view.findViewById<TextView>(R.id.usernameTextView)
        usernameTv.text = entry.username ?: "---"
        usernameTv.setOnClickListener {
            entry.username?.let { copyToClipboard("Username", it) }
        }

        val passwordTv = view.findViewById<TextView>(R.id.passwordTextView)
        if (!isPasswordVisible) {
            passwordTv.text = "•".repeat(entry.passwordLength)
        }
        passwordTv.setOnClickListener {
            viewModel.copyPassword(entry.id) { password ->
                copyToClipboard("Password", password.concatToString())
            }
        }

        view.findViewById<TextView>(R.id.urlsTextView).text = entry.urls.joinToString("\n").ifEmpty { "---" }
        view.findViewById<TextView>(R.id.notesTextView).text = entry.notes ?: "---"
    }

    private fun setupActionButtons(view: View) {
        val toggleButton = view.findViewById<ImageButton>(R.id.togglePasswordButton)
        toggleButton.setOnClickListener {
            val entry = vaultEntry ?: return@setOnClickListener
            val passwordTextView = view.findViewById<TextView>(R.id.passwordTextView)
            if (isPasswordVisible) {
                hidePassword(entry, passwordTextView, toggleButton)
            } else {
                showPassword(entry, passwordTextView, toggleButton)
            }
        }

        view.findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveChanges(view)
        }

        view.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            exitEditMode(view)
        }
    }

    private fun enterCreateMode(view: View) {
        isEditMode = true
        entryDraft = EntryDraft(
            id = UUID.randomUUID().toString(),
            title = "",
            username = "",
            password = "".toCharArray(),
            url = "",
            notes = ""
        )
        updateUiMode(view)
        clearInputFields(view)
    }

    private fun enterEditMode(view: View) {
        val entry = vaultEntry ?: return
        if (!viewModel.isUnlocked()) return

        isEditMode = true
        entryDraft = EntryDraft(
            id = entry.id,
            title = entry.title,
            username = entry.username,
            password = viewModel.requestVisiblePassword(entry.id),
            url = entry.urls.joinToString("\n"),
            notes = entry.notes
        )

        updateUiMode(view)
        populateInputFields(view, entryDraft!!)
    }

    private fun exitEditMode(view: View) {
        if (isCreateMode) {
            findNavController().navigateUp()
            return
        }
        isEditMode = false
        entryDraft?.clear()
        entryDraft = null
        updateUiMode(view)
        vaultEntry?.let { bindEntryViewMode(view, it) }
    }

    private fun updateUiMode(view: View) {
        val viewVisibility = if (isEditMode) View.GONE else View.VISIBLE
        val editVisibility = if (isEditMode) View.VISIBLE else View.GONE

        // View mode components
        view.findViewById<TextView>(R.id.titleTextView).visibility = viewVisibility
        view.findViewById<TextView>(R.id.usernameTextView).visibility = viewVisibility
        view.findViewById<LinearLayout>(R.id.passwordViewContainer).visibility = viewVisibility
        view.findViewById<TextView>(R.id.urlsTextView).visibility = viewVisibility
        view.findViewById<TextView>(R.id.notesTextView).visibility = viewVisibility

        // Edit mode components
        view.findViewById<TextInputLayout>(R.id.titleInputLayout).visibility = editVisibility
        view.findViewById<TextInputLayout>(R.id.usernameInputLayout).visibility = editVisibility
        view.findViewById<TextInputLayout>(R.id.passwordInputLayout).visibility = editVisibility
        view.findViewById<TextInputLayout>(R.id.urlInputLayout).visibility = editVisibility
        view.findViewById<TextInputLayout>(R.id.notesInputLayout).visibility = editVisibility
        view.findViewById<LinearLayout>(R.id.editButtonsContainer).visibility = editVisibility

        // Toolbar menu
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.menu.findItem(R.id.action_edit).isVisible = !isEditMode
        toolbar.menu.findItem(R.id.action_delete).isVisible = !isEditMode && !isCreateMode
    }

    private fun clearInputFields(view: View) {
        view.findViewById<TextInputEditText>(R.id.titleEditText).setText("")
        view.findViewById<TextInputEditText>(R.id.usernameEditText).setText("")
        view.findViewById<TextInputEditText>(R.id.passwordEditText).setText("")
        view.findViewById<TextInputEditText>(R.id.urlEditText).setText("")
        view.findViewById<TextInputEditText>(R.id.notesEditText).setText("")
    }

    private fun populateInputFields(view: View, draft: EntryDraft) {
        view.findViewById<TextInputEditText>(R.id.titleEditText).setText(draft.title)
        view.findViewById<TextInputEditText>(R.id.usernameEditText).setText(draft.username)
        view.findViewById<TextInputEditText>(R.id.passwordEditText).setText(draft.password?.concatToString())
        view.findViewById<TextInputEditText>(R.id.urlEditText).setText(draft.url)
        view.findViewById<TextInputEditText>(R.id.notesEditText).setText(draft.notes)
    }

    private fun saveChanges(view: View) {
        val draft = entryDraft ?: return
        
        val newTitle = view.findViewById<TextInputEditText>(R.id.titleEditText).text.toString()
        if (newTitle.isBlank()) {
            Toast.makeText(requireContext(), "Title cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Update draft with values from UI
        draft.title = newTitle
        draft.username = view.findViewById<TextInputEditText>(R.id.usernameEditText).text.toString().ifBlank { null }
        draft.password = view.findViewById<TextInputEditText>(R.id.passwordEditText).text.toString().toCharArray()
        draft.url = view.findViewById<TextInputEditText>(R.id.urlEditText).text.toString()
        draft.notes = view.findViewById<TextInputEditText>(R.id.notesEditText).text.toString().ifBlank { null }

        if (isCreateMode) {
            viewModel.addEntry(draft)
        } else {
            viewModel.saveEntry(draft.id, draft)
        }
    }

    private fun showPassword(entry: VaultEntry, textView: TextView, button: ImageButton) {
        passwordBuffer = viewModel.requestVisiblePassword(entry.id)
        passwordBuffer?.let {
            textView.text = it.concatToString()
            button.setImageResource(R.drawable.ic_visibility_off)
            isPasswordVisible = true
            handler.removeCallbacks(hidePasswordRunnable)
            handler.postDelayed(hidePasswordRunnable, 30_000)
        }
    }

    private fun hidePassword(entry: VaultEntry, textView: TextView, button: ImageButton) {
        textView.text = "•".repeat(entry.passwordLength)
        button.setImageResource(R.drawable.ic_visibility)
        isPasswordVisible = false
        clearPasswordBuffer()
        handler.removeCallbacks(hidePasswordRunnable)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(requireContext(), "$label copied", Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            if (clipboard.primaryClip?.description?.label == label) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }, 30_000)
    }

    private fun clearPasswordBuffer() {
        passwordBuffer?.fill('\u0000')
        passwordBuffer = null
        viewModel.clearVisiblePassword()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(hidePasswordRunnable)
    }

    override fun onStop() {
        super.onStop()
        clearPasswordBuffer()
        entryDraft?.clear()
    }
}
