package com.swordfish.lemuroid.app.tv.games

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.tv.details.TVGameDetailsFragment
import com.swordfish.lemuroid.app.tv.home.TVHomeFragment
import com.swordfish.lemuroid.app.tv.shared.GamePresenter
import com.swordfish.lemuroid.app.tv.shared.TVTabGrids
import com.swordfish.lemuroid.app.utils.games.GameCardSubtitle
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

/** Games of a system, shown inside the "Consoles" tab so the tabs bar stays on screen. */
class TVGamesFragment : VerticalGridSupportFragment() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    private val metaSystemId: MetaSystemID by lazy {
        MetaSystemID.valueOf(requireArguments().getString(ARG_META_SYSTEM_ID)!!)
    }

    private var alphabetBar: TVAlphabetBar? = null
    private var bigLetterView: TextView? = null
    private var gameLetters: List<Char> = emptyList()
    private var letterPositions: Map<Char, Int> = emptyMap()

    // Card the grid goes back to while the alphabet bar has the focus.
    private var markedCard: View? = null

    /**
     * Position of the game opened in its details. The view of this fragment is created again when coming back (the
     * fragment keeps living), and the grid goes back to that game instead of the first one.
     */
    private var positionToRefocus: Int? = null
    private val hideBigLetter = Runnable { bigLetterView?.animate()?.alpha(0f) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setGridPresenter(TVTabGrids.gamesGridPresenter(resources))
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // The default leanback title is replaced by a centered title with the system name.
        showTitle(false)
        TVTabGrids.addTitle(view as ViewGroup, getString(metaSystemId.titleResId))
        val grid = view.findViewById<VerticalGridView>(androidx.leanback.R.id.browse_grid)
        placeGridBelowTitle(grid)
        setUpAlphabet(view, grid)

        val factory = TVGamesViewModel.Factory(retrogradeDb)
        val gamesViewModel = ViewModelProvider(this, factory)[TVGamesViewModel::class.java]

        // Square cells as in the favorites, 5 per row for every system; each card takes the shape of its cover.
        val cardSize = TVHomeFragment.homeCardSize(resources)
        val pagingAdapter =
            PagingDataAdapter(
                GamePresenter(
                    cardSize,
                    gameInteractor,
                    fitInCell = true,
                    subtitle = GameCardSubtitle.DEVELOPER,
                    onOpenDetails = {
                        // Back from the details, the grid goes back to this game.
                        positionToRefocus = grid.selectedPosition
                        TVGameDetailsFragment.open(this, it)
                    },
                ),
                Game.DIFF_CALLBACK,
            )

        this.adapter = pagingAdapter
        refocusGame(pagingAdapter, grid)

        launchOnState(Lifecycle.State.RESUMED) {
            gamesViewModel.games
                .collect { pagingAdapter.submitData(lifecycle, it) }
        }

        launchOnState(Lifecycle.State.RESUMED) {
            gamesViewModel.gameLetters
                .collect { updateLetters(it, grid) }
        }

        gamesViewModel.metaSystemId.value = metaSystemId

        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                when (item) {
                    is Game -> {
                        if (gameInteractor.showDetailsOnClick()) positionToRefocus = grid.selectedPosition
                        TVGameDetailsFragment.openOrPlay(this, item, gameInteractor)
                    }
                }
            }
    }

    /**
     * The grid has the width of the other tabs content, its first row starts below the title and pressing up on it
     * moves the focus to the tabs bar. Pressing right on its last column moves it to the alphabet bar.
     */
    private fun placeGridBelowTitle(grid: VerticalGridView) {
        val topPadding = resources.getDimensionPixelSize(R.dimen.tv_games_grid_top_padding)
        grid.setPadding(grid.paddingLeft, topPadding, grid.paddingRight, grid.paddingBottom)
        TVTabGrids.setUpGamesGrid(grid)
        TVTabGrids.allowFocusUpToTabs(this, grid, TVHomeFragment.cardsPerRow(resources)) { event ->
            event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                focusAlphabetFromGrid(grid)
        }
    }

    /** Alphabet bar on the right, outside the content width, and the big letter shown when it changes. */
    private fun setUpAlphabet(
        root: ViewGroup,
        grid: VerticalGridView,
    ) {
        val bar =
            TVAlphabetBar(requireContext()).apply {
                isVisible = false
                onLetterSelected = { letter -> jumpToLetter(grid, letter) }
                onExit = {
                    clearMarkedCard()
                    grid.requestFocus()
                }
                setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) clearMarkedCard() }
            }
        root.addView(
            bar,
            FrameLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.tv_alphabet_width),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END,
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.tv_alphabet_top_margin)
                bottomMargin = resources.getDimensionPixelSize(R.dimen.tv_alphabet_bottom_margin)
                marginEnd = resources.getDimensionPixelSize(R.dimen.tv_alphabet_end_margin)
            },
        )
        alphabetBar = bar

        val letterSize = resources.getDimensionPixelSize(R.dimen.tv_alphabet_letter_size)
        val letterView =
            TextView(requireContext()).apply {
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_dark_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.tv_alphabet_letter_text_size))
                background =
                    GradientDrawable().apply {
                        setColor(ContextCompat.getColor(context, R.color.tv_alphabet_letter_background))
                        cornerRadius = resources.getDimension(R.dimen.tv_alphabet_letter_corner_radius)
                    }
                // Above the cards, like the title band.
                translationZ = resources.getDimension(R.dimen.tv_grid_title_elevation)
                outlineProvider = null
                alpha = 0f
            }
        root.addView(letterView, FrameLayout.LayoutParams(letterSize, letterSize, Gravity.CENTER))
        bigLetterView = letterView

        // After a jump the new card is only known once the grid has laid it out.
        grid.addOnChildViewHolderSelectedListener(
            object : OnChildViewHolderSelectedListener() {
                override fun onChildViewHolderSelected(
                    parent: RecyclerView,
                    child: RecyclerView.ViewHolder?,
                    position: Int,
                    subposition: Int,
                ) {
                    if (alphabetBar?.hasFocus() == true) markCard(child?.itemView)
                }
            },
        )
    }

    /** The bar appears only when the console has more games than fit comfortably on screen. */
    private fun updateLetters(
        letters: List<Char>,
        grid: VerticalGridView,
    ) {
        gameLetters = letters
        letterPositions =
            buildMap {
                letters.forEachIndexed { index, letter -> if (letter !in this) put(letter, index) }
            }
        val bar = alphabetBar ?: return
        bar.setAvailableLetters(letterPositions.keys)
        bar.isVisible = letters.size >= resources.getInteger(R.integer.tv_alphabet_min_games)
        if (!bar.isVisible && bar.hasFocus()) grid.requestFocus()
    }

    /** Selects and focuses the game opened before, once the grid has it again. Done only once. */
    private fun refocusGame(
        adapter: ObjectAdapter,
        grid: VerticalGridView,
    ) {
        val position = positionToRefocus ?: return
        val observer =
            object : ObjectAdapter.DataObserver() {
                private var restored = false

                override fun onChanged() = restore()

                override fun onItemRangeInserted(
                    positionStart: Int,
                    itemCount: Int,
                ) = restore()

                private fun restore() {
                    if (restored || adapter.size() <= position) return
                    restored = true
                    positionToRefocus = null
                    grid.selectedPosition = position
                    // Not while the adapter is notifying its observers.
                    grid.post {
                        adapter.unregisterObserver(this)
                        grid.requestFocus()
                    }
                }
            }
        adapter.registerObserver(observer)
    }

    private fun focusAlphabetFromGrid(grid: VerticalGridView): Boolean {
        val bar = alphabetBar?.takeIf { it.isVisible } ?: return false
        val columns = TVHomeFragment.cardsPerRow(resources)
        val position = grid.selectedPosition
        val isLastColumn = position % columns == columns - 1 || position == (adapter?.size() ?: 0) - 1
        if (position < 0 || !isLastColumn) return false

        bar.focusLetter(gameLetters.getOrNull(position) ?: TVAlphabetBar.LETTERS.first())
        markCard(grid.findViewHolderForAdapterPosition(position)?.itemView)
        return true
    }

    private fun jumpToLetter(
        grid: VerticalGridView,
        letter: Char,
    ) {
        val position = letterPositions[letter] ?: return
        grid.selectedPosition = position

        val letterView = bigLetterView ?: return
        letterView.text = letter.toString()
        letterView.animate().cancel()
        letterView.alpha = 1f
        letterView.removeCallbacks(hideBigLetter)
        letterView.postDelayed(hideBigLetter, BIG_LETTER_DURATION_MS)
    }

    private fun markCard(card: View?) {
        if (card == markedCard) return
        clearMarkedCard()
        card?.foreground =
            GradientDrawable().apply {
                setStroke(
                    resources.getDimensionPixelSize(R.dimen.tv_alphabet_target_stroke),
                    ContextCompat.getColor(requireContext(), R.color.md_theme_dark_primary),
                )
                cornerRadius = resources.getDimension(R.dimen.tv_card_corner_radius)
            }
        markedCard = card
    }

    private fun clearMarkedCard() {
        markedCard?.foreground = null
        markedCard = null
    }

    override fun onDestroyView() {
        bigLetterView?.removeCallbacks(hideBigLetter)
        clearMarkedCard()
        alphabetBar = null
        bigLetterView = null
        super.onDestroyView()
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    companion object {
        private const val ARG_META_SYSTEM_ID = "meta_system_id"
        private const val BIG_LETTER_DURATION_MS = 700L

        fun newInstance(metaSystemId: String): TVGamesFragment {
            return TVGamesFragment().apply { arguments = bundleOf(ARG_META_SYSTEM_ID to metaSystemId) }
        }
    }

    @dagger.Module
    class Module
}
