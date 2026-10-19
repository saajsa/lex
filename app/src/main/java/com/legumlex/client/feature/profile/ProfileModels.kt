package com.legumlex.client.feature.profile

/** The contact's profile (customers_api `profile`), as the Mylex profile shows it. */
data class Profile(
    val name: String,
    val avatarUrl: String?,       // their own photo; null shows initials
    val email: String?,
    val emailVerified: Boolean,
    val phone: String?,
    val position: String?,
    val role: String,
    val company: String?,
    val clientSince: String?,
    val joined: String?,
    val lastLogin: String?,
    val passwordChanged: String?,
    val permissions: List<String>,
    val mobile: Mobile,
) {
    /** "SA" for "Saaj Awal"; one letter for one name. */
    val initials: String get() = name.split(' ').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}

/** The verified mobile: what codes and case updates go to. */
data class Mobile(
    val available: Boolean,   // a code can be sent at all
    val number: String?,
    val verified: Boolean,
    val updates: Boolean,     // case updates on WhatsApp/SMS
)

@Suppress("UNCHECKED_CAST")
internal fun parseProfile(d: Map<String, Any?>): Profile {
    val m = d["mobile"] as? Map<String, Any?> ?: emptyMap()
    fun s(k: String) = (d[k] as? String)?.takeIf { it.isNotBlank() }
    return Profile(
        name            = s("name").orEmpty(),
        avatarUrl       = s("avatar_url"),
        email           = s("email"),
        emailVerified   = d["email_verified"] == true,
        phone           = s("phone"),
        position        = s("position"),
        role            = s("role") ?: "Contact",
        company         = s("company"),
        clientSince     = s("client_since"),
        joined          = s("joined"),
        lastLogin       = s("last_login"),
        passwordChanged = s("password_changed"),
        permissions     = (d["permissions"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        mobile          = parseMobile(m),
    )
}

internal fun parseMobile(m: Map<String, Any?>) = Mobile(
    available = m["available"] == true,
    number    = (m["number"] as? String)?.takeIf { it.isNotBlank() },
    verified  = m["verified"] == true,
    updates   = m["updates"] == true,
)
