package com.legumlex.client.data.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

// Endpoint stubs for the customers_api module. Response payloads are intentionally
// loose at this stage (Map<String, Any?>) — model classes get added screen-by-screen
// as we implement each feature against the real response shape.
interface ApiService {

    // --- Authentication ---

    @FormUrlEncoded
    @Headers("X-Skip-Auth: 1")
    @POST("authentication")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String,
    ): Map<String, Any?>

    @GET("authentication/me")
    suspend fun me(): Map<String, Any?>

    @FormUrlEncoded
    @Headers("X-Skip-Auth: 1")
    @POST("authentication/google")
    suspend fun loginWithGoogle(
        @Field("id_token") idToken: String,
    ): Map<String, Any?>

    @Headers("X-Skip-Auth: 1")
    @GET("authentication/google_config")
    suspend fun googleConfig(): Map<String, Any?>

    @GET("logout")
    suspend fun logout(): Map<String, Any?>

    // Sign-in by a code on WhatsApp (or SMS) to the contact's verified mobile:
    // request → { ref, to, message, resend_after }, or 403 error=unverified;
    // status → { state, can_sms, resend_in }; verify → the same body as login.
    @FormUrlEncoded
    @Headers("X-Skip-Auth: 1")
    @POST("authentication/otp/request")
    suspend fun otpRequest(@Field("number") number: String): Map<String, Any?>

    @FormUrlEncoded
    @Headers("X-Skip-Auth: 1")
    @POST("authentication/otp/status")
    suspend fun otpStatus(@Field("ref") ref: String): Map<String, Any?>

    @FormUrlEncoded
    @Headers("X-Skip-Auth: 1")
    @POST("authentication/otp/sms")
    suspend fun otpSms(@Field("ref") ref: String): Map<String, Any?>

    @FormUrlEncoded
    @Headers("X-Skip-Auth: 1")
    @POST("authentication/otp/verify")
    suspend fun otpVerify(
        @Field("ref") ref: String,
        @Field("code") code: String,
    ): Map<String, Any?>

    // --- Workspaces, profile, registry (as Mylex shows them) ---

    /** {tracks{litigation,projects,registry}, offered[], figures{total,awaiting,resolved,reports}} */
    @GET("dashboard/workspaces")
    suspend fun workspaces(): Map<String, Any?>

    @GET("profile")
    suspend fun profile(): Map<String, Any?>

    /** Verify one's own mobile: step = request|status|sms|verify|updates|remove. */
    @FormUrlEncoded
    @POST("profile/phone/{step}")
    suspend fun profilePhone(
        @Path("step") step: String,
        @Field("number") number: String? = null,
        @Field("ref") ref: String? = null,
        @Field("code") code: String? = null,
        @Field("on") on: String? = null,
    ): Map<String, Any?>

    @GET("registry")
    suspend fun registry(): Map<String, Any?>

    @GET("registry/{id}")
    suspend fun registryAgreement(@Path("id") id: String): Map<String, Any?>

    // --- Dashboard ---

    @GET("dashboard/stats")
    suspend fun dashboardStats(): Map<String, Any?>

    @GET("dashboard/profile")
    suspend fun dashboardProfile(): Map<String, Any?>

    @GET("dashboard/next_hearing")
    suspend fun nextHearing(): Map<String, Any?>

    @GET("dashboard/recent_activity")
    suspend fun recentActivity(@Query("limit") limit: Int = 10): Map<String, Any?>

    @GET("dashboard/active_services")
    suspend fun activeServices(): Map<String, Any?>

    @GET("dashboard/legal_health")
    suspend fun legalHealth(): Map<String, Any?>

    @GET("dashboard/action_items")
    suspend fun dashboardActionItems(): Map<String, Any?>

    // Persist a swipe-to-dismiss. `key` is the action item's stable server id
    // (ActionItem.id, e.g. "invoice_42"); the dismissal sticks across refreshes.
    @FormUrlEncoded
    @POST("dashboard/action_dismiss")
    suspend fun dismissActionItem(
        @Field("key") key: String,
    ): Map<String, Any?>

    // Undo a swipe-to-dismiss: the item returns to action_items.
    @FormUrlEncoded
    @POST("dashboard/action_restore")
    suspend fun restoreActionItem(
        @Field("key") key: String,
    ): Map<String, Any?>

    @GET("dashboard/next_event")
    suspend fun dashboardNextEvent(): Map<String, Any?>

    @GET("dashboard/active_matters")
    suspend fun dashboardActiveMatters(): Map<String, Any?>

    @GET("dashboard/chamber")
    suspend fun dashboardChamber(): Map<String, Any?>

    @GET("notifications/unread_count")
    suspend fun notificationsUnreadCount(): Map<String, Any?>

    // --- Cases ---

    @GET("cases")
    suspend fun cases(
        @Query("id") id: String? = null,
        @Query("status") status: String? = null,
    ): Map<String, Any?>

    @GET("cases/timeline/{id}")
    suspend fun caseTimeline(@retrofit2.http.Path("id") id: String): Map<String, Any?>

    // Certified-copy PDF backing a court order (streamed; caller closes the body).
    @Streaming
    @GET("cases/order-document/{orderId}")
    suspend fun downloadCaseOrderDocument(@retrofit2.http.Path("orderId") orderId: String): ResponseBody

    // A client-visible case file (Docs tab).
    @Streaming
    @GET("cases/document/{fileId}")
    suspend fun downloadCaseDocument(@retrofit2.http.Path("fileId") fileId: String): ResponseBody

    // --- Hearings ---

    @GET("hearings")
    suspend fun hearings(@Query("id") id: String? = null): Map<String, Any?>

    @GET("hearings/upcoming")
    suspend fun upcomingHearings(): Map<String, Any?>

    // --- Notifications ---

    @GET("notifications")
    suspend fun notifications(): Map<String, Any?>

    @PUT("notifications/{id}/read")
    suspend fun markNotificationRead(@retrofit2.http.Path("id") id: String): Map<String, Any?>

    @PUT("notifications/mark_all_read")
    suspend fun markAllNotificationsRead(): Map<String, Any?>

    // --- Legal notices (two-way funnel) ---

    @GET("notices")
    suspend fun notices(): Map<String, Any?>

    @GET("notices/types")
    suspend fun noticeTypes(): Map<String, Any?>

    @GET("notices/{id}")
    suspend fun notice(@retrofit2.http.Path("id") id: String): Map<String, Any?>

    /** Inbound: upload the received notice; AI returns extracted fields (no record). */
    @Multipart
    @POST("notices/extract")
    suspend fun extractNotice(@Part file: MultipartBody.Part): Map<String, Any?>

    /** Create a notice. Outbound (draft request) or Inbound (received), with optional file. */
    @Multipart
    @POST("notices")
    suspend fun createNotice(@Part parts: List<MultipartBody.Part>): Map<String, Any?>

    @PUT("notices/{id}/approve")
    suspend fun approveNotice(@retrofit2.http.Path("id") id: String): Map<String, Any?>

    /** Inline-stream a client-visible notice document (caller closes the body). */
    @Streaming
    @GET("notices/{id}/document/{fileId}")
    suspend fun downloadNoticeDocument(
        @retrofit2.http.Path("id") id: String,
        @retrofit2.http.Path("fileId") fileId: String,
    ): ResponseBody

    @GET("consultations")
    suspend fun consultations(
        @Query("id") id: String? = null,
        @Query("phase") phase: String? = null,
        @Query("status") status: String? = null,
        @Query("request_status") requestStatus: String? = null,
        @Query("type") type: String? = null,
        @Query("urgent") urgent: Int? = null,
        @Query("q") query: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("ahc_state") ahcState: String? = null,
        @Query("order_pending_first") orderPendingFirst: Int? = null,
    ): Map<String, Any?>

    /** Cancel a pending self-initiated consultation request. */
    @retrofit2.http.DELETE("consultations/{id}")
    suspend fun deleteConsultation(@Path("id") consultationId: String): Map<String, Any?>

    /**
     * Terminal cancel at any non-terminal state — the design's "Close" sheet
     * action. Unlike DELETE (which only works while a request is still
     * pending), this writes `closure_reason='cancelled_by_client'` + the
     * proper lifecycle columns and works at any stage the consultation is
     * still open. Idempotent — re-calling on an already-closed row returns
     * 200 with the existing closed_at / closure_reason.
     */
    @FormUrlEncoded
    @POST("consultations/{id}/cancel")
    suspend fun cancelConsultation(
        @Path("id") consultationId: String,
        @Field("reason") reason: String? = null,
    ): Map<String, Any?>

    /**
     * Parent / linked child consultation chain for an AHC ↔ General pair.
     * One round-trip serves both directions so the detail screen renders
     * the "Spawned from" / "Spawns" link card without a follow-up call.
     * Both `parent` and `linked` may be null.
     */
    @GET("consultations/{id}/linkage")
    suspend fun consultationLinkage(@Path("id") consultationId: String): Map<String, Any?>

    /**
     * Update the client's preferred date/time before staff has confirmed a real
     * slot. Form-encoded since the server reads both `php://input` parsed
     * params and POST in fallback.
     */
    @FormUrlEncoded
    @retrofit2.http.PUT("consultations/{id}/preferred")
    suspend fun updateConsultationPreferred(
        @Path("id") consultationId: String,
        @Field("preferred_date") preferredDate: String?,
        @Field("preferred_time") preferredTime: String?,
    ): Map<String, Any?>

    /** Remove a file the client uploaded themselves. */
    @retrofit2.http.DELETE("consultations/attachments/{fid}")
    suspend fun deleteConsultationAttachment(@Path("fid") fileId: String): Map<String, Any?>

    /** Client-initiated waive of a doc-request — "I don't have this". */
    @FormUrlEncoded
    @POST("consultations/doc_requests/{rid}/decline")
    suspend fun declineDocRequest(
        @Path("rid") requestId: String,
        @Field("reason") reason: String,
    ): Map<String, Any?>

    /** Stream the AHC source PDF for the given consultation. */
    @Streaming
    @GET("consultations/{id}/ahc/document")
    suspend fun downloadAhcDocument(@Path("id") consultationId: String): ResponseBody

    @GET("consultations/ahc/{id}")
    suspend fun consultationAhc(@Path("id") consultationId: String): Map<String, Any?>

    @FormUrlEncoded
    @POST("consultations/create")
    suspend fun createConsultation(
        @Field("type")           type: String,
        @Field("tag")            tag: String,
        @Field("note")           note: String,
        @Field("is_urgent")      isUrgent: Int,
        @Field("priority")       priority: String,
        @Field("preferred_date") preferredDate: String,
        @Field("preferred_time") preferredTime: String,
        @Field("case_link_id")   caseLinkId: String,
    ): Map<String, Any?>

    @GET("consultations/doc_requests/{id}")
    suspend fun consultationDocRequests(@Path("id") consultationId: String): Map<String, Any?>

    /**
     * Client uploads a file against a pending doc-request row. The server
     * stores it in tblfiles (rel_type='consultation', tag='doc_req_<rid>'),
     * flips the request to status='uploaded', and binds the file id.
     */
    @Multipart
    @POST("consultations/doc_requests/{rid}/fulfill")
    suspend fun fulfillDocRequest(
        @Path("rid") requestId: String,
        @Part file: MultipartBody.Part,
    ): Map<String, Any?>

    /** Stream back the file the client uploaded to fulfil a doc-request row. */
    @Streaming
    @GET("consultations/doc_requests/{rid}/file")
    suspend fun downloadDocRequestFile(@Path("rid") requestId: String): ResponseBody

    /**
     * List the files visible to the client on a consultation — staff-shared
     * docs (visible_to_customer=1) plus the client's own uploads.
     */
    @GET("consultations/{id}/attachments")
    suspend fun consultationAttachments(@Path("id") consultationId: String): Map<String, Any?>

    /** Upload a client-authored attachment to the consultation. */
    @Multipart
    @POST("consultations/{id}/attachments")
    suspend fun uploadConsultationAttachment(
        @Path("id") consultationId: String,
        @Part file: MultipartBody.Part,
    ): Map<String, Any?>

    @GET("consultations/appointment/{id}")
    suspend fun consultationAppointment(@Path("id") consultationId: String): Map<String, Any?>

    // --- Digital Office (booking — DO is the front door, consultation is the record)

    /**
     * Start a DO session for the given advocate. Returns session_id + a `free`
     * flag. A paid session's fee is an invoice once the advocate accepts
     * (doFeeInvoice), paid on the website.
     */
    @FormUrlEncoded
    @POST("digital_office/sessions")
    suspend fun startDoSession(
        @Field("advocate_staff_id") advocateStaffId: String,
    ): Map<String, Any?>

    /**
     * Submit the intake brief. On success the server has generated a Meet
     * link, created the backing consultation, and flipped the session to
     * in_progress. Response carries consultation_id — the app navigates to
     * the consultation detail and treats it as the canonical record.
     */
    @FormUrlEncoded
    @POST("digital_office/sessions/{id}/intake")
    suspend fun submitDoIntake(
        @Path("id") sessionId: String,
        @Field("intake_text") intakeText: String,
    ): Map<String, Any?>

    /** The accepted session's fee as an invoice: {invoice_id, pay_url} — paid on the website. */
    @POST("digital_office/sessions/{id}/invoice")
    suspend fun doFeeInvoice(@Path("id") sessionId: String): Map<String, Any?>

    /** Current state of a DO session — useful for resuming after a backgrounded intake. */
    @GET("digital_office/sessions/{id}")
    suspend fun getDoSession(@Path("id") sessionId: String): Map<String, Any?>

    /**
     * Cancel a DO session at any pre-confirmed state (server migration 106).
     * Frees the advocate slot immediately so the next walk-in/waitroom can
     * proceed. Idempotent; returns 200 on already-cancelled rows.
     */
    @POST("digital_office/sessions/{id}/cancel")
    suspend fun cancelDoSession(@Path("id") sessionId: String): Map<String, Any?>

    /**
     * Caller's active waitroom rows across all advocates queued for. Drives
     * the home-screen "you're in queue" banner + the queue management sheet.
     */
    @GET("digital_office/waitroom")
    suspend fun getDoWaitroom(): Map<String, Any?>

    /**
     * Join an advocate's cabin queue. Server enforces that the advocate must
     * be online AND either busy or already queued; if the cabin is free the
     * caller is told to book a session directly (code=cabin_free).
     */
    @FormUrlEncoded
    @POST("digital_office/waitroom/join")
    suspend fun joinDoWaitroom(
        @Field("advocate_staff_id") advocateStaffId: String,
    ): Map<String, Any?>

    /** Leave a waitroom row owned by the caller. */
    @FormUrlEncoded
    @POST("digital_office/waitroom/leave")
    suspend fun leaveDoWaitroom(
        @Field("waitroom_id") waitroomId: String,
    ): Map<String, Any?>

    /**
     * For a DIGITAL_OFFICE consultation, returns the backing
     * digital_office_sessions row (Meet link, current status, advocate).
     * Replaces consultationAppointment for the DO detail screen.
     */
    @GET("consultations/do_session/{id}")
    suspend fun consultationDoSession(@Path("id") consultationId: String): Map<String, Any?>

    @FormUrlEncoded
    @POST("consultations/reply/{id}")
    suspend fun sendConsultationReply(
        @Path("id") consultationId: String,
        @Field("message") message: String,
    ): Map<String, Any?>

    // --- AHC submission funnel ---

    @GET("ahc/current")
    suspend fun ahcCurrent(): Map<String, Any?>

    @POST("ahc/start")
    suspend fun ahcStart(): Map<String, Any?>

    @FormUrlEncoded
    @PUT("ahc/confirm")
    suspend fun ahcConfirm(
        @Field("contract_type") contractType: String,
        @Field("parties") parties: String,
        @Field("concern") concern: String,
    ): Map<String, Any?>

    @Multipart
    @POST("ahc/upload")
    suspend fun ahcUpload(@Part file: MultipartBody.Part): Map<String, Any?>

    @DELETE("ahc/upload")
    suspend fun ahcDeleteUpload(@Query("file_id") fileId: String): Map<String, Any?>

    @POST("ahc/advance")
    suspend fun ahcAdvance(): Map<String, Any?>

    @POST("ahc/pay/init")
    suspend fun ahcPayInit(): Map<String, Any?>

    @GET("ahc/status")
    suspend fun ahcStatus(): Map<String, Any?>

    @POST("ahc/abandon")
    suspend fun ahcAbandon(): Map<String, Any?>

    // --- Invoices ---

    @GET("invoices")
    suspend fun invoices(@Query("id") id: String? = null): Map<String, Any?>

    // --- Documents ---

    @GET("documents")
    suspend fun documents(
        @Query("rel_type") relType: String? = null,
        @Query("q") query: String? = null,
        @Query("tag") tag: String? = null,
        @Query("case_id") caseId: String? = null,
        @Query("rel_id") relId: String? = null,
    ): Map<String, Any?>

    // @Streaming so Retrofit hands us the raw response body without buffering
    // the whole file into memory. Critical for large PDFs.
    @Streaming
    @GET("documents/download/{id}")
    suspend fun downloadDocument(@Path("id") id: String): ResponseBody

    // --- Projects & Tasks ---

    @GET("projects")
    suspend fun projects(@Query("status") status: String? = null): Map<String, Any?>

    @GET("projects/{id}")
    suspend fun project(@Path("id") id: String): Map<String, Any?>

    @GET("projects/{id}/tasks")
    suspend fun projectTasks(@Path("id") id: String): Map<String, Any?>

    @GET("projects/{id}/milestones")
    suspend fun projectMilestones(@Path("id") id: String): Map<String, Any?>

    @GET("projects/{id}/files")
    suspend fun projectFiles(@Path("id") id: String): Map<String, Any?>

    @GET("projects/{id}/activity")
    suspend fun projectActivity(@Path("id") id: String): Map<String, Any?>

    @GET("tasks/{id}")
    suspend fun task(@Path("id") id: String): Map<String, Any?>

    // --- Projects & Tasks: client write / interaction layer ---

    /** Client posts a comment on a project task (gated by comment_on_tasks). */
    @FormUrlEncoded
    @POST("tasks/{id}/comments")
    suspend fun postTaskComment(
        @Path("id") taskId: String,
        @Field("content") content: String,
    ): Map<String, Any?>

    /** Client creates a task (auto-assigned, client-visible; gated by create_tasks). */
    @FormUrlEncoded
    @POST("projects/{id}/tasks")
    suspend fun createProjectTask(
        @Path("id") projectId: String,
        @Field("name") name: String,
        @Field("startdate") startDate: String,
        @Field("duedate") dueDate: String? = null,
        @Field("priority") priority: Int = 2,
        @Field("milestone") milestone: Int = 0,
        @Field("description") description: String? = null,
    ): Map<String, Any?>

    /** Client uploads a file to a project (gated by upload_files). */
    @Multipart
    @POST("projects/{id}/files")
    suspend fun uploadProjectFile(
        @Path("id") projectId: String,
        @Part file: MultipartBody.Part,
    ): Map<String, Any?>

    /** Stream a customer-visible project file inline. */
    @Streaming
    @GET("projects/files/{fileId}")
    suspend fun downloadProjectFile(@Path("fileId") fileId: String): ResponseBody

    /** Client-visible discussion threads for a project (gated by open_discussions). */
    @GET("projects/{id}/discussions")
    suspend fun projectDiscussions(@Path("id") id: String): Map<String, Any?>

    /** One discussion thread with its comments. */
    @GET("projects/discussions/{id}")
    suspend fun projectDiscussion(@Path("id") discussionId: String): Map<String, Any?>

    /** Client replies in a discussion thread. */
    @FormUrlEncoded
    @POST("projects/discussions/{id}/comments")
    suspend fun postDiscussionComment(
        @Path("id") discussionId: String,
        @Field("content") content: String,
    ): Map<String, Any?>

    // --- Mobile device (FCM) registration ---

    @FormUrlEncoded
    @POST("mobile_devices/register")
    suspend fun registerDevice(
        @Field("device_id") deviceId: String,
        @Field("platform") platform: String = "android",
        @Field("fcm_token") fcmToken: String,
        @Field("device_name") deviceName: String? = null,
        @Field("os_version") osVersion: String? = null,
        @Field("app_version") appVersion: String? = null,
    ): Map<String, Any?>

    // --- The Legum Lex desk (Consult), as a portal client meets it ---

    /** {data:{is_active, base_price, document_types[], max_file_size, max_files, max_total_size, min_chars, max_chars, entity_types[], phone_required, countries[{code,name,dial}], firm, context{...}}} */
    @GET("desk")
    suspend fun desk(): Map<String, Any?>

    /** The desk's reading of a question (cases_desk_read): {data:{verdict, follow_up, area, urgency, urgency_reason, place, intent, related, documents[], reading}} */
    @FormUrlEncoded
    @POST("desk/check")
    suspend fun deskCheck(@Field("text") text: String): Map<String, Any?>

    /** enquiry_text, entity_type, phone, country, country_other, desk_reading, session_hash, attachments[] */
    @Multipart
    @POST("desk/enquiry")
    suspend fun deskEnquiry(@Part parts: List<MultipartBody.Part>): Map<String, Any?>

    /** The fee for a written enquiry, as an invoice paid on its public page: {invoice_id, pay_url, total}; or {is_free | already_processed, step, consultation_id}. */
    @FormUrlEncoded
    @POST("desk/pay")
    suspend fun deskPay(@Field("session_hash") sessionHash: String): Map<String, Any?>

    /** An unpaid enquiry set aside (Funnel_orchestrator::discard_unpaid). */
    @FormUrlEncoded
    @POST("desk/discard")
    suspend fun deskDiscard(@Field("session_hash") sessionHash: String): Map<String, Any?>

    /** The Legum Lex site's public notice tracker (cases/landing/ajax_track), by its own URL. */
    @FormUrlEncoded
    @Headers("X-Skip-Auth: 1")
    @POST
    suspend fun trackNotice(@Url url: String, @Field("reference") reference: String): Map<String, Any?>
}
