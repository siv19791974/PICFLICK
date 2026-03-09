package com.picflick.app.data

import com.google.firebase.firestore.PropertyName

/**
 * User feedback submitted through Contact/Support screen
 */
data class Feedback(
    @PropertyName("id")
    val id: String = "",
    
    @PropertyName("userId")
    val userId: String = "",
    
    @PropertyName("userName")
    val userName: String = "",
    
    @PropertyName("userEmail")
    val userEmail: String = "",
    
    @PropertyName("subject")
    val subject: String = "",
    
    @PropertyName("message")
    val message: String = "",
    
    @PropertyName("category")
    val category: String = "GENERAL", // GENERAL, BUG, FEATURE, BILLING, OTHER
    
    @PropertyName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @PropertyName("status")
    val status: String = "NEW", // NEW, IN_PROGRESS, RESOLVED, CLOSED
    
    @PropertyName("appVersion")
    val appVersion: String = "",
    
    @PropertyName("deviceInfo")
    val deviceInfo: String = ""
)