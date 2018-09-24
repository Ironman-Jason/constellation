package org.constellation.primitives

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import org.constellation.LevelDB.DBGet
import org.constellation.primitives.Schema.{CheckpointBlock, TransactionV1}
import constellation._

trait EdgeExt extends NodeData with Ledger with MetricsExt with PeerInfo {

  def hashToCheckpoint(hash: String) = {
    implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)
    import akka.pattern.ask
    def dbQuery = {
      (dbActor ? DBGet(hash)).mapTo[Option[CheckpointBlock]].getOpt(t=5).flatten
    }
  }

//  def hashToCheckpoint(hash: String) = {
//    implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)
//    import akka.pattern.ask
//    def dbQuery = {
//      (dbActor ? DBGet(hash)).mapTo[Option[CheckpointBlock]].getOpt(t=5).flatten
//    }
//  }
}
