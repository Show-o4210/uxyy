package com.example.pvzh.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** EA token 刷新结果详情。 */
sealed class RefreshResult {
    data object Success : RefreshResult()
    data class HttpError(val code: Int, val errorBody: String) : RefreshResult()
    data class InvalidResponse(val message: String) : RefreshResult()
    data class NetworkError(val cause: Throwable) : RefreshResult()
}

class EaAuthClient(
    private val http: OkHttpClient = HttpClients.shared,
) {
    /**
     * 使用 refresh_token 换取新的 access_token，并返回详细结果。
     */
    suspend fun refreshDetailed(cred: RuntimeCredential): RefreshResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", cred.refreshToken)
            .add("client_id", ApiConfig.CLIENT_ID)
            .add("client_secret", ApiConfig.CLIENT_SECRET)
            .add("redirect_uri", ApiConfig.REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url(ApiConfig.EA_AUTH_URL)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-Expand-Results", "true")
            .header("X-Include-Underage", "true")
            .header("User-Agent", ApiConfig.USER_AGENT)
            .post(form)
            .build()

        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext RefreshResult.HttpError(response.code, body)
                }

                val access = SimpleJson.string(body, "access_token")
                    ?: return@withContext RefreshResult.InvalidResponse("响应包中缺失 access_token 字段")

                val refresh = SimpleJson.string(body, "refresh_token") ?: cred.refreshToken
                val expiresIn = SimpleJson.long(body, "expires_in") ?: 3600L

                cred.accessToken = access
                cred.refreshToken = refresh
                cred.expiresAtEpochSec = System.currentTimeMillis() / 1000 + expiresIn
                RefreshResult.Success
            }
        } catch (e: Exception) {
            RefreshResult.NetworkError(e)
        }
    }

    /**
     * 使用 refresh_token 换取新的 access_token（布尔简易返回）。
     * @return true 表示已写入 [cred] 新 token
     */
    suspend fun refresh(cred: RuntimeCredential): Boolean {
        return refreshDetailed(cred) is RefreshResult.Success
    }

    /** 若 token 可能过期则刷新；无过期时间时也尝试刷新一次提高成功率。 */
    suspend fun ensureAccessToken(cred: RuntimeCredential, forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh && cred.isAccessLikelyValid()) {
            return true
        }
        // 池内 access 可能仍有效但无 expires；先不强制，失败再刷
        if (!forceRefresh && cred.expiresAtEpochSec <= 0L && cred.accessToken.isNotBlank()) {
            return true
        }
        return refresh(cred)
    }

    /**
     * 多线程并发预热刷新凭证池中的所有 Token。
     * @param concurrency 并发线程/协程数（默认 8）
     * @return 成功刷新 Token 的凭证数量
     */
    suspend fun refreshAllConcurrent(
        credentials: List<RuntimeCredential>,
        concurrency: Int = 8,
        forceRefresh: Boolean = false,
    ): Int = coroutineScope {
        if (credentials.isEmpty()) return@coroutineScope 0
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))

        val tasks = credentials.map { cred ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    ensureAccessToken(cred, forceRefresh)
                }
            }
        }
        tasks.awaitAll().count { it }
    }
}
