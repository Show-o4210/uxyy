package com.example.pvzh.data.pool

import com.example.pvzh.data.api.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 凭证池与版本号拉取仓库（瘦身强化版）。
 * 直接拉取 Show-o4210/uxyy 仓库中的 token.json 与 version.json，
 * 去除无用镜像加速逻辑，强化异常处理与结果返回。
 */
class CredentialPoolRepository(
    private val httpClient: OkHttpClient = defaultClient(),
    private val tokenUrls: List<String> = PoolConfig.tokenUrls,
    private val versionUrls: List<String> = PoolConfig.versionUrls,
) {
    @Volatile
    private var cachedCredentials: List<PoolCredential>? = null

    @Volatile
    private var cachedVersion: String? = null

    fun cachedOrNull(): List<PoolCredential>? = cachedCredentials
    fun cachedVersionOrNull(): String? = cachedVersion

    /**
     * 加载/刷新 token.json 凭证池。
     * @param forceRefresh 是否强制重新从网络获取
     */
    suspend fun load(forceRefresh: Boolean = false): Result<List<PoolCredential>> {
        if (!forceRefresh) {
            cachedCredentials?.let { return Result.success(it) }
        }
        return withContext(Dispatchers.IO) {
            val fetched = fetchCredentials()
            fetched.onSuccess { cachedCredentials = it }
            fetched
        }
    }

    /**
     * 拉取 version.json 并自动更新 ApiConfig.contentVersion
     */
    suspend fun fetchVersion(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val text = fetchFirstAvailable(versionUrls)
                val version = CredentialPoolParser.parseVersion(text)
                cachedVersion = version
                ApiConfig.contentVersion = version
                Result.success(version)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 一键获取：同步更新游戏 Content-Version 并获取凭证池
     */
    suspend fun loadAll(forceRefresh: Boolean = false): Result<List<PoolCredential>> {
        val versionResult = fetchVersion()
        if (versionResult.isFailure) {
            return Result.failure(
                IOException("Content-Version 更新失败，已停止加载凭证", versionResult.exceptionOrNull())
            )
        }
        return load(forceRefresh)
    }

    private fun fetchCredentials(): Result<List<PoolCredential>> {
        return try {
            val text = fetchFirstAvailable(tokenUrls)
            val credentials = CredentialPoolParser.parseCredentials(text)
            Result.success(credentials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 网络、HTTP 或空响应失败时，自动尝试下一条镜像。 */
    private fun fetchFirstAvailable(urls: List<String>): String {
        require(urls.isNotEmpty()) { "没有配置可用的数据源" }
        val failures = mutableListOf<String>()
        urls.forEach { url ->
            try {
                return fetchUrlText(url)
            } catch (e: Exception) {
                failures += "${url.substringBefore('?')}: ${e.message}"
            }
        }
        throw IOException("所有数据源均拉取失败：${failures.joinToString(" | ")}")
    }

    private fun fetchUrlText(url: String): String {
        val bust = if (url.contains("?")) "&" else "?"
        val requestUrl = "$url${bust}_=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(requestUrl)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("User-Agent", ApiConfig.USER_AGENT)
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("网络请求失败 [HTTP ${response.code}]: ${response.message} ($url)")
            }
            val bodyText = response.body?.string().orEmpty()
            if (bodyText.isBlank()) {
                throw IOException("网络响应为空 ($url)")
            }
            bodyText
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
