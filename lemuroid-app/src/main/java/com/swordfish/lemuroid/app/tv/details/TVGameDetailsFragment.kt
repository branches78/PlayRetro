package com.swordfish.lemuroid.app.tv.details

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.covers.CoverAspectRatio
import com.swordfish.lemuroid.app.shared.covers.GameLogoOrTitle
import com.swordfish.lemuroid.app.shared.details.GameAction
import com.swordfish.lemuroid.app.shared.details.GameActionsHost
import com.swordfish.lemuroid.app.shared.details.GameDescriptionProvider
import com.swordfish.lemuroid.app.shared.details.GameDetailsViewModel
import com.swordfish.lemuroid.app.shared.details.gameCleanTitle
import com.swordfish.lemuroid.app.shared.details.gameRegion
import com.swordfish.lemuroid.app.shared.details.gameSystemName
import com.swordfish.lemuroid.app.tv.home.TVBackdropView
import com.swordfish.lemuroid.app.tv.home.TVHomeFragment
import com.swordfish.lemuroid.app.tv.shared.TVGameContextMenu
import com.swordfish.lemuroid.app.utils.android.TVNavigationSounds
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Details of a game on TV, opened when a game is chosen (setting "Show game details"), like the details page of a
 * streaming app: screenshot in the top right corner, title, system and description on the left, the actions of the
 * long press menu as buttons (play first and focused) and other games of the same system below.
 */
class TVGameDetailsFragment : Fragment() {
    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var descriptionProvider: GameDescriptionProvider

