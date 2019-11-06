/*
 * Copyright (C) 2010-2019, Danilo Pianini and contributors listed in the main project's alchemist/build.gradle file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */
package it.unibo.alchemist.model

import java.util.Objects

import com.google.common.collect.Lists
import it.unibo.alchemist.model.implementations.actions.SendScafiMessage
import it.unibo.alchemist.model.implementations.conditions.ScafiComputationalRoundComplete
import it.unibo.alchemist.model.implementations.nodes.ScafiNode
import it.unibo.alchemist.model.implementations.actions.RunScafiProgram
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule
import it.unibo.alchemist.model.implementations.reactions.{ChemicalReaction, Event}
import it.unibo.alchemist.model.implementations.timedistributions.{DiracComb, ExponentialTime}
import it.unibo.alchemist.model.implementations.times.DoubleTime
import it.unibo.alchemist.model.interfaces._
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist
import it.unibo.alchemist.scala.ScalaInterpreter
import org.apache.commons.math3.random.RandomGenerator

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

sealed class ScafiIncarnation[T, P <: Position[P]] extends Incarnation[T, P]{

  private[this] def notNull[T](t: T, name: String = "Object"): T = Objects.requireNonNull(t, s"$name must not be null")

  private[this] def toDouble(v: Any): Double = v match {
    case x: Double => x
    case x: Int => x
    case x: String => java.lang.Double.parseDouble(x)
    case x: Boolean => if (x) 1 else 0
    case x: Long => x
    case x: Float => x
    case x: Byte => x
    case x: Short => x
    case _ => Double.NaN
  }

  override def createAction(
      rand: RandomGenerator,
      env: Environment[T, P],
      node: Node[T],
      time: TimeDistribution[T],
      reaction: Reaction[T],
      param: String) = {
    if (!node.isInstanceOf[ScafiNode[T,P]]) {
      throw new IllegalStateException(getClass.getSimpleName + " cannot get cloned on a node of type " + node.getClass.getSimpleName)
    }
    val scafiNode = node.asInstanceOf[ScafiNode[T,P]]

    if(param=="send") {
      import scala.reflect.runtime.universe.typeTag
      implicit val sendScafiMsgTypeTag = typeTag[SendScafiMessage[_,_]]
      val alreadyDone = ScafiIncarnationUtils.allActions[T,P,SendScafiMessage[T,P]](node, classOf[SendScafiMessage[T,P]]).map(_.program)
        // ScafiIncarnationUtils.allScafiProgramsFor[T,P](node).filter(_.isComputationalCycleComplete)
      val spList = ScafiIncarnationUtils.allScafiProgramsFor[T,P](node) -- alreadyDone

      if (spList.isEmpty) {
        throw new IllegalStateException("There is no program requiring a " + classOf[SendScafiMessage[T,P]].getSimpleName + " action")
      }
      if (spList.size > 1) {
        throw new IllegalStateException("There are too many programs requiring a " + classOf[SendScafiMessage[T,P]].getName + " action: " + spList)
      }
       new SendScafiMessage[T,P](env, scafiNode, reaction, spList.head)
    } else {
      new RunScafiProgram[T, P](
        notNull(env, "environment"),
        notNull(node, "node"),
        notNull(reaction, "reaction"),
        notNull(rand, "random generator"),
        notNull(param, "action parameter"))
    }
  }

  /**
   * NOTE: String v may be prefixed by "_" symbol to avoid caching the value resulting from its interpretation
   */
  override def createConcentration(v: String) = {
    /*
     * TODO: support double-try parse in case of strings (to avoid "\"string\"" in the YAML file)
     */
    val doCacheValue = !v.startsWith("_");
    CachedInterpreter[AnyRef](if(doCacheValue) v else v.tail, doCacheValue).asInstanceOf[T]
  }

  override def createCondition(rand: RandomGenerator, env: Environment[T, P] , node: Node[T], time: TimeDistribution[T], reaction: Reaction[T], param: String): Condition[T] = {
    if(!node.isInstanceOf[ScafiNode[T,P]])
      throw new IllegalArgumentException(s"The node must be an instance of ${classOf[ScafiNode[_,_]]}"
      + s", but it is an ${node.getClass} instead.")

    val scafiNode = node.asInstanceOf[ScafiNode[_,P]]
    val ideps = ScafiIncarnationUtils.inboundDependencies(node, classOf[ScafiComputationalRoundComplete[T]])
    val alreadyDone = ideps.collect { case x: RunScafiProgram[T,P] => x }
    val spList = ScafiIncarnationUtils.allScafiProgramsFor(node) -- alreadyDone
    if (spList.isEmpty) {
      throw new IllegalStateException("There is no program requiring a " +
        classOf[ScafiComputationalRoundComplete[_]].getSimpleName + " condition")
    }
    if (spList.size > 1) {
      throw new IllegalStateException("There are too many programs requiring a " +
        classOf[ScafiComputationalRoundComplete[_]].getName + " condition: " + spList)
    }
    new ScafiComputationalRoundComplete(node, spList.head)
  }

