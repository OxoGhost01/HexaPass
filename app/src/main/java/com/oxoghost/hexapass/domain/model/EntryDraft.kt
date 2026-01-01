package com.oxoghost.hexapass.domain.model

data class EntryDraft(
    val id: String,
    var title: String = "",
    var username: String? = null,
    var password: CharArray? = null,
    var url: String = "",
    var notes: String? = null
) {
    fun clear() {
        password?.fill('\u0000')
        password = null
    }
}
