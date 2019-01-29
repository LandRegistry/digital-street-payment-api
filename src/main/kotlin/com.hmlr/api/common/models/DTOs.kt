@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.hmlr.api.common.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.hmlr.model.*
import com.hmlr.states.LandAgreementState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.identity.Party
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.PublicKey
import java.time.*
import java.util.*


data class AddressDTO(val house_name_number: String,
                      val street: String,
                      val town_city: String,
                      val county: String,
                      val country: String,
                      val postcode: String)

fun AddressDTO.toAddress() = Address(
        this.house_name_number,
        this.street,
        this.county,
        this.town_city,
        this.country,
        this.postcode
)

fun Address.toDTO() = AddressDTO(
        this.houseNumber,
        this.streetName,
        this.city,
        this.county,
        this.country,
        this.postalCode
)

data class PartyDetailsDTO(
        val identity: String,
        val first_name: String,
        val last_name: String,
        val email_address: String,
        val phone_number: String,
        val type: String,
        val address: AddressDTO
)

fun CustomParty.toDTO() = PartyDetailsDTO(
        this.userID,
        this.forename,
        this.surname,
        this.email,
        this.phone,
        when (this.userType) {
            UserType.INDIVIDUAL -> "individual"
            UserType.NGO -> "non government organisation"
            UserType.OVERSEAS_COMPANY -> "overseas company"
            UserType.COMPANY -> "company"
        },
        this.address.toDTO()
)

fun PartyDetailsDTO.toCustomParty(confirmationOfIdentity: Boolean,
                                  publicKey: PublicKey?,
                                  signature: ByteArray?) = CustomParty(
        this.first_name,
        this.last_name,
        this.identity,
        this.address.toAddress(),
        when (this.type.toLowerCase()) {
            "individual" -> UserType.INDIVIDUAL
            "non government organisation" -> UserType.NGO
            "overseas company" -> UserType.OVERSEAS_COMPANY
            "company" -> UserType.COMPANY
            else -> throw IllegalArgumentException("User type is invalid.")
        },
        this.email_address,
        this.phone_number,
        confirmationOfIdentity,
        signature,
        publicKey
)

data class X500NameDTO(
        val organisation: String,
        val locality: String,
        val country: String,
        val state: String?,
        val organisational_unit: String?,
        val common_name: String?
)

fun Party.toDTO() = X500NameDTO(
        this.name.organisation,
        this.name.locality,
        this.name.country,
        this.name.state,
        this.name.organisationUnit,
        this.name.commonName
)

data class X500NameWithNameDTO(
        val x500: X500NameDTO,
        val name: String
)

fun Party.toDTOWithName() = X500NameWithNameDTO(
        X500NameDTO(
            this.name.organisation,
            this.name.locality,
            this.name.country,
            this.name.state,
            this.name.organisationUnit,
            this.name.commonName
        ),
        this.name.x500Principal.name
)

data class ChargesUpdateDTO @JsonCreator constructor(
        val action: String
)

data class ChargeDTO(
        val date: LocalDateTime,
        val lender: X500NameDTO,
        val amount: BigDecimal,
        val amount_currency_code: String
)

fun Charge.toDTO() = ChargeDTO(
        this.date.toLocalDateTime(),
        this.lender.toDTO(),
        this.amount.toDecimal(),
        this.amount.token.currencyCode
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true, property = "restriction_type")
@JsonSubTypes(
    JsonSubTypes.Type(value = RestrictionDTO::class, name = RestrictionDTO.RESTRICTION_TYPE),
    JsonSubTypes.Type(value = ChargeRestrictionDTO::class, name = ChargeRestrictionDTO.RESTRICTION_TYPE)
)
open class RestrictionDTO(
        val restriction_id: String,
        val restriction_type: String,
        val restriction_text: String,
        val consenting_party: X500NameDTO,
        val signed_actions: String?,
        val date: LocalDateTime
) {
    companion object {
        const val RESTRICTION_TYPE = "ORES"
    }

    open fun toState(getPartyFromDTO: (X500NameDTO) -> Party?): Restriction {
        val consentingParty = getPartyFromDTO(consenting_party)
                ?: throw IllegalArgumentException("Consenting party is invalid.")

        if (restriction_type != RESTRICTION_TYPE)
            throw IllegalArgumentException("restriction_type should be $RESTRICTION_TYPE.")

        return Restriction(
                restriction_id,
                restriction_text,
                consentingParty,
                ActionOnRestriction.ADD_RESTRICTION,
                signed_actions == "add"
        )
    }
}

class ChargeRestrictionDTO(
        restriction_id: String,
        restriction_type: String,
        restriction_text: String,
        consenting_party: X500NameDTO,
        signed_actions: String?,
        date: LocalDateTime,
        val charge: ChargeDTO
) : RestrictionDTO(restriction_id, restriction_type, restriction_text, consenting_party, signed_actions, date) {
    companion object {
        const val RESTRICTION_TYPE = "CBCR"
    }

    override fun toState(getPartyFromDTO: (X500NameDTO) -> Party?): ChargeRestriction {
        val consentingParty = getPartyFromDTO(consenting_party)
                ?: throw IllegalArgumentException("Consenting party is invalid.")
        val chargeLender = getPartyFromDTO(charge.lender)
                ?: throw IllegalArgumentException("Charge lender party is invalid.")

        if (restriction_type != RESTRICTION_TYPE)
            throw IllegalArgumentException("restriction_type should be $RESTRICTION_TYPE.")

        return ChargeRestriction(
                restriction_id,
                restriction_text,
                consentingParty,
                ActionOnRestriction.ADD_RESTRICTION,
                signed_actions == "add",
                Charge(
                        charge.date.toInstant(),
                        chargeLender,
                        getAmount(charge.amount, charge.amount_currency_code)
                )
        )
    }
}

