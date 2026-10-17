package com.castlefrog.agl.domains.go

/**
 * Area score of a Go position, as counted by [GoSimulator.score].
 *
 * Territory is empty points whose region reaches only that color. An empty
 * region that reaches both colors, or neither, counts for no one.
 *
 * @param komi points added to white
 */
data class GoScore(
    val blackStones: Int,
    val blackTerritory: Int,
    val whiteStones: Int,
    val whiteTerritory: Int,
    val komi: Double
) {
    /** Black's total: stones plus territory. */
    val black: Double get() = (blackStones + blackTerritory).toDouble()

    /** White's total: stones plus territory plus [komi]. */
    val white: Double get() = whiteStones + whiteTerritory + komi
}
