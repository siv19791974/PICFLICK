package com.picflick.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.picflick.app.R

/**
 * Reusable logo component with proper cropping to remove transparent padding
 */
@Composable
fun LogoImage(
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "PicFlick Logo",
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),  // REDUCED from 50dp to 40dp
        contentScale = ContentScale.Fit
    )
}

/**
 * Small logo for headers/toolbars
 */
@Composable
fun SmallLogoImage(modifier: Modifier = Modifier) {
    LogoImage(modifier = modifier)
}

/**
 * Medium logo for normal screens
 */
@Composable
fun MediumLogoImage(modifier: Modifier = Modifier) {
    LogoImage(modifier = modifier)
}

/**
 * Large logo for splash/about screens
 */
@Composable
fun LargeLogoImage(modifier: Modifier = Modifier) {
    LogoImage(modifier = modifier)
}
