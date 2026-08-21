package com.example.pvzh.data.pool

/** 解析失败时的自定义异常，包含明确的错误诊断说明。 */
class PoolParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 解析凭证池 token.json 与版本文件 version.json。
 * 纯 Kotlin 实现（不依赖 org.json / Gson），适配 Android/JVM 环境。
 *
 * token.json 期望格式：
 * ```json
 * [
 *   {
 *     "id": 1,
 *     "access_token": "...",
 *     "refresh_token": "...",
 *     "executor_persona_id": "..."
 *   }
 * ]
 * ```
 */
object CredentialPoolParser {

    private val objectRegex = Regex("""\{[^{}]+\}""")
    private fun stringFieldRegex(key: String) = Regex(""""$key"\s*:\s*"([^"]*)"""")
    private fun intFieldRegex(key: String) = Regex(""""$key"\s*:\s*(\d+)""")

    /**
     * 解析 token.json 并提取凭证列表。
     */
    fun parseCredentials(jsonText: String): List<PoolCredential> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) {
            throw PoolParseException("拉取到的 token.json 内容为空 (Empty body)")
        }

        val list = ArrayList<PoolCredential>()
        val matches = objectRegex.findAll(trimmed).toList()

        if (matches.isEmpty()) {
            throw PoolParseException("token.json 格式错误：未找到有效 JSON 对象列表")
        }

        for ((index, match) in matches.withIndex()) {
            val obj = match.value
            val access = stringField(obj, "access_token")
            val refresh = stringField(obj, "refresh_token")
            val persona = stringField(obj, "executor_persona_id")

            if (access.isNullOrBlank() || refresh.isNullOrBlank() || persona.isNullOrBlank()) {
                // 跳过缺项数据
                continue
            }

            val id = intField(obj, "id") ?: (index + 1)
            list.add(
                PoolCredential(
                    id = id,
                    accessToken = access,
                    refreshToken = refresh,
                    executorPersonaId = persona,
                )
            )
        }

        if (list.isEmpty()) {
            throw PoolParseException("token.json 解析完毕，但没有解析到任何有效凭证（缺少 access_token / refresh_token / executor_persona_id）")
        }

        return list
    }

    /** 兼容原接口名称 */
    fun parse(jsonText: String): List<PoolCredential> = parseCredentials(jsonText)

    /**
     * 解析 version.json，获取 Content-Version 字符串（如 MD5）。
     */
    fun parseVersion(versionText: String): String {
        val trimmed = versionText.trim()
            .removeSurrounding("\"", "\"")
            .trim()
        if (trimmed.isEmpty()) {
            throw PoolParseException("version.json 内容为空")
        }
        if (!Regex("^[0-9a-fA-F]{32}$").matches(trimmed)) {
            throw PoolParseException("version.json 不是合法的 32 位 Content-Version")
        }
        return trimmed
    }

    private fun stringField(obj: String, key: String): String? =
        stringFieldRegex(key).find(obj)?.groupValues?.getOrNull(1)

    private fun intField(obj: String, key: String): Int? =
        intFieldRegex(key).find(obj)?.groupValues?.getOrNull(1)?.toIntOrNull()
}
