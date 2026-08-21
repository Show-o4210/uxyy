package com.example.pvzh.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed class ApiCallResult {
    data class Success(
        val code: Int,
        val responseBody: String,
        val gemsAwarded: Int? = null,
    ) : ApiCallResult()
    data class AuthFailed(val code: Int = 401, val message: String = "未授权或 Token 已失效") : ApiCallResult()
    data class RateLimited(val code: Int = 429, val message: String = "请求过于频繁") : ApiCallResult()
    data class ConcurrentMiss(val code: Int, val responseBody: String) : ApiCallResult()
    data class BusinessRejected(val code: Int, val message: String, val responseBody: String) : ApiCallResult()
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
        postJson(url, executorPersonaId, accessToken, body, expectedGems = gems)
    }

    private fun postJson(
        url: String,
        executorPersonaId: String,
        accessToken: String,
        jsonBody: String,
        expectedGems: Int? = null,
    ): ApiCallResult {
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(executorPersonaId, accessToken))
            .post(jsonBody.toRequestBody(jsonMedia))
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                when {
                    response.code == 401 || response.code == 403 ->
                        ApiCallResult.AuthFailed(response.code, errorMessage(response.code, responseBody, "凭证被拒绝"))
                    response.code == 429 ->
                        ApiCallResult.RateLimited(message = errorMessage(response.code, responseBody, "请求过于频繁"))
                    !response.isSuccessful ->
                        ApiCallResult.Failed(response.code, errorMessage(response.code, responseBody, response.message))
                    expectedGems != null -> validateRewardResponse(response.code, responseBody, expectedGems)
                    else -> ApiCallResult.Success(response.code, responseBody)
                }
            }
        } catch (e: Exception) {
            ApiCallResult.Failed(message = "网络连接异常: ${e.message}", cause = e)
        }
    }

    internal fun validateRewardResponse(code: Int, body: String, expectedGems: Int): ApiCallResult {
        if (!Regex("\"rewards\"\\s*:\\s*\\[").containsMatchIn(body)) {
            return ApiCallResult.BusinessRejected(code, "HTTP $code 但响应中缺少 rewards", body)
        }
        if (Regex("\"rewards\"\\s*:\\s*\\[\\s*]").containsMatchIn(body)) {
            return ApiCallResult.ConcurrentMiss(code, body)
        }
        val awarded = SimpleJson.int(body, "gemsAwarded")
            ?: return ApiCallResult.BusinessRejected(code, "HTTP $code 但响应中缺少 gemsAwarded", body)
        if (awarded != expectedGems) {
            return ApiCallResult.BusinessRejected(
                code,
                "服务器确认发放 $awarded 钻，与请求的 $expectedGems 钻不一致",
                body,
            )
        }
        return ApiCallResult.Success(code, body, awarded)
    }

    private fun errorMessage(code: Int, body: String, fallback: String): String {
        val detail = body.replace(Regex("\\s+"), " ").trim().take(300)
        return "HTTP $code: ${detail.ifBlank { fallback }}"
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
