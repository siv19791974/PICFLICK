package com.picflick.app.ui.screens

import com.picflick.app.Constants
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import coil3.compose.AsyncImage
import com.picflick.app.MainActivity
import com.picflick.app.data.UserProfile
import com.picflick.app.data.getColor
import com.picflick.app.data.getDarkColor
import com.picflick.app.data.getDisplayName
import com.picflick.app.data.getLightColor
import com.picflick.app.repository.FlickRepository
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.components.AddPhotoStyleActionSheet
import com.picflick.app.ui.components.ActionSheetOption
import com.picflick.app.data.getStorageLimitBytes
import com.picflick.app.data.getStorageLimitGB
import com.picflick.app.utils.LocaleHelper
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Settings screen with user preferences and account options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    onManageStorage: () -> Unit = {},
    onPrivacySettings: () -> Unit = {},
    onNotificationsSettings: () -> Unit = {},
    onPlanOptions: () -> Unit = {},
    onHelpSupport: () -> Unit = {},
    onAbout: () -> Unit = {},
    onPhilosophy: () -> Unit = {},
    onLegal: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onDeveloper: () -> Unit = {}
) {
    val context = LocalContext.current
    val appVersionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
        }.getOrDefault("-")
    }
    val isDeveloperUser = userProfile.uid in Constants.DEVELOPER_UIDS
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDeleteAccountFinalDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showContactPopup by remember { mutableStateOf(false) }
    var contactSubject by remember { mutableStateOf("") }
    var contactMessage by remember { mutableStateOf("") }
    var contactCategory by remember { mutableStateOf("GENERAL") }
    var isContactSubmitting by remember { mutableStateOf(false) }
    var contactErrorMessage by remember { mutableStateOf<String?>(null) }
    var showContactCategoryPopup by remember { mutableStateOf(false) }
    val contactCategories = listOf(
        "GENERAL" to "General Feedback",
        "BUG" to "Bug Report",
        "FEATURE" to "Feature Request",
        "BILLING" to "Billing Issue",
        "OTHER" to "Other"
    )
    val contactAppVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" }
            .getOrDefault("Unknown")
    }
    val contactDeviceInfo = remember {
        "Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})"
    }
    val contactRepository = remember { FlickRepository.getInstance() }
    val settingsScope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeveloperPasswordDialog by remember { mutableStateOf(false) }
    var developerPasswordInput by remember { mutableStateOf("") }
    var developerPasswordError by remember { mutableStateOf<String?>(null) }
    // Use ThemeManager for theme state (persists across sessions)
    val isDarkMode by ThemeManager.isDarkMode
    val developerAccessPassword = Constants.DEVELOPER_ACCESS_PASSWORD
    
    // Calculate actual cache size
    fun calculateDirSize(dir: java.io.File): Long {
        if (!dir.exists()) return 0
        var size: Long = 0
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }
    
    fun calculateCacheSize(): String {
        val cacheDir = context.cacheDir
        val size = calculateDirSize(cacheDir)
        return when {
            size > 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
            size > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
            size > 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            else -> "$size B"
        }
    }
    
    var cacheSize by remember { mutableStateOf(calculateCacheSize()) }
    
    // Language state
    var currentLanguage by remember { mutableStateOf(LocaleHelper.getSavedLanguage(context)) }
    
    // Get display info for current language
    fun getLanguageDisplayInfo(code: String): Pair<String, String> {
        return when (code) {
            "ar" -> "🇸🇦" to "العربية"
            "es" -> "🇪🇸" to "Español"
            "fr" -> "🇫🇷" to "Français"
            "de" -> "🇩🇪" to "Deutsch"
            "zh" -> "🇨🇳" to "中文"
            "ja" -> "🇯🇵" to "日本語"
            "ko" -> "🇰🇷" to "한국어"
            "pt" -> "🇵🇹" to "Português"
            "sq" -> "🇦🇱" to "Shqip"
            "hi" -> "🇮🇳" to "हिन्दी"
            "el" -> "🇬🇷" to "Ελληνικά"
            else -> "🇬🇧" to "English"
        }
    }
    val (currentFlag, currentLangName) = getLanguageDisplayInfo(currentLanguage)
    
    // Refresh cache size when dialog is shown
    LaunchedEffect(showClearCacheDialog) {
        if (showClearCacheDialog) {
            cacheSize = calculateCacheSize()
        }
    }

    Scaffold(
        topBar = {
            // Custom compact 48dp title bar
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
                        text = "Settings",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // Spacer for balance (same width as back button)
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
            // Profile Section with Storage Meter and Tier Ring
            ProfileHeaderWithStorage(
                userProfile = userProfile,
                onManageStorage = onManageStorage,
                onProfileClick = onProfileClick,
                isDarkMode = isDarkMode
            )

            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

            // Account Section - Manage Storage and Subscription moved to Profile Header
            SettingsSection(title = "ACCOUNT", isDarkMode = isDarkMode) {
                // Plan Options FIRST - most important
                SettingsItem(
                    icon = Icons.Default.Upgrade,
                    title = "Plan Options",
                    subtitle = "View all plans and upgrade",
                    onClick = onPlanOptions
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications Settings",
                    subtitle = "Push, email preferences",
                    onClick = onNotificationsSettings
                )
            }

            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

            // Preferences Section
            SettingsSection(title = "PREFERENCES", isDarkMode = isDarkMode) {
                SettingsItem(
                    icon = Icons.Default.Menu,
                    title = "Appearance",
                    subtitle = when (ThemeManager.themeMode.value) {
                        ThemeManager.ThemeMode.LIGHT -> "Light mode"
                        ThemeManager.ThemeMode.DARK -> "Dark mode"
                        ThemeManager.ThemeMode.SYSTEM -> "Use phone theme"
                    },
                    onClick = { showAppearanceDialog = true },
                    showArrow = false
                )
                // Language option removed - app uses device default language
                SettingsItem(
                    icon = Icons.Default.Edit,
                    title = "Storage & Data",
                    subtitle = "Clear cache: $cacheSize",
                    onClick = { showClearCacheDialog = true },
                    showArrow = false
                )
            }

            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

            // Support Section - Reorganized with Philosophy, Legal, Privacy Policy, Version
            SettingsSection(title = "SUPPORT", isDarkMode = isDarkMode) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Contact Us",
                    subtitle = "Get help and support",
                    onClick = {
                        contactErrorMessage = null
                        showContactPopup = true
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Menu,
                    title = "Our Philosophy",
                    subtitle = "What we stand for",
                    onClick = onPhilosophy
                )
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Legal",
                    subtitle = "Terms and conditions",
                    onClick = onLegal
                )
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy",
                    subtitle = "Your data, your control",
                    onClick = onPrivacySettings
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = appVersionName,
                    onClick = onAbout
                )
            }

            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

            if (isDeveloperUser) {
                SettingsSection(title = "DEVELOPER", isDarkMode = isDarkMode) {
                    SettingsItem(
                        icon = Icons.Default.DeveloperMode,
                        title = "Developer Tools",
                        subtitle = "Password protected access",
                        onClick = {
                            developerPasswordInput = ""
                            developerPasswordError = null
                            showDeveloperPasswordDialog = true
                        }
                    )
                }
                HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)
            }

            // Danger Zone
            SettingsSection(title = "DANGER ZONE", isDarkMode = isDarkMode) {
                SettingsItem(
                    icon = Icons.Default.Close,
                    title = "Sign Out",
                    titleColor = Color.Red,
                    onClick = { showSignOutDialog = true },
                    showArrow = false
                )
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Delete Account",
                    titleColor = Color.Red,
                    subtitle = "Permanently remove your data",
                    onClick = { showDeleteAccountDialog = true },
                    showArrow = false
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Sign Out - dark sexy popup style
    if (showSignOutDialog) {
        AddPhotoStyleActionSheet(
            title = "Sign Out",
            options = listOf(
                ActionSheetOption(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Sign out now",
                    subtitle = "You'll need to sign in again to access your account",
                    accentColor = Color.Red,
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                )
            ),
            onDismiss = { showSignOutDialog = false },
            cancelTitle = "Stay signed in",
            cancelSubtitle = "Keep me logged in",
            cancelIcon = Icons.Default.Close,
            cancelAccentColor = Color(0xFF4B5563)
        )
    }

    // Delete Account - Step 1 dark sexy popup style
    if (showDeleteAccountDialog) {
        AddPhotoStyleActionSheet(
            title = "Delete Account",
            options = listOf(
                ActionSheetOption(
                    icon = Icons.Default.Warning,
                    title = "Continue to delete",
                    subtitle = "This action is permanent and cannot be undone",
                    accentColor = Color.Red,
                    onClick = {
                        showDeleteAccountDialog = false
                        showDeleteAccountFinalDialog = true
                    }
                )
            ),
            onDismiss = { showDeleteAccountDialog = false },
            cancelTitle = "Cancel",
            cancelSubtitle = "Keep my account",
            cancelIcon = Icons.Default.Close,
            cancelAccentColor = Color(0xFF4B5563)
        )
    }

    // Delete Account - Final explicit confirmation in dark sexy popup style
    if (showDeleteAccountFinalDialog) {
        AddPhotoStyleActionSheet(
            title = "Final confirmation",
            options = listOf(
                ActionSheetOption(
                    icon = Icons.Default.Delete,
                    title = "Delete permanently",
                    subtitle = "All photos, friends, chats, and app data will be removed",
                    accentColor = Color.Red,
                    onClick = {
                        showDeleteAccountFinalDialog = false
                        onDeleteAccount()
                    }
                )
            ),
            onDismiss = { showDeleteAccountFinalDialog = false },
            cancelTitle = "Go back",
            cancelSubtitle = "Don't delete account",
            cancelIcon = Icons.Default.Close,
            cancelAccentColor = Color(0xFF4B5563)
        )
    }

    // Clear Cache - dark sexy popup style
    if (showClearCacheDialog) {
        AddPhotoStyleActionSheet(
            title = "Clear Cache",
            options = listOf(
                ActionSheetOption(
                    icon = Icons.Default.Delete,
                    title = "Clear now",
                    subtitle = "Free up $cacheSize (photos/data stay safe)",
                    accentColor = Color.Red,
                    onClick = {
                        showClearCacheDialog = false
                        val cacheDir = context.cacheDir
                        val deleted = cacheDir.deleteRecursively()
                        if (deleted) {
                            cacheSize = calculateCacheSize()
                            android.widget.Toast.makeText(context, "Cache cleared successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            ),
            onDismiss = { showClearCacheDialog = false },
            cancelTitle = "Cancel",
            cancelSubtitle = "Keep cache"
        )
    }

    if (showDeveloperPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeveloperPasswordDialog = false
                developerPasswordInput = ""
                developerPasswordError = null
            },
            title = { Text("Developer Access") },
            text = {
                Column {
                    Text(
                        text = "Enter developer password",
                        color = if (isDarkMode) Color.LightGray else Color.DarkGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = developerPasswordInput,
                        onValueChange = {
                            developerPasswordInput = it
                            developerPasswordError = null
                        },
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = developerPasswordError != null
                    )
                    developerPasswordError?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (developerPasswordInput == developerAccessPassword) {
                            showDeveloperPasswordDialog = false
                            developerPasswordInput = ""
                            developerPasswordError = null
                            onDeveloper()
                        } else {
                            developerPasswordError = "Incorrect password"
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeveloperPasswordDialog = false
                        developerPasswordInput = ""
                        developerPasswordError = null
                    }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White,
            titleContentColor = if (isDarkMode) Color.White else Color.Black,
            textContentColor = if (isDarkMode) Color.White else Color.Black
        )
    }

    if (showContactPopup) {
        ModalBottomSheet(
            onDismissRequest = { showContactPopup = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF121212),
            contentColor = Color.White,
            dragHandle = {
                Surface(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(width = 44.dp, height = 5.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.28f)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Contact Us",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = contactCategories.find { it.first == contactCategory }?.second ?: "General Feedback",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = {
                        IconButton(onClick = { if (!isContactSubmitting) showContactCategoryPopup = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Choose category")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isContactSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2E86DE),
                        unfocusedBorderColor = Color(0xFF2E86DE).copy(alpha = 0.45f),
                        focusedLabelColor = Color(0xFFB7BDC9),
                        unfocusedLabelColor = Color(0xFFB7BDC9)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = contactSubject,
                    onValueChange = { contactSubject = it },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isContactSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2E86DE),
                        unfocusedBorderColor = Color(0xFF2E86DE).copy(alpha = 0.45f),
                        focusedLabelColor = Color(0xFFB7BDC9),
                        unfocusedLabelColor = Color(0xFFB7BDC9)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = contactMessage,
                    onValueChange = { contactMessage = it },
                    label = { Text("Message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    minLines = 4,
                    maxLines = 8,
                    enabled = !isContactSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2E86DE),
                        unfocusedBorderColor = Color(0xFF2E86DE).copy(alpha = 0.45f),
                        focusedLabelColor = Color(0xFFB7BDC9),
                        unfocusedLabelColor = Color(0xFFB7BDC9)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "App Version: $contactAppVersion\nDevice: $contactDeviceInfo",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7BDC9)
                )

                contactErrorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val canSubmitFeedback = contactSubject.isNotBlank() && contactMessage.isNotBlank() && !isContactSubmitting

                Button(
                    onClick = {
                        isContactSubmitting = true
                        contactErrorMessage = null
                        settingsScope.launch {
                            val result = contactRepository.submitFeedback(
                                userId = userProfile.uid,
                                userName = userProfile.displayName.ifBlank { "Anonymous" },
                                userEmail = userProfile.email.ifBlank { "no-email@picflick.app" },
                                subject = contactSubject,
                                message = contactMessage,
                                category = contactCategory,
                                appVersion = contactAppVersion,
                                deviceInfo = contactDeviceInfo
                            )

                            when (result) {
                                is com.picflick.app.data.Result.Success -> {
                                    android.widget.Toast.makeText(context, "Feedback sent", android.widget.Toast.LENGTH_SHORT).show()
                                    showContactPopup = false
                                    contactSubject = ""
                                    contactMessage = ""
                                }
                                is com.picflick.app.data.Result.Error -> {
                                    contactErrorMessage = result.message ?: "Failed to submit feedback"
                                }
                                else -> Unit
                            }

                            isContactSubmitting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSubmitFeedback,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A4A73),
                        disabledContainerColor = Color(0xFFD3D3D3),
                        contentColor = Color.White,
                        disabledContentColor = Color.White
                    )
                ) {
                    if (isContactSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = if (canSubmitFeedback) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit Feedback", color = if (canSubmitFeedback) Color.White else Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    if (showContactCategoryPopup) {
        AddPhotoStyleActionSheet(
            title = "Select category",
            options = contactCategories.map { (value, label) ->
                ActionSheetOption(
                    icon = Icons.Default.Edit,
                    title = label,
                    subtitle = if (value == contactCategory) "Currently selected" else "Use for this message",
                    accentColor = Color(0xFF2E86DE),
                    onClick = {
                        contactCategory = value
                        showContactCategoryPopup = false
                    }
                )
            },
            onDismiss = { showContactCategoryPopup = false },
            cancelTitle = "Close",
            cancelSubtitle = "Keep current category"
        )
    }

    // Appearance popup - reusing Add Photo style component for exact visual parity
    if (showAppearanceDialog) {
        AddPhotoStyleActionSheet(
            title = "Appearance",
            options = listOf(
                ActionSheetOption(
                    icon = Icons.Default.WbSunny,
                    title = "Light Mode",
                    subtitle = if (ThemeManager.themeMode.value == ThemeManager.ThemeMode.LIGHT) "Always use light theme · Active" else "Always use light theme",
                    accentColor = Color(0xFF2E86DE),
                    onClick = {
                        ThemeManager.setThemeMode(context, ThemeManager.ThemeMode.LIGHT)
                        showAppearanceDialog = false
                    }
                ),
                ActionSheetOption(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    subtitle = if (ThemeManager.themeMode.value == ThemeManager.ThemeMode.DARK) "Always use dark theme · Active" else "Always use dark theme",
                    accentColor = Color(0xFF2E86DE),
                    onClick = {
                        ThemeManager.setThemeMode(context, ThemeManager.ThemeMode.DARK)
                        showAppearanceDialog = false
                    }
                ),
                ActionSheetOption(
                    icon = Icons.Default.Smartphone,
                    title = "Use Phone Theme",
                    subtitle = if (ThemeManager.themeMode.value == ThemeManager.ThemeMode.SYSTEM) "Follow your device setting · Active" else "Follow your device setting",
                    accentColor = Color(0xFF2E86DE),
                    onClick = {
                        ThemeManager.setThemeMode(context, ThemeManager.ThemeMode.SYSTEM)
                        showAppearanceDialog = false
                    }
                )
            ),
            onDismiss = { showAppearanceDialog = false },
            cancelSubtitle = "Close appearance"
        )
    }

    // Language Settings Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    "Language",
                    color = if (isDarkMode) Color.White else Color.Black
                )
            },
            text = {
                Column {
                    LanguageOption(
                        flag = "🇬🇧",
                        name = "English",
                        isSelected = currentLanguage.isEmpty(),
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "")
                            LocaleHelper.setLocale(context, "")
                            currentLanguage = ""
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇸🇦",
                        name = "العربية (Arabic)",
                        isSelected = currentLanguage == "ar",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "ar")
                            LocaleHelper.setLocale(context, "ar")
                            currentLanguage = "ar"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇪🇸",
                        name = "Español (Spanish)",
                        isSelected = currentLanguage == "es",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "es")
                            LocaleHelper.setLocale(context, "es")
                            currentLanguage = "es"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇫🇷",
                        name = "Français (French)",
                        isSelected = currentLanguage == "fr",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "fr")
                            LocaleHelper.setLocale(context, "fr")
                            currentLanguage = "fr"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇩🇪",
                        name = "Deutsch (German)",
                        isSelected = currentLanguage == "de",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "de")
                            LocaleHelper.setLocale(context, "de")
                            currentLanguage = "de"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇨🇳",
                        name = "中文 (Chinese)",
                        isSelected = currentLanguage == "zh",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "zh")
                            LocaleHelper.setLocale(context, "zh")
                            currentLanguage = "zh"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇯🇵",
                        name = "日本語 (Japanese)",
                        isSelected = currentLanguage == "ja",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "ja")
                            LocaleHelper.setLocale(context, "ja")
                            currentLanguage = "ja"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇰🇷",
                        name = "한국어 (Korean)",
                        isSelected = currentLanguage == "ko",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "ko")
                            LocaleHelper.setLocale(context, "ko")
                            currentLanguage = "ko"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇵🇹",
                        name = "Português (Portuguese)",
                        isSelected = currentLanguage == "pt",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "pt")
                            LocaleHelper.setLocale(context, "pt")
                            currentLanguage = "pt"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇦🇱",
                        name = "Shqip (Albanian)",
                        isSelected = currentLanguage == "sq",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "sq")
                            LocaleHelper.setLocale(context, "sq")
                            currentLanguage = "sq"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇮🇳",
                        name = "हिन्दी (Hindi)",
                        isSelected = currentLanguage == "hi",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "hi")
                            LocaleHelper.setLocale(context, "hi")
                            currentLanguage = "hi"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray)
                    
                    LanguageOption(
                        flag = "🇬🇷",
                        name = "Ελληνικά (Greek)",
                        isSelected = currentLanguage == "el",
                        isDarkMode = isDarkMode,
                        onClick = { 
                            LocaleHelper.saveLanguage(context, "el")
                            LocaleHelper.setLocale(context, "el")
                            currentLanguage = "el"
                            showLanguageDialog = false
                            LocaleHelper.restartActivity(context as Activity)
                        }
                    )
                    
                    Text(
                        text = "More languages coming soon",
                        fontSize = 12.sp,
                        color = if (isDarkMode) Color.Gray else Color.DarkGray,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Cancel", color = if (isDarkMode) Color.White else Color.Black)
                }
            },
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        )
    }
}

