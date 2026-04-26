package com.zakazky.app.common.models

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

@OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
object SupabaseManager {
    val client = createSupabaseClient(
        supabaseUrl = "https://xwcbdfudpgyxrkllipxk.supabase.co",
        supabaseKey = "sb_publishable_ZTuKH4LY1CGUez8msu_-og_oeoK2G3e"
    ) {
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        })
        install(Postgrest)
        // Realtime WebSocket odstraněn — způsoboval nepřetržitý scroll jank na iOS.
        // Realtime automaticky otevírá WebSocket a posílá keepalive každé 2-3s na main threadu.
        // Používáme vlastní polling (každých 30s) — Realtime nepotřebujeme.
        // install(Realtime)

        httpConfig {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 60000L
                connectTimeoutMillis = 60000L
                socketTimeoutMillis = 60000L
            }
        }
    }
}
