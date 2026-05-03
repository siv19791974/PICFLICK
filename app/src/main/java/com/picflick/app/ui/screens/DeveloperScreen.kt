package com.picflick.app.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.firebase.firestore.FirebaseFirestore
import com.picflick.app.Constants
import com.picflick.app.data.Feedback
import com.picflick.app.data.Report
import com.picflick.app.data.UserProfile
import com.picflick.app.navigation.Screen
import com.picflick.app.util.CostControlManager
import com.picflick.app.ui.theme.FeatureFlags
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.viewmodel.AuthViewModel
import com.picflick.app.viewmodel.BillingViewModel
import com.picflick.app.viewmodel.HomeViewModel
import com.picflick.app.viewmodel.UploadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    userProfile: UserProfile,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    uploadViewModel: UploadViewModel,
    billingViewModel: BillingViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isDarkMode by ThemeManager.isDarkMode

    val billingConnected by billingViewModel.isConnected.collectAsState()
    val billingProducts by billingViewModel.products.collectAsState()

    var appVersion by remember { mutableStateOf("-") }
    var buildTypeLabel by remember { mutableStateOf("-") }
    var networkStatus by remember { mutableStateOf("Unknown") }
    var firestoreProbe by remember { mutableStateOf("Not checked") }
    var lastProbeLatencyMs by remember { mutableStateOf<Long?>(null) }

    var showCrashDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetLocalDialog by remember { mutableStateOf(false) }

    // Support shared feedback inbox: any feedback assigned to any developer UID is visible
    val developerUids = Constants.DEVELOPER_UIDS.toList()
    var feedbackItems by remember { mutableStateOf<List<Feedback>>(emptyList()) }
    var feedbackLoading by remember { mutableStateOf(false) }
    var feedbackError by remember { mutableStateOf<String?>(null) }
    var selectedFeedback by remember { mutableStateOf<Feedback?>(null) }

    var reportItems by remember { mutableStateOf<List<Report>>(emptyList()) }
    var reportLoading by remember { mutableStateOf(false) }
    var reportError by remember { mutableStateOf<String?>(null) }
    var selectedReport by remember { mutableStateOf<Report?>(null) }

    val devLogs = remember { mutableStateListOf<String>() }

    val flagFastRefresh by FeatureFlags.fastRefreshMode
    val flagAggressiveReconcile by FeatureFlags.aggressiveReconcile
    val flagVerboseLogging by FeatureFlags.verboseLogs
    val flagDeveloperEntry by FeatureFlags.developerEntryEnabled

    fun loadFeedbackInbox() {
        scope.launch {
            feedbackLoading = true
            feedbackError = null
            try {
                // Firestore rules often reject whereIn on auth.uid fields.
                // Run separate whereEqualTo queries (one per dev UID) in parallel
                // and merge client-side — each query independently satisfies the rule.
                val db = FirebaseFirestore.getInstance()
                val allDocs = withContext(Dispatchers.IO) {
                    developerUids.map { uid ->
                        async {
                            db.collection("feedback")
                                .whereEqualTo("assignedToUid", uid)
                                .limit(60)
                                .get()
                                .await()
                                .documents
                        }
                    }.awaitAll().flatten()
                }

                // Deduplicate by document ID in case of overlap
                feedbackItems = allDocs
                    .distinctBy { it.id }
                    .mapNotNull { doc ->
                        doc.toObject(Feedback::class.java)?.copy(id = doc.getString("id") ?: doc.id)
                    }
                    .sortedByDescending { it.timestamp }
                    .take(80)
                devLogs.add(0, "Feedback inbox loaded (${feedbackItems.size})")
            } catch (e: Exception) {
                feedbackError = e.message ?: "Failed to load feedback inbox"
                devLogs.add(0, "Feedback inbox load failed: ${e.message}")
            } finally {
                feedbackLoading = false
            }
        }
    }

    fun markFeedbackResolved(feedback: Feedback) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("feedback")
                        .document(feedback.id)
                        .update(
                            mapOf(
                                "status" to "RESOLVED",
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                        .await()
                }
                feedbackItems = feedbackItems.map {
                    if (it.id == feedback.id) it.copy(status = "RESOLVED") else it
                }
                selectedFeedback = selectedFeedback?.takeIf { it.id != feedback.id } ?: feedback.copy(status = "RESOLVED")
                devLogs.add(0, "Feedback marked RESOLVED: ${feedback.id}")
                Toast.makeText(context, "Marked resolved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadReportsInbox() {
        scope.launch {
            reportLoading = true
            reportError = null
            try {
                val db = FirebaseFirestore.getInstance()
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("reports")
                        .whereEqualTo("status", "pending")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(50)
                        .get()
                        .await()
                }
                reportItems = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.copy(id = doc.id)
                }
                devLogs.add(0, "Reports inbox loaded (${reportItems.size})")
            } catch (e: Exception) {
                reportError = e.message ?: "Failed to load reports inbox"
                devLogs.add(0, "Reports inbox load failed: ${e.message}")
            } finally {
                reportLoading = false
            }
        }
    }

    fun markReportReviewed(report: Report) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("reports")
                        .document(report.id)
                        .update(
                            mapOf(
                                "status" to "reviewed",
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                        .await()
                }
                reportItems = reportItems.map {
                    if (it.id == report.id) it.copy(status = "reviewed") else it
                }
                selectedReport = selectedReport?.takeIf { it.id != report.id } ?: report.copy(status = "reviewed")
                devLogs.add(0, "Report marked REVIEWED: ${report.id}")
                Toast.makeText(context, "Marked reviewed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
        appVersion = "${pInfo.versionName} ($versionCode)"

        buildTypeLabel = if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            "debug"
        } else {
            "release"
        }

        networkStatus = if (isOnline(context)) "Online" else "Offline"
        loadFeedbackInbox()
        loadReportsInbox()
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Developer",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        },
        containerColor = isDarkModeBackground(isDarkMode)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            DevSectionCard("ENVIRONMENT", isDarkMode) {
                DevInfo("App", appVersion, isDarkMode)
                DevInfo("Build", buildTypeLabel, isDarkMode)
                DevInfo("UID", userProfile.uid, isDarkMode)
                DevInfo("Auth UID", authViewModel.currentUser?.uid ?: "None", isDarkMode)
                DevInfo("Email", userProfile.email, isDarkMode)
                DevInfo("Device", "${Build.MANUFACTURER} ${Build.MODEL}", isDarkMode)
                DevInfo("Android", "API ${Build.VERSION.SDK_INT}", isDarkMode)
            }

            DevSectionCard("NAVIGATION & SCREEN DEBUG", isDarkMode) {
                DevActionRow(Icons.Default.Build, "Go Home") { onNavigate(Screen.Home) }
                DevActionRow(Icons.Default.Build, "Go Settings") { onNavigate(Screen.Settings) }
                DevActionRow(Icons.Default.Build, "Go Profile") { onNavigate(Screen.Profile) }
                DevActionRow(Icons.Default.Notifications, "Go Notifications") { onNavigate(Screen.Notifications) }
            }

            DevSectionCard("SUPPORT / FEEDBACK", isDarkMode) {
                DevActionRow(Icons.Default.Info, "Open Contact / Feedback") { onNavigate(Screen.Contact) }
                DevActionRow(Icons.Default.Refresh, "Refresh Feedback Inbox") { loadFeedbackInbox() }
                DevInfo("Assignee UIDs", developerUids.joinToString(", ") { it.take(8) + "…" }, isDarkMode)
                DevInfo("Inbox count", feedbackItems.size.toString(), isDarkMode)

                when {
                    feedbackLoading -> {
                        Text(
                            text = "Loading inbox...",
                            color = if (isDarkMode) Color.LightGray else Color.DarkGray,
                            fontSize = 13.sp
                        )
                    }
                    feedbackError != null -> {
                        Text(
                            text = feedbackError ?: "Unknown error",
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp
                        )
                    }
                    feedbackItems.isEmpty() -> {
                        Text(
                            text = "No feedback assigned.",
                            color = if (isDarkMode) Color.Gray else Color.DarkGray,
                            fontSize = 13.sp
                        )
                    }
                    else -> {
                        feedbackItems.take(12).forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clickable { selectedFeedback = item },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkMode) Color(0xFF2A2A2D) else Color(0xFFF5F5F5)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = item.subject.ifBlank { "(No subject)" },
                                        color = if (isDarkMode) Color.White else Color.Black,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${item.userName} • ${item.category} • ${item.status}",
                                        color = if (isDarkMode) Color.LightGray else Color.DarkGray,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.message,
                                        color = if (isDarkMode) Color(0xFFCCCCCC) else Color(0xFF555555),
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DevSectionCard("PHOTO REPORTS (MODERATION)", isDarkMode) {
                DevActionRow(Icons.Default.Refresh, "Refresh Reports Inbox") { loadReportsInbox() }
                DevInfo("Pending reports", reportItems.size.toString(), isDarkMode)

                when {
                    reportLoading -> {
                        Text(
                            text = "Loading reports...",
                            color = if (isDarkMode) Color.LightGray else Color.DarkGray,
                            fontSize = 13.sp
                        )
                    }
                    reportError != null -> {
                        Text(
                            text = reportError ?: "Unknown error",
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp
                        )
                    }
                    reportItems.isEmpty() -> {
                        Text(
                            text = "No pending reports.",
                            color = if (isDarkMode) Color.Gray else Color.DarkGray,
                            fontSize = 13.sp
                        )
                    }
                    else -> {
                        reportItems.take(12).forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clickable { selectedReport = item },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkMode) Color(0xFF2A2A2D) else Color(0xFFF5F5F5)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${item.reason} • ${item.status.uppercase()}",
                                        color = if (isDarkMode) Color.White else Color.Black,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Flick: ${item.flickId.take(12)}… • Reporter: ${item.reporterId.take(8)}…",
                                        color = if (isDarkMode) Color.LightGray else Color.DarkGray,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (selectedReport?.id == item.id) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { markReportReviewed(item) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF10B981)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Mark Reviewed", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DevSectionCard("UPLOAD / FEED RECONCILE DEBUG", isDarkMode) {
                DevInfo("Feed items", homeViewModel.flicks.size.toString(), isDarkMode)
                DevInfo("Feed loading", homeViewModel.isLoading.toString(), isDarkMode)
                DevInfo("Batch uploading", uploadViewModel.isUploading.toString(), isDarkMode)
                DevInfo("Daily uploads", uploadViewModel.dailyUploadCount.toString(), isDarkMode)
                DevInfo("Last upload error", uploadViewModel.uploadError ?: "None", isDarkMode)

                DevActionRow(Icons.Default.Refresh, "Force feed refresh") {
                    homeViewModel.loadFlicks(userProfile.uid)
                    if (flagVerboseLogging) devLogs.add(0, "Feed refresh forced")
                }
                DevActionRow(Icons.Default.Sync, "Debounced refresh now") {
                    val delay = if (flagFastRefresh) 0L else 1400L
                    homeViewModel.requestDebouncedFeedRefresh(userProfile.uid, delayMs = delay)
                    if (flagVerboseLogging) devLogs.add(0, "Debounced refresh requested (${delay}ms)")
                }
                DevActionRow(Icons.Default.DeleteSweep, "Clear feed error state") {
                    homeViewModel.clearError()
                    homeViewModel.clearLoadMoreFailure()
                    if (flagVerboseLogging) devLogs.add(0, "Cleared feed error state")
                }
            }

            DevSectionCard("BILLING DEBUG", isDarkMode) {
                DevInfo("Profile tier", userProfile.getEffectiveTier().name, isDarkMode)
                DevInfo("Billing connected", billingConnected.toString(), isDarkMode)
                DevInfo("Products loaded", billingProducts.size.toString(), isDarkMode)
                DevInfo("Active purchase", billingViewModel.hasActiveSubscription().toString(), isDarkMode)
                DevInfo("Storage used", userProfile.storageUsedBytes.toString(), isDarkMode)

                DevActionRow(Icons.Default.Sync, "Query products") {
                    billingViewModel.queryProducts()
                    devLogs.add(0, "Billing products query started")
                }
                DevActionRow(Icons.Default.Restore, "Restore purchases") {
                    billingViewModel.restorePurchases()
                    devLogs.add(0, "Restore purchases triggered")
                }
            }

            DevSectionCard("NOTIFICATIONS / FCM", isDarkMode) {
                DevInfo("FCM token", maskToken(userProfile.fcmToken), isDarkMode)
                DevActionRow(Icons.Default.ContentCopy, "Copy full FCM token") {
                    if (userProfile.fcmToken.isNotBlank()) {
                        clipboard.setText(AnnotatedString(userProfile.fcmToken))
                        Toast.makeText(context, "FCM token copied", Toast.LENGTH_SHORT).show()
                        devLogs.add(0, "FCM token copied")
                    }
                }
            }

            DevSectionCard("HEALTH CHECKS", isDarkMode) {
                DevInfo("Network", networkStatus, isDarkMode)
                DevInfo("Firestore probe", firestoreProbe, isDarkMode)
                DevInfo("Probe latency", lastProbeLatencyMs?.let { "$it ms" } ?: "-", isDarkMode)

                DevActionRow(Icons.Default.NetworkCheck, "Refresh network status") {
                    networkStatus = if (isOnline(context)) "Online" else "Offline"
                    devLogs.add(0, "Network status refreshed: $networkStatus")
                }
                DevActionRow(Icons.Default.Sync, "Run Firestore read probe") {
                    scope.launch {
                        val start = System.currentTimeMillis()
                        firestoreProbe = "Running..."
                        firestoreProbe = try {
                            withContext(Dispatchers.IO) {
                                FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(userProfile.uid)
                                    .get()
                                    .await()
                            }
                            lastProbeLatencyMs = System.currentTimeMillis() - start
                            "PASS"
                        } catch (e: Exception) {
                            lastProbeLatencyMs = System.currentTimeMillis() - start
                            "FAIL: ${e.message ?: "unknown error"}"
                        }
                    }
                }
            }

            DevSectionCard("FEATURE FLAGS", isDarkMode) {
                DevToggleRow("Fast refresh mode", flagFastRefresh, isDarkMode) {
                    FeatureFlags.setFastRefreshMode(context, it)
                    if (flagVerboseLogging) devLogs.add(0, "Flag fast_refresh_mode=$it")
                }
                DevToggleRow("Aggressive navigation reconcile", flagAggressiveReconcile, isDarkMode) {
                    FeatureFlags.setAggressiveReconcile(context, it)
                    if (flagVerboseLogging) devLogs.add(0, "Flag aggressive_reconcile=$it")
                }
                DevToggleRow("Verbose developer logs", flagVerboseLogging, isDarkMode) {
                    FeatureFlags.setVerboseLogs(context, it)
                    if (it) devLogs.add(0, "Flag verbose_logs=true")
                }
                DevToggleRow("Show Developer entry in Settings", flagDeveloperEntry, isDarkMode) {
                    FeatureFlags.setDeveloperEntryEnabled(context, it)
                    if (flagVerboseLogging) devLogs.add(0, "Flag developer_entry_enabled=$it")
                }
                DevActionRow(Icons.Default.Flag, "Reset flags to defaults") {
                    FeatureFlags.resetDefaults(context)
                    if (FeatureFlags.verboseLogs.value) devLogs.add(0, "Feature flags reset")
                }
            }

            DevSectionCard("COST KILL-SWITCHES", isDarkMode) {
                val db = remember { FirebaseFirestore.getInstance() }
                var killSnapshot by remember { mutableStateOf(false) }
                var killChat by remember { mutableStateOf(false) }
                var killNotifications by remember { mutableStateOf(false) }
                var reducePagination by remember { mutableStateOf(false) }
                var disableAnalytics by remember { mutableStateOf(false) }
                var disableBilling by remember { mutableStateOf(false) }
                var freeTierBypass by remember { mutableStateOf(false) }
                var panicMode by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    try {
                        withContext(Dispatchers.IO) {
                            val doc = db.collection(Constants.FeatureFlags.CONFIG_COLLECTION)
                                .document(Constants.FeatureFlags.CONFIG_DOCUMENT)
                                .get()
                                .await()
                            val data = doc.data ?: emptyMap()
                            killSnapshot = data[Constants.FeatureFlags.KILL_SNAPSHOT_LISTENERS] == true
                            killChat = data[Constants.FeatureFlags.KILL_CHAT_LISTENERS] == true
                            killNotifications = data[Constants.FeatureFlags.KILL_NOTIFICATION_LISTENERS] == true
                            reducePagination = data[Constants.FeatureFlags.REDUCE_PAGINATION] == true
                            disableAnalytics = data[Constants.FeatureFlags.DISABLE_ANALYTICS] == true
                            disableBilling = data[Constants.FeatureFlags.DISABLE_BILLING] == true
                            freeTierBypass = data[Constants.FeatureFlags.FREE_TIER_BYPASS] == true
                            panicMode = data[Constants.FeatureFlags.PANIC_MODE] == true
                        }
                        devLogs.add(0, "Kill-switches loaded from Firestore")
                    } catch (e: Exception) {
                        devLogs.add(0, "Kill-switches load failed: ${e.message}")
                    }
                }

                // Panic Mode master switch
                if (panicMode) {
                    Text(
                        text = "PANIC MODE ACTIVE — All cost controls engaged",
                        color = Color(0xFFE53935),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                DevToggleRow("PANIC MODE", panicMode, isDarkMode) {
                    scope.launch {
                        val value = it
                        CostControlManager.writeFlag(Constants.FeatureFlags.PANIC_MODE, value)
                        panicMode = value
                        if (value) {
                            // Cascade all kill-switches ON
                            CostControlManager.writeFlag(Constants.FeatureFlags.KILL_SNAPSHOT_LISTENERS, true)
                            CostControlManager.writeFlag(Constants.FeatureFlags.KILL_CHAT_LISTENERS, true)
                            CostControlManager.writeFlag(Constants.FeatureFlags.KILL_NOTIFICATION_LISTENERS, true)
                            CostControlManager.writeFlag(Constants.FeatureFlags.REDUCE_PAGINATION, true)
                            CostControlManager.writeFlag(Constants.FeatureFlags.DISABLE_ANALYTICS, true)
                            killSnapshot = true
                            killChat = true
                            killNotifications = true
                            reducePagination = true
                            disableAnalytics = true
                            devLogs.add(0, "PANIC MODE ON — all listeners killed, pagination reduced, analytics stopped")
                        } else {
                            // Restore all to OFF
                            CostControlManager.writeFlag(Constants.FeatureFlags.KILL_SNAPSHOT_LISTENERS, false)
                            CostControlManager.writeFlag(Constants.FeatureFlags.KILL_CHAT_LISTENERS, false)
                            CostControlManager.writeFlag(Constants.FeatureFlags.KILL_NOTIFICATION_LISTENERS, false)
                            CostControlManager.writeFlag(Constants.FeatureFlags.REDUCE_PAGINATION, false)
                            CostControlManager.writeFlag(Constants.FeatureFlags.DISABLE_ANALYTICS, false)
                            killSnapshot = false
                            killChat = false
                            killNotifications = false
                            reducePagination = false
                            disableAnalytics = false
                            devLogs.add(0, "Panic mode OFF — normal operation restored")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                DevToggleRow("Kill snapshot listeners", killSnapshot, isDarkMode) {
                    scope.launch {
                        CostControlManager.writeFlag(Constants.FeatureFlags.KILL_SNAPSHOT_LISTENERS, it)
                        killSnapshot = it
                        devLogs.add(0, "killSnapshotListeners=$it (live in ~60s)")
                    }
                }
                DevToggleRow("Kill chat listeners", killChat, isDarkMode) {
                    scope.launch {
                        CostControlManager.writeFlag(Constants.FeatureFlags.KILL_CHAT_LISTENERS, it)
                        killChat = it
                        devLogs.add(0, "killChatListeners=$it (live in ~60s)")
                    }
                }
                DevToggleRow("Kill notification listeners", killNotifications, isDarkMode) {
                    scope.launch {
                        CostControlManager.writeFlag(Constants.FeatureFlags.KILL_NOTIFICATION_LISTENERS, it)
                        killNotifications = it
                        devLogs.add(0, "killNotificationListeners=$it (live in ~60s)")
                    }
                }
                DevToggleRow("Reduce pagination", reducePagination, isDarkMode) {
                    scope.launch {
                        CostControlManager.writeFlag(Constants.FeatureFlags.REDUCE_PAGINATION, it)
                        reducePagination = it
                        devLogs.add(0, "reducePagination=$it (live in ~60s)")
                    }
                }
                DevToggleRow("Disable analytics", disableAnalytics, isDarkMode) {
                    scope.launch {
                        CostControlManager.writeFlag(Constants.FeatureFlags.DISABLE_ANALYTICS, it)
                        disableAnalytics = it
                        devLogs.add(0, "disableAnalytics=$it (immediate)")
                    }
                }
                DevToggleRow("Disable billing", disableBilling, isDarkMode) {
                    scope.launch {
                        CostControlManager.writeFlag(Constants.FeatureFlags.DISABLE_BILLING, it)
                        disableBilling = it
                        devLogs.add(0, "disableBilling=$it (restart app to take effect)")
                    }
                }
                DevToggleRow("Free tier bypass (PRO)", freeTierBypass, isDarkMode) {
                    scope.launch {
                        CostControlManager.writeFlag(Constants.FeatureFlags.FREE_TIER_BYPASS, it)
                        freeTierBypass = it
                        devLogs.add(0, "freeTierBypass=$it (immediate)")
                    }
                }
                DevActionRow(Icons.Default.Sync, "Force refresh flags now") {
                    scope.launch {
                        CostControlManager.refresh()
                        devLogs.add(0, "CostControlManager refreshed")
                    }
                }
            }

            DevSectionCard("QUEUE / RETRY MONITOR", isDarkMode) {
                DevInfo("isUploading", uploadViewModel.isUploading.toString(), isDarkMode)
                DevInfo("uploadSuccess", uploadViewModel.uploadSuccess.toString(), isDarkMode)
                DevInfo("uploadError", uploadViewModel.uploadError ?: "None", isDarkMode)
                DevActionRow(Icons.Default.Refresh, "Reset upload state") {
                    uploadViewModel.resetUploadState()
                    devLogs.add(0, "Upload state reset")
                }
            }

            DevSectionCard("LOG EXPORT", isDarkMode) {
                DevInfo("Entries", devLogs.size.toString(), isDarkMode)
                DevActionRow(Icons.Default.ContentCopy, "Copy logs") {
                    val payload = if (devLogs.isEmpty()) "No logs yet" else devLogs.joinToString("\n")
                    clipboard.setText(AnnotatedString(payload))
                    Toast.makeText(context, "Developer logs copied", Toast.LENGTH_SHORT).show()
                }
                DevActionRow(Icons.Default.DeleteSweep, "Clear logs") {
                    devLogs.clear()
                }
                if (devLogs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    devLogs.take(8).forEach { line ->
                        Text(
                            text = "• $line",
                            color = if (isDarkMode) Color.LightGray else Color.DarkGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            DevSectionCard("SAFE MAINTENANCE ACTIONS", isDarkMode) {
                DevActionRow(Icons.Default.DeleteSweep, "Clear app cache") {
                    showClearCacheDialog = true
                }
                DevActionRow(Icons.Default.Restore, "Reset local preferences") {
                    showResetLocalDialog = true
                }
                DevActionRow(Icons.Default.BugReport, "Crash test (debug only)") {
                    showCrashDialog = true
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    selectedFeedback?.let { feedback ->
        AlertDialog(
            onDismissRequest = { selectedFeedback = null },
            title = { Text(feedback.subject.ifBlank { "Feedback detail" }) },
            text = {
                Column {
                    Text("From: ${feedback.userName} (${feedback.userEmail})")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Category: ${feedback.category}")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Status: ${feedback.status}")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(feedback.message)
                    if (feedback.appVersion.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("App: ${feedback.appVersion}")
                    }
                    if (feedback.deviceInfo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Device: ${feedback.deviceInfo}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    markFeedbackResolved(feedback)
                }) {
                    Text("Mark RESOLVED")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedFeedback = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear app cache?") },
            text = { Text("This clears cached files only. Account data remains safe.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        runCatching { context.cacheDir.deleteRecursively() }
                        Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        devLogs.add(0, "App cache cleared")
                    }
                ) { Text("Clear", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetLocalDialog) {
        AlertDialog(
            onDismissRequest = { showResetLocalDialog = false },
            title = { Text("Reset local preferences?") },
            text = { Text("This resets app-local prefs (theme/language/debug flags).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetLocalDialog = false
                        context.getSharedPreferences("PicFlickPrefs", Context.MODE_PRIVATE).edit { clear() }
                        Toast.makeText(context, "Local preferences reset", Toast.LENGTH_SHORT).show()
                        devLogs.add(0, "Local prefs reset")
                    }
                ) { Text("Reset", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showResetLocalDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCrashDialog) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text("Trigger crash test?") },
            text = { Text("This intentionally crashes the app to verify crash reporting.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCrashDialog = false
                        throw RuntimeException("Developer test crash")
                    }
                ) { Text("Crash now", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showCrashDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DevSectionCard(
    title: String,
    isDarkMode: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF616161),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DevInfo(label: String, value: String, isDarkMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = if (isDarkMode) Color.Gray else Color.DarkGray, fontSize = 13.sp)
        Text(text = value, color = if (isDarkMode) Color.White else Color.Black, fontSize = 13.sp)
    }
}

@Composable
private fun DevActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = title, fontSize = 14.sp)
    }
}

@Composable
private fun DevToggleRow(
    label: String,
    checked: Boolean,
    isDarkMode: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (isDarkMode) Color.White else Color.Black, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun maskToken(token: String): String {
    if (token.isBlank()) return "Not available"
    if (token.length <= 10) return "••••••"
    return token.take(6) + "••••••" + token.takeLast(4)
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