@Composable
private fun ProfileHeaderWithStorage(
    userProfile: UserProfile,
    onManageStorage: () -> Unit,
    onProfileClick: () -> Unit,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val tier = userProfile.getEffectiveTier()
    val tierColor = tier.getColor()
    var storageUsed by remember(userProfile.uid) { mutableStateOf(userProfile.storageUsedBytes) }
    val storageLimit = tier.getStorageLimitBytes()
    val liveUserPhoto = rememberLiveUserPhotoUrl(userProfile.uid, userProfile.photoUrl)
    val rawStoragePercent = if (storageLimit > 0) {
        (storageUsed * 100 / storageLimit).toInt()
    } else 0
    val storagePercent = rawStoragePercent

    // One-time storage fetch (avoids spawning a snapshot listener per settings visit)
    LaunchedEffect(userProfile.uid) {
        val snapshot = runCatching {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userProfile.uid)
                .get()
                .await()
        }.getOrNull()
        snapshot?.getNumericLong("storageUsedBytes")?.let { storageUsed = it }
    }

    // Storage accuracy sync: recalculate from Storage and update profile if drifted.
    // Rate-limited to once per day to avoid expensive recursive listAll() scans.
    LaunchedEffect(userProfile.uid) {
        val prefs = context.getSharedPreferences("picflick_storage_sync", android.content.Context.MODE_PRIVATE)
        val lastSyncKey = "last_sync_${userProfile.uid}"
        val lastSync = prefs.getLong(lastSyncKey, 0L)
        val oneDayMs = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastSync < oneDayMs) {
            android.util.Log.d("SettingsScreen", "Skipping storage recalculation: last sync was <24h ago")
            return@LaunchedEffect
        }

        val recalculation = runCatching { calculateStorageUsedBytesFromFiles(userProfile.uid) }.getOrNull() ?: return@LaunchedEffect
        if (recalculation.hadErrors) {
            android.util.Log.w("SettingsScreen", "Skipping storageUsedBytes update due to partial Storage scan errors for user=${userProfile.uid}")
            return@LaunchedEffect
        }

        val recalculatedBytes = recalculation.totalBytes

        // Avoid noisy writes: only update when drift is meaningful (>= 1MB)
        val drift = kotlin.math.abs(recalculatedBytes - storageUsed)
        if (drift >= 1_048_576L) {
            storageUsed = recalculatedBytes
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userProfile.uid)
                    .update("storageUsedBytes", recalculatedBytes)
                    .await()
            }
        }
        prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).apply()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Top row: Profile Photo with Tier Ring + Name/Storage - CLICKABLE to go to profile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onProfileClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Photo with Tier Color Ring
            Box(
                modifier = Modifier
                    .size(82.dp), // Increased from 76.dp to accommodate thicker ring
                contentAlignment = Alignment.Center
            ) {
                // Outer ring with tier color - DOUBLE THICKNESS
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    tierColor,
                                    tier.getDarkColor(),
                                    tier.getLightColor(),
                                    tierColor
                                )
                            )
                        )
                        .padding(6.dp) // DOUBLED from 3.dp to 6.dp - thicker ring
                ) {
                    // Inner profile photo
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (liveUserPhoto.isNotEmpty()) {
                            AsyncImage(
                                model = liveUserPhoto,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = userProfile.displayName.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Founder badge overlay
                if (userProfile.isFounder) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6200EA))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "★",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Name, Email, and Tier Badge
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userProfile.displayName,
                    color = if (isDarkMode) Color.White else Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Tier Badge Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tier Color Dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tierColor)
                            .border(1.dp, Color.Black, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Tier Name
                    Text(
                        text = tier.getDisplayName().uppercase(),
                        color = tierColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (userProfile.isFounder) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• FOUNDER",
                            color = Color(0xFF6200EA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Manage Storage Button with Storage Bar
            ManageStorageButton(
                usedBytes = storageUsed,
                totalBytes = storageLimit,
                percent = storagePercent,
                onClick = onManageStorage,
                modifier = Modifier.fillMaxWidth(),
                isDarkMode = isDarkMode
            )
        }
    }
}

