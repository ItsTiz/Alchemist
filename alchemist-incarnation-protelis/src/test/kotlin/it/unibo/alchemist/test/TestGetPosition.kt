/*
 * Copyright (C) 2010-2022, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */
package it.unibo.alchemist.test

import it.unibo.alchemist.boundary.interfaces.OutputMonitor
import it.unibo.alchemist.core.implementations.Engine
import it.unibo.alchemist.core.interfaces.Simulation
import it.unibo.alchemist.model.ProtelisIncarnation
import it.unibo.alchemist.model.implementations.actions.RunProtelisProgram
import it.unibo.alchemist.model.implementations.environments.Continuous2DEnvironment
import it.unibo.alchemist.model.implementations.linkingrules.NoLinks
import it.unibo.alchemist.model.implementations.positions.Euclidean2DPosition
import it.unibo.alchemist.model.implementations.reactions.Event
import it.unibo.alchemist.model.implementations.timedistributions.ExponentialTime
import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Actionable
import it.unibo.alchemist.model.interfaces.Node.Companion.asProperty
import it.unibo.alchemist.model.interfaces.Time
import org.apache.commons.math3.random.MersenneTwister
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.protelis.lang.datatype.DatatypeFactory
import java.util.Optional

class TestGetPosition {
    private val environment: Environment<Any, Euclidean2DPosition> = Continuous2DEnvironment(ProtelisIncarnation())
    private val randomGenerator = MersenneTwister(0)
    private val node = ProtelisIncarnation<Euclidean2DPosition>().createNode(randomGenerator, environment, null)
    private val reaction = Event(node, ExponentialTime(1.0, randomGenerator))
    private val action = RunProtelisProgram(
        randomGenerator,
        environment,
        node.asProperty(),
        reaction,
        "self.getCoordinates()"
    )

    @BeforeEach
    fun setUp() {
        environment.linkingRule = NoLinks()
        reaction.actions = listOf(action)
        node.addReaction(reaction)
        environment.addNode(node, environment.makePosition(1, 1))
    }

    @Test
    fun testGetPosition() {
        val sim: Simulation<Any, Euclidean2DPosition> = Engine(environment, 100)
        sim.addOutputMonitor(
            object : OutputMonitor<Any, Euclidean2DPosition> {
                override fun finished(environment: Environment<Any, Euclidean2DPosition>, time: Time, step: Long) = Unit
                override fun initialized(environment: Environment<Any, Euclidean2DPosition>) = Unit
                override fun stepDone(
                    environment: Environment<Any, Euclidean2DPosition>,
                    reaction: Actionable<Any>?,
                    time: Time,
                    step: Long
                ) {
                    if (step > 0) {
                        Assertions.assertEquals(
                            DatatypeFactory.createTuple(1.0, 1.0),
                            node.getConcentration(action.asMolecule())
                        )
                    }
                }
            }
        )
        sim.play()
        sim.run()
        Assertions.assertEquals(Optional.empty<Any>(), sim.error)
    }
}
