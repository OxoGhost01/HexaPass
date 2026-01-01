package com.oxoghost.hexapass.domain.model

data class VaultEntry(
    val id: String,
    val title: String,
    val username: String?,
    val urls: List<String>,
    val notes: String?,
    val passwordLength: Int
)
