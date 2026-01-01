package com.oxoghost.hexapass.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oxoghost.hexapass.R
import com.oxoghost.hexapass.domain.model.VaultEntry

class VaultEntryAdapter(
    private val entries: MutableList<VaultEntry>,
    private val onClick: (VaultEntry) -> Unit
) : RecyclerView.Adapter<VaultEntryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.titleText)
        val username: TextView = itemView.findViewById(R.id.usernameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vault_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]

        holder.title.text = entry.title
        holder.username.text = entry.username ?: ""

        holder.itemView.setOnClickListener {
            onClick(entry)
        }
    }

    override fun getItemCount(): Int = entries.size

    /** Call this when vault content changes */
    fun submitList(newEntries: List<VaultEntry>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }
}
