package me.tashila.data

const val MAX_EMAIL_LENGTH = 254
const val MAX_MESSAGE_LENGTH = 20000

val SUPABASE_URL = System.getenv("SUPABASE_URL")
    ?: throw IllegalArgumentException("SUPABASE_URL environment variable not set")
val SUPABASE_ANON_KEY = System.getenv("SUPABASE_ANON_KEY")
    ?: throw IllegalArgumentException("SUPABASE_ANON_KEY environment variable not set")