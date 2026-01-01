package com.oxoghost.hexapass.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.oxoghost.hexapass.MainActivity
import com.oxoghost.hexapass.R

class EntryListFragment : Fragment(R.layout.fragment_entry_list) {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: VaultEntryAdapter

    private val openVaultLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult

            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            promptForMasterPassword(uri)
        }

    private fun promptForMasterPassword(vaultUri: Uri) {
        val passwordInput = EditText(requireContext()).apply {
            inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Master password"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Unlock Vault")
            .setView(passwordInput)
            .setPositiveButton("Unlock") { _, _ ->
                val password = passwordInput.text.toString().toCharArray()
                viewModel.openVault(vaultUri, password)
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            requireActivity(),
            (requireActivity() as MainActivity).viewModelFactory
        )[MainViewModel::class.java]


        val recyclerView = view.findViewById<RecyclerView>(R.id.vaultRecyclerView)
        val openVaultFab = view.findViewById<FloatingActionButton>(R.id.openVaultFab)
        val addEntryFab = view.findViewById<FloatingActionButton>(R.id.addEntryFab)
        val emptyText = view.findViewById<TextView>(R.id.emptyStateText)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = VaultEntryAdapter(mutableListOf()) { entry ->
            val action =
                EntryListFragmentDirections
                    .actionEntryListToEntryDetail(entry.id)
            findNavController().navigate(action)
        }
        recyclerView.adapter = adapter

        renderState(openVaultFab, addEntryFab, emptyText)

        viewModel.entries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            renderState(openVaultFab, addEntryFab, emptyText)
        }

        openVaultFab.setOnClickListener {
            openVaultLauncher.launch(arrayOf("application/octet-stream"))
        }

        addEntryFab.setOnClickListener {
            findNavController().navigate(R.id.action_entryList_to_createEntry)
        }
    }

    private fun renderState(
        openFab: FloatingActionButton,
        addFab: FloatingActionButton,
        emptyText: TextView
    ) {
        viewModel.vaultState.observe(viewLifecycleOwner) { state ->
            val locked = state == VaultState.LOCKED
            openFab.isVisible = locked
            emptyText.isVisible = locked
            addFab.isVisible = !locked
        }
    }
}
