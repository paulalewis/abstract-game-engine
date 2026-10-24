package com.castlefrog.agl.benchmark

import com.castlefrog.agl.Action
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State
import com.castlefrog.agl.agents.PlayoutPolicy
import com.castlefrog.agl.agents.StateEvaluator
import com.castlefrog.agl.agents.UniformRandomPlayoutPolicy
import com.castlefrog.agl.domains.chess.ChessSimulator
import com.castlefrog.agl.domains.draughts.DraughtsSimulator
import com.castlefrog.agl.domains.go.GoAction
import com.castlefrog.agl.domains.go.GoAreaEvaluator
import com.castlefrog.agl.domains.go.GoPlayoutPolicy
import com.castlefrog.agl.domains.go.GoRules
import com.castlefrog.agl.domains.go.GoSimulator
import com.castlefrog.agl.domains.go.GoState
import com.castlefrog.agl.domains.havannah.HavannahSimulator
import com.castlefrog.agl.domains.hex.HexPlayoutPolicy
import com.castlefrog.agl.domains.hex.HexSimulator
import java.util.Locale
import java.util.Random
import kotlin.system.exitProcess

/**
 * Plays random games from the initial state to the end in every domain and
 * reports games per second, moves per second, and where the time goes.
 *
 * Run with `./gradlew benchmark`. Arguments (`--args="..."`):
 * - `--games-scale X` multiplies every domain's game count (default 1.0)
 * - `--only a,b` runs only the named cases (see [cases])
 * - `--seed N` changes the fixed seed (default [DEFAULT_SEED])
 * - `--mode random|mcts` picks what a game is (default random). `random` plays from the
 *   legal set each step and times every [Simulator] call. `mcts` plays as an
 *   [com.castlefrog.agl.agents.MctsAgent] playout does, with only
 *   [PlayoutPolicy.selectActions] and `stateTransition` per step, once with
 *   [UniformRandomPlayoutPolicy] and once with the domain's own policy where it has one.
 *   There the legal-move work is part of the policy share.
 */
fun main(args: Array<String>) {
    var scale = 1.0
    var only: Set<String>? = null
    var seed = DEFAULT_SEED
    var mcts = false
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--games-scale" -> scale = args[++i].toDouble()
            "--only" -> only = args[++i].split(',').map { it.trim() }.toSet()
            "--seed" -> seed = args[++i].toLong()
            "--mode" -> mcts = when (args[++i]) {
                "random" -> false
                "mcts" -> true
                else -> {
                    System.err.println("Unknown mode: ${args[i]}")
                    exitProcess(2)
                }
            }
            else -> {
                System.err.println("Unknown argument: ${args[i]}")
                exitProcess(2)
            }
        }
        i += 1
    }
    val selected = cases().filter { only == null || it.name in only }
    if (selected.isEmpty()) {
        System.err.println("No cases match. Cases: ${cases().joinToString { it.name }}")
        exitProcess(2)
    }
    println("JVM ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, seed $seed, mode ${if (mcts) "mcts" else "random"}")
    println()
    val results = selected.flatMap { case ->
        val games = maxOf(1, (case.games * scale).toInt())
        val runs = if (mcts) case.runMcts(seed, games) else listOf(case.run(seed, games))
        runs.onEach { println(it.detail()) }
    }
    println()
    println(PlayoutResult.TABLE_HEADER)
    results.forEach { println(it.tableRow()) }
}

const val DEFAULT_SEED = 52L

/** The domains and sizes the benchmark covers, with a game count sized to take seconds, not minutes. */
fun cases(): List<BenchmarkCase<*, *>> = listOf(
    BenchmarkCase("hex13", HexSimulator(boardSize = 13), games = 2000, playoutPolicy = HexPlayoutPolicy),
    BenchmarkCase("hex19", HexSimulator(boardSize = 19), games = 500, playoutPolicy = HexPlayoutPolicy),
    BenchmarkCase("havannah8", HavannahSimulator(base = 8), games = 500),
    BenchmarkCase("havannah10", HavannahSimulator(base = 10), games = 200),
    BenchmarkCase(
        "go13", GoSimulator(boardSize = 13, komi = 6.5, rules = GoRules.CHINESE), games = 20,
        moveCap = GoPlayoutPolicy.moveCap(13), policy = ::goSelect,
        playoutPolicy = GoPlayoutPolicy(), evaluator = GoAreaEvaluator
    ),
    BenchmarkCase(
        "go19", GoSimulator(boardSize = 19, rules = GoRules.CHINESE), games = 5,
        moveCap = GoPlayoutPolicy.moveCap(19), policy = ::goSelect,
        playoutPolicy = GoPlayoutPolicy(), evaluator = GoAreaEvaluator
    ),
    BenchmarkCase("chess", ChessSimulator(), games = 1000),
    BenchmarkCase("draughts", DraughtsSimulator(), games = 2000)
)

