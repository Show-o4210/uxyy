package com.example.pvzh.data.api

/** 极简字段提取，避免依赖完整 JSON 库。 */
internal object SimpleJson {
    fun string(json: String, key: String): String? {
        val m = Regex(""""$key"\s*:\s*"([^"]*)"""").find(json) ?: return null
        return m.groupValues[1]
    }

    fun long(json: String, key: String): Long? {
        val m = Regex(""""$key"\s*:\s*(-?\d+)""").find(json) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    fun int(json: String, key: String): Int? = long(json, key)?.toInt()
}
