package com.example.diamond.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFF6EE7F9)
private val Violet = Color(0xFF8B7CFF)
private val Panel = Color(0xE8151A2B)
private val Muted = Color(0xFF9CA7BE)
private const val QQ_GROUP_URL = "https://qm.qq.com/q/r4HIsE2HQc"
private const val GITHUB_URL = "https://github.com/Show-o4210/uxyy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val canStart = !uiState.isInjecting && !uiState.isLoadingPool &&
        uiState.targetPersonaId.isNotBlank() && uiState.runtimeCredentials.isNotEmpty()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF080B14), Color(0xFF101426), Color(0xFF070911))))) {
        Box(Modifier.fillMaxWidth().height(280.dp).background(Brush.radialGradient(listOf(Violet.copy(alpha = .22f), Color.Transparent))))
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xCC090C16)),
                    title = {
                        Surface(shape = RoundedCornerShape(12.dp), color = Violet) {
                            Icon(Icons.Default.Diamond, null, tint = Color.White, modifier = Modifier.padding(8.dp).size(22.dp))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))) }
                        ) {
                            Icon(Icons.Default.Code, "查看开源代码", tint = Color.White)
                        }
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URL))) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Accent.copy(alpha = .6f)),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.Groups, null, tint = Accent, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("加入群聊", color = Accent, fontSize = 13.sp)
                        }
                        IconButton(onClick = { viewModel.loadPool(true) }, enabled = !uiState.isLoadingPool && !uiState.isInjecting) {
                            Icon(Icons.Default.Refresh, "刷新凭证", tint = Color.White)
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusCard(uiState)
                SettingsCard(uiState, viewModel)
                Button(
                    onClick = { if (uiState.isInjecting) viewModel.cancelTask() else viewModel.startInjection() },
                    enabled = uiState.isInjecting || canStart,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isInjecting) Color(0xFFE55E67) else Violet,
                        disabledContainerColor = Color(0xFF292E43), disabledContentColor = Color(0xFF727C93)
                    )
                ) {
                    Icon(if (uiState.isInjecting) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isInjecting) "停止任务" else "开始执行", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (!canStart && !uiState.isInjecting) {
                    Text(
                        if (uiState.targetPersonaId.isBlank()) "填写 Persona ID 后即可开始" else "凭证尚未就绪，请点击右上角刷新",
                        color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                LogCard(uiState, viewModel)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(state: MainUiState) = GlassCard {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text("当前状态", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(state.statusMessage, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Surface(shape = RoundedCornerShape(50), color = if (state.runtimeCredentials.isNotEmpty()) Color(0xFF173E38) else Color(0xFF353243)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = if (state.runtimeCredentials.isNotEmpty()) Color(0xFF55D6A6) else Muted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("${state.runtimeCredentials.size} 个凭证", color = Color.White, fontSize = 12.sp)
            }
        }
    }
    if (state.isLoadingPool || state.isInjecting) {
        Spacer(Modifier.height(14.dp))
        if (state.isInjecting && state.totalTasks > 0) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("任务进度", color = Muted, fontSize = 12.sp)
                Text("${state.completedTasks} / ${state.totalTasks}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { state.completedTasks.toFloat() / state.totalTasks.toFloat() },
                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                color = Accent,
                trackColor = Color.White.copy(alpha = .08f)
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Accent, trackColor = Color.White.copy(alpha = .08f))
        }
    }
}

@Composable
private fun SettingsCard(state: MainUiState, viewModel: MainViewModel) = GlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("任务参数", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
    Spacer(Modifier.height(18.dp))
    AppTextField(state.targetPersonaId, viewModel::updateTargetPersonaId, "目标 Persona ID", Modifier.fillMaxWidth()) {
        if (state.targetPersonaId.isNotEmpty()) IconButton(onClick = { viewModel.updateTargetPersonaId("") }) {
            Icon(Icons.Default.Clear, "清除", tint = Muted)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(state.gemsAmount, viewModel::updateGemsAmount, "钻石数量", Modifier.weight(1f))
        AppTextField(state.sendTimes, viewModel::updateSendTimes, "执行次数", Modifier.weight(1f))
    }
}

@Composable
private fun AppTextField(value: String, change: (String) -> Unit, label: String, modifier: Modifier, trailing: @Composable (() -> Unit)? = null) {
    OutlinedTextField(
        value, change, modifier = modifier, label = { Text(label) }, singleLine = true, trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF3B435A), focusedLabelColor = Accent,
            unfocusedLabelColor = Muted, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            cursorColor = Accent, focusedContainerColor = Color(0x66101523), unfocusedContainerColor = Color(0x66101523)
        )
    )
}

@Composable
private fun LogCard(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.logs.size) { if (state.logs.isNotEmpty()) listState.animateScrollToItem(state.logs.lastIndex) }
    Card(
        Modifier.fillMaxWidth().height(220.dp), RoundedCornerShape(18.dp),
        CardDefaults.cardColors(containerColor = Color(0xF3090C14)), border = BorderStroke(1.dp, Color(0xFF252B3D))
    ) {
        Column(Modifier.fillMaxSize().padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("运行日志", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Row {
                    IconButton(onClick = { viewModel.copyLogsToClipboard(context) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ContentCopy, "复制日志", tint = Muted, modifier = Modifier.size(17.dp)) }
                    IconButton(onClick = viewModel::clearLogs, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.DeleteOutline, "清空日志", tint = Muted, modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(Modifier.height(9.dp))
            if (state.logs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无日志", color = Muted, fontSize = 12.sp) }
            else LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(state.logs, key = { it.id }) { log ->
                    val color = when (log.level) { LogLevel.SUCCESS -> Color(0xFF55D6A6); LogLevel.WARN -> Color(0xFFFFC66D); LogLevel.ERROR -> Color(0xFFFF747C); LogLevel.INFO -> Color(0xFF8AB4F8) }
                    Text("[${log.timestamp}] ${log.message}", color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .09f))
    ) { Column(Modifier.fillMaxWidth().padding(18.dp), content = content) }
}
