/*
 * Copyright (C) 2010-2022, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

package it.unibo.alchemist.loader.m2m.syntax

import kotlin.reflect.KProperty

internal object DocumentRoot : SyntaxElement {
    object JavaType : SyntaxElement {
        val type by OwnName()
        val parameters by OwnName()
        override val validDescriptors = setOf(
            validDescriptor {
                mandatory(type)
                optional(parameters)
            }
        )
    }
    object DependentVariable : SyntaxElement {
        val language by OwnName()
        val formula by OwnName()
        val timeout by OwnName()
        override val validDescriptors = JavaType.validDescriptors + setOf(
            validDescriptor {
                mandatory(formula)
                optional(language)
                optional(timeout)
            }
        )
    }
    object Deployment : SyntaxElement {
        val contents by OwnName()
        val nodes by OwnName()
        val programs by OwnName()
        override val validDescriptors = setOf(
            validDescriptor {
                mandatory(JavaType.type)
                optional(JavaType.parameters, contents, nodes, programs)
                forbidden(Contents.shapes)
            }
        )
        object Contents : SyntaxElement {
            val molecule by OwnName()
            val concentration by OwnName()
            const val shapes = "in"
            override val validDescriptors = setOf(
                validDescriptor {
                    mandatory(molecule, concentration)
                    optional(shapes)
                }
            )
        }
        object Program : SyntaxElement {
            val program by OwnName()
            val actions by OwnName()
            val conditions by OwnName()
            const val timeDistribution = "time-distribution"
            override val validDescriptors = setOf(
                validDescriptor {
                    mandatory(JavaType.type)
                    optional(JavaType.parameters, conditions, timeDistribution, actions)
                },
                validDescriptor {
                    mandatory(program)
                    optional(timeDistribution)
                }
            )
        }
    }

    object Export : SyntaxElement {
        val data by OwnName()
        override val validDescriptors = setOf(
            validDescriptor { mandatory(JavaType.type, JavaType.parameters, data) },
        )
        object Data : SyntaxElement {
            val time by OwnName()
            val molecule by OwnName()
            val property by OwnName()
            val aggregators by OwnName()
            const val valueFilter = "value-filter"
            override val validDescriptors = JavaType.validDescriptors + setOf(
                validDescriptor { mandatory(time) },
                validDescriptor {
                    mandatory(molecule)
                    optional(property, aggregators, valueFilter)
                }
            )
        }
    }

    object Layer : SyntaxElement {
        val molecule by OwnName()
        override val validDescriptors = setOf(
            validDescriptor {
                mandatory(JavaType.type, molecule)
                optional(JavaType.parameters)
            }
        )
    }
    object Seeds : SyntaxElement {
        val scenario by OwnName()
        val simulation by OwnName()
        override val validDescriptors = setOf(
            validDescriptor { optional(simulation, scenario) }
        )
    }
    object Variable : SyntaxElement {
        val min by OwnName()
        val max by OwnName()
        val default by OwnName()
        val step by OwnName()
        override val validDescriptors = JavaType.validDescriptors + setOf(
            validDescriptor { mandatory(min, max, default, step) }
        )
    }
    val deployments by OwnName()
    val environment by OwnName()
    val export by OwnName()
    val incarnation by OwnName()
    val layers by OwnName()
    const val linkingRule = "network-model"
    const val remoteDependencies = "remote-dependencies"
    val seeds by OwnName()
    val terminate by OwnName()
    val variables by OwnName()
    override val validDescriptors = setOf(
        validDescriptor {
            mandatory(incarnation)
            optional(*validKeys.toTypedArray())
        }
    )
}

internal class OwnName {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String = property.name.lowercase()
}

private fun validDescriptor(configuration: DescriptorBuilder.() -> Unit): SyntaxElement.ValidDescriptor =
    DescriptorBuilder().apply(configuration).build()