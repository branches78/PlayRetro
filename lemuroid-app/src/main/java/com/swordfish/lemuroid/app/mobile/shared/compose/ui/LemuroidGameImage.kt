package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.app.shared.covers.CoverRatioCache
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun LemuroidGameImage(
    modifier: Modifier = Modifier,
    game: Game,
    // Shape of the image (width / height): covers of another shape are shown whole and centered.
    fixedRatio: Float,
) {
    val fallbackDrawable =
        remember(game) {
            CoverUtils.getFallbackDrawable(game)
        }

    val fallbackPainter = rememberDrawablePainter(drawable = fallbackDrawable)

    val coverUrl = game.displayCoverUrl()
    val coverRatio = CoverRatioCache.get(coverUrl)
    val cardRatio = fixedRatio
    val contentScale =
        if (CoverRatioCache.needsLetterbox(coverRatio, cardRatio)) ContentScale.Fit else ContentScale.Crop

    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .build(),
        contentDescription = game.displayName(),
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(cardRatio),
        fallback = fallbackPainter,
        error = fallbackPainter,
        contentScale = contentScale,
        onSuccess = { CoverRatioCache.store(coverUrl, it.result.drawable) },
    )
}
