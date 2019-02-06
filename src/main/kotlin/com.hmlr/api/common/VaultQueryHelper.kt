@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.hmlr.api.common

import com.hmlr.api.common.models.*
import com.hmlr.model.RequestIssuanceStatus
import com.hmlr.states.*
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.getOrThrow
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.io.PrintWriter
import java.io.StringWriter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

abstract class VaultQueryHelperConsumer {

    abstract val rpcOps: CordaRPCOps
    abstract val myIdentity: Party

    protected object VaultQueryHelper {

        /**
         * Gets all unconsumed states where the [filterPredicate] is met, and wrap inside [StateAndInstant].
         */
        fun <T : LinearState> VaultQueryHelperConsumer.getStatesBy(classOfState: Class<T>, filterPredicate: (StateAndRef<T>) -> kotlin.Boolean): List<StateAndInstant<T>> {
            //Return state, filtered by filterPredicate
            return builder {
                //Unconsumed states where I am a participant
                val customCriteria = QueryCriteria.LinearStateQueryCriteria(participants = listOf(myIdentity), status = Vault.StateStatus.UNCONSUMED)
                //Sort options
                val sort = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_INDEX), Sort.Direction.DESC)))
                //States are of type T::class.java
                val results = rpcOps.vaultQueryBy(customCriteria, PageSpecification(), sort, classOfState)

                //Filter by criteria
                val filteredResults = results.states.filter(filterPredicate)
                //Bind metadata to results
                val statesAndMetadata = filteredResults.zip(results.statesMetadata)
                statesAndMetadata.map {
                    val stateAndRef = it.first
                    val metadata = it.second
                    StateAndInstant(stateAndRef.state.data, metadata.recordedTime)
                }
            }
        }

        /**
         * Gets all unconsumed states where the [filterPredicate] is met, and wrap inside [StateAndInstant].
         */
        inline fun <reified T : LinearState> VaultQueryHelperConsumer.getStatesBy(noinline filterPredicate: (StateAndRef<T>) -> Boolean): List<StateAndInstant<T>> = getStatesBy(T::class.java, filterPredicate)

        /**
         * Gets the latest unconsumed state where the [filterPredicate] is met, and wrap inside [StateAndInstant].
         */
        inline fun <reified T : LinearState> VaultQueryHelperConsumer.getStateBy(noinline filterPredicate: (StateAndRef<T>) -> Boolean): StateAndInstant<T>? = getStatesBy(filterPredicate).firstOrNull()

        /**
         * Gets all unconsumed states and wrap inside [StateAndInstant].
         */
        inline fun <reified T : LinearState> VaultQueryHelperConsumer.getStates(): List<StateAndInstant<T>> = getStatesBy(T::class.java) { true }

        /**
         * Gets all unconsumed states where the [filterPredicate] is met, mapped to the value [indexPredicate] and wrapped inside [StateAndInstant].
         */
        inline fun <reified T : LinearState> VaultQueryHelperConsumer.getStatesMap(noinline filterPredicate: (StateAndRef<T>) -> Boolean = { true }, noinline indexPredicate: (StateAndInstant<T>) -> String): Map<String, List<StateAndInstant<T>>> {
            val list = getStatesBy(filterPredicate)
            return list.groupBy { indexPredicate.invoke(it) }
        }

        /**
         * Builds a list of [TitleTransferDTO] objects
         */
        fun TitleTransferDTO.Companion.buildList(landTitleStateAndInstants: Map<String, List<StateAndInstant<LandTitleState>>>,
                                                 agreementStateAndInstants: Map<String, List<StateAndInstant<LandAgreementState>>>,
                                                 paymentStateAndInstants: Map<String, List<StateAndInstant<PaymentConfirmationState>>>,
                                                 chargesAndRestrictionsStateAndInstants: Map<String, List<StateAndInstant<ProposedChargesAndRestrictionsState>>>,
                                                 requestIssuanceStateAndInstants: Map<String, List<StateAndInstant<RequestIssuanceState>>>,
                                                 requestIssuanceNotFailedStateAndInstants: Map<String, List<StateAndInstant<RequestIssuanceState>>>): List<TitleTransferDTO> {
            val titleTransferDTOs = mutableListOf<TitleTransferDTO>()

            val titleNumbers = setOf<String>()
                    .union(landTitleStateAndInstants.keys)
                    .union(agreementStateAndInstants.keys)
                    .union(paymentStateAndInstants.keys)
                    .union(chargesAndRestrictionsStateAndInstants.keys)
                    .union(requestIssuanceStateAndInstants.keys)
                    .union(requestIssuanceNotFailedStateAndInstants.keys)

            for (titleNumber in titleNumbers) {
                titleTransferDTOs.add(TitleTransferDTO.build(
                        titleNumber,
                        landTitleStateAndInstants.getOrDefault(titleNumber, listOf()),
                        agreementStateAndInstants.getOrDefault(titleNumber, listOf()),
                        paymentStateAndInstants.getOrDefault(titleNumber, listOf()),
                        chargesAndRestrictionsStateAndInstants.getOrDefault(titleNumber, listOf()),
                        requestIssuanceStateAndInstants.getOrDefault(titleNumber, listOf()),
                        requestIssuanceNotFailedStateAndInstants.getOrDefault(titleNumber, listOf())
                ))
            }

            return titleTransferDTOs
        }

        /**
         * Builds a [TitleTransferDTO]
         */
        fun TitleTransferDTO.Companion.build(titleNumber: String,
                                             landTitleStateAndInstants: List<StateAndInstant<LandTitleState>>,
                                             agreementStateAndInstants: List<StateAndInstant<LandAgreementState>>,
                                             paymentStateAndInstants: List<StateAndInstant<PaymentConfirmationState>>,
                                             chargesAndRestrictionsStateAndInstants: List<StateAndInstant<ProposedChargesAndRestrictionsState>>,
                                             requestIssuanceStateAndInstants: List<StateAndInstant<RequestIssuanceState>>,
                                             requestIssuanceNotFailedStateAndInstants: List<StateAndInstant<RequestIssuanceState>>): TitleTransferDTO {
            //Build state statuses
            val stateStatuses = mutableListOf<Pair<String, StateSummaryDTO>>().apply {
                addAll(StateSummaryDTO.buildPairs("land_title", landTitleStateAndInstants.map { it to it.state.status.name }))
                addAll(StateSummaryDTO.buildPairs("sales_agreement", agreementStateAndInstants.map { it to it.state.status.name }))
                addAll(StateSummaryDTO.buildPairs("payment", paymentStateAndInstants.map { it to it.state.status.name }))
                addAll(StateSummaryDTO.buildPairs("proposed_charges_and_restrictions", chargesAndRestrictionsStateAndInstants.map { it to it.state.status.name }))
                addAll(StateSummaryDTO.buildPairs("request_issuance", requestIssuanceStateAndInstants.map { it to it.state.status.name }))
            }.toMap()

            val landTitleStateAndInstant = landTitleStateAndInstants.firstOrNull()
            val agreementStateAndInstant = agreementStateAndInstants.firstOrNull()
            val paymentStateAndInstant = paymentStateAndInstants.firstOrNull()
            val chargesAndRestrictionsStateAndInstant = chargesAndRestrictionsStateAndInstants.firstOrNull()

            //Build status
            val status: String = when {
                run {
                    requestIssuanceNotFailedStateAndInstants.firstOrNull()?.state?.status?.name?.toLowerCase() == "pending"
                } -> "request_issuance_pending"
                run {
                    chargesAndRestrictionsStateAndInstant?.state?.status?.name?.toLowerCase() == "issued" &&
                            landTitleStateAndInstant?.state?.status?.name?.toLowerCase() == "issued"
                } -> "land_title_issued"
                run {
                    chargesAndRestrictionsStateAndInstant?.state?.status?.name?.toLowerCase() == "request_to_add_consent_for_discharge"
                } -> "proposed_request_to_add_consent_for_discharge"
                run {
                    chargesAndRestrictionsStateAndInstant?.state?.status?.name?.toLowerCase() == "consent_for_discharge"
                } -> "proposed_consent_for_discharge"
                run {
                    chargesAndRestrictionsStateAndInstant?.state?.status?.name?.toLowerCase() == "assign_buyer_conveyancer" &&
                            landTitleStateAndInstant?.state?.status?.name?.toLowerCase() == "assign_buyer_conveyancer" &&
                            agreementStateAndInstant?.state?.status?.name?.toLowerCase() == "created"
                } -> "sales_agreement_created"
                run {
                    chargesAndRestrictionsStateAndInstant?.state?.status?.name?.toLowerCase() == "consent_for_new_charge" &&
                            agreementStateAndInstant?.state?.status?.name?.toLowerCase() == "created"
                } -> "proposed_consent_for_new_charge"
                run {
                    agreementStateAndInstant?.state?.status?.name?.toLowerCase() == "approved"
                } -> "sales_agreement_approved"
                run {
                    paymentStateAndInstant?.state?.status?.name?.toLowerCase() == "confirm_payment_received_in_escrow"
                } -> "payment_received_in_escrow"
                run {
                    agreementStateAndInstant?.state?.status?.name?.toLowerCase() == "signed"
                } -> "sales_agreement_seller_party_signed"
                run {
                    agreementStateAndInstant?.state?.status?.name?.toLowerCase() == "completed"
                } -> "sales_agreement_completed"
                run {
                    paymentStateAndInstant?.state?.status?.name?.toLowerCase() == "confirm_payment_received_in_escrow"
                } -> "payment_received_in_escrow"
                run {
                    landTitleStateAndInstant?.state?.status?.name?.toLowerCase() == "transferred"
                } -> "land_title_transferred"
                run {
                    chargesAndRestrictionsStateAndInstant?.state?.status?.name?.toLowerCase() == "issued" &&
                            landTitleStateAndInstant == null
                } -> "land_title_not_yet_issued"
                run {
                    paymentStateAndInstant?.state?.status?.name?.toLowerCase() == "issued"
                } -> "payment_issued"
                run {
                    paymentStateAndInstant?.state?.status?.name?.toLowerCase() == "request_for_payment"
                } -> "payment_request_for_payment"
                run {
                    paymentStateAndInstant?.state?.status?.name?.toLowerCase() == "confirm_funds_released"
                } -> "payment_funds_released"
                else -> "ERROR"
            }

            //Build DTO
            var salesAgreement = if (agreementStateAndInstant == null || agreementStateAndInstant.state.status.name.toLowerCase() == "transferred") null else {
                val referencedPaymentState = paymentStateAndInstants.first { it.state.landAgreementStateLinearId == agreementStateAndInstant.state.linearId.toString() }
                val paymentSettler = referencedPaymentState.state.settlingParty
                agreementStateAndInstant.state.toDTO(paymentSettler, agreementStateAndInstant.instant?.toLocalDateTime())
            }

            // This is because the Payment UI needs access to the buyer and buyer conveyancer details
            // (which the payment state has), however there is sub object to display that data
            // in the current TitleTransferDTO. Instead, in the essence of time, we are just bashing
            // the payment state data into the sales agreement sub object.
            if (salesAgreement == null && paymentStateAndInstant != null) salesAgreement = SalesAgreementDTO(
                    paymentStateAndInstant.state.buyer.toDTO(),
                    paymentStateAndInstant.state.buyerConveyancer.toDTO(),
                    LocalDate.ofEpochDay(0),
                    LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC),
                    0.0,
                    BigDecimal.ZERO,
                    "GBP",
                    BigDecimal.ZERO,
                    "GBP",
                    null,
                    null,
                    BigDecimal.ZERO,
                    "GBP",
                    "full",
                    paymentStateAndInstant.state.settlingParty.toDTO(),
                    paymentStateAndInstant.instant?.toLocalDateTime()
            )

            return TitleTransferDTO(
                    title_number = titleNumber,
                    title = if (landTitleStateAndInstant == null) null else TitleDTO(
                            landTitleStateAndInstant.state.landTitleProperties.address.toDTO(),
                            landTitleStateAndInstant.state.landTitleProperties.owner.toDTO(),
                            landTitleStateAndInstant.state.landTitleProperties.ownerConveyancer.toDTO(),
                            landTitleStateAndInstant.state.titleType.name.toLowerCase(),
                            landTitleStateAndInstant.state.lastSoldValue?.toDecimal(),
                            landTitleStateAndInstant.state.lastSoldValue?.token?.currencyCode,
                            landTitleStateAndInstant.state.charges.map { it.toDTO() },
                            landTitleStateAndInstant.state.restrictions.map { it.toDTO() }
                    ),
                    proposed_title = if (chargesAndRestrictionsStateAndInstant == null || landTitleStateAndInstant == null || landTitleStateAndInstant.state.status.name.toLowerCase() == "transferred") null else TitleDTO(
                            landTitleStateAndInstant.state.landTitleProperties.address.toDTO(),
                            agreementStateAndInstant?.state?.buyer?.toDTO()
                                    ?: landTitleStateAndInstant.state.landTitleProperties.owner.toDTO(),
                            agreementStateAndInstant?.state?.buyerConveyancer?.toDTO()
                                    ?: landTitleStateAndInstant.state.landTitleProperties.ownerConveyancer.toDTO(),
                            landTitleStateAndInstant.state.titleType.name.toLowerCase(),
                            agreementStateAndInstant?.state?.purchasePrice?.toDecimal()
                                    ?: landTitleStateAndInstant.state.lastSoldValue?.toDecimal(),
                            agreementStateAndInstant?.state?.purchasePrice?.token?.currencyCode
                                    ?: landTitleStateAndInstant.state.lastSoldValue?.token?.currencyCode,
                            chargesAndRestrictionsStateAndInstant.state.charges.map { it.toDTO() },
                            chargesAndRestrictionsStateAndInstant.state.restrictions.map { it.toDTO() }
                    ),
                    sales_agreement = salesAgreement,
                    states = stateStatuses,
                    status = status
            )
        }

        /**
         * Builds a [TitleTransferDTO] from data in the vault
         */
        fun TitleTransferDTO.Companion.build(vaultQueryHelperConsumer: VaultQueryHelperConsumer, titleNumber: String): TitleTransferDTO? {
            //Get states and instants
            val landTitleStateAndInstants: List<StateAndInstant<LandTitleState>> = vaultQueryHelperConsumer.getStatesBy { it.state.data.titleID == titleNumber }
            val requestIssuanceStateAndInstants: List<StateAndInstant<RequestIssuanceState>> = vaultQueryHelperConsumer.getStatesBy { it.state.data.titleID == titleNumber }
            val paymentStateAndInstants: List<StateAndInstant<PaymentConfirmationState>> = vaultQueryHelperConsumer.getStatesBy { it.state.data.titleID == titleNumber }

            //Get title number
            val stateTitleNumber = when {
                landTitleStateAndInstants.firstOrNull() != null -> landTitleStateAndInstants.first().state.titleID
                requestIssuanceStateAndInstants.firstOrNull() != null -> requestIssuanceStateAndInstants.first().state.titleID
                paymentStateAndInstants.firstOrNull() != null -> paymentStateAndInstants.first().state.titleID
                else -> null
            }

            //No states exist for given title number
            stateTitleNumber ?: return null

            //Get more states and instants
            val agreementStateAndInstants: List<StateAndInstant<LandAgreementState>> = vaultQueryHelperConsumer.getStatesBy { it.state.data.titleID == titleNumber }
            val chargesAndRestrictionsStateAndInstants: List<StateAndInstant<ProposedChargesAndRestrictionsState>> = vaultQueryHelperConsumer.getStatesBy { it.state.data.titleID == titleNumber }
            val requestIssuanceNotFailedStateAndInstants: List<StateAndInstant<RequestIssuanceState>> = vaultQueryHelperConsumer.getStatesBy {
                val hasFailed = it.state.data.status == RequestIssuanceStatus.FAILED || it.state.data.status == RequestIssuanceStatus.TITLE_ALREADY_ISSUED
                it.state.data.titleID == titleNumber && !hasFailed
            }

            return TitleTransferDTO.build(
                    stateTitleNumber,
                    landTitleStateAndInstants,
                    agreementStateAndInstants,
                    paymentStateAndInstants,
                    chargesAndRestrictionsStateAndInstants,
                    requestIssuanceStateAndInstants,
                    requestIssuanceNotFailedStateAndInstants
            )
        }

        /**
         * Builds a [TitleTransferDTO] from data in the vault
         */
        fun TitleTransferDTO.Companion.buildList(vaultQueryHelperConsumer: VaultQueryHelperConsumer): List<TitleTransferDTO> {
            //Get states and instants
            val landTitleStateAndInstantsFilterPair = vaultQueryHelperConsumer.getStatesMap<LandTitleState> { it.state.titleID }
            val requestIssuanceStateAndInstantsFilterPair = vaultQueryHelperConsumer.getStatesMap<RequestIssuanceState> { it.state.titleID }
            val requestIssuanceNotFailedStateAndInstantsFilterPair = vaultQueryHelperConsumer.getStatesMap<RequestIssuanceState>(
                    {
                        val hasFailed = it.state.data.status == RequestIssuanceStatus.FAILED || it.state.data.status == RequestIssuanceStatus.TITLE_ALREADY_ISSUED
                        !hasFailed
                    },
                    { it.state.titleID }
            )
            val agreementStateAndInstantsFilterPair = vaultQueryHelperConsumer.getStatesMap<LandAgreementState> { it.state.titleID }
            val paymentStateAndInstantsFilterPair = vaultQueryHelperConsumer.getStatesMap<PaymentConfirmationState> { it.state.titleID }
            val chargesAndRestrictionsStateAndInstantsFilterPair = vaultQueryHelperConsumer.getStatesMap<ProposedChargesAndRestrictionsState> { it.state.titleID }

            return TitleTransferDTO.buildList(
                    landTitleStateAndInstantsFilterPair,
                    agreementStateAndInstantsFilterPair,
                    paymentStateAndInstantsFilterPair,
                    chargesAndRestrictionsStateAndInstantsFilterPair,
                    requestIssuanceStateAndInstantsFilterPair,
                    requestIssuanceNotFailedStateAndInstantsFilterPair
            )
        }

        /**
         * Builds a [TitleTransferDTO] from data in the vault
         */
        fun VaultQueryHelperConsumer.buildTitleTransferDTO(titleNumber: String): TitleTransferDTO? = TitleTransferDTO.build(this, titleNumber)

        /**
         * Builds all possible [TitleTransferDTO] from data in the vault
         */
        fun VaultQueryHelperConsumer.buildTitleTransferDTOs(): List<TitleTransferDTO> = TitleTransferDTO.buildList(this)

    }

    protected inline fun <A> vaultQueryHelper(block: VaultQueryHelper.() -> A) = block(VaultQueryHelper)

    /**
     * Find well known party from DTO
     */
    fun X500NameDTO.toWellKnownParty(): Party? {
        return rpcOps.wellKnownPartyFromX500Name(CordaX500Name(
                this.common_name,
                this.organisational_unit,
                this.organisation,
                this.locality,
                this.state,
                this.country
        ))
    }

    /**
     * Return 200 if [FlowHandle] returns, else return 500
     */
    fun <T> responseEntityFromFlowHandle(handle: (rpcOps: CordaRPCOps) -> FlowHandle<T>): ResponseEntity<Any?> {
        return try {
            handle(rpcOps).returnValue.getOrThrow()
            ResponseEntity.ok().build()
        } catch (ex: Throwable) {
            val sw = StringWriter()
            ex.printStackTrace(PrintWriter(sw))
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(sw.toString())
        }
    }

}
