package com.picflick.app.data

import com.google.firebase.firestore.PropertyName

/**
 * Photo report submitted by users for moderation
 */
data class Report(
    @PropertyName("id")
    val id: String = "",

    @PropertyName("flickId")
    val flickId: String = "",

    @PropertyName("reporterId")
    val reporterId: String = "",

    @PropertyName("reason")
    val reason: String = "",

    @PropertyName("details")
    val details: String = "",

    @PropertyName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @PropertyName("status")
    val status: String = "pending", // pending, reviewed, action_taken, dismissed

    @PropertyName("pushSentToDevs")
    val pushSentToDevs: Boolean = false
)
