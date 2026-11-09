package com.example.myapplication

data class StudentItem(
    val userId: String = "",
    val firstname: String = "",
    val lastname: String = "",
    val section: String = "",
    val profile_image_url: String = "",
    var totalHours: Double = 0.0,
    var isSameDept: Boolean = true
)

data class SectionItem(
    val sectionName: String = "",
    var totalHours: Double = 0.0
)