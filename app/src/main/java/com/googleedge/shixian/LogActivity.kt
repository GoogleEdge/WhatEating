@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.googleedge.shixian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { ShiXianTheme { LogPage(AppStore(this), ::finish) } }
    }
}

@Composable private fun LogPage(store: AppStore, close: () -> Unit) {
    var logs by remember { mutableStateOf(store.logs.reversed()) }
    Scaffold(topBar = { TopAppBar(title = { Text("运行日志") }, navigationIcon = { IconButton(close) { Icon(Icons.Rounded.ArrowBack, null) } }, actions = { IconButton({ store.clearLogs(); logs = emptyList() }) { Icon(Icons.Rounded.DeleteSweep, null) } }) }) { padding ->
        if (logs.isEmpty()) Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有日志", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) { items(logs) { entry -> LogRow(entry) } }
    }
}

@Composable private fun LogRow(entry: LogEntry) {
    val time = remember(entry.time) { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.time)) }
    ListItem(headlineContent = { Text(entry.message, fontWeight = FontWeight.Medium) }, supportingContent = { Column { Text("$time · ${entry.kind}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary); if (entry.detail.isNotBlank()) Text(entry.detail, fontSize = 12.sp) } }, leadingContent = { Icon(if (entry.kind == "AI 错误") Icons.Rounded.ErrorOutline else Icons.Rounded.Article, null) }); HorizontalDivider()
}
