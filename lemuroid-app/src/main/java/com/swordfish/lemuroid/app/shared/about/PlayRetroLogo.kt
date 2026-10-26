package com.swordfish.lemuroid.app.shared.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.swordfish.lemuroid.R

/**
 * Logo of the app (its name in pixel letters), in the bright green of the app icon, also on mobile. The width follows
 * its shape.
 */
@Composable
fun PlayRetroLogo(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        modifier = modifier.height(height),
        painter = painterResource(R.drawable.playretro_logo),
        contentDescription = stringResource(R.string.lemuroid_name),
        contentScale = ContentScale.FillHeight,
        colorFilter = ColorFilter.tint(colorResource(R.color.playretro_brand_green)),
    )
}
