package com.studywithme.app.data

/** Days until the next review after passing review number 1, 2 and 3 (1, then 3, then 7 days later). */
private val INTERVALS = listOf(1, 3, 7)

/** First review of a new note is one day after it was added. */
fun firstReviewDay(createdDay: Long): Long = createdDay + INTERVALS[0]

/**
 * Result of one review on [today]: the new stage and the next review day (null when the note is mastered).
 * A wrong answer restarts the cycle; passing the third review (stage 2 -> 3) finishes it.
 */
fun afterReview(stage: Int, correct: Boolean, today: Long): Pair<Int, Long?> = when {
    !correct -> 0 to today + INTERVALS[0]
    stage + 1 >= INTERVALS.size -> INTERVALS.size to null
    else -> (stage + 1) to today + INTERVALS[stage + 1]
}