fun Restriction.toDTO(): RestrictionDTO {
    return when (this) {
        is ChargeRestriction -> ChargeRestrictionDTO(
                this.restrictionId,
                ChargeRestrictionDTO.RESTRICTION_TYPE,
                this.restrictionText,
                this.consentingParty.toDTO(),
                if (this.consentGiven) {
                    when (this.action) {
                        ActionOnRestriction.ADD_RESTRICTION -> "add"
                        ActionOnRestriction.DISCHARGE -> "remove"
                        else -> null
                    }
                } else null,
                LocalDateTime.ofEpochSecond(0,0, ZoneOffset.UTC),
                this.charge.toDTO()
        )
        else -> RestrictionDTO(
                this.restrictionId,
                RestrictionDTO.RESTRICTION_TYPE,
                this.restrictionText,
                this.consentingParty.toDTO(),
                if (this.consentGiven) {
                    when (this.action) {
                        ActionOnRestriction.ADD_RESTRICTION -> "add"
                        ActionOnRestriction.DISCHARGE -> "remove"
                        else -> null
                    }
                } else null,
                LocalDateTime.ofEpochSecond(0,0, ZoneOffset.UTC)
        )
    }
}

data class TitleDTO(
        val address: AddressDTO,
        val owner: PartyDetailsDTO,
        val owner_conveyancer: X500NameDTO,
        val title_type: String,
        val last_sold_value: BigDecimal?,
        val last_sold_value_currency_code: String?,
        val charges: List<ChargeDTO>,
        val restrictions: List<RestrictionDTO>
)

data class TitleOwnerDTO(
        val title_number: String,
        val owner: PartyDetailsDTO
)

data class SalesAgreementDTO(
        val buyer: PartyDetailsDTO,
        val buyer_conveyancer: X500NameDTO,
        val creation_date: LocalDate,
        val completion_date: LocalDateTime,
        val contract_rate: Double,
        val purchase_price: BigDecimal,
        val purchase_price_currency_code: String,
        val deposit: BigDecimal,
        val deposit_currency_code: String,
        val contents_price: BigDecimal?,
        val contents_price_currency_code: String?,
        val balance: BigDecimal,
        val balance_currency_code: String,
        val guarantee: String,
        val payment_settler: X500NameDTO,
        val latest_update_date: LocalDateTime?
)

data class SalesAgreementSignDTO(
        val action: String,
        val signatory: X500NameDTO?,
        val signatory_individual: PartyDetailsDTO?
)

fun LandAgreementState.toDTO(paymentSettler: Party, latestUpdateDate: LocalDateTime? = null) = SalesAgreementDTO(
        this.buyer.toDTO(),
        this.buyerConveyancer.toDTO(),
        this.creationDate,
        this.completionDate.toLocalDateTime(),
        this.contractRate,
        this.purchasePrice.toDecimal(),
        this.purchasePrice.token.currencyCode,
        this.deposit.toDecimal(),
        this.deposit.token.currencyCode,
        this.contentsPrice?.toDecimal(),
        this.contentsPrice?.token?.currencyCode,
        this.balance.toDecimal(),
        this.balance.token.currencyCode,
        this.titleGuarantee.name.toLowerCase(),
        paymentSettler.toDTO(),
        latestUpdateDate
)

data class StateSummaryDTO(
        val state_status: String,
        val timestamp: LocalDateTime
) {
    companion object {
        fun <T : LinearState> buildPairs(name: String, stateAndStatus: List<Pair<StateAndInstant<T>, String>>): List<Pair<String, StateSummaryDTO>> {
            return stateAndStatus.mapNotNull {
                buildPair(name, it.first, it.second)
            }
        }

        fun <T : LinearState> buildPair(name: String, state: StateAndInstant<T>?, status: String?): Pair<String, StateSummaryDTO>? {
            val stateSummaryDTO = build(state, status)
            stateSummaryDTO ?: return null
            return name.toLowerCase() to stateSummaryDTO
        }

        fun <T : LinearState> build(state: StateAndInstant<T>?, status: String?): StateSummaryDTO? {
            state ?: return null
            status ?: return null
            val instant = state.instant?.toLocalDateTime()
            instant ?: return null
            return StateSummaryDTO(status.toLowerCase(), instant)
        }
    }
}

data class TitleTransferDTO(
        val title_number: String,
        val title: TitleDTO?,
        val proposed_title: TitleDTO?,
        val sales_agreement: SalesAgreementDTO?,
        val states: Map<String, StateSummaryDTO>,
        val status: String
) {
    companion object
}

fun Instant.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, OffsetDateTime.now().offset)
fun LocalDateTime.toInstant(): Instant = this.toInstant(OffsetDateTime.now().offset)

fun getAmount(quantity: BigDecimal, currencyCode: String): Amount<Currency> {
    return Amount.fromDecimal(quantity, Currency.getInstance(currencyCode), RoundingMode.FLOOR)
}

@JvmName("getAmountNullable")
fun getAmount(quantity: BigDecimal?, currencyCode: String?): Amount<Currency>? {
    quantity ?: return null
    currencyCode ?: return null

    return getAmount(quantity, currencyCode)
}
