package com.adamoutler.ssh.ui.screens.audit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamoutler.ssh.security.audit.AuditEventType
import com.adamoutler.ssh.security.audit.AuditIntegrityResult
import com.adamoutler.ssh.security.audit.AuditRecord
import com.adamoutler.ssh.security.audit.SecurityAuditLogManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AuditCategoryFilter(val label: String) {
    ALL("All"),
    AUTH("Auth"),
    SESSIONS("Sessions"),
    HOST_KEYS("Host Keys"),
    APP_LOCK("App Lock"),
    KEYS("Keys"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditLogScreen(
    onNavigateBack: () -> Unit,
    auditManager: SecurityAuditLogManager? = null,
) {
    val context = LocalContext.current
    val manager = remember {
        auditManager ?: SecurityAuditLogManager.getInstance(context)
    }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var records by remember { mutableStateOf<List<AuditRecord>>(emptyList()) }
    var integrityResult by remember { mutableStateOf<AuditIntegrityResult>(AuditIntegrityResult.EmptyLog) }
    var selectedFilter by remember { mutableStateOf(AuditCategoryFilter.ALL) }
    var expandedRecordSeq by remember { mutableStateOf<Long?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    fun refreshData() {
        coroutineScope.launch {
            isVerifying = true
            records = manager.getRecords()
            integrityResult = manager.verifyIntegrity()
            isVerifying = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Security Audit Log") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("AuditLogBackButton"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshData() },
                        enabled = !isVerifying,
                        modifier = Modifier.testTag("VerifyChainButton"),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Re-verify Hash Chain")
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val jsonLd = manager.exportLogAsJsonLd()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("CoSSH Audit Log JSON-LD", jsonLd)
                                clipboard.setPrimaryClip(clip)
                                snackbarHostState.showSnackbar("Audit Log exported to clipboard (JSON-LD)")
                            }
                        },
                        modifier = Modifier.testTag("ExportAuditLogButton"),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Export Audit Log")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        SecurityAuditLogContent(
            records = records,
            integrityResult = integrityResult,
            modifier = Modifier.padding(paddingValues),
            selectedFilter = selectedFilter,
            expandedRecordSeq = expandedRecordSeq,
            onFilterChange = { selectedFilter = it },
            onRecordToggle = { seq -> expandedRecordSeq = if (expandedRecordSeq == seq) null else seq },
            onCopyHash = { hash ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Hash", hash))
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Hash copied to clipboard")
                }
            },
        )
    }
}

@Composable
fun SecurityAuditLogContent(
    records: List<AuditRecord>,
    integrityResult: AuditIntegrityResult,
    modifier: Modifier = Modifier,
    selectedFilter: AuditCategoryFilter = AuditCategoryFilter.ALL,
    expandedRecordSeq: Long? = null,
    onFilterChange: (AuditCategoryFilter) -> Unit = {},
    onRecordToggle: (Long) -> Unit = {},
    onCopyHash: (String) -> Unit = {},
) {
    val filteredRecords = remember(records, selectedFilter) {
        when (selectedFilter) {
            AuditCategoryFilter.ALL -> records.reversed()

            AuditCategoryFilter.AUTH -> records.filter {
                it.eventType == AuditEventType.AUTH_FAILURE || it.payload.containsKey("auth_type")
            }.reversed()

            AuditCategoryFilter.SESSIONS -> records.filter {
                it.eventType == AuditEventType.SESSION_CONNECTED || it.eventType == AuditEventType.SESSION_TERMINATED
            }.reversed()

            AuditCategoryFilter.HOST_KEYS -> records.filter {
                it.eventType == AuditEventType.HOST_KEY_VERIFIED || it.eventType == AuditEventType.HOST_KEY_REJECTED
            }.reversed()

            AuditCategoryFilter.APP_LOCK -> records.filter {
                it.eventType == AuditEventType.APP_LOCK_CONFIGURED ||
                    it.eventType == AuditEventType.APP_LOCK_UNLOCKED ||
                    it.eventType == AuditEventType.APP_LOCK_FAILED
            }.reversed()

            AuditCategoryFilter.KEYS -> records.filter {
                it.eventType == AuditEventType.KEY_PAIR_GENERATED ||
                    it.eventType == AuditEventType.SECURITY_CONFIG_MODIFIED ||
                    it.eventType == AuditEventType.SECURITY_INITIALIZED ||
                    it.eventType == AuditEventType.CERTIFICATE_VALIDATED
            }.reversed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Status Banner: Hash Chain Integrity Verified vs TAMPERING DETECTED
        IntegrityStatusBanner(integrityResult)

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("AuditFilterChipsRow"),
        ) {
            items(AuditCategoryFilter.values()) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) },
                    modifier = Modifier.testTag("FilterChip_${filter.name}"),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Records List
        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(40.dp))
                    Text(
                        text = "No audit log entries matching '${selectedFilter.label}'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().testTag("AuditLogList"),
            ) {
                items(filteredRecords, key = { it.sequenceNumber }) { record ->
                    val isExpanded = expandedRecordSeq == record.sequenceNumber
                    AuditRecordCard(
                        record = record,
                        isExpanded = isExpanded,
                        onToggleExpand = { onRecordToggle(record.sequenceNumber) },
                        onCopyHash = onCopyHash,
                    )
                }
            }
        }
    }
}

@Composable
fun IntegrityStatusBanner(result: AuditIntegrityResult) {
    when (result) {
        is AuditIntegrityResult.Valid -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B).copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                    .testTag("IntegrityBannerValid"),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = "Hash Chain Integrity Verified ✅",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                        )
                        Text(
                            text = "${result.totalEntries} continuous entries anchored to Keystore head",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is AuditIntegrityResult.Corrupted -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                    .testTag("IntegrityBannerCorrupted"),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Corrupted",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                    Column {
                        Text(
                            text = "TAMPERING DETECTED ⚠️",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Entry #${result.sequenceNumber}: ${result.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        is AuditIntegrityResult.EmptyLog -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().testTag("IntegrityBannerEmpty"),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Audit Log is clean (Genesis State - 0 entries).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun AuditRecordCard(
    record: AuditRecord,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyHash: (String) -> Unit,
) {
    val statusColor = when (record.eventType) {
        AuditEventType.AUTH_FAILURE, AuditEventType.APP_LOCK_FAILED -> Color(0xFFEF4444)
        AuditEventType.HOST_KEY_REJECTED, AuditEventType.APP_LOCK_CONFIGURED -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val formattedTime = remember(record.timestampMs) { dateFormatter.format(Date(record.timestampMs)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("AuditRecord_${record.sequenceNumber}"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape),
                    )
                    Text(
                        text = record.eventType.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "#${record.sequenceNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Primary payload summary
            if (record.payload.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val summary = record.payload.entries.take(2).joinToString(" | ") { "${it.key}: ${it.value}" }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }

            // Expandable hash proof details
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Cryptographic Proof Details",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Parent Hash (H_{n-1}):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = record.previousHash,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        modifier = Modifier.clickable { onCopyHash(record.previousHash) },
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Entry Hash (H_n):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = record.entryHash,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        modifier = Modifier.clickable { onCopyHash(record.entryHash) },
                    )

                    if (record.payload.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Payload Metadata:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        record.payload.forEach { (k, v) ->
                            Text(
                                text = "• $k: $v",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = { onCopyHash(record.entryHash) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Copy Hash Proof", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
