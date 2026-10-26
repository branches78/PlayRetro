package com.swordfish.lemuroid.app.tv.favorites

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.tv.details.TVGameDetailsFragment
import com.swordfish.lemuroid.app.tv.home.TVHomeFragment
import com.swordfish.lemuroid.app.tv.shared.GamePresenter
import com.swordfish.lemuroid.app.tv.shared.TVTabGrids
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

/**
 * All the favorites ("Show all" card of the home row), shown inside the "Home" tab so the tabs bar stays on screen.
 * The cards are the same square cards of the home rows, 5 per row.
 */
class TVFavoritesFragment : VerticalGridSupportFragment() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Without the focus dimmer the covers always keep their colors; the focused card is already zoomed.
        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false)
        gridPresenter.numberOfColumns = TVHomeFragment.cardsPerRow(resources)
        setGridPresenter(gridPresenter)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        showTitle(false)
        TVTabGrids.addTitle(view as ViewGroup, getString(R.string.favorites))
        view.findViewById<VerticalGridView>(androidx.leanback.R.id.browse_grid)?.let { grid ->
            val topPadding = resources.getDimensionPixelSize(R.dimen.tv_games_grid_top_padding)
            grid.setPadding(grid.paddingLeft, topPadding, grid.paddingRight, grid.paddingBottom)
            TVTabGrids.setUpHomeCardsGrid(grid)
            TVTabGrids.allowFocusUpToTabs(this, grid, TVHomeFragment.cardsPerRow(resources))
        }

        val factory = TVFavoritesViewModel.Factory(retrogradeDb)
        val favoritesViewModel = ViewModelProvider(this, factory)[TVFavoritesViewModel::class.java]

        val cardSize = TVHomeFragment.homeCardSize(resources)
        val pagingAdapter =
            PagingDataAdapter(
                GamePresenter(cardSize, gameInteractor, fitInCell = true),
                Game.DIFF_CALLBACK,
            )

        this.adapter = pagingAdapter

        launchOnState(Lifecycle.State.RESUMED) {
            favoritesViewModel.favorites
                .collect { pagingAdapter.submitData(lifecycle, it) }
        }

        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                when (item) {
                    is Game -> TVGameDetailsFragment.openOrPlay(this, item, gameInteractor)
                }
            }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    @dagger.Module
    class Module
}