@Composable
private fun StorageProgressBar(
    usedBytes: Long,
    totalBytes: Long,
    percent: Int,
    tierColor: Color,
    onClick: () -> Unit
) {
    val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
    val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Storage Text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Storage Used",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Text(
                text = String.format(Locale.US, "%.2f GB / %.0f GB", usedGB, totalGB),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Progress Bar Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2C2C2E))
        ) {
            // Progress Fill
                            val progressFraction = (percent / 100f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    tierColor.copy(alpha = 0.7f),
                                    tierColor
                                )
                            )
                        )
                )
        }
        
        // Percentage Text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPercent = percent.coerceAtMost(100)
            Text(
                text = "$displayPercent% full",
                color = if (percent >= 90) Color.Red else Color.Gray,
                fontSize = 12.sp
            )
            if (percent >= 90) {
                Text(
                    text = if (percent >= 110) "Delete or upgrade" else "Warning at 90%",
                    color = Color(0xFF00D09C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon circle with tier color
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (text.contains("Storage")) Icons.Default.Cloud else Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = text,
                color = if (isDarkMode) Color.White else Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ManageStorageButton(
    usedBytes: Long,
    totalBytes: Long,
    percent: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean
) {
    // Color coding: Green (0-60%), Yellow (60-80%), Orange (80-90%), Red (90%+)
    val barColor = when {
        percent < 60 -> Color(0xFF00C853)  // Green
        percent < 80 -> Color(0xFFFFD600)  // Yellow
        percent < 90 -> Color(0xFFFF6D00)  // Orange
        else -> Color.Red                   // Red
    }
    
    val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
    val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
    
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon circle with blue color
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1565C0).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = Color(0xFF1565C0),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Storage Percentage
            Text(
                text = "$percent%",
                color = barColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Storage Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE0E0E0))
            ) {
                val progressFraction = (percent / 100f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // GB used / total
            Text(
                text = String.format(Locale.US, "%.2f / %.0f GB", usedGB, totalGB),
                color = if (isDarkMode) Color.Gray else Color.DarkGray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    isDarkMode: Boolean = true,
    titleColor: Color = if (isDarkMode) Color.White else Color.Black,
    iconBackgroundColor: Color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE3F2FD),
    iconColor: Color = if (titleColor == Color.Red) Color.Red else if (isDarkMode) Color.White else Color(0xFF1565C0)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with custom background
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = if (isDarkMode) Color.Gray else Color.DarkGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    titleColor: Color = if (ThemeManager.isDarkMode.value) Color.White else Color.Black
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (ThemeManager.isDarkMode.value) Color(0xFF2C2C2E) else Color(0xFFE3F2FD)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (titleColor == Color.Red) Color.Red else if (ThemeManager.isDarkMode.value) Color.White else Color(0xFF1565C0),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (ThemeManager.isDarkMode.value) Color.Gray else Color.DarkGray,
                    fontSize = 13.sp
                )
            }
        }

        // Arrow
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Get storage subtitle text for the Manage Storage settings item
 */
