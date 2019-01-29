package com.hmlr.api.rpcClient

import com.hmlr.api.controllers.ApiController
import com.sun.media.jfxmedia.logging.Logger
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

object CORDA_VARS {
    internal const val CORDA_USER_NAME = "CONFIG_RPC_USERNAME"
    internal const val CORDA_USER_PASSWORD = "CONFIG_RPC_PASSWORD"
    internal const val CORDA_NODE_HOST = "CONFIG_RPC_HOST"
    internal const val CORDA_NODE_RPC_PORT = "CONFIG_RPC_PORT"
}

/**
 * Wraps a node RPC proxy.
 *
 * The RPC proxy is configured based on the properties in `application.properties`.
 *
* @property proxy The RPC proxy.
*/

@Component
open class NodeRPCConnection : AutoCloseable {

    lateinit var rpcConnection: CordaRPCConnection
        private set
    lateinit var proxy: CordaRPCOps
        private set

    @PostConstruct
    fun initialiseNodeRPCConnection() {
        loggerFor<NodeRPCConnection>().info("Connecting to RPC: ${System.getenv(CORDA_VARS.CORDA_USER_NAME)}@${System.getenv(CORDA_VARS.CORDA_NODE_HOST)}:${System.getenv(CORDA_VARS.CORDA_NODE_RPC_PORT).toInt()}")
        val rpcAddress = NetworkHostAndPort(System.getenv(CORDA_VARS.CORDA_NODE_HOST), System.getenv(CORDA_VARS.CORDA_NODE_RPC_PORT).toInt())
        val rpcClient = CordaRPCClient(rpcAddress)
        val rpcConnection = rpcClient.start(System.getenv(CORDA_VARS.CORDA_USER_NAME), System.getenv(CORDA_VARS.CORDA_USER_PASSWORD))
        proxy = rpcConnection.proxy
    }

    @PreDestroy
    override fun close() {
        rpcConnection.notifyServerAndClose()
    }
}