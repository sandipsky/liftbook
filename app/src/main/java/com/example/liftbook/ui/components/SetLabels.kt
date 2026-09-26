package com.example.liftbook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetType

/**
 * Working-set numbers, in order: warm-ups aren't counted (FR-3.10) and get null; every other set
 * — normal, drop or failure — takes the next number.
 */
fun workingSetNumbers(sets: List<LoggedSet>): List<Int?> {
    var count = 0
    return sets.map { if (it.setType == SetType.WARMUP) null else ++count }
}

/** The compact marker shown beside a set in a list: its number, or W / D / F. */
@Composable
fun setMarker(setType: SetType, number: Int?): String = when (setType) {
    SetType.WARMUP -> stringResource(R.string.set_marker_warmup)
    SetType.DROP -> stringResource(R.string.set_marker_drop)
    SetType.FAILURE -> stringResource(R.string.set_marker_failure)
    SetType.NORMAL -> number?.toString().orEmpty()
}

/** A set's full label: "Set 2", "Warm-up", "Set 3 · Drop". */
@Composable
fun setTitle(setType: SetType, number: Int?): String = when {
    setType == SetType.WARMUP || number == null -> stringResource(R.string.set_title_warmup)
    setType == SetType.DROP -> stringResource(R.string.set_title_drop, number)
    setType == SetType.FAILURE -> stringResource(R.string.set_title_failure, number)
    else -> stringResource(R.string.set_title, number)
}

/** What TalkBack says for a set's marker: "Set 2", "Warm-up set", "Set 3, drop set". */
@Composable
fun setSpokenTitle(setType: SetType, number: Int?): String = when {
    setType == SetType.WARMUP || number == null -> stringResource(R.string.set_spoken_warmup)
    setType == SetType.DROP -> stringResource(R.string.set_spoken_drop, number)
    setType == SetType.FAILURE -> stringResource(R.string.set_spoken_failure, number)
    else -> stringResource(R.string.set_title, number)
}
