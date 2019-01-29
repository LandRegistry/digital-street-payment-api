package com.hmlr.api.listener

import com.hmlr.api.rpcClient.CORDA_VARS
import com.hmlr.states.PaymentConfirmationState
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.ContractState
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import net.corda.core.messaging.CordaRPCOps

class EventListenerRPC {

    companion object {
        val logger: Logger = loggerFor<EventListenerRPC>()
    }

    private fun processState(state: ContractState, proxy: CordaRPCOps) {
        when (state) {
            is PaymentConfirmationState -> {
                logger.info("Got an PaymentConfirmationState!")

                // Make API calls here
            }
            else -> logger.info("Got an unknown state: ${state.javaClass.name}")
        }
    }

    fun run() {
        val nodeIpAndPort = "${System.getenv(CORDA_VARS.CORDA_NODE_HOST)}:${System.getenv(CORDA_VARS.CORDA_NODE_RPC_PORT)}"
        val nodeAddress = NetworkHostAndPort.parse(nodeIpAndPort)

        val client = CordaRPCClient(nodeAddress)

        val nodeUsername = System.getenv(CORDA_VARS.CORDA_USER_NAME)
        val nodePassword = System.getenv(CORDA_VARS.CORDA_USER_PASSWORD)
        val proxy = client.start(nodeUsername, nodePassword).proxy

        val (snapshot, updates) = proxy.vaultTrack(PaymentConfirmationState::class.java)

        logger.info("Hopefully we should be tracking PaymentConfirmationState")

        //snapshot.states.forEach {}
        updates.toBlocking().subscribe { update ->
            if (update.produced.isEmpty()) {
                logger.warn("Update is empty!")
            } else logger.info("We have an update!")

            update.produced.forEach { processState(it.state.data, proxy) }
        }
    }
}
