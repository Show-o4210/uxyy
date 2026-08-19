package com.example.pvzh.data.pool

/**
 * 公用凭证池中的单条执行凭证。
 * 字段名与仓库 JSON 对齐（snake_case 解析）。
 */
data class PoolCredential(
    val id: Int,
    val accessToken: String,
    val refreshToken: String,
    val executorPersonaId: String,
)
