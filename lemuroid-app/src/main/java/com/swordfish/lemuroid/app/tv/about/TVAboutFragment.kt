package com.swordfish.lemuroid.app.tv.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.about.AboutContent
import com.swordfish.lemuroid.app.utils.android.TVNavigationSounds

/** Content of the "About" tab on TV: the same information as the mobile "About" tab. */
class TVAboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TVNavigationSounds {
                    AppTheme {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            AboutContent(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = dimensionResource(R.dimen.tv_content_side_margin),
                                            vertical = 8.dp,
                                        ),
                                focusableSections = true,
                            )
                        }
                    }
                }
            }
        }
    }
}
