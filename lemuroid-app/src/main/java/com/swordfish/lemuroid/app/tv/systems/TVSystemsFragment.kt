package com.swordfish.lemuroid.app.tv.systems

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo
import com.swordfish.lemuroid.app.tv.home.SystemPresenter
import com.swordfish.lemuroid.app.tv.home.TVHomeFragment
import com.swordfish.lemuroid.app.tv.home.TVHomeViewModel
import com.swordfish.lemuroid.app.tv.main.TVMainTabsFragment
import com.swordfish.lemuroid.app.tv.shared.TVEmptyLibraryCard
import com.swordfish.lemuroid.app.tv.shared.TVTabGrids
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Content of the "Systems" tab on TV: a grid with every system that has games. */
class TVSystemsFragment : VerticalGridSupportFragment() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    init {
        // Without the focus dimmer the icons always keep their colors; the focused card is already zoomed.
        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false)
        gridPresenter.numberOfColumns = GRID_COLUMNS
        setGridPresenter(gridPresenter)
    }

    private var emptyLibraryCard: TVEmptyLibraryCard? = null

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val gridRoot = super.onCreateView(inflater, container, savedInstanceState) ?: return null

        // Same card as the "Home" tab when the library is empty (e.g. first launch), instead of an empty grid.
        val root = FrameLayout(requireContext())
        root.addView(gridRoot, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val emptyView = inflater.inflate(R.layout.tv_home_empty_library, root, false)
        root.addView(emptyView)
        emptyLibraryCard = TVEmptyLibraryCard(emptyView, gridRoot)
        return root
    }

    override fun onDestroyView() {
        emptyLibraryCard = null
        super.onDestroyView()
    }

    private fun updateEmptyLibraryCard(viewState: TVHomeViewModel.HomeViewState) {
        emptyLibraryCard?.update(
            isLibraryEmpty = viewState.metaSystems.isEmpty(),
            indexInProgress = viewState.indexInProgress,
            scanProgress = viewState.scanProgress,
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // The tabs bar already works as title, so the default leanback title area is not needed.
        showTitle(false)
        view.findViewById<View>(androidx.leanback.R.id.browse_grid)?.let {
            val topPadding = resources.getDimensionPixelSize(R.dimen.tv_systems_grid_top_padding)
            it.setPadding(it.paddingLeft, topPadding, it.paddingRight, it.paddingBottom)
        }
        (view.findViewById<View>(androidx.leanback.R.id.browse_grid) as? VerticalGridView)?.let {
            // Same width and cards as the other grids: the content width of the tabs.
            TVTabGrids.setUpHomeCardsGrid(it)
            TVTabGrids.allowFocusUpToTabs(this, it, GRID_COLUMNS)
        }

        val cardSize = TVHomeFragment.homeCardSize(resources)
        val cardPadding = resources.getDimensionPixelSize(com.swordfish.lemuroid.lib.R.dimen.systems_card_padding)
        val systemsAdapter = ArrayObjectAdapter(SystemPresenter(cardSize, cardPadding))
        adapter = systemsAdapter

        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                if (item is MetaSystemInfo) {
                    (parentFragment as? TVMainTabsFragment)?.showSystemGames(item.metaSystem.name)
                }
            }

        val factory = TVHomeViewModel.Factory(retrogradeDb, requireContext().applicationContext)
        val viewModel = ViewModelProvider(this, factory)[TVHomeViewModel::class.java]

        launchOnState(Lifecycle.State.RESUMED) {
            viewModel.getViewStates()
                .collect { updateEmptyLibraryCard(it) }
        }

        launchOnState(Lifecycle.State.RESUMED) {
            viewModel.getViewStates()
                .map { it.metaSystems }
                .distinctUntilChanged()
                .collect { systemsAdapter.setItems(it, TVHomeFragment.LEANBACK_SYSTEM_DIFF_CALLBACK) }
        }
    }

    companion object {
        private const val GRID_COLUMNS = 5
    }

    @dagger.Module
    class Module
}
