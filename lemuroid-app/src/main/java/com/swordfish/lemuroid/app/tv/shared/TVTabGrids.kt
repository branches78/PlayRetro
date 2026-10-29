package com.swordfish.lemuroid.app.tv.shared

import android.content.res.Resources
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import androidx.leanback.widget.VerticalGridView
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.tv.home.TVHomeFragment
import com.swordfish.lemuroid.app.tv.main.TVMainTabsFragment

/** Helpers for the leanback grids shown inside the tabs of the TV start screen. */
object TVTabGrids {
    /**
     * Title of a grid (e.g. the system name), right below the tabs bar and aligned with the first column of cards.
     * It sits on an opaque band above the grid, so the cards scroll under it instead of over the text.
     */
    fun addTitle(
        root: ViewGroup,
        text: String,
    ) {
        val context = root.context
        val resources = context.resources
        val band =
            FrameLayout(context).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.leanback_background))
                // Above the cards (also the zoomed ones), without a shadow.
                translationZ = resources.getDimension(R.dimen.tv_grid_title_elevation)
                outlineProvider = null
            }
        val titleView =
            TextView(context).apply {
                this.text = text
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_dark_onBackground))
                textSize = TITLE_TEXT_SIZE_SP
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        band.addView(
            titleView,
            FrameLayout.LayoutParams(
                TVHomeFragment.rowsContentWidth(resources),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL,
            ),
        )
        root.addView(
            band,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.tv_grid_title_height),
                Gravity.TOP,
            ),
        )
    }

    /**
     * Leanback does not let the focus leave a grid from its first row (it tries to reach its own title area,
     * hidden here), so pressing up there moves the focus to the selected tab of the tabs bar instead.
     * [onOtherKey] gets the keys this does not handle (the grid has a single key intercept listener).
     */
    fun allowFocusUpToTabs(
        fragment: Fragment,
        grid: VerticalGridView,
        columns: Int,
        onOtherKey: (KeyEvent) -> Boolean = { false },
    ) {
        grid.setOnKeyInterceptListener { event ->
            val isUpOnFirstRow =
                event.action == KeyEvent.ACTION_DOWN &&
                    event.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                    grid.selectedPosition < columns
            if (isUpOnFirstRow && (fragment.parentFragment as? TVMainTabsFragment)?.focusSelectedTab() == true) {
                true
            } else {
                onOtherKey(event)
            }
        }
    }

    /**
     * Grid with the square cards of the home rows, 5 per row (games of a system, favorites). It is as wide as the
     * other tabs content and its cards start from the left, also when a row is not full.
     */
    fun setUpHomeCardsGrid(grid: VerticalGridView) {
        val resources = grid.resources
        grid.horizontalSpacing = TVHomeFragment.homeCardSpacing(resources)
        grid.setGravity(Gravity.START)
        (grid.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width = TVHomeFragment.rowsContentWidth(resources) + grid.paddingLeft + grid.paddingRight
            it.gravity = Gravity.CENTER_HORIZONTAL
            grid.layoutParams = it
        }
    }

    /**
     * Presenter of the grids of games (games of a system, favorites), like the home rows: the focused card grows by
     * 1.1, and without the leanback shadow and corners, which replaced the rounded outline of the cards.
     */
    fun gamesGridPresenter(resources: Resources): VerticalGridPresenter {
        // Without the focus dimmer the covers always keep their colors; the focused card is already zoomed.
        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        gridPresenter.numberOfColumns = TVHomeFragment.cardsPerRow(resources)
        gridPresenter.shadowEnabled = false
        gridPresenter.enableChildRoundedCorners(false)
        return gridPresenter
    }

    /**
     * [setUpHomeCardsGrid] for a grid of games, with room between the rows for the zoom of the focused card: a tall
     * cover fills its whole cell, and the zoom must not cover the row below.
     * The cards have the height of their cover (a wide one is shorter) and all of them start at the top of their row,
     * so the grid is aligned from that top (half a cell below it), not from the middle of the focused card: otherwise
     * the rows moved up and down when the focus went to a card of another height in the same row.
     */
    fun setUpGamesGrid(grid: VerticalGridView) {
        setUpHomeCardsGrid(grid)
        val resources = grid.resources
        grid.verticalSpacing = resources.getDimensionPixelSize(R.dimen.tv_games_grid_row_spacing)
        grid.itemAlignmentOffsetPercent = BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED
        grid.itemAlignmentOffset = TVHomeFragment.homeCardSize(resources) / 2
    }

    private const val TITLE_TEXT_SIZE_SP = 28f
}