    /** Game of "More from" opened from this screen: back from it, the focus goes to its card instead of Play. */
    private var openedGameId: Int? = null

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val gameId = requireArguments().getInt(ARG_GAME_ID)
        // The view is made again when coming back from another game: only then it has one to focus.
        val refocusGameId = openedGameId
        openedGameId = null
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TVNavigationSounds {
                    AppTheme {
                        val viewModel: GameDetailsViewModel =
                            viewModel(
                                factory =
                                    GameDetailsViewModel.Factory(
                                        gameId,
                                        retrogradeDb,
                                        descriptionProvider,
                                    ),
                            )
                        val state by viewModel.state.collectAsState()
                        val game = state.game
                        Box(modifier = Modifier.fillMaxSize().background(colorResource(R.color.leanback_background))) {
                            // Shown when everything read from the device is there, instead of growing piece by piece.
                            if (game != null && state.isReady) {
                                TVGameDetailsScreen(
                                    state = state,
                                    game = game,
                                    gameInteractor = gameInteractor,
                                    onOpenCoreOptions = {
                                        TVGameContextMenu.show(context, it, gameInteractor, startWithCoreOptions = true)
                                    },
                                    onOpenGame = {
                                        openedGameId = it.id
                                        open(this@TVGameDetailsFragment, it)
                                    },
                                    refocusGameId = refocusGameId,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_GAME_ID = "game_id"

        /**
         * A game chosen on TV: opens its details, or starts it right away without the setting "Show game details".
         * "Continue playing" doesn't use it: it always starts the game.
         */
        fun openOrPlay(
            fragment: Fragment,
            game: Game,
            gameInteractor: GameInteractor,
        ) {
            if (gameInteractor.showDetailsOnClick()) open(fragment, game) else gameInteractor.onGamePlay(game)
        }

        /** Opens the details of [game] over the current screen with a crossfade, like on mobile; back returns to it. */
        fun open(
            fragment: Fragment,
            game: Game,
        ) {
            val options =
                navOptions {
                    anim {
                        enter = R.anim.tv_details_fade_in
                        exit = R.anim.tv_details_fade_out
                        popEnter = R.anim.tv_details_fade_in
                        popExit = R.anim.tv_details_fade_out
                    }
                }
            fragment.findNavController().navigate(R.id.navigation_game_details, bundleOf(ARG_GAME_ID to game.id), options)
        }
    }

    @dagger.Module
    class Module
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TVGameDetailsScreen(
    state: GameDetailsViewModel.State,
    game: Game,
    gameInteractor: GameInteractor,
    onOpenCoreOptions: (Game) -> Unit,
    onOpenGame: (Game) -> Unit,
    refocusGameId: Int?,
) {
    var moreRowFocused by remember { mutableStateOf(false) }
    // Back from a game of "More from": its card is focused instead of Play, with the row scrolled to it.
    val refocusIndex = state.moreGames.indexOfFirst { it.id == refocusGameId }
    val refocusRequester = remember { FocusRequester() }
    var showFullDescription by remember { mutableStateOf(false) }
    // With the focus on the other games the details move up and fade, like a streaming app.
    val detailsOffset by animateDpAsState(if (moreRowFocused) (-DETAILS_SCROLL).dp else 0.dp, label = "detailsOffset")
    val detailsAlpha by animateFloatAsState(if (moreRowFocused) 0f else 1f, label = "detailsAlpha")
    val backdropAlpha by animateFloatAsState(if (moreRowFocused) 0.25f else 1f, label = "backdropAlpha")

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(720.dp, 405.dp)
                    .graphicsLayer { alpha = backdropAlpha },
            factory = { TVBackdropView(it) },
            update = { it.show(game) },
        )

        // Taller than the screen: measured with its whole height, otherwise the row of other games gets only the room
        // left at the bottom and its cards are cut. With the focus on that row everything moves up (offset).
        Column(
            modifier =
                Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .padding(top = 64.dp)
                    .offset(y = detailsOffset),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(start = SIDE_PADDING.dp)
                        .width(520.dp)
                        .graphicsLayer { alpha = detailsAlpha },
            ) {
                TVDetailsTexts(state, game) { showFullDescription = true }
            }

            GameActionsHost(game, gameInteractor, onOpenCoreOptions) { actions ->
                val playFocusRequester = remember { FocusRequester() }
                // Only Play/Resume has its name; the other buttons are round icons whose name shows below them while
                // focused. The row still scrolls to the focused one if it doesn't fit (the padding is inside the
                // scroll, so the zoom of the first and last ones is not cut).
                // Coming from the description or from "More from", the focus always enters on Play/Resume, not on
                // the button closest to where it was.
                Row(
                    modifier =
                        Modifier
                            .graphicsLayer { alpha = detailsAlpha }
                            .focusProperties { enter = { playFocusRequester } }
                            .focusGroup()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = SIDE_PADDING.dp),
                    horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    TVActionButton(actions.play, iconOnly = false, Modifier.focusRequester(playFocusRequester))
                    actions.restart?.let { TVActionButton(it, iconOnly = true) }
                    actions.coreOptions?.let { TVActionButton(it, iconOnly = true) }
                    actions.selectCore?.let { TVActionButton(it, iconOnly = true) }
                    TVActionButton(actions.favorite, iconOnly = true)
                    TVActionButton(actions.customName, iconOnly = true)
                    TVActionButton(actions.customCover, iconOnly = true)
                    actions.shortcut?.let { TVActionButton(it, iconOnly = true) }
                }
                // Play is focused when the screen opens, so playing is just one more press of OK.
                LaunchedEffect(game.id) {
                    val requester = if (refocusIndex >= 0) refocusRequester else playFocusRequester
                    repeat(FOCUS_ATTEMPTS) {
                        if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
                        delay(FOCUS_ATTEMPT_DELAY_MS)
                    }
                }
            }

            if (state.moreGames.isNotEmpty()) {
                Text(
                    // Right below the room of the name of the focused button.
                    modifier = Modifier.padding(start = SIDE_PADDING.dp, bottom = 4.dp),
                    text = stringResource(R.string.game_details_more_from, gameSystemName(game).orEmpty()),
                    color = colorResource(R.color.md_theme_dark_onSurfaceVariant),
                    // Same size as the titles of the home rows (Leanback headers), bigger than the game names.
                    fontSize = ROW_TITLE_SIZE.sp,
                )
                // The side and vertical padding leave room for the zoom of the focused card, which would be cut by
                // the edges of the row otherwise; the first card stays aligned with the texts above.
                LazyRow(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = refocusIndex.coerceAtLeast(0)),
                    modifier = Modifier.onFocusChanged { moreRowFocused = it.hasFocus },
                    horizontalArrangement = Arrangement.spacedBy(homeCardSpacing()),
                    contentPadding = PaddingValues(horizontal = SIDE_PADDING.dp, vertical = MORE_ROW_ZOOM_ROOM.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    items(state.moreGames, key = { it.id }) { other ->
                        TVMoreGameCard(
                            game = other,
                            modifier = if (other.id == refocusGameId) Modifier.focusRequester(refocusRequester) else Modifier,
                        ) { onOpenGame(other) }
                    }
                }
            }
        }
    }

    val description = state.description
    if (showFullDescription && description != null) {
        TVDescriptionDialog(game, description) { showFullDescription = false }
    }
}

@Composable
private fun TVDetailsTexts(
    state: GameDetailsViewModel.State,
    game: Game,
    onOpenDescription: () -> Unit,
) {
    // Same order as on mobile: title (the logo of the game when it has one), then system, developer and region.
    GameLogoOrTitle(game, maxHeight = LOGO_MAX_HEIGHT.dp, maxWidth = LOGO_MAX_WIDTH.dp) {
        Text(
            text = gameCleanTitle(game),
            color = colorResource(R.color.md_theme_dark_onSurface),
            fontSize = 40.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 44.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val meta =
        listOfNotNull(gameSystemName(game), game.developer?.takeIf { it.isNotBlank() }, gameRegion(game))
            .joinToString(" · ")
    val facts =
        listOfNotNull(
            game.lastPlayedAt?.let {
                stringResource(R.string.game_details_last_played) to
                    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                        .toString()
            },
        )

    // TEXTS_GAP (the room below the buttons) separates the last text from the buttons; the facts are centered between
    // the description or the meta line and the buttons. The meta line always stays right below the title, and it and
    // the description already keep 8dp below them (padding of the description highlight).
    Text(
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        text = meta,
        color = colorResource(R.color.md_theme_dark_primary),
        fontSize = 14.sp,
    )

    TVDescription(state.description, onOpenDescription)

    if (facts.isNotEmpty()) {
        Column(
            modifier = Modifier.padding(top = (TEXTS_GAP - 8).dp, bottom = TEXTS_GAP.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            facts.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(modifier = Modifier.width(110.dp), text = label, color = Color(0xFF9AA395), fontSize = 12.sp)
                    Text(text = value, color = colorResource(R.color.md_theme_dark_onSurface), fontSize = 14.sp)
                }
            }
        }
    } else {
        // The meta line or the description is the last one: the same gap above the buttons.
        Spacer(modifier = Modifier.height((TEXTS_GAP - 8).dp))
    }
}

/**
 * Description of the game, as tall as its text up to four lines, and nothing at all when the game has none: what
 * follows moves up. Only when the text doesn't fit it can be focused (up from the buttons): its background lights up,
 * and OK opens it whole in a dialog.
 */
@Composable
private fun TVDescription(
    description: String?,
    onOpen: () -> Unit,
) {
    if (description.isNullOrBlank()) return
    var focused by remember { mutableStateOf(false) }
    var overflows by remember(description) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    // Focused, only the background changes (no border).
    // The padding of the highlight is taken back with the offset, so the text stays aligned with the title.
    Text(
        modifier =
            Modifier
                .offset(x = (-DESCRIPTION_PADDING).dp)
                .clip(shape)
                .background(if (focused) colorResource(R.color.md_theme_dark_surfaceContainerHigh) else Color.Transparent)
                .onFocusChanged { focused = it.isFocused }
                .then(if (overflows) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(DESCRIPTION_PADDING.dp),
        text = description,
        color = colorResource(R.color.md_theme_dark_onSurfaceVariant),
        fontSize = 16.sp,
        lineHeight = 24.sp,
        maxLines = DESCRIPTION_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { overflows = it.hasVisualOverflow },
    )
}

/** The whole description: ↑ and ↓ scroll it, back closes it. */
@Composable
private fun TVDescriptionDialog(
    game: Game,
    description: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        val focusRequester = remember { FocusRequester() }
        val scrollStep = with(LocalDensity.current) { DIALOG_SCROLL_STEP.dp.toPx() }

        Surface(
            modifier = Modifier.widthIn(max = 640.dp).heightIn(max = 440.dp),
            shape = RoundedCornerShape(20.dp),
            color = colorResource(R.color.md_theme_dark_surfaceContainer),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = gameCleanTitle(game),
                    color = colorResource(R.color.md_theme_dark_onSurface),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier =
                        Modifier
                            .padding(top = 16.dp)
                            .weight(1f, fill = false)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val delta =
                                    when (event.key) {
                                        Key.DirectionDown -> scrollStep
                                        Key.DirectionUp -> -scrollStep
                                        else -> return@onPreviewKeyEvent false
                                    }
                                scope.launch { scrollState.animateScrollBy(delta) }
                                true
                            }
                            .focusable()
                            .verticalScroll(scrollState),
                    text = description,
                    color = colorResource(R.color.md_theme_dark_onSurfaceVariant),
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                )
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = stringResource(R.string.game_details_description_source),
                    color = Color(0xFF9AA395),
                    fontSize = 12.sp,
                )
            }
        }

        LaunchedEffect(Unit) {
            repeat(FOCUS_ATTEMPTS) {
                if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(FOCUS_ATTEMPT_DELAY_MS)
            }
        }
    }
}

