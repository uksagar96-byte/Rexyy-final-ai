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

    /**
     * Resolves a phone number to a Contact Name using Android ContactsContract.PhoneLookup
     */
    fun resolveCallerName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
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
     */
    fun findPhoneNumberByName(context: Context, contactName: String): String? {
        val result = searchContacts(context, contactName)
        return result.firstOrNull()?.phoneNumber
    }

    /**
     * Searches all contacts matching the given query string with ranking (exact match > prefix > contains).
     */
    fun searchContacts(context: Context, query: String): List<ContactMatch> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactSearchResult.PermissionNeeded
        }

        val matches = searchContacts(context, query)
        return when {
            matches.isEmpty() -> ContactSearchResult.NotFound(query)
            matches.size == 1 -> ContactSearchResult.SingleMatch(matches.first())
            else -> {
                // If there is an exact case-insensitive match, prioritize it
                val exactMatch = matches.firstOrNull { it.name.equals(query.trim(), ignoreCase = true) }
                if (exactMatch != null && matches.count { it.name.equals(query.trim(), ignoreCase = true) } == 1) {
                    ContactSearchResult.SingleMatch(exactMatch)
                } else {
                    ContactSearchResult.MultipleMatches(query, matches)
                }
            }
        }
    }
}
