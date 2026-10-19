package com.legumlex.client.feature.healthchecks.funnel

import com.legumlex.client.data.api.ApiService
import com.legumlex.client.feature.consultations.RiskLevel
import com.legumlex.client.feature.payment.RazorpayOptions
import com.legumlex.client.feature.payment.razorpayOptionsFrom
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Typed exception raised when the server returns 410 — funnel session expired. */
class AhcFunnelExpired : RuntimeException("This draft has been archived.")

class AhcFunnelRepository(private val api: ApiService) {

    suspend fun current(): Result<AhcFunnelSession?> = runCatching { parse(api.ahcCurrent()) }

    suspend fun start(): Result<AhcFunnelSession> = runCatching {
        parse(api.ahcStart()) ?: error("Server did not return a session")
    }

    suspend fun uploadFile(
        displayName: String,
        mime: String,
        bytes: ByteArray,
    ): Result<AhcFunnelSession> = runCatching {
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", displayName, body)
        parse(api.ahcUpload(part)) ?: error("Server returned an empty session")
    }

    suspend fun deleteFile(serverFileId: String): Result<AhcFunnelSession> = runCatching {
        parse(api.ahcDeleteUpload(serverFileId)) ?: error("Server returned an empty session")
    }

    suspend fun advance(): Result<AhcFunnelSession> = runCatching {
        parse(api.ahcAdvance()) ?: error("Server returned an empty session")
    }

    suspend fun confirm(
        contractType: AhcContractType,
        parties: String,
        concern: String,
    ): Result<AhcFunnelSession> = runCatching {
        parse(api.ahcConfirm(contractType.key, parties.trim(), concern.trim()))
            ?: error("Server returned an empty session")
    }

    /** The session, and the checkout to open when the check has a fee. */
    suspend fun payInit(): Result<Pair<AhcFunnelSession, RazorpayOptions?>> = runCatching {
        val response = api.ahcPayInit()
        val session = parse(response) ?: error("Server returned an empty session")
        @Suppress("UNCHECKED_CAST")
        val checkout = ((response["data"] as? Map<String, Any?>)?.get("checkout") as? Map<String, Any?>)
            ?.let { razorpayOptionsFrom(it, "Agreement Check") }
        session to checkout
    }

    suspend fun payVerify(orderId: String, paymentId: String, signature: String): Result<AhcFunnelSession> = runCatching {
        parse(api.ahcPayVerify(orderId, paymentId, signature)) ?: error("Server returned an empty session")
    }

    suspend fun status(): Result<AhcFunnelSession?> = runCatching { parse(api.ahcStatus()) }

    suspend fun abandon(): Result<Unit> = runCatching {
        api.ahcAbandon()
        Unit
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(response: Map<String, Any?>): AhcFunnelSession? {
        val data = response["data"] as? Map<String, Any?> ?: return null
        val docsRaw = (data["documents"] as? List<Map<String, Any?>>).orEmpty()
        val extMap  = data["extraction"] as? Map<String, Any?>
        val confMap = data["confirmed"]  as? Map<String, Any?>

        val extraction = extMap?.let {
            AhcExtraction(
                status        = it["status"]?.toString() ?: "failed",
                contractType  = AhcContractType.from(it["contract_type"] as? String),
                parties       = it["parties"]?.toString().orEmpty(),
                summary       = it["summary"]?.toString().orEmpty(),
                riskHint      = RiskLevel.from(it["risk_hint"] as? String),
                confidence    = (it["confidence"]?.toString()?.toFloatOrNull()),
                error         = it["error"]?.toString(),
            )
        }
        val confirmed = confMap?.let {
            AhcConfirmed(
                contractType  = AhcContractType.from(it["contract_type"] as? String),
                parties       = it["parties"]?.toString().orEmpty(),
                concern       = it["concern"]?.toString().orEmpty(),
            )
        }
        return AhcFunnelSession(
            sessionHash    = data["session_hash"]?.toString().orEmpty(),
            currentStep    = AhcStep.from(data["current_step"] as? String),
            status         = data["status"]?.toString().orEmpty(),
            consultationId = data["consultation_id"]?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            paymentStatus  = data["payment_status"]?.toString().orEmpty(),
            amountPaid     = (data["amount_paid"]?.toString()?.toDoubleOrNull()?.toInt()) ?: 0,
            pricePaise     = (data["price_paise"]?.toString()?.toDoubleOrNull()?.toInt()) ?: 0,
            documents      = docsRaw.map { d ->
                AhcDocument(
                    id        = d["id"]?.toString().orEmpty(),
                    name      = d["name"]?.toString().orEmpty(),
                    sizeBytes = d["size"]?.toString()?.toLongOrNull() ?: 0L,
                    mime      = d["mime"]?.toString().orEmpty(),
                )
            },
            extraction = extraction,
            confirmed  = confirmed,
        )
    }
}
