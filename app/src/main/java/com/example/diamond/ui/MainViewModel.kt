package com.example.diamond.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pvzh.data.api.ApiConfig
import com.example.pvzh.data.api.EaAuthClient
import com.example.pvzh.data.api.InjectionEngine
import com.example.pvzh.data.api.RuntimeCredential
import com.example.pvzh.data.pool.CredentialPoolRepository
import com.example.pvzh.data.pool.PoolCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, SUCCESS, WARN, ERROR }

data class LogItem(
    val id: Long = System.currentTimeMillis() + (Math.random() * 1000).toLong(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val message: String,
    val level: LogLevel = LogLevel.INFO,
)

data class MainUiState(
    val targetPersonaId: String = "",
    val gemsAmount: String = "10000",
    val sendTimes: String = "1",
    val poolCredentials: List<PoolCredential> = emptyList(),
    val runtimeCredentials: List<RuntimeCredential> = emptyList(),
    val contentVersion: String = ApiConfig.contentVersion,
    val isLoadingPool: Boolean = false,
    val isInjecting: Boolean = false,
    val statusMessage: String = "准备就绪",
    val logs: List<LogItem> = emptyList(),
    val isCancelled: Boolean = false,
    val completedTasks: Int = 0,
    val totalTasks: Int = 0,
)

class MainViewModel(
    private val repository: CredentialPoolRepository = CredentialPoolRepository(),
    private val eaAuthClient: EaAuthClient = EaAuthClient(),
    private val injectionEngine: InjectionEngine = InjectionEngine(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var injectionJob: Job? = null

    init {
        loadPool(forceRefresh = false)
    }

    fun updateTargetPersonaId(id: String) {
        _uiState.update { it.copy(targetPersonaId = id.trim()) }
    }

    fun updateGemsAmount(gems: String) {
        val digits = gems.filter { c -> c.isDigit() }
        if (digits.isEmpty()) {
            _uiState.update { it.copy(gemsAmount = "") }
            return
        }
        val valInt = digits.toIntOrNull() ?: 10000
        val clamped = valInt.coerceIn(1, 10000)
        _uiState.update { it.copy(gemsAmount = clamped.toString()) }
    }

    fun updateSendTimes(times: String) {
        val digits = times.filter { c -> c.isDigit() }
        if (digits.isEmpty()) {
            _uiState.update { it.copy(sendTimes = "") }
            return
        }
        _uiState.update { it.copy(sendTimes = digits.trimStart('0').ifEmpty { "0" }) }
    }

    fun loadPool(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPool = true, statusMessage = "正在加载凭证...") }

            val result = repository.loadAll(forceRefresh)
            result.onSuccess { pool ->
                val runtime = RuntimeCredential.fromPool(pool)
                val version = repository.cachedVersionOrNull() ?: ApiConfig.contentVersion
                _uiState.update {
                    it.copy(
                        poolCredentials = pool,
                        runtimeCredentials = runtime,
                        contentVersion = version,
                        isLoadingPool = false,
                        statusMessage = "凭证加载成功",
                    )
                }
                addLog("[SUCCESS] 正常加载凭证成功", LogLevel.SUCCESS)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingPool = false,
                        statusMessage = "凭证加载失败: ${err.message}",
                    )
                }
                addLog("[ERROR] 凭证加载失败", LogLevel.ERROR)
            }
        }
    }

    fun startInjection() {
        val state = _uiState.value
        val personaId = state.targetPersonaId.trim()
        val gems = state.gemsAmount.toIntOrNull() ?: 0
        val times = state.sendTimes.toIntOrNull() ?: 0

        if (personaId.isBlank()) {
            addLog("[WARN] 未输入目标 Persona ID", LogLevel.WARN)
            _uiState.update { it.copy(statusMessage = "请输入目标 Persona ID") }
            return
        }

        if (gems < 1 || gems > 10000) {
            addLog("[WARN] 钻石数量超出允许范围 (1~10000)", LogLevel.WARN)
            _uiState.update { it.copy(statusMessage = "钻石数量必须在 1~10000 之间") }
            return
        }

        if (times < 1) {
            addLog("[WARN] 执行次数必须大于 0", LogLevel.WARN)
            _uiState.update { it.copy(statusMessage = "请输入有效的执行次数") }
            return
        }

        if (state.runtimeCredentials.isEmpty()) {
            addLog("[WARN] 凭证池为空，请重新加载凭证", LogLevel.WARN)
            _uiState.update { it.copy(statusMessage = "凭证池为空") }
            return
        }

        _uiState.update {
            it.copy(
                isInjecting = true,
                isCancelled = false,
                completedTasks = 0,
                totalTasks = times,
                statusMessage = "正在发送中...",
            )
        }

        injectionJob = viewModelScope.launch {
            addLog("[INFO] 开始发送任务 (单次 $gems 钻, 共 $times 次)...", LogLevel.INFO)

            try {
                if (times == 1) {
                    val result = injectionEngine.injectOnceSequential(
                        credentials = state.runtimeCredentials,
                        targetPersonaId = personaId,
                        gems = gems,
                        requirePreflightLogin = true,
                        isCancelled = { _uiState.value.isCancelled },
                    )
                    _uiState.update { it.copy(completedTasks = 1) }
                    if (result.isSuccess) {
                        addLog("[SUCCESS] 注入完成！", LogLevel.SUCCESS)
                        _uiState.update { it.copy(statusMessage = "完成") }
                    } else {
                        addLog("[ERROR] 注入未完成：${result.message}", LogLevel.ERROR)
                        result.logs.takeLast(6).forEach { detail ->
                            addLog("[DETAIL] $detail", LogLevel.ERROR)
                        }
                        _uiState.update { it.copy(statusMessage = "失败") }
                    }
                } else {
                    val results = injectionEngine.injectBatchConcurrent(
                        credentials = state.runtimeCredentials,
                        targetPersonaId = personaId,
                        gemsPerTimes = gems,
                        times = times,
                        concurrency = 32,
                        requirePreflightLogin = true,
                        isCancelled = { _uiState.value.isCancelled },
                        onProgress = { completed ->
                            _uiState.update { it.copy(completedTasks = completed) }
                        },
                    )
                    val successCount = results.count { it.isSuccess }
                    if (successCount > 0) {
                        addLog("[SUCCESS] 批量注入完成 (成功 $successCount/$times 次)！", LogLevel.SUCCESS)
                        _uiState.update { it.copy(statusMessage = "完成 (成功 $successCount/$times 次)") }
                    } else {
                        val reasons = results
                            .groupingBy { it.message }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }
                            .take(3)
                            .joinToString("；") { (message, count) -> "$message ×$count" }
                        addLog("[ERROR] 批量注入未完成：$reasons", LogLevel.ERROR)
                        results.firstOrNull()?.logs?.takeLast(6)?.forEach { detail ->
                            addLog("[DETAIL] $detail", LogLevel.ERROR)
                        }
                        _uiState.update { it.copy(statusMessage = "失败") }
                    }
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(statusMessage = "任务已中止") }
            } catch (e: Exception) {
                addLog("[ERROR] 运行过程出现错误", LogLevel.ERROR)
                _uiState.update { it.copy(statusMessage = "异常终止") }
            } finally {
                _uiState.update { it.copy(isInjecting = false) }
                injectionJob = null
            }
        }
    }

    fun cancelTask() {
        _uiState.update { it.copy(isCancelled = true, isInjecting = false, statusMessage = "任务已中止") }
        injectionJob?.cancel()
        injectionJob = null
        addLog("[WARN] 任务已中止", LogLevel.WARN)
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun copyLogsToClipboard(context: Context) {
        val text = _uiState.value.logs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("PVZH_Logs", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
    }

    private fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        val item = LogItem(message = message, level = level)
        _uiState.update { it.copy(logs = it.logs + item) }
    }
}
