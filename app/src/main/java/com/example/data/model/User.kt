package com.example.data.model

data class User(
    val id: String,
    val name: String,
    val photoUrl: String = "",
    val bio: String = "",
    val statusText: String = "Online",
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)
