package org.constellation.primitives

import org.constellation.primitives.Schema._
import constellation._
import org.constellation.LevelDB.DBPut

import scala.collection.concurrent.TrieMap

trait Genesis extends NodeData with Ledger with BundleDataExt with EdgeDAO {

  val CoinBaseHash = "coinbase"

  def createDistribution(ids: Seq[Id], genesisSOE: SignedObservationEdge) = {

    val distr = ids.map{ id =>
      createTransaction(selfAddressStr, id.address.address, 1e6.toLong, keyPair)
    }

    val distrCB = CheckpointEdgeData(distr.map{_.edge.signedObservationEdge.signatureBatch.hash})

    val distrOE = ObservationEdge(
      TypedEdgeHash(genesisSOE.hash, EdgeHashType.CheckpointHash),
      TypedEdgeHash(genesisSOE.hash, EdgeHashType.CheckpointHash),
      data = Some(TypedEdgeHash(distrCB.hash, EdgeHashType.CheckpointDataHash))
    )

    val distrSOE = signedObservationEdge(distrOE)

    val distrROE = ResolvedObservationEdge(genesisSOE, genesisSOE, Some(distrCB))

    val distrRED = Edge(distrOE, distrSOE, distrROE)

    val distrCBO = CheckpointBlock(distr, CheckpointEdge(distrRED))

    distrCBO
  }

  /**
    * Build genesis tips and example distribution among initial nodes
    * @param ids: Initial node public keys
    * @return : Resolved edges for state update
    */
  def createGenesisAndInitialDistribution(ids: Set[Id]): GenesisObservation = {

    val debtAddress = makeKeyPair().address.address

    val redTXGenesisResolved = createTransaction(debtAddress, selfAddressStr, 4e9.toLong, keyPair)

    val genTXHash = redTXGenesisResolved.edge.signedObservationEdge.signatureBatch.hash

    val cb = CheckpointEdgeData(Seq(genTXHash))

    val oe = ObservationEdge(
      TypedEdgeHash(CoinBaseHash, EdgeHashType.CheckpointHash),
      TypedEdgeHash(CoinBaseHash, EdgeHashType.CheckpointHash),
      data = Some(TypedEdgeHash(cb.hash, EdgeHashType.CheckpointDataHash))
    )

    val soe = signedObservationEdge(oe)

    val roe = ResolvedObservationEdge(
      null.asInstanceOf[SignedObservationEdge],
      null.asInstanceOf[SignedObservationEdge],
      Some(cb)
    )

    val redGenesis = Edge(oe, soe, roe)

    val genesisCBO = CheckpointBlock(Seq(redTXGenesisResolved), CheckpointEdge(redGenesis))

    val distr1CBO = createDistribution(ids.toSeq, soe)
    val distr2CBO = createDistribution(ids.toSeq, soe)

    GenesisObservation(genesisCBO, distr1CBO, distr2CBO)
  }

  def acceptGenesis(go: GenesisObservation): Unit = {
    // Store hashes for the edges
    go.genesis.store(dbActor, inDAG = true, resolved = true)
    go.initialDistribution.store(dbActor, inDAG = true, resolved = true)

    // Store the balance for the genesis TX minus the distribution along with starting rep score.
    go.genesis.transactions.foreach{
      rtx =>
        dbActor ! DBPut(
          rtx.dst.hash,
          AddressCacheData(rtx.amount - (go.initialDistribution.transactions.map{_.amount}.sum*2), Some(1000D))
        )
    }

    // Store the balance for the initial distribution addresses along with starting rep score.
    go.initialDistribution.transactions.foreach{ t =>
      dbActor ! DBPut(t.dst.hash, AddressCacheData(t.amount, Some(1000D)))
    }

    val numTX = (1 + go.initialDistribution.transactions.size).toString
    metricsManager ! UpdateMetric("validTransactions", numTX)
    metricsManager ! UpdateMetric("uniqueAddressesInLedger", numTX)

    genesisObservation = Some(go)

    // Dumb way to set these as active tips, won't pass a double validation but no big deal.
    checkpointMemPool(go.initialDistribution.baseHash) = go.initialDistribution
    checkpointMemPool(go.initialDistribution2.baseHash) = go.initialDistribution2
    checkpointMemPoolThresholdMet(go.initialDistribution.baseHash) = 0
    checkpointMemPoolThresholdMet(go.initialDistribution2.baseHash) = 0

    metricsManager ! UpdateMetric("activeTips", "2")

    println(s"accept genesis = ", go)
  }

}
