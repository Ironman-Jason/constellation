package org.constellation.consensus

import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.util.Timeout
import org.constellation.Data
import org.constellation.LevelDB.{DBGet, DBPut}
import org.constellation.primitives.Schema.{CheckpointBlock, CheckpointCacheData, SignedObservationEdgeCache}
import constellation.EasyFutureBlock

import scala.concurrent.{ExecutionContext, Future}

object Resolve {

  implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)

  def markUnresolvedChildCheckpoint(parentCh: String, dao: Data)(implicit executionContext: ExecutionContext): Unit = {
    //todo mark the child checkpoint blocks resolved, add to cache
    val childrenHashes = dao.resolveNotifierCallbacks(parentCh).map {
      child =>
        val checkpointBlockToHash = dao.hashToCheckpoint(parentCh)//send checkpoint block of child back to resolveCheckpoint to be validated

//        val checkpointBlockToHash = (dao.dbActor ? DBGet(child)).mapTo[Option[CheckpointBlock]]//send checkpoint block of child back to resolveCheckpoint to be validated
        checkpointBlockToHash
    }
  }

  // @synchronized?
  def resolveCheckpoint(dao: Data, cb: CheckpointBlock)(implicit executionContext: ExecutionContext) = {
    val ancestry: Future[Seq[(String, Option[CheckpointCacheData])]] =
      Future.sequence(cb.checkpoint.edge.parentHashes
        .map { h =>
          (dao.dbActor ? DBGet(h)).mapTo[Option[CheckpointCacheData]].map(l => h -> l)
        }
      )
    val isResolved = ancestry.map { parents =>
      parents.map { case (parentHash, parentCb: Option[CheckpointCacheData]) =>
        parentCb.exists(!_.resolved)
      }.forall(_ == true)
    }
    isResolved.foreach {
     resolved =>
       if (resolved) {
         dao.resolveNotifierCallbacks(cb.hash).+(cb.checkpoint.edge.observationEdge.hash) // points parent -> child so we can update as resolved //todo think of cb as a parent. if parent resolved look for children. if cb is not resolved, store it's hash to children. We want the result to be a future,.seq of this parent and its children. then we do an onComplete inside of th endppoint.
       }
       resolved
  }
  }
}
