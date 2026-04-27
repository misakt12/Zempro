package com.zakazky.app.common.models

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.util.Base64

object Base64ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Base64ByteArray", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }
    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.getDecoder().decode(decoder.decodeString())
    }
}

object ByteArrayListSerializer : KSerializer<List<ByteArray>> {
    private val delegate = ListSerializer(Base64ByteArraySerializer)
    override val descriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<ByteArray>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<ByteArray> = delegate.deserialize(decoder)
}

@Serializable
enum class Role {
    ADMIN, EMPLOYEE
}

@Serializable
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val pin: String = "1234",
    val photoUrl: String? = null,
    val role: Role = Role.EMPLOYEE,
    val totalHoursLogged: Double = 0.0,
    val isActive: Boolean = true,
    val phone: String = ""
)

@Serializable
data class UserNotification(
    val id: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val targetUserId: String = "",  // Komu je notifikace určena (prázdné = všem)
    val shouldPlaySound: Boolean = false  // Přehraje zvuk při doručení
)

@Serializable
data class AttachedDocument(
    val id: String = "",
    val name: String = "",
    @Serializable(with = Base64ByteArraySerializer::class)
    val data: ByteArray = ByteArray(0)  // Výchozí hodnota: odolnost vůči poškozené/chybějící hodnotě v JSON
)

@Serializable
enum class TaskStatus {
    AVAILABLE, IN_PROGRESS, COMPLETED
}

val TaskStatus.displayName: String
    get() = when (this) {
        TaskStatus.AVAILABLE -> "K dispozici"
        TaskStatus.IN_PROGRESS -> "Probíhá"
        TaskStatus.COMPLETED -> "Dokončeno"
    }

@Serializable
data class TimeLog(
    val id: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val hours: Double = 0.0,
    val note: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class ReworkLog(
    val id: String = "",
    val solverId: String = "",
    val solverName: String = "", 
    val solverHours: Double = 0.0,
    val guiltyId: String = "",
    val guiltyName: String = "", 
    val penaltyHours: Double = 0.0,
    val note: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class InvoiceItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0
)

@Serializable
data class Task(
    val id: String = "",
    val title: String = "",
    val brand: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val customerAddress: String = "",
    val spz: String = "",
    val vin: String = "",
    val description: String = "",
    val createdBy: String = "",
    val assignedTo: String? = null,
    val status: TaskStatus = TaskStatus.AVAILABLE,
    val photoUrls: List<String> = emptyList(),
    @Serializable(with = ByteArrayListSerializer::class)
    val taskImages: List<ByteArray> = emptyList(),
    @Serializable(with = ByteArrayListSerializer::class)
    val localPhotos: List<ByteArray> = emptyList(),
    val attachedDocuments: List<AttachedDocument> = emptyList(),
    val timeLogs: List<TimeLog> = emptyList(), // Odpracovaný čas mechanika
    val electricTimeLogs: List<TimeLog> = emptyList(), // Odpracovaný čas elektrikáře
    val reworks: List<ReworkLog> = emptyList(),
    val vehicleKm: Int? = null,
    val invoiceItems: List<InvoiceItem> = emptyList(),
    val mechanicWorkPrice: Double = 0.0,
    val electricWorkPrice: Double = 0.0,
    val mechanicHourlyRate: Double = 0.0,  // Hodinová sazba mechanika (Kč/hod) — šéf zadá v historii
    val electricHourlyRate: Double = 0.0,  // Hodinová sazba elektrikáře (Kč/hod) — šéf zadá v historii
    val isInvoiceClosed: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val readAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val isDeleted: Boolean = false,   // true = zakázka je v koši
    val deletedAt: Long? = null       // čas přesunutí do koše
)

// Dynamická obalová třída pro CRM (Zákazníci a Garáž) - Neukládá se přímo do Supabase!
data class CustomerProfile(
    val name: String,
    val phone: String,
    val email: String,
    val address: String,
    val historyTasks: List<Task> = emptyList(),
    val garageVehicles: List<Task> = emptyList() // Na každé unikátní auto (podle SPZ/VIN) jedna reprezentativní zakázka
)
