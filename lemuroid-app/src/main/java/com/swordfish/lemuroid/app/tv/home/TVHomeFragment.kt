package com.swordfish.lemuroid.app.tv.home

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.covers.GameScreenshots
import com.swordfish.lemuroid.app.shared.details.GameDescriptionProvider
import com.swordfish.lemuroid.app.shared.details.gameCleanTitle
import com.swordfish.lemuroid.app.shared.details.gameRegion
import com.swordfish.lemuroid.app.shared.home.HomeDynamicRows
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo
import com.swordfish.lemuroid.app.tv.details.TVGameDetailsFragment
import com.swordfish.lemuroid.app.tv.main.TVMainTabsFragment
import com.swordfish.lemuroid.app.tv.shared.GamePresenter
import com.swordfish.lemuroid.app.tv.shared.TVEmptyLibraryCard
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Content of the "Home" tab on TV. Discover always comes first as an immersive list: a screenshot, the title and a
 * description of the focused game are shown above it. The other rows follow the mobile order (recents, favorites...).
 * Recents and favorites are hidden while empty, while an empty discover row shows a hint in its middle.
 * With an empty library only the directory selection is shown.
 */
class TVHomeFragment : RowsSupportFragment() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var descriptionProvider: GameDescriptionProvider

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    private var emptyLibraryCard: TVEmptyLibraryCard? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // The rows keep only a small side padding for the zoom of the focused card (see TVHomeRowsOverlay).
        val rowsInflater = inflater.cloneInContext(ContextThemeWrapper(requireContext(), R.style.TVHomeRowsOverlay))
        val rowsView = super.onCreateView(rowsInflater, container, savedInstanceState) ?: return null

        // The rows are wrapped to add the card shown when the library is empty, like the mobile one.
        // They are centered with the width of the other tabs and clipped there, so no cover is ever cut
        // by the screen sides, also while scrolling.
        val root = FrameLayout(requireContext())
        val rowEdge = resources.getDimensionPixelSize(R.dimen.tv_home_row_edge)
        val contentWidth = contentWidth(resources)

        // Texts of the immersive list, aligned with the first card of the first row and ending the guidelines gap
        // above it. The rows are drawn over them, as they go up when the focus moves to the other rows.
        val infoContainer = FrameLayout(requireContext())
        val infoView = inflater.inflate(R.layout.tv_home_immersive_info, infoContainer, false)
        (infoView.layoutParams as FrameLayout.LayoutParams).apply {
            marginStart = rowEdge
            gravity = Gravity.START or Gravity.BOTTOM
        }
        infoContainer.addView(infoView)
        val infoHeight =
            resources.getDimensionPixelSize(R.dimen.tv_immersive_row_top) -
                resources.getDimensionPixelSize(R.dimen.tv_immersive_info_gap)
        val infoLayoutParams = FrameLayout.LayoutParams(contentWidth + 2 * rowEdge, infoHeight)
        infoLayoutParams.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(infoContainer, infoLayoutParams)
        immersiveInfo = ImmersiveInfo(infoView)

        val rowsLayoutParams =
            FrameLayout.LayoutParams(contentWidth + 2 * rowEdge, ViewGroup.LayoutParams.MATCH_PARENT)
        rowsLayoutParams.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(rowsView, rowsLayoutParams)

        val emptyView = inflater.inflate(R.layout.tv_home_empty_library, root, false)
        root.addView(emptyView)
        emptyLibraryCard = TVEmptyLibraryCard(emptyView, rowsView)

        return root
    }

    override fun onDestroyView() {
        emptyLibraryCard = null
        immersiveInfo = null
        immersiveJob = null
        immersiveGame = null
        discoverRow = null
        recentsRow = null
        rowsAlignmentOffset = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        immersiveGame?.let { tabsFragment()?.setHomeBackdrop(it) }
    }

    override fun onPause() {
        // Hidden while another tab or screen is shown: the backdrop belongs to the tabs screen, behind all of them.
        tabsFragment()?.setHomeBackdrop(null)
        super.onPause()
    }

    private fun tabsFragment(): TVMainTabsFragment? = parentFragment as? TVMainTabsFragment

    /** Texts of the immersive list: title, system, developer and region, and description of the focused game. */
    private class ImmersiveInfo(val root: View) {
        val logo: ImageView = root.findViewById(R.id.tv_immersive_logo)
        val title: TextView = root.findViewById(R.id.tv_immersive_title)
        val meta: TextView = root.findViewById(R.id.tv_immersive_meta)
        val description: TextView = root.findViewById(R.id.tv_immersive_description)
    }

    private var immersiveInfo: ImmersiveInfo? = null
    private var immersiveJob: Job? = null
    private var immersiveGame: Game? = null

    /**
     * Updates the immersive list for the [game] focused in its row, or hides it when the focus is on another row.
     * Title and system change right away; the screenshot and the description, which may need the network,
     * only once the focus stays on the game for a moment.
     */
    private fun showImmersiveGame(game: Game?) {
        val info = immersiveInfo ?: return
        if (game == immersiveGame) return
        immersiveGame = game
        immersiveJob?.cancel()

        if (game == null) {
            info.root.animate().alpha(0f).setDuration(IMMERSIVE_FADE_MS).start()
            tabsFragment()?.setHomeBackdrop(null)
            return
        }

        // Without the region and other tags of the file name: "Banjo-Tooie (USA)" -> "Banjo-Tooie".
        info.title.text = gameCleanTitle(game)
        showImmersiveLogo(info, game)
        info.meta.text = immersiveMeta(game)
        info.description.text = descriptionProvider.getCachedDescription(game).orEmpty()
        info.root.animate().alpha(1f).setDuration(IMMERSIVE_FADE_MS).start()

        immersiveJob =
            viewLifecycleOwner.lifecycleScope.launch {
                delay(IMMERSIVE_DELAY_MS)
                if (isResumed) tabsFragment()?.setHomeBackdrop(game)
                val description = descriptionProvider.getDescription(game)
                if (immersiveGame == game) info.description.text = description.orEmpty()
            }
    }

    // Same line as in the details of a game.
    /** The title is shown until the logo of the game is loaded, and stays when the game has none. */
    private fun showImmersiveLogo(
        info: ImmersiveInfo,
        game: Game,
    ) {
        info.logo.isVisible = false
        info.title.isVisible = true
        val logoUrl = GameScreenshots.logoUrl(game)
        if (logoUrl == null) {
            info.logo.dispose()
            info.logo.setImageDrawable(null)
            return
        }
        // A new load cancels the previous one of the view, so the result always belongs to this game.
        info.logo.load(logoUrl) {
            listener(
                onSuccess = { _, _ ->
                    info.logo.isVisible = true
                    info.title.isVisible = false
                },
            )
        }
    }

    private fun immersiveMeta(game: Game): String {
        val system = runCatching { getString(GameSystem.findById(game.systemId).titleResId) }.getOrNull()
        return listOfNotNull(system, game.developer?.takeIf { it.isNotBlank() }, gameRegion(game)).joinToString(" · ")
    }

    private fun onItemSelected(
        item: Any?,
        row: Row?,
    ) {
        val isImmersiveRow = row != null && row === discoverRow
        showImmersiveGame(if (isImmersiveRow) item as? Game else null)
    }

    private var hasImmersiveRow = false
    private var rowsAlignmentOffset: Int? = null

    /**
     * Where the selected row goes. The immersive list stays low on the screen, below its texts (its expanded state);
     * the other rows go to the top, and the list collapses: its texts and screenshot are hidden.
     */
    private fun updateRowsAlignment(selectedPosition: Int) {
        val gridView = verticalGridView ?: return
        val immersiveSelected = hasImmersiveRow && selectedPosition <= 0
        val offset =
            resources.getDimensionPixelSize(
                if (immersiveSelected) R.dimen.tv_immersive_row_top else R.dimen.tv_home_rows_top,
            )
        if (offset == rowsAlignmentOffset) return
        val firstTime = rowsAlignmentOffset == null
        rowsAlignmentOffset = offset

        gridView.setPadding(gridView.paddingLeft, 0, gridView.paddingRight, gridView.paddingBottom)
        gridView.windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
        gridView.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        gridView.windowAlignmentOffset = offset
        gridView.itemAlignmentOffset = 0
        gridView.itemAlignmentOffsetPercent = 0f

        // The selection is reported before the grid scrolls, but scroll again in case it already used the old offset.
        if (!firstTime) {
            gridView.post {
                if (gridView.selectedPosition == selectedPosition) gridView.setSelectedPositionSmooth(selectedPosition)
            }
        }
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

        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, row ->
                when {
                    // "Continue playing" always starts the game; elsewhere a game opens its details.
                    item is Game && row === recentsRow -> gameInteractor.onGamePlay(item)
                    item is Game -> {
                        rememberFocusedGame(row, item)
                        TVGameDetailsFragment.openOrPlay(this, item, gameInteractor)
                    }
                    item is TVSetting -> onSettingClicked(item)
                }
            }

        onItemViewSelectedListener =
            OnItemViewSelectedListener { _, item, _, row -> onItemSelected(item, row) }

        verticalGridView?.addOnChildViewHolderSelectedListener(
            object : OnChildViewHolderSelectedListener() {
                override fun onChildViewHolderSelected(
                    parent: RecyclerView,
                    child: RecyclerView.ViewHolder?,
                    position: Int,
                    subposition: Int,
                ) {
                    updateRowsAlignment(position)
                }
            },
        )

        createAdapter(emptyList())

        val factory = TVHomeViewModel.Factory(retrogradeDb, requireContext().applicationContext)
        val homeViewModel = ViewModelProvider(this, factory)[TVHomeViewModel::class.java]

        launchOnState(Lifecycle.State.RESUMED) {
            homeViewModel.getViewStates()
                .collect { update(it) }
        }
    }

    private fun onSettingClicked(setting: TVSetting) {
        when (setting.type) {
            TVSettingType.SHOW_ALL_FAVORITES -> (parentFragment as? TVMainTabsFragment)?.showFavorites()
            else -> Unit
        }
    }

    /** A row of the home screen: [key] identifies it, [items] are games or placeholders. */
    private data class HomeRow(val key: String, val title: String, val items: List<Any>)

    private var currentRows: List<Pair<String, String>> = emptyList()

    /** The immersive list: the first row, without a title. */
    private var discoverRow: ListRow? = null

    /** "Continue playing": its games start right away and keep the long press menu. */
    private var recentsRow: ListRow? = null

    /**
     * Row (key) and game opened in its details. The rows are built again when coming back (this fragment keeps
     * living, its view doesn't), and the focus goes back to that game instead of the start of the screen.
     */
    private var gameToRefocus: Pair<String, Int>? = null

    private fun rememberFocusedGame(
        row: Row?,
        game: Game,
    ) {
        if (!gameInteractor.showDetailsOnClick()) return
        val rowIndex = (adapter as? ArrayObjectAdapter)?.indexOf(row) ?: -1
        val rowKey = currentRows.getOrNull(rowIndex)?.first ?: return
        gameToRefocus = rowKey to game.id
    }

    /** Selects and focuses the game opened before, once the rows are back. Done only once, found or not. */
    private fun refocusGame(rows: List<HomeRow>) {
        val (rowKey, gameId) = gameToRefocus ?: return
        if (rows.isEmpty()) return
        gameToRefocus = null

        val rowIndex = rows.indexOfFirst { it.key == rowKey }
        val itemIndex = rows.getOrNull(rowIndex)?.items?.indexOfFirst { (it as? Game)?.id == gameId } ?: -1
        if (rowIndex < 0 || itemIndex < 0) return

        setSelectedPosition(rowIndex, false, ListRowPresenter.SelectItemViewHolderTask(itemIndex))
        view?.post { verticalGridView?.requestFocus() }
    }

    private fun update(viewState: TVHomeViewModel.HomeViewState) {
        updateEmptyLibraryCard(viewState)

        val rows = buildRows(viewState)

        // Rows appear, disappear or get a new title (e.g. "Because you played..."): rebuild them.
        val rowsStructure = rows.map { it.key to it.title }
        if (rowsStructure != currentRows) {
            createAdapter(rows)
        }

        val rowsAdapter = adapter ?: return
        rows.forEachIndexed { index, row ->
            val listRow = rowsAdapter.get(index) as ListRow
            (listRow.adapter as ArrayObjectAdapter).setItems(row.items, LEANBACK_MULTI_DIFF_CALLBACK)
        }

        hasImmersiveRow = viewState.metaSystems.isNotEmpty() && viewState.discoveryGames.isNotEmpty()
        updateRowsAlignment(selectedPosition)

        // The selection is not reported again when the games of the row change: keep the texts in sync.
        val selectedRow = rowsAdapter.takeIf { selectedPosition in 0 until it.size() }?.get(selectedPosition)
        if (emptyLibraryCard?.isShown == true) {
            // Nothing behind the card of the first scan: the games found so far would show their screenshot and texts.
            showImmersiveGame(null)
        } else if (selectedRow === discoverRow) {
            val selectedGame = immersiveGame?.let { current -> viewState.discoveryGames.firstOrNull { it.id == current.id } }
            showImmersiveGame(selectedGame ?: viewState.discoveryGames.firstOrNull())
        } else if (!hasImmersiveRow) {
            showImmersiveGame(null)
        }

        refocusGame(rows)
    }

    private fun buildRows(viewState: TVHomeViewModel.HomeViewState): List<HomeRow> {
        val rows = mutableListOf<HomeRow>()

        // Empty library (e.g. first launch): no rows, the centered directory card is shown instead.
        if (viewState.metaSystems.isEmpty()) {
            return rows
        }

        // Discover always comes first, as the immersive list (without a title: the focused game is shown above it).
        val discoverItems = viewState.discoveryGames.ifEmpty { listOf(EmptyRowMessage(R.string.home_row_empty_discover)) }
        rows += HomeRow(KEY_DISCOVER, "", discoverItems)

        // Recents and favorites stay hidden until they have games.
        if (viewState.recentGames.isNotEmpty()) {
            rows += HomeRow(KEY_RECENTS, getString(R.string.tv_home_section_recents), viewState.recentGames)
        }

        if (viewState.favoritesGames.isNotEmpty()) {
            val favoritesItems =
                if (viewState.favoritesGames.size <= TVHomeViewModel.CAROUSEL_MAX_ITEMS) {
                    viewState.favoritesGames
                } else {
                    viewState.favoritesGames.subList(0, TVHomeViewModel.CAROUSEL_MAX_ITEMS) +
                        listOf(TVSetting(TVSettingType.SHOW_ALL_FAVORITES))
                }
            rows += HomeRow(KEY_FAVORITES, getString(R.string.tv_home_section_favorites), favoritesItems)
        }

        rows += dynamicRows(viewState, HomeDynamicRows.Placement.BEFORE_DISCOVER)
        rows += dynamicRows(viewState, HomeDynamicRows.Placement.AFTER_DISCOVER)

        return rows
    }

    private fun dynamicRows(
        viewState: TVHomeViewModel.HomeViewState,
        placement: HomeDynamicRows.Placement,
    ): List<HomeRow> {
        return viewState.dynamicRows
            .filter { it.placement == placement }
            .map { HomeRow(it.key, it.title, it.games) }
    }

    private fun createAdapter(rows: List<HomeRow>) {
        val rowEdge = resources.getDimensionPixelSize(R.dimen.tv_home_row_edge)
        val result = ArrayObjectAdapter(HomeListRowPresenter(rowEdge, rowCardSpacing(resources)))
        val cardWidth = rowCardWidth(resources)
        val cardHeight = rowCardHeight(resources)
        val settingsCardPadding =
            resources.getDimensionPixelSize(
                com.swordfish.lemuroid.lib.R.dimen.settings_card_padding,
            )

        val presenter =
            ClassPresenterSelector().apply {
                addClassPresenter(
                    Game::class.java,
                    // A square cell with the cover inside it, like in the grids of the consoles, small enough for 5
                    // whole cells in the content width with the spacing of the rows. The covers start at the top of
                    // the row, aligned whatever their height.
                    GamePresenter(cardWidth, gameInteractor, fitInCell = true),
                )
                addClassPresenter(TVSetting::class.java, SettingPresenter(cardHeight, settingsCardPadding, cardWidth))
                addClassPresenter(
                    EmptyRowMessage::class.java,
                    EmptyRowMessagePresenter(cardHeight, contentWidth(resources)),
                )
            }

        discoverRow = null
        recentsRow = null
        rows.forEachIndexed { index, row ->
            val listRow =
                when (row.key) {
                    KEY_DISCOVER -> ListRow(ArrayObjectAdapter(presenter)).also { discoverRow = it }
                    // "Continue playing": its games start right away (see onViewCreated).
                    KEY_RECENTS ->
                        ListRow(HeaderItem(index.toLong(), row.title), ArrayObjectAdapter(presenter))
                            .also { recentsRow = it }
                    else -> ListRow(HeaderItem(index.toLong(), row.title), ArrayObjectAdapter(presenter))
                }
            result.add(listRow)
        }

        adapter = result
        currentRows = rows.map { it.key to it.title }
    }

    /**
     * Rows that move one card at a time: the focused card is aligned to the first position (like Google TV),
     * and at the end of the row the last cards fill it. As all the cards have the same width, the visible
     * cards are always whole. The focused card grows by 1.1, as the TV guidelines of the immersive list ask
     * (the default is 1.14).
     */
    private class HomeListRowPresenter(
        private val rowEdge: Int,
        private val cardSpacing: Int,
    ) : ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL) {
        init {
            // Rows without a title (the immersive list) don't keep the empty space of the title.
            (headerPresenter as? RowHeaderPresenter)?.isNullItemVisibilityGone = true
            // The shadow container of each card covers its whole cell, also the margins that center the cover in
            // it, and showed a grey box above and below the cover. Like in the grids of the consoles, no shadow.
            shadowEnabled = false
        }

        override fun initializeRowViewHolder(holder: RowPresenter.ViewHolder) {
            super.initializeRowViewHolder(holder)
            val gridView = (holder as ListRowPresenter.ViewHolder).gridView
            gridView.horizontalSpacing = cardSpacing
            gridView.windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
            gridView.windowAlignmentOffset = rowEdge
            gridView.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            gridView.itemAlignmentOffset = 0
            gridView.itemAlignmentOffsetPercent = 0f
        }
    }

    companion object {
        /** Width of the content of every tab: the screen without the side margins of the TV guidelines. */
        fun contentWidth(resources: Resources): Int {
            val sideMargin = resources.getDimensionPixelSize(R.dimen.tv_content_side_margin)
            return resources.displayMetrics.widthPixels - 2 * sideMargin
        }

        /** Width of the grids of square cards, rounded down to whole cards. */
        fun rowsContentWidth(resources: Resources): Int {
            val cardsPerRow = cardsPerRow(resources)
            return cardsPerRow * homeCardSize(resources) + (cardsPerRow - 1) * homeCardSpacing(resources)
        }

        /** Size of the square game cards of the grids (games of a system, favorites, consoles, "More from"). */
        fun homeCardSize(resources: Resources): Int {
            val cardsPerRow = cardsPerRow(resources)
            return (contentWidth(resources) - (cardsPerRow - 1) * homeCardSpacing(resources)) / cardsPerRow
        }

        fun homeCardSpacing(resources: Resources): Int = resources.getDimensionPixelSize(R.dimen.tv_home_card_spacing)

        fun cardsPerRow(resources: Resources): Int = resources.getInteger(R.integer.tv_home_cards_per_row)

        /** Width of the 16:9 cards of the home rows: [R.integer.tv_home_row_cards] of them fill the content. */
        private fun rowCardWidth(resources: Resources): Int {
            val cards = resources.getInteger(R.integer.tv_home_row_cards)
            return (contentWidth(resources) - (cards - 1) * rowCardSpacing(resources)) / cards
        }

        private fun rowCardHeight(resources: Resources): Int = (rowCardWidth(resources) / ROW_CARD_RATIO).roundToInt()

        private fun rowCardSpacing(resources: Resources): Int =
            resources.getDimensionPixelSize(R.dimen.tv_home_row_card_spacing)

        private const val ROW_CARD_RATIO = 16f / 9f

        private const val KEY_RECENTS = "recents"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_DISCOVER = "discover"

        private const val IMMERSIVE_DELAY_MS = 300L
        private const val IMMERSIVE_FADE_MS = 200L
        val LEANBACK_MULTI_DIFF_CALLBACK =
            object : DiffCallback<Any>() {
                override fun areContentsTheSame(
                    oldItem: Any,
                    newItem: Any,
                ): Boolean {
                    return when {
                        (oldItem is Game && newItem is Game) -> {
                            LEANBACK_GAME_DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        }
                        (oldItem is TVSetting && newItem is TVSetting) -> {
                            LEANBACK_SETTING_DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        }
                        else -> oldItem == newItem
                    }
                }

                override fun areItemsTheSame(
                    oldItem: Any,
                    newItem: Any,
                ): Boolean {
                    return when {
                        (oldItem is Game && newItem is Game) -> {
                            LEANBACK_GAME_DIFF_CALLBACK.areItemsTheSame(oldItem, newItem)
                        }
                        (oldItem is TVSetting && newItem is TVSetting) -> {
                            LEANBACK_SETTING_DIFF_CALLBACK.areItemsTheSame(oldItem, newItem)
                        }
                        else -> oldItem == newItem
                    }
                }
            }

        val LEANBACK_GAME_DIFF_CALLBACK =
            object : DiffCallback<Game>() {
                override fun areContentsTheSame(
                    oldItem: Game,
                    newItem: Game,
                ): Boolean {
                    return oldItem == newItem
                }

                override fun areItemsTheSame(
                    oldItem: Game,
                    newItem: Game,
                ): Boolean {
                    return oldItem.id == newItem.id
                }
            }

        val LEANBACK_SYSTEM_DIFF_CALLBACK =
            object : DiffCallback<MetaSystemInfo>() {
                override fun areContentsTheSame(
                    oldInfo: MetaSystemInfo,
                    newInfo: MetaSystemInfo,
                ): Boolean {
                    return oldInfo == newInfo
                }

                override fun areItemsTheSame(
                    oldInfo: MetaSystemInfo,
                    newInfo: MetaSystemInfo,
                ): Boolean {
                    return oldInfo.metaSystem.name == newInfo.metaSystem.name
                }
            }

        val LEANBACK_SETTING_DIFF_CALLBACK =
            object : DiffCallback<TVSetting>() {
                override fun areContentsTheSame(
                    oldItem: TVSetting,
                    newItem: TVSetting,
                ): Boolean {
                    return oldItem == newItem
                }

                override fun areItemsTheSame(
                    oldItem: TVSetting,
                    newItem: TVSetting,
                ): Boolean {
                    return oldItem.type == newItem.type
                }
            }
    }

    @dagger.Module
    class Module
}
