package com.swordfish.lemuroid.app.shared.covers

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.swordfish.lemuroid.app.shared.details.gameCleanTitle
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Logo of the game from the libretro thumbnails server in place of its [title], which is shown while the logo loads
 * and when there is none. The logo keeps its shape and is scaled down to fit in [maxWidth] x [maxHeight], aligned to
 * the start like the title.
 */
@Composable
fun GameLogoOrTitle(
    game: Game,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Infinity,
    title: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val logoUrl = remember(game.coverFrontUrl) { GameScreenshots.logoUrl(game) }
    // Loaded at its own size even while it is not drawn (the title is drawn instead until it arrives).
    val painter =
        rememberAsyncImagePainter(
            model = remember(logoUrl) { ImageRequest.Builder(context).data(logoUrl).size(Size.ORIGINAL).build() },
        )
    val hasLogo = logoUrl != null && painter.state is AsyncImagePainter.State.Success

    Box(modifier = modifier) {
        if (hasLogo) {
            Image(
                modifier = Modifier.widthIn(max = maxWidth).heightIn(max = maxHeight),
                painter = painter,
                contentDescription = gameCleanTitle(game),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
        } else {
            title()
        }
    }
}
