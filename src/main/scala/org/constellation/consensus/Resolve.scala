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

  def resolveChildCheckpoint(parentCh: String, dao: Data)(implicit executionContext: ExecutionContext): Unit = {
    //todo mark the child checkpoint blocks resolved, add to cache
    val childrenHashes = dao.resolveNotifierCallbacks(parentCh).map {
      child =>
      val checkpointBlockToHash = (dao.dbActor ? DBGet(child)).mapTo[Option[CheckpointBlock]]//send checkpoint block of child back to resolveCheckpoint to be validated
        checkpointBlockToHash
    }
    Future.sequence(childrenHashes).foreach(seq => seq.foreach(op => op.foreach(resolveCheckpoint(dao, _))))
  }

  // @synchronized?
  def resolveCheckpoint(dao: Data, cb: CheckpointBlock)(implicit executionContext: ExecutionContext): Future[Boolean] = {
    val loadingParents: Future[Seq[(String, Option[SignedObservationEdgeCache])]] =
      Future.sequence(cb.checkpoint.edge.parentHashes
        .map { h =>
          (dao.dbActor ? DBGet(h)).mapTo[Option[SignedObservationEdgeCache]].map(l => h -> l)
        }
      )
    //make sure not already resolving same hash somewhere else if valid but unresolved add to trie map
    val resolvedParents = loadingParents.map { parents =>
      parents.map { case (parentHash, parentCache) =>
        if (parentCache.exists(!_.resolved)) parentCache.foreach { p =>
          dao.resolveNotifierCallbacks(parentHash).+(cb.checkpoint.edge.observationEdge.hash)// points parent -> child so we can update as resolved
          false
          // todo unresolved pass actual checkpoint block of p into handleCheckpoint
//          EdgeProcessor.handleCheckpoint(cb.checkpoint.edge.parents.filter(_.hash == parentHash).head, true)
        }
          true
      }
    }
    resolvedParents.map(_.forall(_ == true))
  }
}



//if resolved, see if this is a dependency of an awaiting resolution and
//    val unresolvedChildHashes: Seq[Future[Option[SignedObservationEdgeCache]]] =
//      dao.resolveNotifierCallbacks(cb.checkpoint.edge.observationEdge.hash)
//        .map(hash => (dao.dbActor ? DBGet(hash))
//          .mapTo[Option[SignedObservationEdgeCache]].filter(_.exists(_.resolved))
//        )
//    unresolvedChildHashes.foreach(ch =>
//      resolveChildCheckpoint
//        EdgeProcessor.handleCheckpoint(ch, true)
//    )
//    // Later on need to add transaction resolving, they're all already here though (sent with the request)
// which adds redundancy but simplifies this step.