/** Picks an action from the mover's legal set. The default is uniform. */
typealias LegalSetPolicy<S, A> = (state: S, legal: Set<A>, random: Random) -> A

/**
 * The Go stopping rule in doc/decisions.md, applied to the full legal set: a uniform pick
 * among placements that are not the mover's own eye, or a pass when none is left.
 */
fun goSelect(state: GoState, legal: Set<GoAction>, random: Random): GoAction {
    val stone = if (state.agentTurn == GoState.TURN_BLACK) GoState.LOCATION_BLACK else GoState.LOCATION_WHITE
    val candidates = legal.filter { !it.isPass && !GoPlayoutPolicy.isOwnEye(state, it.x, it.y, stone) }
    return if (candidates.isEmpty()) GoAction.pass() else candidates[random.nextInt(candidates.size)]
}

fun <A> uniform(legal: Set<A>, random: Random): A {
    var index = random.nextInt(legal.size)
    for (action in legal) {
        if (index == 0) return action
        index -= 1
    }
    throw IllegalStateException("unreachable")
}

class BenchmarkCase<S : State<S>, A : Action>(
    val name: String,
    private val simulator: Simulator<S, A>,
    val games: Int,
    private val moveCap: Int = Int.MAX_VALUE,
    private val policy: LegalSetPolicy<S, A> = { _, legal, random -> uniform(legal, random) },
    /** The domain's MCTS playout policy for `--mode mcts`, if it has one. */
    private val playoutPolicy: PlayoutPolicy? = null,
    private val evaluator: StateEvaluator? = null
) {
    /**
     * Plays [games] MCTS-style playouts with [UniformRandomPlayoutPolicy], then with
     * [playoutPolicy] if there is one, each after its own warm-up. Playouts stop at
     * [moveCap] if it is set.
     */
    fun runMcts(seed: Long, games: Int): List<PlayoutResult> {
        val policies = listOfNotNull(
            "uniform" to UniformRandomPlayoutPolicy,
            playoutPolicy?.let { "policy" to it }
        )
        return policies.map { (label, policy) ->
            val warmupRandom = kotlin.random.Random(seed xor WARMUP_SEED_MASK)
            repeat(maxOf(1, games / 5)) { mctsPlayOut(policy, warmupRandom, Timings()) }
            val timings = Timings()
            val random = kotlin.random.Random(seed)
            val start = System.nanoTime()
            repeat(games) { mctsPlayOut(policy, random, timings) }
            PlayoutResult("$name/$label", games, timings, System.nanoTime() - start)
        }
    }

    /** One playout as `MctsAgent` plays it: evaluator, cap, policy, transition. */
    private fun mctsPlayOut(policy: PlayoutPolicy, random: kotlin.random.Random, t: Timings) {
        val rewards = mctsRewards(policy, random, t)
        when {
            rewards[0] > rewards[1] -> t.firstWins += 1
            rewards[1] > rewards[0] -> t.secondWins += 1
            else -> t.draws += 1
        }
    }

    private fun mctsRewards(policy: PlayoutPolicy, random: kotlin.random.Random, t: Timings): DoubleArray {
        var state = simulator.initialState
        while (true) {
            val atCap = t.currentPlies >= moveCap
            var mark = System.nanoTime()
            val estimate = evaluator?.evaluate(state, simulator, t.currentPlies, atCap)
            var now = System.nanoTime()
            t.rewards += now - mark
            if (atCap) t.capped += 1
            if (estimate != null) return estimate.also { t.endPlayout() }
            if (atCap) return DoubleArray(simulator.numberOfPlayers()).also { t.endPlayout() }

            mark = now
            val actions = policy.selectActions(state, simulator, random)
            now = System.nanoTime()
            t.policy += now - mark
            if (actions == null) {
                val rewards = simulator.calculateRewards(state).map { it.toDouble() }.toDoubleArray()
                t.rewards += System.nanoTime() - now
                t.endPlayout()
                return rewards
            }

            mark = now
            state = simulator.stateTransition(state, actions)
            t.transition += System.nanoTime() - mark
            t.currentPlies += 1
        }
    }

    /** Warms the JIT on a different seed, then times [games] games from [seed]. */
    fun run(seed: Long, games: Int): PlayoutResult {
        val warmup = Timings()
        val warmupRandom = Random(seed xor WARMUP_SEED_MASK)
        repeat(maxOf(1, games / 5)) { playOut(warmupRandom, warmup) }
        val timings = Timings()
        val random = Random(seed)
        val start = System.nanoTime()
        repeat(games) { playOut(random, timings) }
        return PlayoutResult(name, games, timings, System.nanoTime() - start)
    }

    private fun playOut(random: Random, t: Timings) {
        val players = simulator.numberOfPlayers()
        var state = simulator.initialState
        var plies = 0
        while (true) {
            var mark = System.nanoTime()
            val terminal = simulator.isTerminalState(state)
            var now = System.nanoTime()
            t.terminal += now - mark
            if (terminal) break
            if (plies >= moveCap) {
                t.capped += 1
                break
            }

            mark = now
            val legal = simulator.calculateLegalActions(state)
            now = System.nanoTime()
            t.legal += now - mark

            mark = now
            val mover = legal.indexOfFirst { it.isNotEmpty() }
            val actions = ArrayList<A?>(players)
            for (p in 0 until players) {
                actions.add(if (p == mover) policy(state, legal[p], random) else null)
            }
            now = System.nanoTime()
            t.policy += now - mark

            mark = now
            state = simulator.stateTransition(state, actions)
            t.transition += System.nanoTime() - mark
            plies += 1
        }
        val mark = System.nanoTime()
        val rewards = simulator.calculateRewards(state)
        t.rewards += System.nanoTime() - mark
        t.moves += plies
        when {
            rewards[0] > rewards[1] -> t.firstWins += 1
            rewards[1] > rewards[0] -> t.secondWins += 1
            else -> t.draws += 1
        }
    }

    private companion object {
        const val WARMUP_SEED_MASK = 0x5DEECE66DL
    }
}

