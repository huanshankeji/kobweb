package com.varabyte.kobweb.compose.foundation.layout

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import org.jetbrains.compose.web.dom.Div

/**
 * An element which grows to consume all remaining space in a [Row] or [Column].
 */
@Composable
fun Spacer(modifier: Modifier = Modifier) {
    Div(attrs = modifier.toAttrs { classes("kobweb-spacer") })
}
