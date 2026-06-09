package com.zyz4.gamepademu.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class LayoutPreset(
    val version: Int = 1,
    val buttons: List<ButtonPosition> = emptyList(),
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): LayoutPreset {
            val type = object : TypeToken<LayoutPreset>() {}.type
            return gson.fromJson(json, type)
        }

        fun toJson(preset: LayoutPreset): String = gson.toJson(preset)
    }

    fun toJson(): String = gson.toJson(this)
}
