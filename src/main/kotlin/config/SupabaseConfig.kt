package me.tashila.config

data class SupabaseConfig(
    val url: String,
    val anonKey: String,
    val jwtSecret: String,
    val serviceRoleKey: String
)