private fun getStorageSubtitle(userProfile: UserProfile): String {
    val usedGB = userProfile.calculateStorageUsedGB()
    val totalGB = userProfile.getEffectiveTier().getStorageLimitGB()
    return "$usedGB GB / $totalGB GB used"
}

@Composable
private fun LanguageOption(
    flag: String,
    name: String,
    isSelected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flag emoji
        Text(
            text = flag,
            fontSize = 24.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        // Language name
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = if (isDarkMode) Color.White else Color.Black
        )

        // Checkmark if selected
        if (isSelected) {
            Text("✓", color = Color(0xFF00D09C))
        }
    }
}

private fun DocumentSnapshot.getNumericLong(field: String): Long? {
    val value = get(field) ?: return null
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private data class StorageCalculationResult(
    val totalBytes: Long,
    val hadErrors: Boolean
)

private suspend fun calculateStorageUsedBytesFromFiles(userId: String): StorageCalculationResult {
    val root = FirebaseStorage.getInstance().reference.child("photos").child(userId)
    return calculateFolderBytesRecursive(root)
}

private suspend fun calculateFolderBytesRecursive(folderRef: StorageReference): StorageCalculationResult {
    val listResult = runCatching { folderRef.listAll().await() }.getOrElse {
        return StorageCalculationResult(totalBytes = 0L, hadErrors = true)
    }

    var total = 0L
    var hadErrors = false

    listResult.items.forEach { itemRef ->
        val itemBytes = runCatching { itemRef.metadata.await().sizeBytes }.getOrElse {
            hadErrors = true
            0L
        }
        total += itemBytes
    }

    listResult.prefixes.forEach { childFolder ->
        val childResult = calculateFolderBytesRecursive(childFolder)
        total += childResult.totalBytes
        hadErrors = hadErrors || childResult.hadErrors
    }

    return StorageCalculationResult(totalBytes = total, hadErrors = hadErrors)
}


