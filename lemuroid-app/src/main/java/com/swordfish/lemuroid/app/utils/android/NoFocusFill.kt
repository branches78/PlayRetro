package com.swordfish.lemuroid.app.utils.android

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/**
 * Without the lighter fill that clickable items draw while focused or pressed: on TV the white border of the focus is
 * enough to show where it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoFocusFill(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        // Material components (e.g. surfaces with a click) draw their own ripple: null turns it off.
        LocalRippleConfiguration provides null,
        content = content,
    )
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = javaClass.hashCode()
}
