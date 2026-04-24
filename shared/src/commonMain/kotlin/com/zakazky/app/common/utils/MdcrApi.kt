package com.zakazky.app.common.utils

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

object MdcrApi {
    private val client = HttpClient()
    private const val API_KEY = "YV00VZreJ5GnkU5p9kAWjtxnv43GuwHO"

    data class VehicleData(
        val znacka: String,
        val model: String,
        val rok: String,
        val motor: String,
        val vin: String
    )

    suspend fun getVehicleData(vin: String): Result<VehicleData> = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.get("https://api.dataovozidlech.cz/api/vehicletechnicaldata/v2") {
                url {
                    parameters.append("vin", vin.uppercase().trim())
                }
                header("API_KEY", API_KEY)
                header(HttpHeaders.Accept, "application/json")
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext Result.failure(Exception("API_ERROR: ${response.status}"))
            }

            val bodyText = response.bodyAsText()
            val root = Json.parseToJsonElement(bodyText).jsonObject
            val status = root["Status"]?.jsonPrimitive?.intOrNull

            if (status != 1) {
                return@withContext Result.failure(Exception("Vozidlo nenalezeno (Status: $status)"))
            }

            val dataObj = root["Data"]?.jsonObject
            if (dataObj == null) {
                return@withContext Result.failure(Exception("Prázdná data vozidla"))
            }

            val znacka = dataObj["TovarniZnacka"]?.jsonPrimitive?.contentOrNull ?: ""
            val model = dataObj["ObchodniOznaceni"]?.jsonPrimitive?.contentOrNull ?: ""
            val rokVyrobyStr = dataObj["DatumPrvniRegistrace"]?.jsonPrimitive?.contentOrNull ?: "" 
            val rok = if (rokVyrobyStr.length >= 4) rokVyrobyStr.substring(0, 4) else ""
            val palivo = dataObj["Palivo"]?.jsonPrimitive?.contentOrNull ?: ""
            // Odlapime String i Double
            val objem = dataObj["MotorZdvihObjem"]?.jsonPrimitive?.contentOrNull ?: "0"
            
            // Format motor
            val motorStr = if (objem.isNotBlank() && palivo.isNotBlank() && objem != "0") {
                val liters = (objem.toDoubleOrNull() ?: 0.0) / 1000.0
                val litersFormatted = if (liters > 0) (kotlin.math.round(liters * 10.0) / 10.0).toString() else ""
                val palivoType = when {
                    palivo.contains("BA", ignoreCase = true) -> "Benzín"
                    palivo.contains("NM", ignoreCase = true) -> "Nafta"
                    palivo.contains("EL", ignoreCase = true) -> "Elektro"
                    else -> palivo
                }
                "$litersFormatted $palivoType".trim()
            } else {
                "Nezjištěno"
            }

            Result.success(VehicleData(
                znacka = znacka.split(" ").map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }.joinToString(" "),
                model = model,
                rok = rok,
                motor = motorStr,
                vin = vin.uppercase()
            ))

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
