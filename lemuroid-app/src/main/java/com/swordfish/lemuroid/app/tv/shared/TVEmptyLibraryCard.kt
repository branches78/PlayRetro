package com.swordfish.lemuroid.app.tv.shared

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.EmptyLibraryLoading
import com.swordfish.lemuroid.app.shared.library.LibraryLoadingProgress
import com.swordfish.lemuroid.app.shared.library.LibraryScanProgressFormatter
import com.swordfish.lemuroid.app.shared.settings.LibrarySourceActivity

/**
 * Card of the TV "Home" and "Consoles" tabs for an empty library. While the first scan runs (the library was empty
 * when it started) it shows its progress instead of the button, and the tab content stays hidden until it ends.
 */
class TVEmptyLibraryCard(private val cardView: View, private val contentView: View) {

    init {
        cardView.findViewById<View>(R.id.tv_empty_library_action)
            // Asks first whether the games come from a folder of the device or a network share.
            .setOnClickListener { LibrarySourceActivity.show(cardView.context) }
    }

    /** Whether the card covers the tab (empty library or its first scan). */
    val isShown: Boolean
        get() = cardView.isVisible

    fun update(
        isLibraryEmpty: Boolean,
        indexInProgress: Boolean,
        scanProgress: LibraryLoadingProgress?,
    ) {
        val loadingEmptyLibrary = EmptyLibraryLoading.update(isLibraryEmpty, scanProgress)

        val showCard = isLibraryEmpty || loadingEmptyLibrary
        cardView.isVisible = showCard
        // The tab content is still focusable and would take the remote focus instead of the button.
        contentView.isInvisible = showCard

        val actionView = cardView.findViewById<View>(R.id.tv_empty_library_action)
        val progressView = cardView.findViewById<View>(R.id.tv_empty_library_progress)
        actionView.isVisible = !loadingEmptyLibrary
        actionView.isEnabled = !indexInProgress
        progressView.isVisible = loadingEmptyLibrary

        cardView.findViewById<TextView>(R.id.tv_empty_library_title).setText(
            if (loadingEmptyLibrary) R.string.library_loading_title else R.string.home_empty_title,
        )
        cardView.findViewById<TextView>(R.id.tv_empty_library_message).setText(
            if (loadingEmptyLibrary) R.string.library_loading_message else R.string.home_empty_message,
        )

        if (loadingEmptyLibrary && scanProgress != null) {
            bindProgress(scanProgress)
        }
    }

    private fun bindProgress(scanProgress: LibraryLoadingProgress) {
        val context = cardView.context
        val progressBar = cardView.findViewById<ProgressBar>(R.id.tv_empty_library_progress_bar)
        val fraction = scanProgress.fraction
        progressBar.isIndeterminate = fraction == null
        if (fraction != null) {
            progressBar.progress = (fraction * progressBar.max).toInt()
        }
        cardView.findViewById<TextView>(R.id.tv_empty_library_progress_percent).text =
            fraction?.let { LibraryScanProgressFormatter.percent(it) }.orEmpty()
        cardView.findViewById<TextView>(R.id.tv_empty_library_progress_details).text =
            LibraryScanProgressFormatter.details(context, scanProgress)
    }
}
