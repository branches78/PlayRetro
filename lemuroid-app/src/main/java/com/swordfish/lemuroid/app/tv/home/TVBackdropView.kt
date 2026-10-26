package com.swordfish.lemuroid.app.tv.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import coil.imageLoader
import coil.load
import coil.size.Scale
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.GameScreenshots
import com.swordfish.lemuroid.app.shared.covers.TrimBlackBordersTransformation
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Background of the immersive list of the TV home screen: a screenshot of the focused game in the top right corner,
 * faded into the background color on the left and at the bottom so the texts and the tabs stay readable.
 *
 * The screenshot comes from the libretro thumbnails server, like the covers. When a game has none, its title screen
 * is used, and then its cover (blurred where Android allows it).
 */
class TVBackdropView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : AppCompatImageView(context, attrs) {
        private val backgroundColor = ContextCompat.getColor(context, R.color.leanback_background)
        private val leftScrim = Paint()
        private val bottomScrim = Paint()
        private val topScrim = Paint()

        private var currentGameId: Int? = null

        init {
            scaleType = ScaleType.CENTER_CROP
            alpha = 0f
        }

        fun show(game: Game) {
            animate().alpha(1f).setDuration(FADE_DURATION_MS).start()
            if (currentGameId == game.id) return
            currentGameId = game.id
            loadImage(GameScreenshots.urls(game), game.displayCoverUrl())
        }

        fun hide(onHidden: () -> Unit) {
            animate().alpha(0f).setDuration(FADE_DURATION_MS).withEndAction(onHidden).start()
        }

        private fun loadImage(
            urls: List<String>,
            coverUrl: String?,
        ) {
            val url = urls.firstOrNull()
            if (url == null) {
                loadCover(coverUrl)
                return
            }
            load(url, context.imageLoader) {
                crossfade(CROSSFADE_DURATION_MS)
                // Every image fills the whole view, also while crossfading from one of another shape.
                scale(Scale.FILL)
                transformations(TrimBlackBordersTransformation())
                listener(
                    onSuccess = { _, result ->
                        setBlurred(false)
                        // Screenshots are small pixel art images: they look sharper scaled without filtering.
                        (result.drawable as? BitmapDrawable)?.isFilterBitmap = false
                    },
                    onError = { _, _ -> loadImage(urls.drop(1), coverUrl) },
                )
            }
        }

        private fun loadCover(coverUrl: String?) {
            if (coverUrl == null) {
                setImageDrawable(null)
                return
            }
            load(coverUrl, context.imageLoader) {
                crossfade(CROSSFADE_DURATION_MS)
                scale(Scale.FILL)
                listener(onSuccess = { _, _ -> setBlurred(true) })
            }
        }

        private fun setBlurred(blurred: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            setRenderEffect(
                if (blurred) RenderEffect.createBlurEffect(COVER_BLUR_RADIUS, COVER_BLUR_RADIUS, Shader.TileMode.CLAMP) else null,
            )
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            // Eased fades (instead of linear ones) so the edges of the screenshot melt into the background
            // without a visible line where each fade starts.
            leftScrim.shader = edgeFade(0f, 0f, w * LEFT_SCRIM_WIDTH, 0f, 1f, LEFT_SCRIM_SOLID)
            bottomScrim.shader = edgeFade(0f, h.toFloat(), 0f, h * (1 - BOTTOM_SCRIM_HEIGHT), 1f, BOTTOM_SCRIM_SOLID)
            topScrim.shader = edgeFade(0f, 0f, 0f, h * TOP_SCRIM_HEIGHT, TOP_SCRIM_ALPHA, 0f)
        }

        /**
         * Background color from [startAlpha] at (x0, y0) to transparent at (x1, y1), following an ease-out curve.
         * The first [solid] part of the way keeps [startAlpha], so the very edge of the image never shows: a bright
         * image would otherwise show a line there even under a nearly opaque fade.
         */
        private fun edgeFade(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            startAlpha: Float,
            solid: Float,
        ): Shader {
            val positions = FloatArray(FADE_STEPS) { solid + (1 - solid) * it / (FADE_STEPS - 1f) }
            val colors =
                IntArray(FADE_STEPS) {
                    val remaining = 1f - it / (FADE_STEPS - 1f)
                    val alpha = startAlpha * remaining * remaining * (3 - 2 * remaining)
                    ColorUtils.setAlphaComponent(backgroundColor, (alpha * 255).toInt())
                }
            return LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
        }

        // An ImageView without background says it has no overlapping drawing, so while it fades (when the tab
        // changes) Android applies the transparency to the screenshot and to each fade on its own: the fades stop
        // covering the edges of the screenshot. Drawn as one layer, the fades stay over it the whole time.
        override fun hasOverlappingRendering(): Boolean = true

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            // The parents don't clip their children (for the zoom of the focused tabs and cards): keep the cropped
            // screenshot inside the view, where the fades are.
            canvas.save()
            canvas.clipRect(0f, 0f, w, h)
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, w, h, leftScrim)
            canvas.drawRect(0f, 0f, w, h, bottomScrim)
            canvas.drawRect(0f, 0f, w, h, topScrim)
            canvas.restore()
        }

        companion object {
            private const val FADE_DURATION_MS = 250L
            private const val CROSSFADE_DURATION_MS = 300
            private const val COVER_BLUR_RADIUS = 40f
            private const val LEFT_SCRIM_WIDTH = 0.7f
            private const val LEFT_SCRIM_SOLID = 0.1f
            private const val BOTTOM_SCRIM_HEIGHT = 0.55f
            private const val BOTTOM_SCRIM_SOLID = 0.05f
            private const val TOP_SCRIM_HEIGHT = 0.3f
            private const val TOP_SCRIM_ALPHA = 0.8f
            private const val FADE_STEPS = 8
        }
    }
