package com.picflick.app.ui.components

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ContentUris
import android.content.Intent
import android.provider.Settings
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.picflick.app.ui.theme.isDarkModeBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SingleSelectMediaItem(
    val uri: Uri,
    val id: Long,
    val dateAddedSeconds: Long
)

@Composable
fun SingleSelectMediaPicker(
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onPhotoSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val visualUserSelectedPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    } else null

    fun hasAnyMediaPermission(): Boolean {
        val hasBase = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            mediaPermission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasSelected = visualUserSelectedPermission != null &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                visualUserSelectedPermission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        return hasBase || hasSelected
    }

    fun isFullMediaAccess(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            mediaPermission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var hasMediaPermission by remember { mutableStateOf(hasAnyMediaPermission()) }
    var isLimitedAccess by remember { mutableStateOf(false) }

    fun checkAccessState() {
        hasMediaPermission = hasAnyMediaPermission()
        isLimitedAccess = hasMediaPermission && !isFullMediaAccess()
    }

    val mediaPermissionsToRequest = remember(mediaPermission, visualUserSelectedPermission) {
        buildList {
            add(mediaPermission)
            if (visualUserSelectedPermission != null) add(visualUserSelectedPermission)
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantedMap ->
        checkAccessState()
    }

    var mediaItems by remember { mutableStateOf<List<SingleSelectMediaItem>>(emptyList()) }
    var isLoadingMedia by remember { mutableStateOf(true) }

    LaunchedEffect(hasMediaPermission) {
        if (!hasMediaPermission) {
            isLoadingMedia = false
            mediaItems = emptyList()
            return@LaunchedEffect
        }
        isLoadingMedia = true
        mediaItems = withContext(Dispatchers.IO) { loadSingleSelectDeviceMedia(context) }
        isLoadingMedia = false
    }

    LaunchedEffect(Unit) {
        checkAccessState()
        if (!hasMediaPermission) {
            mediaPermissionLauncher.launch(mediaPermissionsToRequest.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Black)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Choose from Gallery",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isLimitedAccess) {
            LimitedAccessBanner(
                isDarkMode = isDarkMode,
                onGrantFullAccess = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasMediaPermission -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Allow photo permission to show your gallery",
                            color = if (isDarkMode) Color.White.copy(alpha = 0.9f) else Color(0xFF374151),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { mediaPermissionLauncher.launch(mediaPermissionsToRequest.toTypedArray()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E86DE),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Grant permission")
                        }
                    }
                }

                isLoadingMedia && mediaItems.isEmpty() -> {
                    GridShimmer(
                        modifier = Modifier.fillMaxSize(),
                        itemCount = 18,
                        columns = 3
                    )
                }

                !isLoadingMedia && mediaItems.isEmpty() -> {
                    Text(
                        text = "No photos found on device",
                        color = if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color(0xFF374151),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(mediaItems, key = { it.id }) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onPhotoSelected(item.uri) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.uri)
                                        .size(300)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Media",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_MEDIA_ITEMS = 2000

@Composable
private fun LimitedAccessBanner(
    isDarkMode: Boolean,
    onGrantFullAccess: () -> Unit
) {
    val bgColor = if (isDarkMode) Color(0xFF3D3400) else Color(0xFFFFF3CD)
    val textColor = if (isDarkMode) Color(0xFFFFE082) else Color(0xFF856404)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Limited gallery access. Only selected photos shown.",
            color = textColor,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onGrantFullAccess,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Grant Full Access",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun loadSingleSelectDeviceMedia(context: android.content.Context): List<SingleSelectMediaItem> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED
    )

    val result = mutableListOf<SingleSelectMediaItem>()
    context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val dateTaken = cursor.getLong(dateTakenColumn)
            val dateAdded = cursor.getLong(dateAddedColumn)
            // DATE_TAKEN is millis; DATE_ADDED is seconds. Fallback when DATE_TAKEN missing (0).
            val effectiveDate = if (dateTaken > 0) dateTaken else dateAdded * 1000L
            val contentUri = ContentUris.withAppendedId(collection, id)
            result.add(SingleSelectMediaItem(uri = contentUri, id = id, dateAddedSeconds = effectiveDate))
        }
    }

    // Sort by effective date descending (newest first)
    return result.sortedByDescending { it.dateAddedSeconds }.take(MAX_MEDIA_ITEMS)
}

/**
 * Skeleton shimmer grid shown while device photos are loading.
 * Matches the 3-column grid layout so photos appear to "replace" the placeholders.
 */
@Composable
fun GridShimmer(
    modifier: Modifier = Modifier,
    itemCount: Int = 18,
    columns: Int = 3
) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.1f),
        Color.LightGray.copy(alpha = 0.3f)
    )
    val transition = rememberInfiniteTransition(label = "picker_shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "picker_shimmer_translate"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(itemCount) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}
