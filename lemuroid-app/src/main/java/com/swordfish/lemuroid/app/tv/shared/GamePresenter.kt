package com.swordfish.lemuroid.app.tv.shared

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.covers.CoverAspectRatio
import com.swordfish.lemuroid.app.shared.covers.CoverRatioCache
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.utils.games.GameCardSubtitle
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlin.math.roundToInt

class GamePresenter(
    private val cardSize: Int,
    private val gameInteractor: GameInteractor,
    // Grids: every game takes a square cell of [cardSize] to keep the columns aligned, and the card (with its
    // border and focus frame) takes the real shape of the cover inside it, at the top of the cell so all the covers of
    // a row start at the same height. Otherwise (search) every card has the height [cardSize] and the width of its
    // cover.
    private val fitInCell: Boolean = false,
    // System in the home rows, favorites and search; developer inside a system.
    private val subtitle: GameCardSubtitle = GameCardSubtitle.SYSTEM,
    // Opens the details of a game from the last action of the long press menu.
    private val onOpenDetails: ((Game) -> Unit)? = null,
) : Presenter() {
    override fun onBindViewHolder(
        viewHolder: Presenter.ViewHolder?,
        item: Any?,
    ) {
        if (item == null || viewHolder !is ViewHolder) return
        val game = item as Game
        viewHolder.mCardView.titleText = game.displayName()
        // A space when there is no developer, so every card keeps the same height.
        viewHolder.mCardView.contentText =
            subtitle.textFor(viewHolder.mCardView.context, game).ifEmpty { NO_SUBTITLE }
        // Same height for every card; the width follows the cover shape (like on mobile).
        updateCardShape(viewHolder, game)
        viewHolder.updateCardViewImage(game) { updateCardShape(viewHolder, game) }
        // A long press opens the same actions menu as on mobile, everywhere.
        viewHolder.view.setOnLongClickListener {
            TVGameContextMenu.show(it.context, game, gameInteractor, onOpenDetails = onOpenDetails)
            true
        }
    }

    private fun updateCardShape(
        viewHolder: ViewHolder,
        game: Game,
    ) {
        // Real shape of the cover once it has been loaded, the usual one of its system before.
        val cardRatio = CoverAspectRatio.forGame(game)
        val (cardWidth, cardHeight) =
            when {
                !fitInCell -> (cardSize * cardRatio).roundToInt() to cardSize
                cardRatio >= 1f -> cardSize to (cardSize / cardRatio).roundToInt()
                else -> (cardSize * cardRatio).roundToInt() to cardSize
            }
        val imageView = viewHolder.mCardView.mainImageView
        val layoutParams = imageView?.layoutParams
        if (layoutParams == null || layoutParams.width != cardWidth || layoutParams.height != cardHeight) {
            viewHolder.mCardView.setMainImageDimensions(cardWidth, cardHeight)
        }
        if (fitInCell) placeInCell(viewHolder.view, cardWidth, cardHeight)

        val coverRatio = CoverRatioCache.get(game.displayCoverUrl())
        imageView?.scaleType =
            if (CoverRatioCache.needsLetterbox(coverRatio, cardRatio)) {
                ImageView.ScaleType.FIT_CENTER
            } else {
                ImageView.ScaleType.CENTER_CROP
            }
    }

    /**
     * Margins that keep the card centered at the top of its square cell, so the columns of the grid stay aligned and
     * the covers of a row start at the same height.
     */
    private fun placeInCell(
        view: View,
        cardWidth: Int,
        cardHeight: Int,
    ) {
        val horizontal = (cardSize - cardWidth) / 2
        val vertical = 0
        val params =
            (view.layoutParams as? ViewGroup.MarginLayoutParams)
                ?: ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val right = cardSize - cardWidth - horizontal
        val bottom = cardSize - cardHeight - vertical
        // All four: the top margin is always 0, and a recycled card must still get the bottom
        // margin of its new cover, or the height of the row changes while scrolling.
        if (params.leftMargin != horizontal ||
            params.topMargin != vertical ||
            params.rightMargin != right ||
            params.bottomMargin != bottom ||
            view.layoutParams !== params
        ) {
            params.setMargins(horizontal, vertical, right, bottom)
            view.layoutParams = params
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val cardView = ImageCardView(parent.context)
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        TVCardStyle.apply(cardView)
        return ViewHolder(cardView)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder?) {
        val viewHolder = viewHolder as ViewHolder
        viewHolder.mCardView.mainImage = null
        viewHolder.view.setOnLongClickListener(null)
    }

    companion object {
        private const val NO_SUBTITLE = "\u00A0"
    }

    class ViewHolder(view: ImageCardView) : Presenter.ViewHolder(view) {
        val mCardView: ImageCardView = view

        fun updateCardViewImage(
            game: Game,
            onCoverMeasured: () -> Unit,
        ) {
            // A new load cancels the previous one of this view, so the callback always belongs to this game.
            CoverUtils.loadCover(game, mCardView.mainImageView) { drawable ->
                CoverRatioCache.store(game.displayCoverUrl(), drawable)
                onCoverMeasured()
            }
        }
    }
}
