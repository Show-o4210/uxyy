package com.example.pvzh.data.api

import com.example.diamond.BuildConfig

/**
 * 与 EA / PVZH 游戏服务器交互的配置与常量。
 * 支持通过 version.json 动态更新 Content-Version。
 */
object ApiConfig {
    const val EA_AUTH_URL = "https://eadp.ea.com/accounts/api/v1/anonymous/login"
    const val CLIENT_ID = "ea-pvzheroes-production"
    val CLIENT_SECRET: String = BuildConfig.EA_CLIENT_SECRET
    const val REDIRECT_URI = "nucleus:rest"

    const val GAME_HOST = "https://pvz-heroes.awspopcap.com"
    const val EADP_CLIENT_ID = "pvzheroes-2015-google-client"
    const val PVZH_PLATFORM = "Android"
    const val PVZH_CLIENT_VERSION = "1.64.6"

    /**
     * 默认 Content-Version 与仓库 version.json 保持对齐。
     * 可通过 CredentialPoolRepository.fetchVersion() 动态更新。
     */
    @Volatile
    var contentVersion: String = "38b9447f96a43d37877273e6d457f8e2"

    /** 兼容旧代码调用的常量名 */
    val PVZH_CONTENT_VERSION: String
        get() = contentVersion

    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
}
