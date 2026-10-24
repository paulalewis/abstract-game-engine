package com.castlefrog.agl.agents

import kotlin.math.sqrt
import kotlin.time.Duration

/**
 * Settings for [MctsAgent].
 *
 * The search runs until the first of [iterations] or [timeLimit] is reached, or until it
 * is cancelled. At least one of the two must be set.
 *
 * @property iterations the most search iterations per move (one playout each), or null
 *           for no iteration limit
 * @property timeLimit the most time to search per move, or null for no time limit
 * @property explorationConstant the UCB1 constant `c` in `mean + c * sqrt(ln N / n)`,
 *           on the scale of the rewards. The default √2 is for rewards between -1 and 1,
 *           the scale every built-in domain uses; there it is equivalent to about 0.71 on
 *           a 0 to 1 scale.
 * @property temperature how the final move is chosen from the root's visit counts. 0
 *           plays the most visited move. Above 0 a move is drawn with probability
 *           proportional to `visits^(1 / temperature)`, a softmax over log visit counts:
 *           1 picks in proportion to visits, and higher values play closer to random.
 *           Used to weaken low levels.
 * @property maxPlayoutLength the most steps one playout may play from a new node. A
 *           playout that reaches it is scored by [evaluator] if there is one, and
 *           otherwise as neutral (0 for every player). Keeps playouts finite in games
 *           such as Go, where random play may never end.
 * @property playoutPolicy picks the moves in a playout
 * @property evaluator optionally ends playouts early with estimated rewards
 */
data class MctsConfig(
    val iterations: Int? = DEFAULT_ITERATIONS,
    val timeLimit: Duration? = null,
    val explorationConstant: Double = sqrt(2.0),
    val temperature: Double = 0.0,
    val maxPlayoutLength: Int = DEFAULT_MAX_PLAYOUT_LENGTH,
    val playoutPolicy: PlayoutPolicy = UniformRandomPlayoutPolicy,
    val evaluator: StateEvaluator? = null
) {
    init {
        require(iterations != null || timeLimit != null) { "Set iterations, timeLimit, or both" }
        require(iterations == null || iterations > 0) { "iterations must be positive: $iterations" }
        require(timeLimit == null || timeLimit.isPositive()) { "timeLimit must be positive: $timeLimit" }
        require(explorationConstant >= 0.0) { "explorationConstant must not be negative: $explorationConstant" }
        require(temperature >= 0.0) { "temperature must not be negative: $temperature" }
        require(maxPlayoutLength >= 0) { "maxPlayoutLength must not be negative: $maxPlayoutLength" }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 1000
        const val DEFAULT_MAX_PLAYOUT_LENGTH = 500
    }
}

/**
 * Preset strengths for [MctsAgent], weakest first. Each sets an iteration budget and a
 * [MctsConfig.temperature]; the lower levels also pick their move with some randomness
 * so they play weakly rather than strangely. Playouts cost more in big games, so an app
 * may adjust a level with [MctsConfig.copy], for example to add a time limit.
 */
enum class MctsLevel(val iterations: Int, val temperature: Double) {
    BEGINNER(64, 1.0),
    EASY(256, 0.5),
    MEDIUM(1024, 0.25),
    HARD(4096, 0.0),
    EXPERT(16384, 0.0);

    /** The [MctsConfig] for this level, with the other settings at their defaults. */
    fun config(): MctsConfig = MctsConfig(iterations = iterations, temperature = temperature)
}
