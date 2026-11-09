package com.example.myapplication

import com.google.firebase.Timestamp

data class Notification(
    val id: String = "",
    val description: String = "",
    val from: String = "",
    val status: Int = 0,
    val created_at: Timestamp? = null
)