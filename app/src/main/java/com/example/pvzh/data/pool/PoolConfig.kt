package com.example.pvzh.data.pool

/** 凭证池与游戏版本的数据源配置。 */
object PoolConfig {
    const val OWNER = "Show-o4210"
    const val REPO = "uxyy"
    const val BRANCH = "main"
    const val TOKEN_PATH = "token.json"
    const val VERSION_PATH = "version.json"

    private fun urls(path: String): List<String> {
        val raw = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/$path"
        return listOf(
            // 优先 CDN/代理，最后回退 GitHub 官方 Raw。
            "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$BRANCH/$path",
            "https://ghproxy.net/$raw",
            "https://gh-proxy.com/$raw",
            raw,
        )
    }

    val tokenUrls: List<String> get() = urls(TOKEN_PATH)
    val versionUrls: List<String> get() = urls(VERSION_PATH)
}