/**
 * Button of the details: a pill with icon and text, or a round icon whose name shows below it while focused.
 * The focused one is white with dark content and a bit bigger, like the TV tabs.
 */
@Composable
private fun TVActionButton(
    action: GameAction,
    iconOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "buttonScale")
    val background = if (focused) Color(0xFFF1F3EC) else colorResource(R.color.md_theme_dark_secondaryContainer)
    val content = if (focused) Color(0xFF111311) else colorResource(R.color.md_theme_dark_onSurface)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier =
                modifier
                    .scale(scale)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(BUTTON_HEIGHT.dp / 2))
                    .background(background)
                    .clickable(onClick = action.onClick)
                    .height(BUTTON_HEIGHT.dp)
                    .then(
                        if (iconOnly) {
                            Modifier.width(BUTTON_HEIGHT.dp)
                        } else {
                            Modifier.padding(horizontal = BUTTON_SIDE_PADDING.dp)
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                modifier = Modifier.size(BUTTON_ICON_SIZE.dp),
                imageVector = action.icon,
                contentDescription = action.label,
                tint = content,
            )
            if (!iconOnly) {
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = action.label,
                    color = content,
                    fontSize = BUTTON_TEXT_SIZE.sp,
                    maxLines = 1,
                )
            }
        }
        if (iconOnly) {
            // The name is centered below the icon without widening it, so the buttons don't move.
            Text(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                            layout(0, placeable.height) { placeable.place(-placeable.width / 2, 0) }
                        }
                        // After the layout: a layer as wide as the text. With zero width it was never drawn.
                        .graphicsLayer { alpha = if (focused) 1f else 0f },
                text = action.label,
                color = colorResource(R.color.md_theme_dark_onSurface),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * Card of another game, like the cards of the TV home rows: a square cell with room for the texts, and inside it,
 * at the top and centered, the card with the real shape of the cover and the title and system below, on the light
 * tone of the cards. Focused it only grows (no border).
 */
@Composable
private fun TVMoreGameCard(
    game: Game,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "cardScale")
    val shape = RoundedCornerShape(12.dp)
    val cardSize = homeCardSize()
    // Real shape of the cover once loaded, the usual one of its system before.
    val coverRatio = CoverAspectRatio.forGame(game)
    val coverWidth = if (coverRatio >= 1f) cardSize else cardSize * coverRatio

    Box(modifier = Modifier.width(cardSize), contentAlignment = Alignment.TopCenter) {
        // Size of the cell: the square of the cover and the texts, the same whatever the shape of the cover.
        Column(modifier = Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics { }) {
            Spacer(modifier = Modifier.size(cardSize))
            TVMoreGameTexts(game)
        }
        Column(
            modifier =
                modifier
                    .width(coverWidth)
                    .scale(scale)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(shape)
                    .background(colorResource(R.color.tv_card_info_background))
                    .clickable(onClick = onClick),
        ) {
            LemuroidGameImage(game = game, fixedRatio = coverRatio)
            TVMoreGameTexts(game)
        }
    }
}

