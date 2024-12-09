package com.varabyte.kobweb.compose.attributes

import org.jetbrains.compose.web.attributes.AttrsScope

/**
 * @see AttrsScope.attr
 */
fun AttrsScope<*>.attr(attr: String, value: Boolean = true) =
    attr(attr, value.toString())
