package com.trainingtracker.app.data.remote

import com.trainingtracker.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Lazily builds the Supabase client from credentials injected via local.properties (see
 * app/build.gradle.kts) — never hardcoded, per project security rules.
 *
 * Sync is a pure backup mechanism (requirements.txt 3i): if no credentials are configured, the
 * app must keep working fully offline, so callers treat a null client as "sync unavailable" and
 * silently skip, never crash.
 */
object SupabaseClientProvider {
    val client by lazy {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
            ) {
                install(Postgrest)
            }
        }
    }
}
