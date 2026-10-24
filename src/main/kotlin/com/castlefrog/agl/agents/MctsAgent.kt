package com.castlefrog.agl.agents

import com.castlefrog.agl.Action
import com.castlefrog.agl.Agent
import com.castlefrog.agl.Simulator
import com.castlefrog.agl.State
import com.castlefrog.agl.util.getPlayerActions
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * A Monte Carlo Tree Search agent (UCT) that plays any domain through the [Simulator]
 * interface alone.
 *
 * Each iteration walks down the tree choosing moves by UCB1, adds one new node, plays
 * a game out from it with [MctsConfig.playoutPolicy], and backs the playout's reward
 * vector up the path. At each node, every player with legal actions chooses, and each
 * one's statistics hold their own reward, so the search works for any number of players
 * and for simultaneous moves (decoupled UCT). In a turn-based game this is plain UCT.
 * See [MctsConfig] for the budget, playout cap and strength settings, and [MctsLevel]
 * for presets.
 *
 * Threading: a call may run on any thread. The search tree is local to each call; the
 * only state kept between calls is [random]. Do not run two searches on one instance at
 * once with a seeded [Random], which is not thread-safe; give each thread its own agent.
 * A search stops early when its thread is interrupted, or when the `shouldStop` callback
 * passed to [search] returns true, and still returns its best move so far.
 *
 * @param config search settings
 * @param random source of randomness for tie-breaking, playouts and the final move;
 *        a seeded one makes the agent reproducible
 */
