package com.example.pvzh.data.api

import com.example.pvzh.data.pool.PoolCredential

/** 运行时可刷新的凭证副本。 */
data class RuntimeCredential(
    val id: Int,
    var accessToken: String,
    var refreshToken: String,
    val executorPersonaId: String,
    var expiresAtEpochSec: Long = 0L,
) {
    fun isAccessLikelyValid(skewSec: Long = 300): Boolean {
        if (expiresAtEpochSec <= 0L) return accessToken.isNotBlank()
        val now = System.currentTimeMillis() / 1000
        return now < (expiresAtEpochSec - skewSec)
    }

    companion object {
        fun fromPool(pool: List<PoolCredential>): List<RuntimeCredential> =
            pool.map {
                RuntimeCredential(
                    id = it.id,
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                    executorPersonaId = it.executorPersonaId,
                    expiresAtEpochSec = 0L,
                )
            }
    }
}
