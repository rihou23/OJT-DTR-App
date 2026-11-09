package com.example.myapplication

data class LeaderboardItem(
    val userId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val section: String = "",
    val profileImageUrl: String = "",
    var totalHours: Double = 0.0
)