class MctsAgent(
    private val config: MctsConfig = MctsConfig(),
    private val random: Random = Random
) : Agent {

    override fun <S : State<S>, A : Action> selectAction(
        playerId: Int,
        state: S,
        simulator: Simulator<S, A>
    ): A? = search(playerId, state, simulator).action

    /**
     * Searches from [state] and picks an action for [playerId].
     *
     * @param shouldStop checked before every iteration, from the searching thread; return
     *        true to stop early, for example when another thread sets a flag
     * @return the chosen action, or null if [playerId] has no legal action, with the
     *         search statistics
     */
    fun <S : State<S>, A : Action> search(
        playerId: Int,
        state: S,
        simulator: Simulator<S, A>,
        shouldStop: () -> Boolean = { false }
    ): MctsResult<A> {
        val legalActions = simulator.calculateLegalActions(state)
        val playerActions = legalActions.getPlayerActions(playerId)
            ?: return MctsResult(null, 0, emptyMap())
        if (playerActions.size == 1) {
            return MctsResult(playerActions.first(), 0, emptyMap())
        }
        val search = Search(simulator)
        val root = Node<S, A>(state).apply { expand(legalActions, random) }
        val deadline = config.timeLimit?.let { TimeSource.Monotonic.markNow() + it }
        val maxIterations = config.iterations
        var iterations = 0
        while ((maxIterations == null || iterations < maxIterations) &&
            (deadline == null || !deadline.hasPassedNow()) &&
            !Thread.currentThread().isInterrupted &&
            !shouldStop()
        ) {
            search.iterate(root)
            iterations++
        }
        val mover = root.movers.indexOf(playerId)
        val actions = root.actions[mover]
        val visits = root.visits[mover]
        val index = chooseFinal(visits, root.totals[mover])
        return MctsResult(
            action = actions[index],
            iterations = iterations,
            visits = actions.indices.associate { actions[it] to visits[it] }
        )
    }

    private fun chooseFinal(visits: IntArray, totals: DoubleArray): Int {
        val maxVisits = visits.max()
        if (maxVisits == 0) {
            return 0 // actions are shuffled, so this is a random legal action
        }
        if (config.temperature == 0.0) {
            var best = 0
            for (i in 1 until visits.size) {
                if (visits[i] > visits[best] ||
                    (visits[i] == visits[best] && totals[i] > totals[best])
                ) {
                    best = i
                }
            }
            return best
        }
        val exponent = 1.0 / config.temperature
        val weights = DoubleArray(visits.size) { (visits[it].toDouble() / maxVisits).pow(exponent) }
        var pick = random.nextDouble() * weights.sum()
        for (i in weights.indices) {
            pick -= weights[i]
            if (pick < 0.0 && weights[i] > 0.0) {
                return i
            }
        }
        return visits.indexOf(maxVisits)
    }

    override fun toString(): String = "${MctsAgent::class.java.simpleName}($config)"

    /**
     * One node of the tree: a state, and per-player action statistics for each player to
     * move. Legal actions are computed when the search first chooses from the node, not
     * when it is added, so a new leaf costs only its playout.
     */
    private class Node<S : State<S>, A : Action>(val state: S) {
        var isExpanded = false
            private set

        /** Players with legal actions here. Empty when terminal. */
        var movers: IntArray = IntArray(0)
            private set

        /** Each mover's legal actions, shuffled once so unvisited ones are tried in random order. */
        var actions: Array<List<A>> = emptyArray()
            private set
        var visits: Array<IntArray> = emptyArray()
            private set
        var totals: Array<DoubleArray> = emptyArray()
            private set
        var tried = IntArray(0)
            private set
        private var playerCount = 0
        var count = 0
        val children = HashMap<List<A?>, Node<S, A>>()

        val isTerminal: Boolean get() = movers.isEmpty()

        fun expand(legalActions: List<Set<A>>, random: Random) {
            playerCount = legalActions.size
            movers = legalActions.indices.filter { legalActions[it].isNotEmpty() }.toIntArray()
            actions = Array(movers.size) { legalActions[movers[it]].shuffled(random) }
            visits = Array(movers.size) { IntArray(actions[it].size) }
            totals = Array(movers.size) { DoubleArray(actions[it].size) }
            tried = IntArray(movers.size)
            isExpanded = true
        }

        fun jointActions(choice: IntArray): List<A?> {
            val joint = arrayOfNulls<Any>(playerCount)
            for (m in movers.indices) {
                joint[movers[m]] = actions[m][choice[m]]
            }
            @Suppress("UNCHECKED_CAST")
            return joint.toList() as List<A?>
        }
    }

    /** The per-call search: everything here is local to one [search] call. */
    private inner class Search<S : State<S>, A : Action>(private val simulator: Simulator<S, A>) {
        private val playerCount = simulator.numberOfPlayers()

        fun iterate(root: Node<S, A>) {
            val path = ArrayList<Pair<Node<S, A>, IntArray>>()
            var node = root
            val rewards: DoubleArray
            while (true) {
                if (!node.isExpanded) {
                    node.expand(simulator.calculateLegalActions(node.state), random)
                }
                if (node.isTerminal) {
                    rewards = terminalRewards(node.state)
                    break
                }
                val choice = select(node)
                path.add(node to choice)
                val joint = node.jointActions(choice)
                val child = node.children[joint]
                if (child == null) {
                    val nextState = simulator.stateTransition(node.state, joint)
                    node.children[joint] = Node(nextState)
                    rewards = playout(nextState)
                    break
                }
                node = child
            }
            for ((visited, choice) in path) {
                visited.count++
                for (m in visited.movers.indices) {
                    visited.visits[m][choice[m]]++
                    visited.totals[m][choice[m]] += rewards.getOrElse(visited.movers[m]) { 0.0 }
                }
            }
        }

        /** UCB1 for each mover independently; an untried action first. */
        private fun select(node: Node<S, A>): IntArray = IntArray(node.movers.size) { m ->
            if (node.tried[m] < node.actions[m].size) {
                node.tried[m]++
            } else {
                val visits = node.visits[m]
                val totals = node.totals[m]
                val logCount = ln(node.count.toDouble())
                var best = 0
                var bestValue = Double.NEGATIVE_INFINITY
                for (i in visits.indices) {
                    val value = totals[i] / visits[i] + config.explorationConstant * sqrt(logCount / visits[i])
                    if (value > bestValue) {
                        best = i
                        bestValue = value
                    }
                }
                best
            }
        }

        /**
         * Plays from [start] with the playout policy, which also detects the end, so no
         * step computes legal actions unless the policy does.
         */
        private fun playout(start: S): DoubleArray {
            var state = start
            var length = 0
            while (true) {
                val atCap = length >= config.maxPlayoutLength
                config.evaluator?.evaluate(state, simulator, length, atCap)?.let { return it }
                if (atCap) {
                    // A finished game still gets its real rewards at the cap.
                    return if (simulator.isTerminalState(state)) terminalRewards(state) else DoubleArray(playerCount)
                }
                val actions = config.playoutPolicy.selectActions(state, simulator, random)
                    ?: return terminalRewards(state)
                state = simulator.stateTransition(state, actions)
                length++
            }
        }

        private fun terminalRewards(state: S): DoubleArray {
            val rewards = simulator.calculateRewards(state)
            return DoubleArray(playerCount) { rewards.getOrElse(it) { 0 }.toDouble() }
        }
    }
}

/**
 * The outcome of one [MctsAgent.search].
 *
 * @property action the chosen action, or null if the player had no legal action
 * @property iterations the iterations run; 0 when the player had at most one legal action
 * @property visits how often the search tried each of the player's actions at the root
 */
data class MctsResult<A : Action>(
    val action: A?,
    val iterations: Int,
    val visits: Map<A, Int>
)
