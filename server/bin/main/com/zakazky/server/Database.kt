package com.zakazky.server

import com.zakazky.app.common.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.Base64

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

object UsersTable : Table("users") {
    val id = varchar("id", 100)
    val name = varchar("name", 100)
    val email = varchar("email", 100)
    val pin = varchar("pin", 20)
    val photoUrl = text("photoUrl").nullable()
    val role = varchar("role", 50)
    val totalHoursLogged = double("totalHoursLogged").default(0.0)
    val isActive = bool("isActive").default(true)
    val phone = varchar("phone", 50)

    override val primaryKey = PrimaryKey(id)
}

object TasksTable : Table("tasks") {
    val id = varchar("id", 100)
    val title = text("title")
    val brand = text("brand")
    val customerName = text("customerName")
    val customerPhone = text("customerPhone")
    val customerEmail = text("customerEmail")
    val customerAddress = text("customerAddress")
    val spz = text("spz")
    val vin = text("vin")
    val description = text("description")
    val createdBy = text("createdBy")
    val assignedTo = text("assignedTo").nullable()
    val status = varchar("status", 50)
    val photoUrls = text("photoUrls") // JSON array of strings
    val taskImages = text("taskImages") // JSON array of ByteArray base64 (or URLs)
    val localPhotos = text("localPhotos") // JSON array of ByteArray base64 (or URLs)
    val attachedDocuments = text("attachedDocuments") // JSON
    val timeLogs = text("timeLogs") // JSON
    val electricTimeLogs = text("electricTimeLogs") // JSON
    val reworks = text("reworks") // JSON
    val vehicleKm = integer("vehicleKm").nullable()
    val invoiceItems = text("invoiceItems") // JSON
    val mechanicWorkPrice = double("mechanicWorkPrice").default(0.0)
    val electricWorkPrice = double("electricWorkPrice").default(0.0)
    val mechanicHourlyRate = double("mechanicHourlyRate").default(0.0)
    val electricHourlyRate = double("electricHourlyRate").default(0.0)
    val isInvoiceClosed = bool("isInvoiceClosed").default(false)
    val createdAt = long("createdAt")
    val updatedAt = long("updatedAt")
    val readAt = long("readAt").nullable()
    val startedAt = long("startedAt").nullable()
    val completedAt = long("completedAt").nullable()
    val isDeleted = bool("isDeleted").default(false)
    val deletedAt = long("deletedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AppDatabase {
    fun init() {
        val dbFile = File("data/zempro.db")
        dbFile.parentFile.mkdirs()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(UsersTable, TasksTable)
            
            // Default admin if missing
            if (UsersTable.selectAll().empty()) {
                UsersTable.insert {
                    it[id] = "admin1"
                    it[name] = "Michal"
                    it[email] = "sef@zempro.cz"
                    it[pin] = "0000"
                    it[role] = Role.ADMIN.name
                    it[isActive] = true
                    it[phone] = ""
                }
            }
        }
    }

    fun getAllTasks(): List<Task> {
        return transaction {
            TasksTable.selectAll().map { row ->
                Task(
                    id = row[TasksTable.id],
                    title = row[TasksTable.title],
                    brand = row[TasksTable.brand],
                    customerName = row[TasksTable.customerName],
                    customerPhone = row[TasksTable.customerPhone],
                    customerEmail = row[TasksTable.customerEmail],
                    customerAddress = row[TasksTable.customerAddress],
                    spz = row[TasksTable.spz],
                    vin = row[TasksTable.vin],
                    description = row[TasksTable.description],
                    createdBy = row[TasksTable.createdBy],
                    assignedTo = row[TasksTable.assignedTo],
                    status = TaskStatus.valueOf(row[TasksTable.status]),
                    photoUrls = json.decodeFromString(row[TasksTable.photoUrls]),
                    taskImages = emptyList(), // WE NEVER SEND HEAVY IMAGES IN LIST
                    localPhotos = emptyList(), // WE NEVER SEND HEAVY IMAGES IN LIST
                    attachedDocuments = json.decodeFromString(row[TasksTable.attachedDocuments]),
                    timeLogs = json.decodeFromString(row[TasksTable.timeLogs]),
                    electricTimeLogs = json.decodeFromString(row[TasksTable.electricTimeLogs]),
                    reworks = json.decodeFromString(row[TasksTable.reworks]),
                    vehicleKm = row[TasksTable.vehicleKm],
                    invoiceItems = json.decodeFromString(row[TasksTable.invoiceItems]),
                    mechanicWorkPrice = row[TasksTable.mechanicWorkPrice],
                    electricWorkPrice = row[TasksTable.electricWorkPrice],
                    mechanicHourlyRate = row[TasksTable.mechanicHourlyRate],
                    electricHourlyRate = row[TasksTable.electricHourlyRate],
                    isInvoiceClosed = row[TasksTable.isInvoiceClosed],
                    createdAt = row[TasksTable.createdAt],
                    updatedAt = row[TasksTable.updatedAt],
                    readAt = row[TasksTable.readAt],
                    startedAt = row[TasksTable.startedAt],
                    completedAt = row[TasksTable.completedAt],
                    isDeleted = row[TasksTable.isDeleted],
                    deletedAt = row[TasksTable.deletedAt]
                )
            }
        }
    }

