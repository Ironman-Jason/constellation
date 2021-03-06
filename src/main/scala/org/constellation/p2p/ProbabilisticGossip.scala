package org.constellation.p2p

import java.net.InetSocketAddress

import akka.http.scaladsl.model.StatusCodes
import org.constellation.Data
import org.constellation.primitives.Schema._
import org.constellation.util.{APIClient, ProductHash}
import constellation._
import org.constellation.LevelDB.{DBDelete, DBPut}

import scala.concurrent.Future
import scala.util.{Failure, Random, Success, Try}

// TODO: deprecated, temporary for reference only
trait ProbabilisticGossip extends PeerAuth {

  val data: Data

  import data._

  /*
  def dataRequest(): Unit = {

    // Request missing bundle data
    if (syncPendingBundleHashes.nonEmpty) {
      broadcastUDP(BatchBundleHashRequest(syncPendingBundleHashes))
    }

    // Request missing transaction data
    if (syncPendingTXHashes.nonEmpty) {
      // println("Requesting data sync pending of " + syncPendingTXHashes)
      broadcast(BatchTXHashRequest(syncPendingTXHashes))


      if (syncPendingTXHashes.size > 1500) {
        val toRemove = txSyncRequestTime.toSeq.sortBy(_._2).zipWithIndex.filter{_._2 > 50}.map{_._1._1}.toSet
        syncPendingTXHashes --= toRemove
      }

      // ask peers for this transaction data
      broadcastUDP(BatchTXHashRequest(syncPendingTXHashes))
    }
  }

  def getParentHashEmitter(stackDepthFilter: Int = minGenesisDistrSize - 1): ParentBundleHash = {
    val mb = maxBundle.get
    def pbHash = ParentBundleHash(mb.hash)
    val pbh = if (totalNumValidatedTX == 1) pbHash
    else if (mb.maxStackDepth < stackDepthFilter) mb.extractParentBundleHash
    else pbHash
    pbh
  }

  def poolEmit(): Unit = {
    val mb = maxBundle
    val pbh = getParentHashEmitter()

    val maybeData = lookupBundle(pbh.pbHash)
    val ids = maybeData.get.bundle.extractIds
    val lastPBWasSelf = maybeData.exists(_.bundle.bundleData.id == id)
    val selfIsFacilitator = (BigInt(pbh.pbHash, 16) % ids.size).toInt == 0
    val doEmit = !lastPBWasSelf && selfIsFacilitator

    if (!lastPBWasSelf || totalNumValidatedTX == 1) {

      // Emit an origin bundle. This needs to be managed by prob facil check on hash of previous + ids
      val memPoolEmit = Random.nextInt() < 0.04
      val filteredPool = memPool.diff(txInMaxBundleNotInValidation).filterNot(last10000ValidTXHash.contains)

      val memPoolConstantOffset = if (totalNumValidatedTX == 1) minGenesisDistrSize + 1 else 0
      val memPoolSelSize = Random.nextInt(4) + 1
      val memPoolSelection = Random.shuffle(filteredPool.toSeq)
        .slice(0, memPoolSelSize + memPoolConstantOffset)

      if (memPoolEmit && filteredPool.nonEmpty) {

        val b = Bundle(
          BundleData(
            memPoolSelection.map {
              TransactionHash
            } :+ pbh
          ).signed()
        )

        val meta = mb.get.meta.get

        updateBundleFrom(meta, Sheaf(b))

        numMempoolEmits += 1

        broadcastUDP(b)

      }
    }
  }

  def acceptInitialDistribution(): Unit = {
    // Force accept initial distribution
    if (totalNumValidatedTX == 1 && maxBundle.get.extractTX.size >= (minGenesisDistrSize - 1)) {

      maxBundle.get.extractTX.foreach{tx =>
        acceptTransaction(tx)
        tx.txData.data.updateLedger(memPoolLedger)
      }

      totalNumValidBundles += 1
      last100ValidBundleMetaData :+= maxBundleMetaData.get
    }
  }

  def attemptResolvePeerBundles(): Unit = peerSync.foreach{
    case (id, hb) =>
      if (hb.maxBundle.meta.exists(z => !z.isResolved)) {
        attemptResolveBundle(hb.maxBundle.meta.get, hb.maxBundle.extractParentBundleHash.pbHash)
      }
  }

  def combineBundles(): Unit = {

    // Maybe only emit when PBH matches our current?
    // Need to slice this down here
    // Only emit max by new total score?
    // val groupedBundles = activeDAGBundles.groupBy(b => b.bundle.extractParentBundleHash -> b.bundle.maxStackDepth)
    var toRemove = Set[Sheaf]()

    activeDAGManager.cellKeyToCell.filter{_._2.members.size > 1}.foreach{
      case (ck, cell) =>
        if (Random.nextDouble() > 0.1) {
          val best = cell.members.slice(0, 2)
          val allIds = best.flatMap{_.bundle.extractIds}.toSeq
          if (!allIds.contains(id)) {
            val b = Bundle(BundleData(best.map {
              _.bundle
            }.toSeq).signed())
            val maybeData = lookupBundle(ck.hashPointer)
            maybeData.foreach{ pbData =>

              updateBundleFrom(pbData, Sheaf(b))
              // Skip ids when depth below a certain amount, else tell everyone.
              // TODO : Fix ^
              broadcast(b, skipIDs = allIds)
              //apiBroadcast(_.post("rxBundle", b), skipIDs = allIds) // .foreach{println}
            }
          } else {
            toRemove ++= best.toSet
          }
        }


        if (cell.members.size > 10) {
          val toRemoveHere = cell.members.toSeq.sortBy(z => z.totalScore.get).zipWithIndex.filter{_._2 < 5}.map{_._1}.toSet
          toRemove ++= toRemoveHere
        }
    }

    if (toRemove.nonEmpty) {
      activeDAGManager.activeSheafs = activeDAGManager.activeSheafs.filterNot(toRemove.contains)
    }

  }

  def bundleCleanup(): Unit = {
    if (heartbeatRound % 30 == 0 && maxBundleMetaData.exists {
      _.height.exists {
        _ > 50
      }
    }) {

      val currentHeight = maxBundleMetaData.get.height.get // last100ValidBundleMetaData.head.height.get

      var numDeleted = 0

      bundleToSheaf.foreach { case (h, s) =>

        val heightOld = s.height.exists(h => h < (currentHeight - 35))
        val partOfValidation = last100ValidBundleMetaData.contains(s) //|| ancestorsMinus100.contains(s)

        val isOld = s.rxTime < (System.currentTimeMillis() - 120 * 1000)

        val unresolved = s.height.isEmpty && isOld

        def removeTX(): Unit = s.bundle.extractTXHash.foreach { txH =>
          removeTransactionFromMemory(txH.txHash)
        }

        if (unresolved) {
          numDeleted += 1
          deleteBundle(h, dbDelete = false)
          removeTX()
        }

        if (heightOld) {

          if (partOfValidation) {
            deleteBundle(h, dbDelete = false)
          } else {
            deleteBundle(h, dbDelete = false)
          }

          removeTX()

          numDeleted += 1

        }
      }

    }
  }

  def cleanupStrayChains(): Unit = {
    activeDAGManager.cleanup(maxBundleMetaData.get.height.get)
    bundleCleanup()
  }
  */

}