@Composable
private fun TVMoreGameTexts(game: Game) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(
            text = game.displayName(),
            color = colorResource(R.color.md_theme_dark_onSurface),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = remember(game.id) { GameUtils.getGameSubtitle(context, game) },
            color = colorResource(R.color.md_theme_dark_onSurfaceVariant),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Size of the square cards of the TV home rows. */
@Composable
private fun homeCardSize(): Dp {
    val resources = LocalContext.current.resources
    return with(LocalDensity.current) { TVHomeFragment.homeCardSize(resources).toDp() }
}

@Composable
private fun homeCardSpacing(): Dp {
    val resources = LocalContext.current.resources
    return with(LocalDensity.current) { TVHomeFragment.homeCardSpacing(resources).toDp() }
}

private const val SIDE_PADDING = 56
private const val LOGO_MAX_WIDTH = 480
private const val LOGO_MAX_HEIGHT = 96
// Play/Resume is a pill with its name, the other buttons are round icons of the same height. Only one button has
// a name, so they have their original size again.
private const val BUTTON_HEIGHT = 44
private const val BUTTON_SIDE_PADDING = 20
private const val BUTTON_ICON_SIZE = 24
private const val BUTTON_TEXT_SIZE = 16
private const val BUTTON_SPACING = 10
private const val DETAILS_SCROLL = 250
private const val DESCRIPTION_LINES = 4
private const val ROW_TITLE_SIZE = 20
private const val DESCRIPTION_PADDING = 8

// Between the last text of the details and the buttons, like the room below the buttons.
private const val TEXTS_GAP = 28
private const val MORE_ROW_ZOOM_ROOM = 12
private const val DIALOG_SCROLL_STEP = 120
private const val FOCUS_ATTEMPTS = 20
private const val FOCUS_ATTEMPT_DELAY_MS = 50L
