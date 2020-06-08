package it.unibo.alchemist.model.implementations.actions.steeringstrategies

import it.unibo.alchemist.model.implementations.positions.Euclidean2DPosition
import it.unibo.alchemist.model.interfaces.GroupSteeringAction
import it.unibo.alchemist.model.interfaces.Pedestrian
import it.unibo.alchemist.model.interfaces.SteeringAction
import it.unibo.alchemist.model.interfaces.SteeringActionWithTarget
import it.unibo.alchemist.model.interfaces.SteeringStrategy
import it.unibo.alchemist.model.interfaces.environments.Euclidean2DEnvironment

/**
 * Steering logic where each steering action is associated to a weight
 * and the final computed position is their weighted sum.
 *
 * @param environment
 *          the environment in which the pedestrian moves.
 * @param pedestrian
 *          the owner of the steering action this strategy belongs to.
 * @param weight
 *          lambda to associate each steering action a numerical value representing
 *          its relevance in the position computation.
 */
open class Weighted<T>(
    private val environment: Euclidean2DEnvironment<T>,
    private val pedestrian: Pedestrian<T>,
    private val weight: SteeringAction<T, Euclidean2DPosition>.() -> Double
) : SteeringStrategy<T, Euclidean2DPosition> {

    override fun computeNextPosition(actions: List<SteeringAction<T, Euclidean2DPosition>>): Euclidean2DPosition =
        actions.partition { it is GroupSteeringAction<T, Euclidean2DPosition> }.let { (groupActions, steerActions) ->
            groupActions.calculatePosition() + steerActions.calculatePosition()
        }

    /**
     * The overall target is computed as follows: only [SteeringActionWithTarget] are considered,
     * and we pick the action whose target is closest to the current position of the pedestrian,
     * which will be considered the overall target.
     */
    override fun computeTarget(actions: List<SteeringAction<T, Euclidean2DPosition>>): Euclidean2DPosition =
        with(environment.getPosition(pedestrian) ?: environment.origin) {
            actions
                .filterIsInstance<SteeringActionWithTarget<T, out Euclidean2DPosition>>()
                .map { it.target() }
                .minBy { it.distanceTo(this) }
                ?: this
        }

    private fun List<SteeringAction<T, Euclidean2DPosition>>.calculatePosition(): Euclidean2DPosition =
        if (size > 1) {
            map { it.nextPosition() to it.weight() }.run {
                val totalWeight = map { it.second }.sum()
                map { it.first * (it.second / totalWeight) }.reduce { acc, pos -> acc + pos }
            }
        } else firstOrNull()?.nextPosition() ?: environment.origin
}
