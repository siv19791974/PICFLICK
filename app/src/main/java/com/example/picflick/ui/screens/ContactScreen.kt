package com.example.picflick.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.picflick.ui.components.LogoImage
import com.example.picflick.ui.components.TopBarWithBackButton
import com.example.picflick.ui.theme.PicFlickBackground
import com.example.picflick.ui.theme.PicFlickBannerBackground

/**
 * Contact screen for sending feedback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                title = "Contact",
                onBackClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PicFlickBackground)
                .padding(padding)
        ) {
            // Logo banner at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PicFlickBannerBackground)
                    .padding(top = 36.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                LogoImage()
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = "mailto:".toUri()
                        putExtra(Intent.EXTRA_SUBJECT, "PicFlick: $subject")
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    context.startActivity(Intent.createChooser(intent, "Send email"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = subject.isNotBlank() && message.isNotBlank()
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Feedback")
            }
        }
    }
}
