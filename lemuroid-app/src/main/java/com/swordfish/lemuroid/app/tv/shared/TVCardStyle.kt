package com.swordfish.lemuroid.app.tv.shared

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import com.swordfish.lemuroid.R

/** Makes leanback cards look like the mobile ones: rounded corners, same colors and texts. */
object TVCardStyle {
    fun apply(cardView: ImageCardView) {
        val context = cardView.context
        val cornerRadius = context.resources.getDimension(R.dimen.tv_card_corner_radius)
        // The light tone behind the images of the consoles.
        val cardColor = ContextCompat.getColor(context, R.color.tv_card_info_background)

        cardView.setBackgroundColor(cardColor)
        cardView.setInfoAreaBackgroundColor(cardColor)
        cardView.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        cardView.clipToOutline = true
        cardView.foreground = ContextCompat.getDrawable(context, R.drawable.tv_card_focus_foreground)

        cardView.findViewById<TextView>(androidx.leanback.R.id.title_text)
            ?.setTextColor(ContextCompat.getColor(context, R.color.md_theme_dark_onSurface))
        cardView.findViewById<TextView>(androidx.leanback.R.id.content_text)
            ?.setTextColor(ContextCompat.getColor(context, R.color.md_theme_dark_onSurfaceVariant))
    }
}