  override def createMolecule(s: String): SimpleMolecule = {
    new SimpleMolecule(notNull(s, "simple molecule name"))
  }

  override def createNode(rand: RandomGenerator, env: Environment[T, P], param: String) = {
    new ScafiNode(env)
  }

  override def createReaction(rand: RandomGenerator, env: Environment[T, P], node: Node[T], time: TimeDistribution[T], param: String): Reaction[T] = {
    import scala.collection.JavaConversions._

    val isSend = "send".equalsIgnoreCase(param)
    val result: Reaction[T] =
      if (isSend)
        new ChemicalReaction[T](Objects.requireNonNull[Node[T]](node), Objects.requireNonNull[TimeDistribution[T]](time))
      else
        new Event[T](node, time)
    if (param != null)
      result.setActions(ListBuffer(createAction(rand, env, node, time, result, param)))
    if (isSend)
      result.setConditions(ListBuffer(createCondition(rand, env, node, time, result, null)))

    result
  }

  override def createTimeDistribution(rand: RandomGenerator, env: Environment[T, P], node: Node[T], param: String): TimeDistribution[T] = {
    if (param == null) return new ExponentialTime[T](Double.PositiveInfinity, rand)
    // Objects.requireNonNull(param, "Frequency parameter to createTimeDistribution must not be null")
    val frequency = toDouble(param)
    if (frequency.isNaN()) {
      throw new IllegalArgumentException(param + " is not a valid number, the time distribution could not be created.")
    }
    new DiracComb(new DoubleTime(rand.nextDouble() / frequency), frequency);
  }

  override def getProperty(node: Node[T], molecule: Molecule, propertyName: String) = {
    val target = node.getConcentration(molecule)
    if (propertyName == null || propertyName.trim.isEmpty) {
      toDouble(target)
    } else {
      toDouble(ScalaInterpreter("val value = " + target + ";" + propertyName))
    }
  }
}

object ScafiIncarnationUtils {
  import it.unibo.alchemist.model.interfaces.Action

  import collection.JavaConverters._

  def possibleRefs(node: Node[_]): Iterable[RunScafiProgram[_,_]] = {
    for(reaction <- node.getReactions.asScala;
        action <- reaction.getActions.asScala;
        if action.isInstanceOf[RunScafiProgram[_,_]])
      yield action.asInstanceOf[RunScafiProgram[_,_]]
  }

  def allActions[T,P<:Position[P],C](node: Node[T], klass: Class[C]): mutable.Buffer[C] = {
    for(
      reaction: Reaction[T] <- node.getReactions().asScala;
      action: Action[T] <- reaction.getActions().asScala;
      if klass.isInstance(action)
      //&& action.asInstanceOf[RunScafiProgram[P]].program.getClass == programClass
    )
      yield action.asInstanceOf[C]
  }

  def allScafiProgramsFor[T,P<:Position[P]](node: Node[T]) = {
    allActions[T,P,RunScafiProgram[T,P]](node, classOf[RunScafiProgram[T,P]])
  }

  def allConditionsFor[T](node: Node[T], conditionClass: Class[_]): mutable.Buffer[Condition[T]] = {
    for(reaction <- node.getReactions.asScala;
        condition <- reaction.getConditions.asScala;
        if conditionClass.isInstance(condition))
      yield condition
  }

  def inboundDependencies[T](node: Node[T], conditionClass: Class[_]): mutable.Buffer[Dependency] = {
    for(c <- allConditionsFor(node, conditionClass);
      dep <- c.getInboundDependencies.iterator().asScala)
      yield dep
  }

  def allCompletedScafiProgram[T](node: Node[T], conditionClass: Class[_]): mutable.Buffer[Dependency] = {
    inboundDependencies(node, classOf[ScafiComputationalRoundComplete[T]])
  }
}

object CachedInterpreter {

  import com.google.common.cache.CacheBuilder
  import scalacache.{ScalaCache, _}
  import scalacache.guava.GuavaCache
  private val underlyingGuavaCache = CacheBuilder.newBuilder()
    .maximumSize(1000L)
    .build[String, Object]
  private implicit val scalaCache = ScalaCache(GuavaCache(underlyingGuavaCache))

  /**
   * Evaluates str using Scala reflection.
   * When doCacheValue is true, the result of the evaluation is cached.
   * When doCacheValue is false, only the result of parsing is cached.
   */
  def apply[A <: AnyRef](str: String, doCacheValue: Boolean = true): A =
    (if(doCacheValue) {
      sync.caching("//VAL"+str)(ScalaInterpreter[A](str))
    } else {
      ScalaInterpreter(str)
    }).asInstanceOf[A]
}
