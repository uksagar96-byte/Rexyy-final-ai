package com.rexyy.app.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object CallerIdentityResolver {

    data class ContactMatch(val name: String, val phoneNumber: String)

    sealed class ContactSearchResult {
        object PermissionNeeded : ContactSearchResult()
        data class NotFound(val query: String) : ContactSearchResult()
        data class SingleMatch(val contact: ContactMatch) : ContactSearchResult()
        data class MultipleMatches(val query: String, val matches: List<ContactMatch>) : ContactSearchResult()
    }

    sealed class ContactActionResult {
        data class Resolved(val contact: ContactMatch) : ContactActionResult()
        data class Multiple(val query: String, val matches: List<ContactMatch>) : ContactActionResult()
        data class NotFound(val query: String) : ContactActionResult()
        object PermissionNeeded : ContactActionResult()
    }

    @Volatile
    var testContacts: List<ContactMatch>? = null

    /**
     * Resolves a phone number to a Contact Name using Android ContactsContract.PhoneLookup
     */
    fun resolveCallerName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null

        testContacts?.let { list ->
            val cleanTarget = phoneNumber.replace(Regex("[^0-9+]"), "")
            return list.firstOrNull { it.phoneNumber.replace(Regex("[^0-9+]"), "") == cleanTarget }?.name
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Looks up a contact's phone number by name from the device Contacts.
     * Safely returns phone number only if unambiguous.
     */
    fun findPhoneNumberByName(context: Context, contactName: String): String? {
        return when (val res = resolveContactForAction(context, contactName)) {
            is ContactActionResult.Resolved -> res.contact.phoneNumber
            else -> null
        }
    }

    /**
     * Searches all contacts matching the given query string with ranking (exact match > prefix > contains).
     */
    fun searchContacts(context: Context, query: String): List<ContactMatch> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        testContacts?.let { list ->
            val lower = trimmed.lowercase()
            val matches = list.filter { it.name.lowercase().contains(lower) || it.phoneNumber.contains(trimmed) }
            val deduped = matches.distinctBy { it.name.lowercase() to it.phoneNumber.replace(Regex("[^0-9+]"), "") }
            return deduped.sortedWith(
                compareBy<ContactMatch> {
                    when {
                        it.name.equals(trimmed, ignoreCase = true) -> 0
                        it.name.startsWith(trimmed, ignoreCase = true) -> 1
                        else -> 2
                    }
                }.thenBy { it.name }
            )
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val matches = mutableListOf<ContactMatch>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$trimmed%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                    val number = if (numberIndex != -1) cursor.getString(numberIndex) else null
                    if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                        matches.add(ContactMatch(name = name.trim(), phoneNumber = number.trim()))
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        // Deduplicate and rank: exact match first, then prefix, then contains
        val deduped = matches.distinctBy { it.name.lowercase() to it.phoneNumber.replace(Regex("[^0-9+]"), "") }
        return deduped.sortedWith(
            compareBy<ContactMatch> {
                when {
                    it.name.equals(trimmed, ignoreCase = true) -> 0
                    it.name.startsWith(trimmed, ignoreCase = true) -> 1
                    else -> 2
                }
            }.thenBy { it.name }
        )
    }

    /**
     * Resolves contact query to a structured search result for voice/assistant responses.
     */
    fun resolveContactSummary(context: Context, query: String): ContactSearchResult {
        val trimmed = query.trim()
        Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_SEARCHING, trimmed, 0)

        if (testContacts == null && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_FAILED, trimmed, 0)
            return ContactSearchResult.PermissionNeeded
        }

        val matches = searchContacts(context, trimmed)
        val result = when {
            matches.isEmpty() -> {
                Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_NOT_FOUND, trimmed, 0)
                ContactSearchResult.NotFound(trimmed)
            }
            matches.size == 1 -> {
                Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_RESOLVED, trimmed, 1)
                ContactSearchResult.SingleMatch(matches.first())
            }
            else -> {
                // If there is an exact case-insensitive match and it's unique (single phone number)
                val exactMatches = matches.filter { it.name.equals(trimmed, ignoreCase = true) }
                if (exactMatches.size == 1) {
                    Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_RESOLVED, trimmed, 1)
                    ContactSearchResult.SingleMatch(exactMatches.first())
                } else {
                    // Ambiguous: multiple contacts or multiple phone numbers for the same contact
                    Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_AMBIGUOUS, trimmed, matches.size)
                    ContactSearchResult.MultipleMatches(trimmed, matches)
                }
            }
        }
        return result
    }

    /**
     * Unambiguously resolves a contact query for an action (Call, SMS, WhatsApp).
     * If multiple contacts match and none is uniquely exact, stops safely with Multiple.
     */
    fun resolveContactForAction(context: Context, query: String): ContactActionResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_NOT_FOUND, query, 0)
            return ContactActionResult.NotFound(query)
        }

        // If the query is already an explicit phone number with at least 3 digits
        val digitsOnly = trimmed.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 7 && digitsOnly.length == trimmed.replace(" ", "").replace("-", "").length) {
            Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_RESOLVED, trimmed, 1)
            return ContactActionResult.Resolved(ContactMatch(name = trimmed, phoneNumber = trimmed))
        }

        return when (val summary = resolveContactSummary(context, trimmed)) {
            is ContactSearchResult.PermissionNeeded -> {
                Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_FAILED, trimmed, 0)
                ContactActionResult.PermissionNeeded
            }
            is ContactSearchResult.NotFound -> {
                Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_NOT_FOUND, trimmed, 0)
                ContactActionResult.NotFound(trimmed)
            }
            is ContactSearchResult.SingleMatch -> {
                Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_RESOLVED, trimmed, 1)
                ContactActionResult.Resolved(summary.contact)
            }
            is ContactSearchResult.MultipleMatches -> {
                Phase7DiagnosticManager.updateContactsState(ContactsLookupState.CONTACT_AMBIGUOUS, trimmed, summary.matches.size)
                ContactActionResult.Multiple(trimmed, summary.matches)
            }
        }
    }
}
