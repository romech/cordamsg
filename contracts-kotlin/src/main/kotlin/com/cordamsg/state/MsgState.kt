package com.cordamsg.state

import com.cordamsg.contract.MsgContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(MsgContract::class)
data class MsgState(val content: String,
                    val sender: Party,
                    val receiver: Party): ContractState {

    override val participants: List<AbstractParty> get() = listOf(sender, receiver)

    val isUnderRussiansCare = sender.name.country == "RU" || receiver.name.country == "RU"

    val isExtremist = isUnderRussiansCare &&
            listOf("navalny", "navalniy", "meeting", "bomb").any { content.contains(it, ignoreCase = true) }

    val isBlocked = isUnderRussiansCare &&
            listOf("telegram", "bomb").any { content.contains(it, ignoreCase = true)}
}