class Timings {
    var legal = 0L
    var transition = 0L
    var terminal = 0L
    var rewards = 0L
    var policy = 0L
    var moves = 0L
    var capped = 0
    /** Plies in the MCTS-style playout under way; added to [moves] when it ends. */
    var currentPlies = 0

    fun endPlayout() {
        moves += currentPlies
        currentPlies = 0
    }
    var firstWins = 0
    var secondWins = 0
    var draws = 0
}

class PlayoutResult(val name: String, val games: Int, val t: Timings, val wallNanos: Long) {
    private val seconds = wallNanos / 1e9
    private val steps = listOf(
        "calculateLegalActions" to t.legal,
        "stateTransition" to t.transition,
        "isTerminalState" to t.terminal,
        "calculateRewards" to t.rewards,
        "policy" to t.policy
    )
    private val measured = steps.sumOf { it.second }.toDouble()
    private fun share(nanos: Long) = if (measured == 0.0) 0.0 else 100.0 * nanos / measured
    private val slowest = steps.maxBy { it.second }.first

    fun detail(): String = buildString {
        append(
            String.format(
                Locale.ROOT, "%s: %d games, %d moves (%.1f per game) in %.2f s; %.2f games/s, %.0f moves/s",
                name, games, t.moves, t.moves.toDouble() / games, seconds, games / seconds, t.moves / seconds
            )
        )
        append(String.format(Locale.ROOT, "%n  results: %d first, %d second, %d drawn", t.firstWins, t.secondWins, t.draws))
        if (t.capped > 0) append(", ${t.capped} hit the move cap")
        for ((step, nanos) in steps) {
            append(String.format(Locale.ROOT, "%n  %-22s %6.1f%%  %8.2f us/move", step, share(nanos), nanos / 1e3 / maxOf(1, t.moves)))
        }
        append(String.format(Locale.ROOT, "%n  most expensive: %s", slowest))
    }

    fun tableRow(): String = String.format(
        Locale.ROOT, "| %s | %d | %.1f | %.2f | %.0f | %.1f | %.1f | %.1f | %.1f | %.1f | %s |",
        name, games, t.moves.toDouble() / games, games / seconds, t.moves / seconds,
        share(t.legal), share(t.transition), share(t.terminal), share(t.rewards), share(t.policy), slowest
    )

    companion object {
        const val TABLE_HEADER =
            "| case | games | moves/game | games/s | moves/s | legal % | transition % | terminal % | rewards % | policy % | most expensive |\n" +
                "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"
    }
}
