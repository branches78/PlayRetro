package com.swordfish.lemuroid.app.tv.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.leanback.widget.BrowseFrameLayout
import androidx.leanback.widget.SearchOrbView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncChanges
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork
import com.swordfish.lemuroid.app.tv.about.TVAboutFragment
import com.swordfish.lemuroid.app.tv.favorites.TVFavoritesFragment
import com.swordfish.lemuroid.app.tv.games.TVGamesFragment
import com.swordfish.lemuroid.app.tv.home.TVBackdropView
import com.swordfish.lemuroid.app.tv.home.TVHomeFragment
import com.swordfish.lemuroid.app.tv.settings.TVSettingsTabFragment
import com.swordfish.lemuroid.app.tv.systems.TVSystemsFragment
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV start screen: search button on the left, centered tabs (home, systems, settings)
 * and the app name on the right. The selected tab content is shown below.
 */
class TVMainTabsFragment : Fragment(R.layout.fragment_tv_main_tabs) {
    private var selectedTab = TAB_HOME
    private var tabs: List<View> = emptyList()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(KEY_SELECTED_TAB, TAB_HOME)
        }

        tabs =
            listOf(
                view.findViewById(R.id.tv_tab_home),
                view.findViewById(R.id.tv_tab_systems),
                view.findViewById(R.id.tv_tab_settings),
                view.findViewById(R.id.tv_tab_about),
            )

        tabs.forEachIndexed { index, tab ->
            // Like most TV launchers, moving the focus over a tab switches the content.
            tab.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) selectTab(index)
                // The focus is moving: update the colors once it has landed (on a tab or in the content).
                tab.post { updateTabColors() }
            }
            tab.setOnClickListener { focusContent() }
        }

        tabPill = view.findViewById(R.id.tv_tab_pill)
        // Tabs change size with the language: the pill follows them once they are laid out.
        view.findViewById<View>(R.id.tv_tabs).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (pillAnimator?.isRunning != true) movePill(animate = false)
        }

        view.findViewById<SearchOrbView>(R.id.tv_search_orb).apply {
            // White like the rest of the interface (only the logo is green), with a dark icon.
            orbColors =
                SearchOrbView.Colors(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_dark_primary),
                    ContextCompat.getColor(requireContext(), R.color.tv_tab_focused_background),
                    ContextCompat.getColor(requireContext(), R.color.md_theme_dark_background),
                )
            setOnOrbClickedListener { findNavController().navigate(R.id.navigation_search) }
        }

        setupSyncOrb(view.findViewById(R.id.tv_sync_orb))

        view.findViewById<BrowseFrameLayout>(R.id.tv_tabs_root).onFocusSearchListener =
            BrowseFrameLayout.OnFocusSearchListener { focused, direction ->
                onFocusSearch(focused, direction)
            }

        if (childFragmentManager.findFragmentById(R.id.tv_tab_content) == null) {
            showTabContent(selectedTab, previousTab = null)
        }
        updateTabsState(animate = false)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            when {
                isShowingInnerScreen() -> closeInnerScreen()
                getContentView()?.hasFocus() == true -> tabs[selectedTab].requestFocus()
                selectedTab != TAB_HOME -> tabs[TAB_HOME].requestFocus()
                else -> {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        // The search orb is the first focusable view, so Android gives it the initial focus: start on the tab instead.
        // Back from a screen opened from the content (e.g. the details of a game), the content gets the focus back
        // instead: its fragment puts it on the element that was chosen (see TVHomeFragment), maybe once its data
        // is read again, so the tab is only used if nothing took the focus a moment later.
        val searchOrb = view.findViewById<View>(R.id.tv_search_orb)
        val returningToContent = contentHadFocus
        contentHadFocus = false
        view.post {
            if (returningToContent) {
                focusContent()
                view.postDelayed({ focusTabIfNothingFocused(searchOrb) }, CONTENT_FOCUS_WAIT_MS)
            } else {
                focusTabIfNothingFocused(searchOrb)
            }
        }
    }

    /** Same look as the search orb, with a cloud. A click while a sync is running is ignored and keeps the focus. */
    private fun setupSyncOrb(syncOrb: SearchOrbView) {
        syncOrb.orbColors =
            SearchOrbView.Colors(
                ContextCompat.getColor(requireContext(), R.color.md_theme_dark_primary),
                ContextCompat.getColor(requireContext(), R.color.tv_tab_focused_background),
                ContextCompat.getColor(requireContext(), R.color.md_theme_dark_background),
            )
        syncOrb.orbIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_tv_sync_orb)

        var syncInProgress = false
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PendingOperationsMonitor(requireContext().applicationContext)
                    .anySaveOperationInProgress()
                    .collect { syncInProgress = it }
            }
        }
        syncOrb.setOnOrbClickedListener {
            if (!syncInProgress) SaveSyncWork.enqueueManualWork(requireContext().applicationContext)
        }

        // Only usable when there is something to sync: otherwise it is dimmed and the focus skips it.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SaveSyncChanges.hasChanges.collect { hasChanges ->
                    if (!hasChanges && syncOrb.hasFocus()) tabs.getOrNull(selectedTab)?.requestFocus()
                    syncOrb.isFocusable = hasChanges
                    syncOrb.isEnabled = hasChanges
                    syncOrb.alpha = if (hasChanges) 1f else DISABLED_ORB_ALPHA
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The sync may have been configured or turned off in the settings meanwhile.
        val syncOrb = view?.findViewById<View>(R.id.tv_sync_orb) ?: return
        val saveSyncManager = (activity as? MainTVActivity)?.saveSyncManager ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val available =
                withContext(Dispatchers.IO) { saveSyncManager.isSupported() && saveSyncManager.isConfigured() }
            if (!available && syncOrb.hasFocus()) tabs.getOrNull(selectedTab)?.requestFocus()
            syncOrb.isVisible = available
        }
    }

    private fun focusTabIfNothingFocused(searchOrb: View) {
        val view = view ?: return
        if (!view.hasFocus() || searchOrb.hasFocus()) {
            tabs.getOrNull(selectedTab)?.requestFocus()
        }
    }

    /** Whether the content had the focus when this screen was covered by another one (e.g. the details of a game). */
    private var contentHadFocus = false

    override fun onPause() {
        // Read here and not when the view is destroyed: by then the new screen has already taken the focus.
        contentHadFocus = getContentView()?.hasFocus() == true
        super.onPause()
    }

    override fun onDestroyView() {
        pillAnimator?.cancel()
        pillAnimator = null
        tabPill = null
        pillTab = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTab)
    }

    private fun onFocusSearch(
        focused: View?,
        direction: Int,
    ): View? {
        val content = getContentView() ?: return null
        if (focused == null || !isDescendant(content, focused)) {
            return null
        }

        val nextInContent = FocusFinder.getInstance().findNextFocus(content, focused, direction)
        return when {
            nextInContent != null -> null
            // When moving up out of the content, always land on the selected tab.
            direction == View.FOCUS_UP -> tabs[selectedTab]
            // Left, right or down out of the content (e.g. left on the first card of a row) keeps the focus where it
            // is: otherwise Android would move it to the search button, the closest view on that side.
            else -> focused
        }
    }

    private fun isDescendant(
        parent: ViewGroup,
        view: View,
    ): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === parent) return true
            current = current.parent as? View
        }
        return false
    }

    private fun selectTab(index: Int) {
        if (index == selectedTab && childFragmentManager.findFragmentById(R.id.tv_tab_content) != null) {
            updateTabsState()
            return
        }
        val previousTab = selectedTab
        selectedTab = index
        showTabContent(index, previousTab)
        updateTabsState()
    }

    /**
     * Shows the content of the tab [index]. Coming from [previousTab], the content slides in from the side of the
     * new tab, like the TV Material tabs.
     */
    private fun showTabContent(
        index: Int,
        previousTab: Int?,
    ) {
        // The games of a system and the favorites belong to their tab: they are closed when another tab is selected.
        childFragmentManager.popBackStack(BACK_STACK_INNER_SCREEN, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val fragment =
            when (index) {
                TAB_SYSTEMS -> TVSystemsFragment()
                TAB_SETTINGS -> TVSettingsTabFragment()
                TAB_ABOUT -> TVAboutFragment()
                else -> TVHomeFragment()
            }
        val transaction = childFragmentManager.beginTransaction()
        when {
            previousTab == null || previousTab == index -> Unit
            index > previousTab -> transaction.setCustomAnimations(R.anim.tv_tab_enter_from_end, R.anim.tv_tab_exit_to_start)
            else -> transaction.setCustomAnimations(R.anim.tv_tab_enter_from_start, R.anim.tv_tab_exit_to_end)
        }
        transaction
            .replace(R.id.tv_tab_content, fragment)
            .commit()
    }

    private fun updateTabsState(animate: Boolean = true) {
        tabs.forEachIndexed { index, tab -> tab.isSelected = index == selectedTab }
        movePill(animate)
    }

    private var tabPill: View? = null
    private var pillAnimator: ValueAnimator? = null

    /** Tab the pill is under, or null while it slides from one tab to another. */
    private var pillTab: Int? = null

    /** Slides the pill to the selected tab (or puts it there right away, e.g. on start). */
    private fun movePill(animate: Boolean) {
        val pill = tabPill ?: return
        val target = tabs.getOrNull(selectedTab) ?: return
        // Not laid out yet: the layout listener moves it later.
        if (target.width == 0) return

        pillAnimator?.cancel()
        val toX = target.left.toFloat()
        val toWidth = target.width
        val alreadyThere = pill.translationX == toX && pill.width == toWidth
        if (!animate || pill.width == 0 || alreadyThere) {
            setPillBounds(pill, toX, toWidth)
            pillTab = selectedTab
            updateTabColors()
            return
        }

        val fromX = pill.translationX
        val fromWidth = pill.width
        val targetTab = selectedTab
        pillTab = null
        updateTabColors()
        pillAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = resources.getInteger(R.integer.tv_tab_animation_duration).toLong()
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    setPillBounds(pill, fromX + (toX - fromX) * fraction, (fromWidth + (toWidth - fromWidth) * fraction).toInt())
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        private var cancelled = false

                        override fun onAnimationCancel(animation: Animator) {
                            cancelled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (cancelled) return
                            pillTab = targetTab
                            updateTabColors()
                        }
                    },
                )
                start()
            }
    }

    /** The pill takes the height of the tabs (not the one of the bar) and the position and width given. */
    private fun setPillBounds(
        pill: View,
        x: Float,
        width: Int,
    ) {
        val tab = tabs.getOrNull(selectedTab) ?: return
        pill.translationX = x
        pill.translationY = tab.top.toFloat()
        if (pill.layoutParams.width != width || pill.layoutParams.height != tab.height) {
            pill.layoutParams = pill.layoutParams.apply {
                this.width = width
                this.height = tab.height
            }
        }
    }

    /**
     * Like Google TV: while the tabs bar has the focus the pill is white with dark text, otherwise it is green.
     * The text only turns dark once the pill is under it.
     */
    private fun updateTabColors() {
        val context = context ?: return
        val tabsFocused = tabs.any { it.isFocused }
        val pillColor = if (tabsFocused) R.color.tv_tab_focused_background else R.color.tv_tab_selected_background
        ((tabPill?.background?.mutate()) as? GradientDrawable)?.setColor(ContextCompat.getColor(context, pillColor))

        tabs.forEachIndexed { index, tab ->
            val textColor =
                when {
                    index != pillTab -> R.color.tv_tab_default_text
                    tabsFocused -> R.color.tv_tab_focused_text
                    else -> R.color.tv_tab_selected_text
                }
            (tab as? TextView)?.setTextColor(ContextCompat.getColor(context, textColor))
        }
    }

    /**
     * Screenshot of the game focused in the immersive list of the home screen, or null to hide it. It is drawn
     * behind the tabs bar, which is transparent meanwhile.
     */
    fun setHomeBackdrop(game: Game?) {
        val view = view ?: return
        val backdrop = view.findViewById<TVBackdropView>(R.id.tv_backdrop)
        val topBar = view.findViewById<View>(R.id.tv_top_bar)
        if (game != null) {
            topBar.background = null
            backdrop.show(game)
        } else {
            backdrop.hide { topBar.setBackgroundResource(R.color.leanback_background) }
        }
    }

    /** Opens the games of a system in place of the "Consoles" grid, keeping the tabs bar on screen. */
    fun showSystemGames(metaSystemId: String) {
        showInnerScreen(TVGamesFragment.newInstance(metaSystemId))
    }

    /** Opens all the favorites ("Show all" card) in place of the "Home" rows, keeping the tabs bar on screen. */
    fun showFavorites() {
        showInnerScreen(TVFavoritesFragment())
    }

    private fun showInnerScreen(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.tv_tab_content, fragment)
            .addToBackStack(BACK_STACK_INNER_SCREEN)
            .commit()
        childFragmentManager.executePendingTransactions()
        focusContent()
    }

    private fun isShowingInnerScreen(): Boolean = childFragmentManager.backStackEntryCount > 0

    /** Back from the games of a system or from the favorites returns to the tab content they were opened from. */
    private fun closeInnerScreen() {
        val contentHadFocus = getContentView()?.hasFocus() == true
        childFragmentManager.popBackStackImmediate()
        if (contentHadFocus) focusContent()
    }

    /** Used by the tab contents that cannot move the focus up to the tabs bar by themselves. */
    fun focusSelectedTab(): Boolean {
        return tabs.getOrNull(selectedTab)?.requestFocus() == true
    }

    private fun focusContent() {
        getContentView()?.requestFocus()
    }

    private fun getContentView(): ViewGroup? = view?.findViewById(R.id.tv_tab_content)

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val CONTENT_FOCUS_WAIT_MS = 600L
        private const val DISABLED_ORB_ALPHA = 0.4f
        private const val BACK_STACK_INNER_SCREEN = "inner_screen"
        private const val TAB_HOME = 0
        private const val TAB_SYSTEMS = 1
        private const val TAB_SETTINGS = 2
        private const val TAB_ABOUT = 3
    }
}
