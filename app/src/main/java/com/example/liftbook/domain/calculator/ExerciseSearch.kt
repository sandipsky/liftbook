package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseFilter
import java.text.Normalizer
import java.util.Locale

/**
 * Library search and filtering (FR-1.4).
 *
 * Runs in memory rather than in SQL: the library is a few hundred rows at most, so this is
 * instant, and it allows matching that `LIKE` can't express. Every word of the query must
 * match the name, in any order. A word matches if it starts a word of the name ("bench" →
 * "Bench Press"), or — ranked lower, and only from three letters up — appears anywhere in it
 * once spacing and punctuation are ignored ("pullup" → "Pull-Up"). Case and accents are
 * ignored, a trailing plural "s" is forgiven, and common gym shorthand ("db", "rdl", "ohp")
 * is understood.
 *
 * With no query, results are alphabetical. With one, names that start with the query come
 * first, then whole-word matches, then partial ones — alphabetical within each group.
 */
fun searchExercises(exercises: List<Exercise>, filter: ExerciseFilter): List<Exercise> {
    val query = normalizeForSearch(filter.query)
    val terms = query.split(' ').filter { it.isNotEmpty() }.map(::alternativesFor)
    return exercises.asSequence()
        .filter { filter.muscleGroup == null || it.primaryMuscle == filter.muscleGroup }
        .filter { filter.equipment == null || it.equipment == filter.equipment }
        .mapNotNull { exercise ->
            val name = SearchableName(exercise.name)
            name.rank(query, terms)?.let { rank -> Ranked(exercise, rank, name.normalized) }
        }
        .sortedWith(compareBy(Ranked::rank, Ranked::sortKey))
        .map(Ranked::exercise)
        .toList()
}

/** Lower-cased, accents stripped, and everything but letters and digits turned into single spaces. */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()

private class Ranked(val exercise: Exercise, val rank: Int, val sortKey: String)

private class SearchableName(name: String) {
    val normalized = normalizeForSearch(name)
    private val words = normalized.split(' ')
    private val compact = normalized.replace(" ", "")

    /** Null when some term doesn't match at all. */
    fun rank(query: String, terms: List<List<List<String>>>): Int? {
        if (terms.isEmpty()) return RANK_ANY
        if (terms.all { alternatives -> alternatives.any { phrase -> phrase.all(::startsAWord) } }) {
            return if (normalized.startsWith(query)) RANK_NAME_PREFIX else RANK_WORD_PREFIX
        }
        val matchesLoosely = terms.all { alternatives ->
            alternatives.any { phrase -> phrase.all { startsAWord(it) || appearsInside(it) } }
        }
        return if (matchesLoosely) RANK_PARTIAL else null
    }

    private fun startsAWord(term: String) = words.any { it.startsWith(term) }

    // Short fragments match inside far too many words ("ab" in "cable"), so they must start one.
    private fun appearsInside(term: String) = term.length >= MIN_PARTIAL_LENGTH && compact.contains(term)
}

/**
 * The ways one query word can be satisfied, each a list of words that must all match: the word
 * itself, its possible singulars, and its expansion if it is known shorthand. The literal word
 * is always kept, so a custom exercise actually named "DB Shrug" is still found by "db".
 */
private fun alternativesFor(word: String): List<List<String>> = buildList {
    add(listOf(word))
    singularsOf(word).forEach { add(listOf(it)) }
    SHORTHAND[word]?.let(::add)
}

/** Both candidate singulars, since English can't tell them apart: lunges → lunge, presses → press. */
private fun singularsOf(word: String): List<String> = buildList {
    if (word.length >= 3 && word.endsWith('s') && !word.endsWith("ss")) add(word.dropLast(1))
    if (word.length >= 5 && word.endsWith("es")) add(word.dropLast(2))
}

private val SHORTHAND: Map<String, List<String>> = mapOf(
    "db" to listOf("dumbbell"),
    "bb" to listOf("barbell"),
    "kb" to listOf("kettlebell"),
    "bp" to listOf("bench", "press"),
    "dl" to listOf("deadlift"),
    "rdl" to listOf("romanian", "deadlift"),
    "sldl" to listOf("stiff", "leg", "deadlift"),
    "ohp" to listOf("overhead", "press"),
    "bss" to listOf("bulgarian", "split", "squat"),
)

private const val RANK_ANY = 0
private const val RANK_NAME_PREFIX = 0
private const val RANK_WORD_PREFIX = 1
private const val RANK_PARTIAL = 2

private const val MIN_PARTIAL_LENGTH = 3

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
