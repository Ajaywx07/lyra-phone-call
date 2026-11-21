package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CloudContactBackup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val label: String = "Mobile",
    val priorityContact: Boolean = false,
    val ownerContact: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("name", name.take(120).ifBlank { "Unknown" })
        put("phoneNumber", phoneNumber.take(40))
        put("label", label.take(40).ifBlank { "Mobile" })
        put("priorityContact", priorityContact)
        put("ownerContact", ownerContact)
        put("createdAt", createdAt ?: FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}
