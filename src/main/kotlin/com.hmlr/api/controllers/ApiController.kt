package com.hmlr.api.controllers

import com.hmlr.api.common.VaultQueryHelperConsumer
import com.hmlr.api.common.models.*
import com.hmlr.api.rpcClient.NodeRPCConnection
import com.hmlr.flows.*
import com.hmlr.model.*
import com.hmlr.states.*
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.loggerFor
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@Suppress("unused")
@RestController
@RequestMapping("/api")
class ApiController(@Suppress("CanBeParameter") private val rpc: NodeRPCConnection) : VaultQueryHelperConsumer() {

    override val rpcOps = rpc.proxy
    override val myIdentity = rpcOps.nodeInfo().legalIdentities.first()

    companion object {
        private val logger = loggerFor<ApiController>()
    }

    /**
     * Return the node's name
     */
    @GetMapping(value = "/me", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun me() = mapOf("me" to myIdentity.toDTOWithName())

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GetMapping(value = "/peers", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun peers() = mapOf("peers" to rpcOps.networkMapSnapshot()
            .asSequence()
            .filter { nodeInfo -> nodeInfo.legalIdentities.first() != myIdentity }
            .map { it.legalIdentities.first().toDTOWithName() }
            .toList())

    /**
     * Returns all titles
     */
    @GetMapping(value = "/titles", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getTitles(): ResponseEntity<Any?> {
        logger.info("GET /titles")

        vaultQueryHelper {
            //Build Title Transfer DTOs
            val titleTransferDTO = buildTitleTransferDTOs()

            //Return Title Transfer DTOs
            return ResponseEntity.ok().body(titleTransferDTO)
        }
    }

    /**
     * Returns a title
     */
    @GetMapping(value = "/titles/{title-number}", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getTitle(@PathVariable("title-number") titleNumber: String): ResponseEntity<Any?> {
        logger.info("GET /titles/$titleNumber")

        vaultQueryHelper {
            //Build Title Transfer DTO
            val titleTransferDTO = buildTitleTransferDTO(titleNumber)

            //Return 404 if null
            titleTransferDTO ?: return ResponseEntity.notFound().build()

            //Return Title Transfer DTO
            return ResponseEntity.ok().body(titleTransferDTO)
        }
    }

}