    fun upsertTasks(tasks: List<Task>) {
        val uploadsDir = File("data/uploads")
        uploadsDir.mkdirs()

        transaction {
            tasks.forEach { task ->
                // PŘEVOD FOTEK Z BASE64 NA SOUBORY NA DISKU:
                // To zajistí, že databáze neroste do nesmyslných rozměrů.
                val existingPhotoUrls = task.photoUrls.toMutableList()
                
                // Uložíme všechny nové fotky, které přišly z klienta v localPhotos
                task.localPhotos.forEachIndexed { index, bytes ->
                    if (bytes.isNotEmpty()) {
                        val fileName = "${task.id}_local_${System.currentTimeMillis()}_$index.jpg"
                        val file = File(uploadsDir, fileName)
                        file.writeBytes(bytes)
                        existingPhotoUrls.add("api/uploads/$fileName")
                    }
                }
                
                // Uložíme taskImages
                task.taskImages.forEachIndexed { index, bytes ->
                    if (bytes.isNotEmpty()) {
                        val fileName = "${task.id}_task_${System.currentTimeMillis()}_$index.jpg"
                        val file = File(uploadsDir, fileName)
                        file.writeBytes(bytes)
                        existingPhotoUrls.add("api/uploads/$fileName")
                    }
                }

                // Pokud úkol existuje, updatni, jinak insert. 
                // V Exposed pro SQLite můžeme použít replace()
                TasksTable.replace {
                    it[id] = task.id
                    it[title] = task.title
                    it[brand] = task.brand
                    it[customerName] = task.customerName
                    it[customerPhone] = task.customerPhone
                    it[customerEmail] = task.customerEmail
                    it[customerAddress] = task.customerAddress
                    it[spz] = task.spz
                    it[vin] = task.vin
                    it[description] = task.description
                    it[createdBy] = task.createdBy
                    it[assignedTo] = task.assignedTo
                    it[status] = task.status.name
                    // Uložíme nové URLs fotek!
                    it[photoUrls] = json.encodeToString(existingPhotoUrls.distinct())
                    it[taskImages] = "[]" // Vždy prázdné
                    it[localPhotos] = "[]" // Vždy prázdné
                    it[attachedDocuments] = json.encodeToString(task.attachedDocuments)
                    it[timeLogs] = json.encodeToString(task.timeLogs)
                    it[electricTimeLogs] = json.encodeToString(task.electricTimeLogs)
                    it[reworks] = json.encodeToString(task.reworks)
                    it[vehicleKm] = task.vehicleKm
                    it[invoiceItems] = json.encodeToString(task.invoiceItems)
                    it[mechanicWorkPrice] = task.mechanicWorkPrice
                    it[electricWorkPrice] = task.electricWorkPrice
                    it[mechanicHourlyRate] = task.mechanicHourlyRate
                    it[electricHourlyRate] = task.electricHourlyRate
                    it[isInvoiceClosed] = task.isInvoiceClosed
                    it[createdAt] = task.createdAt
                    it[updatedAt] = task.updatedAt
                    it[readAt] = task.readAt
                    it[startedAt] = task.startedAt
                    it[completedAt] = task.completedAt
                    it[isDeleted] = task.isDeleted
                    it[deletedAt] = task.deletedAt
                }
            }
        }
    }

    fun getAllUsers(): List<User> {
        return transaction {
            UsersTable.selectAll().map { row ->
                User(
                    id = row[UsersTable.id],
                    name = row[UsersTable.name],
                    email = row[UsersTable.email],
                    pin = row[UsersTable.pin],
                    photoUrl = row[UsersTable.photoUrl],
                    role = Role.valueOf(row[UsersTable.role]),
                    totalHoursLogged = row[UsersTable.totalHoursLogged],
                    isActive = row[UsersTable.isActive],
                    phone = row[UsersTable.phone]
                )
            }
        }
    }

    fun upsertUsers(users: List<User>) {
        transaction {
            users.forEach { user ->
                UsersTable.replace {
                    it[id] = user.id
                    it[name] = user.name
                    it[email] = user.email
                    it[pin] = user.pin
                    it[photoUrl] = user.photoUrl
                    it[role] = user.role.name
                    it[totalHoursLogged] = user.totalHoursLogged
                    it[isActive] = user.isActive
                    it[phone] = user.phone
                }
            }
        }
    }
}
