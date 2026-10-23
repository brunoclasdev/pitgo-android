package com.trustarx.pitgo.core.account

/** Commercial identity is separate from the operational dispatch mode. */
enum class ProviderKind(val label: String, val locationMode: String) {
    PITTEC("PitTec", "PITTEC"), GUINCHO("Guincho", "MOBILE"), STORE("Loja", "FIXED");

    companion object {
        fun parse(value: String?): ProviderKind? = entries.firstOrNull { it.name == value }
    }
}
