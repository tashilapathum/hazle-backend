package me.tashila.config

object AppConfig {
    fun loadSupabaseConfig(): SupabaseConfig {
        val supabaseUrl = System.getenv("SUPABASE_URL")
            ?: throw IllegalArgumentException("SUPABASE_URL environment variable not set")
        val supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY")
            ?: throw IllegalArgumentException("SUPABASE_ANON_KEY environment variable not set")
        val supabaseJwtSecret = System.getenv("SUPABASE_JWT_SECRET")
            ?: throw IllegalArgumentException("SUPABASE_JWT_SECRET environment variable not set")
        val supabaseServiceRoleKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY")
            ?: throw IllegalArgumentException("SUPABASE_SERVICE_ROLE_KEY environment variable not set")

        return SupabaseConfig(supabaseUrl, supabaseServiceRoleKey, supabaseJwtSecret, supabaseAnonKey)
    }
}