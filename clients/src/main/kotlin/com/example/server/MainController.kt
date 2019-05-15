package com.example.server

import com.cordamsg.MessagingFlow.Initiator
import com.cordamsg.state.MsgState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

val SERVICE_NAMES = listOf("Notary", "Network Map Service", "TovarischMajor", "GreatFirewall")

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */

@RestController
@RequestMapping("/api/") // The paths for GET and POST requests are relative to this base path.
class MainController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    /**
     * Returns the node's name.
     */
    @GetMapping(value = [ "me" ], produces = [ APPLICATION_JSON_VALUE ])
    fun whoami() = mapOf("me" to humanReadableName(myLegalName))

    /**
     * Returns all parties registered with the network map service. These names can be used to look up identities using
     * the identity service.
     */
    @GetMapping(value = [ "peers" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }


    @PostMapping(value = [ "write" ], produces = [ TEXT_PLAIN_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun writeMsg(request: HttpServletRequest): ResponseEntity<String> {
        val textValue = request.getParameter("text")
        val partyName = request.getParameter("partyName")
        if (partyName == null){
            return ResponseEntity.badRequest().body("Query parameter 'partyName' must not be null.\n")
        }
        if (textValue.isEmpty()) {
            return ResponseEntity.badRequest().body("Query parameter 'text' must be non-empty.\n")
        }
        val partyX500Name = CordaX500Name.parse(partyName)
        val otherParty = proxy.wellKnownPartyFromX500Name(partyX500Name) ?: return ResponseEntity.badRequest().body("Party named $partyName cannot be found.\n")

        return try {
            val signedTx = proxy.startTrackedFlow(::Initiator, textValue, otherParty).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${signedTx.id} committed to ledger.\n")

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ex.message!!)
        }
    }

    fun queryStates(): List<StateAndRef<MsgState>> = proxy.vaultQueryBy<MsgState>().states

    @GetMapping(value = ["states"], produces = [APPLICATION_JSON_VALUE])
    fun getStates(): ResponseEntity<List<StateAndRef<MsgState>>> {
        return ResponseEntity.ok(queryStates())
    }

    @GetMapping(value = [ "history" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getMsgs() : ResponseEntity<List<StateAndRef<MsgState>>> {
        val states = queryStates()

        return ResponseEntity.ok(
            when(myLegalName.organisation) {
                "TovarischMajor" -> states.filter { it.state.data.isExtremist }
                "GreatFirewall" -> states.filter { it.state.data.isUnderRussiansCare }
                else -> states
            }
        )
    }

    @GetMapping(value = [ "my-messages" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getMyMsgs(): ResponseEntity<List<StateAndRef<MsgState>>>  {
        val myMsgs = proxy.vaultQueryBy<MsgState>().states.filter { it.state.data.sender.equals(proxy.nodeInfo().legalIdentities.first()) }
        return ResponseEntity.ok(myMsgs)
    }

    private fun humanReadableName(cordaName: CordaX500Name): String =
            "${cordaName.organisation} (${cordaName.locality}, ${cordaName.country})"

    private fun humanReadableState(msgState: MsgState): Map<String, String> =
        mapOf("content" to msgState.content,
                "sender" to humanReadableName(msgState.sender.name),
                "receiver" to humanReadableName(msgState.receiver.name))
}

