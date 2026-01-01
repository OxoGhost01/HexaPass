package com.oxoghost.hexapass.domain.model

class SecurePassword(private var value: CharArray?) {

    fun get(): CharArray? = value

    fun wipe() {
        value?.fill('\u0000')
        value = null
    }
}
