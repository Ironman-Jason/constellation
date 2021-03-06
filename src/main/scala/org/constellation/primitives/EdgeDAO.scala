package org.constellation.primitives

import java.util.concurrent.Executors

import akka.actor.ActorRef
import org.constellation.primitives.Schema._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

trait EdgeDAO {

  var genesisObservation: Option[GenesisObservation] = None
  val maxWidth = 50
  val minCheckpointFormationThreshold = 3
  val minTXSignatureThreshold = 3
  val minCBSignatureThreshold = 3
  val maxUniqueTXSize = 500
  val maxNumSignaturesPerTX = 20

  val transactionMemPool : TrieMap[String, Transaction] = TrieMap()
  val checkpointMemPool : TrieMap[String, CheckpointBlock] = TrieMap()

  @volatile var transactionMemPoolThresholdMet: Set[String] = Set()

  // Map from checkpoint hash to number of times used as a tip (number of children)
  val checkpointMemPoolThresholdMet: TrieMap[String, Int] = TrieMap()
  val resolveNotifierCallbacks: TrieMap[String, Seq[String]] = TrieMap()

  def canCreateCheckpoint: Boolean = {
    transactionMemPoolThresholdMet.size >= minCheckpointFormationThreshold && checkpointMemPoolThresholdMet.size >= 2
  }

  def reuseTips: Boolean = checkpointMemPoolThresholdMet.size < maxWidth

}
