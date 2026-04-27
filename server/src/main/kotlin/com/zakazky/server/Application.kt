package com.zakazky.server

import com.zakazky.app.common.models.Task
import com.zakazky.app.common.models.User
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    // Inicializace databáze při startu
    AppDatabase.init()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(CallLogging)
    
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }

    routing {
        get("/") {
            call.respondText("Zempro API Server is running!")
        }

        route("/api") {
            // --- TASKS ---
            get("/tasks") {
                try {
                    val tasks = AppDatabase.getAllTasks()
                    call.respond(tasks)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/tasks/upsert") {
                try {
                    // Ktor automaticky deserializuje JSON na List<Task>
                    val chunk = call.receive<List<Task>>()
                    AppDatabase.upsertTasks(chunk)
                    call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // --- USERS ---
            get("/users") {
                try {
                    val users = AppDatabase.getAllUsers()
                    call.respond(users)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/users/upsert") {
                try {
                    val users = call.receive<List<User>>()
                    AppDatabase.upsertUsers(users)
                    call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // --- UPLOADS (Získání obrázků) ---
            get("/uploads/{filename}") {
                val filename = call.parameters["filename"]
                if (filename == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing filename")
                    return@get
                }
                val file = File("data/uploads", filename)
                if (file.exists() && !file.isDirectory) {
                    call.respondFile(file)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
