package com.example.pvzh.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed class ApiCallResult {
    data object Success : ApiCallResult()
    data class AuthFailed(val code: Int = 401, val message: String = "未授权或 Token 已失效") : ApiCallResult()
    data class Failed(val code: Int = -1, val message: String = "请求失败", val cause: Throwable? = null) : ApiCallResult()
}

class PvzhApiClient(
    private val http: OkHttpClient = HttpClients.shared,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * 更新每日登录进度。
     * @param targetPersonaId 目标玩家 ID（URL）
     * @param executorPersonaId 执行端身份（Header，凭证池中的 persona）
     */
    suspend fun updateDailyLogin(
        targetPersonaId: String,
        executorPersonaId: String,
        accessToken: String,
        day: Int = 1,
    ): ApiCallResult = withContext(Dispatchers.IO) {
        val url = "${ApiConfig.GAME_HOST}/updateDailyLoginProgress?userId=$targetPersonaId"
        val body = """{"day":$day,"forStreak":true,"currentStreakSetCompleted":false}"""
        postJson(url, executorPersonaId, accessToken, body)
    }

    /**
     * 联赛奖励同步（钻石等）。
     */
    suspend fun syncLeagueRewards(
        targetPersonaId: String,
        executorPersonaId: String,
        accessToken: String,
        gems: Int,
        tickets: Int = 0,
        sparks: Int = 0,
    ): ApiCallResult = withContext(Dispatchers.IO) {
        val url = "${ApiConfig.GAME_HOST}/pvp/v1/leagueRewards/sync?playerId=$targetPersonaId"
        val body =
            """{"tickets":$tickets,"gems":$gems,"sparks":$sparks,"packs":[],"specificCards":[]}"""
        postJson(url, executorPersonaId, accessToken, body)
    }

    private fun postJson(
        url: String,
        executorPersonaId: String,
        accessToken: String,
        jsonBody: String,
    ): ApiCallResult {
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(executorPersonaId, accessToken))
            .post(jsonBody.toRequestBody(jsonMedia))
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> ApiCallResult.Success
                    response.code == 401 || response.code == 403 ->
                        ApiCallResult.AuthFailed(response.code, "HTTP ${response.code}: 凭证被拒绝")
                    else ->
                        ApiCallResult.Failed(response.code, "HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            ApiCallResult.Failed(message = "网络连接异常: ${e.message}", cause = e)
        }
    }

    private fun buildHeaders(executorPersonaId: String, accessToken: String) =
        okhttp3.Headers.Builder()
            .add("Content-Type", "application/json")
            .add("EADP-AUTH-TOKEN", accessToken)
            .add("EADP-PERSONA-ID", executorPersonaId)
            .add("X-EADP-Client-Id", ApiConfig.EADP_CLIENT_ID)
            .add("X-Pvzh-Platform", ApiConfig.PVZH_PLATFORM)
            .add("X-Pvzh-Content-Version", ApiConfig.contentVersion)
            .add("X-Pvzh-Client-Version", ApiConfig.PVZH_CLIENT_VERSION)
            .add("X-Pvzh-UTC", System.currentTimeMillis().toString())
            .add("User-Agent", ApiConfig.USER_AGENT)
            .build()
}
