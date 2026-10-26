package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.swordfish.lemuroid.app.shared.covers.CoverAspectRatio
import com.swordfish.lemuroid.app.utils.games.GameCardSubtitle
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Game of a grid: every cell has the shape [cellRatio] so the rows stay aligned, and inside it the card (with its
 * corners and shadow) takes the real shape of the cover, at the bottom of the cell, right above the texts. No empty
 * bands around the cover.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LemuroidGameGridCard(
    modifier: Modifier = Modifier,
    game: Game,
    cellRatio: Float,
    subtitle: GameCardSubtitle = GameCardSubtitle.SYSTEM,
    onClick: () -> Unit = { },
    onLongClick: () -> Unit = { },
) {
    Column(
        modifier =
            modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(cellRatio),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Real shape of the cover once loaded, the usual one of its system before.
            val coverRatio = CoverAspectRatio.forGame(game)
            val coverWidth = if (coverRatio >= cellRatio) maxWidth else maxHeight * coverRatio
            ElevatedCard(modifier = Modifier.width(coverWidth)) {
                LemuroidGameImage(game = game, fixedRatio = coverRatio)
            }
        }
        LemuroidGameTexts(game = game, reserveTitleLines = true, subtitle = subtitle)
    }
}
