package com.example.liftbook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.liftbook.R

enum class TopBarNavigation {
    /** Up to the previous screen. */
    Back,

    /** Dismiss a screen that edits something — leaving means not saving. */
    Close,
}

/**
 * The standard top bar for screens below the top level. It sits on the background colour and
 * shifts one surface tone when content scrolls beneath it — a divider would be heavier.
 * [showTitle] lets a screen reveal the title only once its own large headline scrolls away;
 * [subtitle], shown with it, is a slot so a line that changes often redraws on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiftBookTopBar(
    title: String,
    navigation: TopBarNavigation,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = {
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(TITLE_FADE_MILLIS)),
                exit = fadeOut(tween(TITLE_FADE_MILLIS)),
            ) {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.labelMedium,
                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            subtitle()
                        }
                    }
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                when (navigation) {
                    TopBarNavigation.Back -> Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                    TopBarNavigation.Close -> Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}

private const val TITLE_FADE_MILLIS = 150
