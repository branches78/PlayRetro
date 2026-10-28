package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.swordfish.lemuroid.app.shared.covers.CoverAspectRatio
import com.swordfish.lemuroid.app.utils.games.GameCardSubtitle
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Game of a grid or a home row, like the cards of the TV: every cell has room for a cover of the shape [cellRatio]
 * and the texts, so the rows stay aligned, and inside it one card (with its corners and shadow) with the cover in its
 * real shape and the name and [subtitle] below, as wide as the cover and at the top of the cell, so all the covers of
 * a row start at the same height. No empty bands around the cover; on narrow covers the name is cut.
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
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        val coverAreaHeight = maxWidth / cellRatio
        // Real shape of the cover once loaded, the usual one of its system before.
        val coverRatio = CoverAspectRatio.forGame(game)
        val coverWidth = if (coverRatio >= cellRatio) maxWidth else coverAreaHeight * coverRatio

        // Size of the cell: the room of the cover and of the texts, the same whatever the shape of the cover.
        Column(modifier = Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics { }) {
            Spacer(modifier = Modifier.fillMaxWidth().height(coverAreaHeight))
            LemuroidGameTexts(game = game, singleLineTitle = true, subtitle = subtitle)
        }
        ElevatedCard(modifier = Modifier.width(coverWidth)) {
            Column(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
                LemuroidGameImage(game = game, fixedRatio = coverRatio)
                LemuroidGameTexts(game = game, singleLineTitle = true, subtitle = subtitle)
            }
        }
    }
}
