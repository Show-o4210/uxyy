package com.example.pvzh.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * 详细的发钻/资源注入执行结果诊断结构。
 */
data class InjectionDetailResult(
    val isSuccess: Boolean,
    val successfulIndex: Int,
    val message: String,
    val logs: List<String> = emptyList(),
)

/**
 * 高并发发钻引擎。
 * 支持单线程轮询、多线程并发竞速（谁快用谁）、并发批量注入、以及可关禁的前置登录校验。
 */
class InjectionEngine(
    private val eaAuth: EaAuthClient = EaAuthClient(),
    private val gameApi: PvzhApiClient = PvzhApiClient(),
) {
    /**
     * 顺序/简易发钻接口（兼容原有方法签名）。
     * 默认开启多线程并发竞速以最大化速度。
     */
    suspend fun injectOnce(
        credentials: List<RuntimeCredential>,
        targetPersonaId: String,
        gems: Int,
        startIndex: Int = 0,
        isCancelled: () -> Boolean = { false },
    ): Pair<Boolean, Int> {
        val detail = injectOnceConcurrent(
            credentials = credentials,
            targetPersonaId = targetPersonaId,
            gems = gems,
            concurrency = 8,
            requirePreflightLogin = false,
            isCancelled = isCancelled,
        )
        return detail.isSuccess to detail.successfulIndex
    }

    /**
     * 顺序轮询发钻（单线程调试用）。
     */
    suspend fun injectOnceSequential(
        credentials: List<RuntimeCredential>,
        targetPersonaId: String,
        gems: Int,
        startIndex: Int = 0,
        requirePreflightLogin: Boolean = false,
        isCancelled: () -> Boolean = { false },
    ): InjectionDetailResult {
        if (credentials.isEmpty()) {
            return InjectionDetailResult(
                isSuccess = false,
                successfulIndex = 0,
                message = "凭证池为空，无法执行发钻",
                logs = listOf("凭证列表无元素"),
            )
        }

        val logs = mutableListOf<String>()
        val n = credentials.size
        val start = ((startIndex % n) + n) % n

        for (offset in 0 until n) {
            if (isCancelled()) {
                logs.add("操作已被用户手动取消")
                return InjectionDetailResult(false, start, "任务已取消", logs)
            }

            val index = (start + offset) % n
            val cred = credentials[index]
            logs.add("尝试凭证 #${cred.id} (index $index, persona ${cred.executorPersonaId})")

            val ok = tryWithCredential(cred, targetPersonaId, gems, requirePreflightLogin, logs, isCancelled)
            if (ok) {
                logs.add("凭证 #${cred.id} 执行成功！")
                return InjectionDetailResult(
                    isSuccess = true,
                    successfulIndex = index,
                    message = "注入成功 (使用凭证 #${cred.id})",
                    logs = logs,
                )
            }
        }

        logs.add("凭证池中所有 $n 张凭证均尝试完毕且失败")
        return InjectionDetailResult(
            isSuccess = false,
            successfulIndex = start,
            message = "所有凭证尝试均失败，请检查网络或更新凭证池",
            logs = logs,
        )
    }

    /**
     * 【高并发竞速发钻】
     * 多线程同时尝试凭证池中的凭证，一旦任意一张凭证发钻成功，立即返回结果。
     * 速度提升数倍至数十倍。
     *
     * @param concurrency 并发线程数（默认 8）
     * @param requirePreflightLogin 是否需要前置 updateDailyLogin（默认 false，直接发起同步，无负荷且更迅速）
     */
    suspend fun injectOnceConcurrent(
        credentials: List<RuntimeCredential>,
        targetPersonaId: String,
        gems: Int,
        concurrency: Int = 8,
        requirePreflightLogin: Boolean = false,
        isCancelled: () -> Boolean = { false },
    ): InjectionDetailResult = coroutineScope {
        if (credentials.isEmpty()) {
            return@coroutineScope InjectionDetailResult(
                isSuccess = false,
                successfulIndex = 0,
                message = "凭证池为空，无法执行发钻",
                logs = listOf("凭证列表无元素"),
            )
        }

        val logs = Collections.synchronizedList(mutableListOf<String>())
        val isFinished = AtomicBoolean(false)
        val successResult = AtomicReference<InjectionDetailResult?>(null)
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))

        logs.add("启动高并发竞速发钻 (并发度: $concurrency, 前置登录: $requirePreflightLogin)...")

        val jobs = credentials.mapIndexed { index, cred ->
            async(Dispatchers.IO) {
                if (isFinished.get() || isCancelled()) return@async

                semaphore.withPermit {
                    if (isFinished.get() || isCancelled()) return@withPermit

                    val taskLogs = mutableListOf<String>()
                    taskLogs.add("线程尝试凭证 #${cred.id} (index $index)")

                    val ok = tryWithCredential(
                        cred = cred,
                        targetPersonaId = targetPersonaId,
                        gems = gems,
                        requirePreflightLogin = requirePreflightLogin,
                        logs = taskLogs,
                        isCancelled = { isFinished.get() || isCancelled() },
                    )

                    if (ok && isFinished.compareAndSet(false, true)) {
                        taskLogs.add("凭证 #${cred.id} 竞速获胜！")
                        logs.addAll(taskLogs)
                        val res = InjectionDetailResult(
                            isSuccess = true,
                            successfulIndex = index,
                            message = "高并发注入成功 (凭证 #${cred.id} 竞速成功)",
                            logs = logs.toList(),
                        )
                        successResult.set(res)
                    } else {
                        logs.addAll(taskLogs)
                    }
                }
            }
        }

        // 等待并发任务
        jobs.forEach { runCatching { it.await() } }

        val winner = successResult.get()
        if (winner != null) {
            return@coroutineScope winner
        }

        if (isCancelled()) {
            return@coroutineScope InjectionDetailResult(false, 0, "任务已被用户取消", logs.toList())
        }

        InjectionDetailResult(
            isSuccess = false,
            successfulIndex = 0,
            message = "并发尝试所有 ${credentials.size} 张凭证均未成功",
            logs = logs.toList(),
        )
    }

    /**
     * 【并发批量发钻】
     * 对多次发钻任务进行多线程并发并行处理。
     */
    suspend fun injectBatchConcurrent(
        credentials: List<RuntimeCredential>,
        targetPersonaId: String,
        gemsPerTimes: Int,
        times: Int,
        concurrency: Int = 8,
        requirePreflightLogin: Boolean = false,
        isCancelled: () -> Boolean = { false },
        onProgress: (completed: Int) -> Unit = {},
    ): List<InjectionDetailResult> = coroutineScope {
        if (times <= 0 || credentials.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        val completed = AtomicInteger(0)

        val tasks = (0 until times).map { count ->
            async(Dispatchers.IO) {
                val result = if (isCancelled()) {
                    InjectionDetailResult(false, 0, "批处理在第 ${count + 1} 次前被取消")
                } else {
                    semaphore.withPermit {
                        // 每次失败后按凭证顺序自动切换到下一枚 Token，直到成功或池耗尽。
                        injectOnceSequential(
                            credentials = credentials,
                            targetPersonaId = targetPersonaId,
                            gems = gemsPerTimes,
                            requirePreflightLogin = requirePreflightLogin,
                            isCancelled = isCancelled,
                        )
                    }
                }
                onProgress(completed.incrementAndGet())
                result
            }
        }
        tasks.awaitAll()
    }

    private suspend fun tryWithCredential(
        cred: RuntimeCredential,
        targetPersonaId: String,
        gems: Int,
        requirePreflightLogin: Boolean,
        logs: MutableList<String>,
        isCancelled: () -> Boolean,
    ): Boolean {
        if (isCancelled()) return false

        // 确保 EA token 处于大致可用状态
        eaAuth.ensureAccessToken(cred, forceRefresh = false)

        var result = runPipeline(cred, targetPersonaId, gems, requirePreflightLogin)
        if (result is ApiCallResult.Success) return true

        logs.add("  - 凭证 #${cred.id} 首次请求结果: $result")

        if (result is ApiCallResult.AuthFailed || result is ApiCallResult.Failed) {
            if (isCancelled()) return false
            logs.add("  - 凭证 #${cred.id} 刷新 EA Token 并重试...")

            val refreshed = eaAuth.refresh(cred)
            if (refreshed) {
                result = runPipeline(cred, targetPersonaId, gems, requirePreflightLogin)
                if (result is ApiCallResult.Success) return true
                logs.add("  - 凭证 #${cred.id} 刷新后重试结果: $result")
            } else {
                logs.add("  - 凭证 #${cred.id} Token 刷新失败")
            }
        }

        return false
    }

    private suspend fun runPipeline(
        cred: RuntimeCredential,
        targetPersonaId: String,
        gems: Int,
        requirePreflightLogin: Boolean,
    ): ApiCallResult {
        // 如果开启了前置登录，先发 updateDailyLogin；否则直接进行联赛奖励同步（更高速）
        if (requirePreflightLogin) {
            val login = gameApi.updateDailyLogin(
                targetPersonaId = targetPersonaId,
                executorPersonaId = cred.executorPersonaId,
                accessToken = cred.accessToken,
            )
            if (login is ApiCallResult.AuthFailed) return login
        }

        val league = gameApi.syncLeagueRewards(
            targetPersonaId = targetPersonaId,
            executorPersonaId = cred.executorPersonaId,
            accessToken = cred.accessToken,
            gems = gems,
        )

        return when (league) {
            is ApiCallResult.Success -> ApiCallResult.Success
            is ApiCallResult.AuthFailed -> league
            else -> league
        }
    }
}
