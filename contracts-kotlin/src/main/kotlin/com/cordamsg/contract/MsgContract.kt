package com.cordamsg.contract

import com.cordamsg.state.MsgState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class MsgContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.cordamsg.contract.MsgContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Create>()
        requireThat {
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<MsgState>().single()
            "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

            "The IOU's value must be non-empty." using (out.content.isNotEmpty())
        }
    }

    class Create : CommandData
}