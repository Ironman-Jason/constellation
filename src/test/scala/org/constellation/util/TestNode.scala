package org.constellation.util

import java.net.InetSocketAddress
import java.security.KeyPair

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.constellation.ConstellationNode
import org.constellation.crypto.KeyUtils

import scala.concurrent.ExecutionContextExecutor

object TestNode {

  def apply(seedHosts: Seq[InetSocketAddress] = Seq(),
            keyPair: KeyPair = KeyUtils.makeKeyPair(),
            heartbeatEnabled: Boolean = false,
            randomizePorts: Boolean = true,
            generateRandomTransactions: Boolean = false)(
    implicit system: ActorSystem,
    materialize: ActorMaterializer,
    executionContext: ExecutionContextExecutor
  ): ConstellationNode = {

    val randomPort = if (randomizePorts) scala.util.Random.nextInt(50000) + 5000 else 9000
    val randomPeerPort = if (randomizePorts) scala.util.Random.nextInt(50000) + 5000 else 9001
    val randomPeerTCPPort = if (randomizePorts) scala.util.Random.nextInt(50000) + 5000 else 9002
    val randomUDPPort = if (randomizePorts) scala.util.Random.nextInt(50000) + 5000 else 16180

    val node = new ConstellationNode(keyPair, seedHosts, "0.0.0.0", randomPort, udpPort = randomUDPPort,
      heartbeatEnabled = heartbeatEnabled, generateRandomTransactions = generateRandomTransactions, autoSetExternalAddress = true,
      peerHttpPort = randomPeerPort,
      peerTCPPort = randomPeerTCPPort
    )

    node.data.confirmWindow = 6

    node
  }

}
