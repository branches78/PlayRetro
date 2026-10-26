package com.swordfish.lemuroid.app.tv.home

import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.swordfish.lemuroid.app.tv.shared.TVCardStyle

class SettingPresenter(
    private val cardSize: Int,
    private val cardPadding: Int,
    // Width of the card, square by default (the home rows have 16:9 cards).
    private val cardWidth: Int = cardSize,
) : Presenter() {
    override fun onBindViewHolder(
        viewHolder: Presenter.ViewHolder?,
        item: Any,
    ) {
        val setting = item as TVSetting
        (viewHolder as ViewHolder).mCardView.titleText = viewHolder.view.context.resources.getString(setting.type.text)
        viewHolder.mCardView.setMainImageDimensions(cardWidth, cardSize)
        viewHolder.mCardView.mainImageView.setImageResource(setting.type.icon)

        viewHolder.mCardView.mainImageView.setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
        viewHolder.mCardView.setMainImageScaleType(ImageView.ScaleType.FIT_CENTER)

        viewHolder.mCardView.isEnabled = setting.enabled
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val cardView = ImageCardView(parent.context)
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        TVCardStyle.apply(cardView)
        return ViewHolder(cardView)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder?) {}

    class ViewHolder(view: ImageCardView) : Presenter.ViewHolder(view) {
        val mCardView: ImageCardView = view
    }
}
