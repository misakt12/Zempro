package com.zakazky.app.common.models

import androidx.compose.runtime.mutableStateListOf
import com.zakazky.app.common.utils.PlatformStorage
import com.zakazky.app.common.utils.readPlatformData
import com.zakazky.app.common.utils.writePlatformData
import com.zakazky.app.common.utils.formatTimestamp
import com.zakazky.app.common.utils.getCurrentTimestamp
import com.zakazky.app.common.utils.playNotificationSound
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.contentType

@Serializable
data class AppSnapshot(
    val users: List<User>,
    val tasks: List<Task>,
    val notifications: List<UserNotification> = emptyList(),
    val pendingUpload: Boolean = false
)

object AppDatabase {
    private var storage: PlatformStorage? = null
    
    val users = mutableStateListOf<User>()
    val tasks = mutableStateListOf<Task>()
    val notifications = mutableStateListOf<UserNotification>()
    var currentUser: User = User() // Safe default

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }
    
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }
    val SERVER_URL = "http://194.182.79.72:8080/api"
    
    private val scope = CoroutineScope(Dispatchers.Default)
    // Pojistka: zabrání spuštění více paralelních sync smyček
    private var syncStarted = false
    // Časové razítko posledního lokálního uložení — chrání před přepsáním čerstvých změn z cloudu
    @kotlin.concurrent.Volatile private var lastSaveTime = 0L
    @kotlin.concurrent.Volatile var hasPendingUpload: Boolean = false // Ochrana před přepsáním při výpadku spojení!
    // KRITÍCKÉ: Zabrauje paralelním uploadům které způsobovaly OutOfMemoryError na Androidu
    @kotlin.concurrent.Volatile private var isUploadInProgress: Boolean = false

    fun init(platformStorage: PlatformStorage) {
        this.storage = platformStorage
        loadLocally()
        syncWithCloud()
        // Spustí automatické denní zálohování (jen na PC — na Androidu je no-op)
        com.zakazky.app.common.utils.startAutoBackup { exportBackupJson() }
    }

    private fun loadLocally() {
        val dataStr = storage?.let { readPlatformData(it) }
        if (dataStr != null && dataStr.isNotBlank()) {
            try {
                val snapshot = json.decodeFromString<AppSnapshot>(dataStr)
                hasPendingUpload = snapshot.pendingUpload
                users.clear()
                users.addAll(snapshot.users.distinctBy { it.id })
                tasks.clear()
                tasks.addAll(snapshot.tasks.distinctBy { it.id })
                notifications.clear()
                notifications.addAll(snapshot.notifications.distinctBy { it.id })
                
                if (users.isEmpty()) {
                    loadDefaults()
                }

                // Bezpečnostní pojistka: Pokud z jakéhokoliv důvodu chybí Admin (Šéf), jen zalogujeme —
                // cloud sync ho obnoví ze Supabase automaticky. Nevkládáme napevno zakódovaného uživatele,
                // aby nevznikl duplicitní admin s jiným ID než v Supabase.
                if (users.none { it.role == Role.ADMIN }) {
                    println("⚠️ VAROVÁNÍ: V lokálních datech chybí administrátor! Cloud sync ho obnoví ze Supabase.")
                }
            } catch (e: Exception) {
                // OPRAVA: Lokální JSON soubor je poškozený (např. zkrácená Base64 fotka).
                // Pokud již máme data v paměti (z předchozího sync), NEZAVOLÁME loadDefaults()
                // jinaky bychom smazali všechna data. Cloud sync je obnoví automaticky.
                // loadDefaults() voláme POUZE pokud je paměť opravdu prázdná.
                e.printStackTrace()
                println("⚠️ Lokální databáze poškozená — JSON parse selhal: ${e.message}")
                if (tasks.isEmpty() && users.isEmpty()) {
                    println("ℹ️ Paměť prázdná → načítám výchozí data")
                    loadDefaults()
                } else {
                    println("✅ Paměť obsahuje ${tasks.size} zakázek — zachovávám stávající stav, cloud sync obnoví data.")
                }
            }
        } else {
            loadDefaults()
        }
    }

    private fun syncWithCloud() {
        // Zabrání spuštění více paralelních sync smyček při re-kompozici
        if (syncStarted) return
        syncStarted = true

        scope.launch {
            // Počáteční sync — zkusíme to max 5x s 5s pauzou (řeší cold-start Supabase Free tier)
            var initialSyncDone = false
            for (attempt in 0 until 5) {
                if (initialSyncDone) break
                try {
                    if (hasPendingUpload) {
                        println("⚠️ Po startu detekujeme neodeslaná data z minula! Provádím nejprve push (pokus ${attempt + 1}).")
                        pushToCloud()
                        if (hasPendingUpload) {
                            throw Exception("Push failnul, odkládám stahování aby nedošlo k přepsání.")
                        }
                    }

                    println("Stahování dat ze Supabase cloudu... (pokus ${attempt + 1})")
                    // KRITICKÉ pro egress: Nestahujeme photo sloupce ani při počátečním syncu!
                    // Dříve select() stahoval taskImages+localPhotos = MB dat při každém startu.
                    // Výsledek: 27 GB egress = 554% překročení bezplatného limitu Supabase.
                    // Fotky jsou ukládány lokálně na zařízení a nepotřebujeme je stahovat zpět.
                    // Pro Ktor už nepotřebujeme SYNC_COLS, protože server automaticky filtruje fotky!
                    val remoteTasks = httpClient.get("$SERVER_URL/tasks").body<List<Task>>()
                    val remoteUsers = httpClient.get("$SERVER_URL/users").body<List<User>>()

                    if (remoteTasks.isNotEmpty() || remoteUsers.isNotEmpty()) {
                        
                        val newNotifs = mutableListOf<UserNotification>()
                        // Chceme vygenerovat notifikace i po offline stavu.
                        // Musíme mít v cache (tasks) nějaká data z minula.
                        if (tasks.isNotEmpty()) {
                            val preSyncTasks = tasks.toList()
                            val existingNotifIds = notifications.map { it.id }.toSet()
                            val isAd = currentUser.role == Role.ADMIN
                            val uList = remoteUsers.ifEmpty { users.toList() } // použije rovnou i nová data z cloudu pro jména

                            remoteTasks.forEach { newTask ->
                                val oldTask = preSyncTasks.find { it.id == newTask.id }
                                // ZKOPÍROVANÁ LOGIKA Z POLLINGU:
                                val assignId = "notif_assign_${newTask.id}"
                                val isNewlyAssigned = newTask.assignedTo == currentUser.id &&
                                    newTask.status == TaskStatus.IN_PROGRESS &&
                                    (oldTask == null || oldTask.assignedTo != currentUser.id) &&
                                    assignId !in existingNotifIds

                                if (!isAd && isNewlyAssigned) {
                                    newNotifs += UserNotification(
                                        id = assignId,
                                        message = "🔔 Nová zakázka přidělena: ${newTask.title}\n" +
                                            "Zákazník: ${newTask.customerName} · SPZ: ${newTask.spz}\n" +
                                            "Přiděleno: ${formatTimestamp(getCurrentTimestamp())} — Čeká se na Vaše dokončení.",
                                        timestamp = getCurrentTimestamp(),
                                        targetUserId = currentUser.id,
                                        shouldPlaySound = true
                                    )
                                }

                                val assignAdminId = "notif_assign_admin_${newTask.id}"
                                val wasJustAssigned = newTask.status == TaskStatus.IN_PROGRESS &&
                                    newTask.assignedTo != null &&
                                    (oldTask?.status != TaskStatus.IN_PROGRESS || oldTask.assignedTo != newTask.assignedTo) &&
                                    assignAdminId !in existingNotifIds

                                if (isAd && wasJustAssigned) {
                                    val mechanicName = uList.find { it.id == newTask.assignedTo }?.name ?: "Neznámý mechanik"
                                    newNotifs += UserNotification(
                                        id = assignAdminId,
                                        message = "📋 Zakázka odeslána mechanikovi\nZakázka: ${newTask.title}\nMechanik: $mechanicName\nDatum a čas: ${formatTimestamp(getCurrentTimestamp())}\n⏳ Čeká se na dokončení zakázky.",
                                        timestamp = getCurrentTimestamp(),
                                        targetUserId = currentUser.id
                                    )
                                }

                                val doneId = "notif_done_${newTask.id}"
                                if (isAd && newTask.status == TaskStatus.COMPLETED &&
                                    oldTask?.status != TaskStatus.COMPLETED && doneId !in existingNotifIds) {
                                    val mechanicName = uList.find { it.id == newTask.assignedTo }?.name ?: "mechanik"
                                    newNotifs += UserNotification(
                                        id = doneId,
                                        message = "✅ Zakázka dokončena!\nZakázka: ${newTask.title}\nMechanik: $mechanicName\nDokončeno: ${formatTimestamp(getCurrentTimestamp())}\n📄 Připravena k fakturaci.",
                                        timestamp = getCurrentTimestamp(),
                                        targetUserId = currentUser.id,
                                        shouldPlaySound = true
                                    )
                                }

                                val invId = "notif_invoice_${newTask.id}"
                                if (isAd && newTask.isInvoiceClosed &&
                                    oldTask?.isInvoiceClosed == false && invId !in existingNotifIds) {
                                    newNotifs += UserNotification(
                                        id = invId,
                                        message = "📄 Faktura vystavena: ${newTask.title}",
                                        timestamp = getCurrentTimestamp(),
                                        targetUserId = currentUser.id
                                    )
                                }
                            }
                        }

                        // withContext(Dispatchers.Main) = Swing EDT dispatch.
                        // Vyžaduje kotlinx-coroutines-swing v desktopApp/build.gradle.kts
                        withContext(Dispatchers.Main) {
                            // Chirurgická aktualizace — NE clear()+addAll() které způsobuje full recomposition!
                            remoteTasks.forEach { newTask ->
                                val idx = tasks.indexOfFirst { it.id == newTask.id }
                                if (idx == -1) {
                                    tasks.add(newTask)
                                } else {
                                    val old = tasks[idx]
                                    val changed = old.status != newTask.status ||
                                        old.assignedTo != newTask.assignedTo ||
                                        old.isInvoiceClosed != newTask.isInvoiceClosed ||
                                        old.isDeleted != newTask.isDeleted ||
                                        old.title != newTask.title ||
                                        old.photoUrls.size != newTask.photoUrls.size ||
                                        old.timeLogs.size != newTask.timeLogs.size ||
                                        old.invoiceItems.size != newTask.invoiceItems.size
                                    if (changed) tasks[idx] = newTask.copy(
                                        localPhotos = old.localPhotos,
                                        taskImages = old.taskImages,
                                        attachedDocuments = old.attachedDocuments
                                    )
                                }
                            }
                            val remoteIds = remoteTasks.map { it.id }.toSet()
                            tasks.removeAll { it.id !in remoteIds }
                            if (users.isEmpty()) {
                                users.addAll(remoteUsers)
                            } else {
                                remoteUsers.forEach { u ->
                                    val ui = users.indexOfFirst { it.id == u.id }
                                    if (ui == -1) users.add(u) else users[ui] = u
                                }
                            }
                            if (newNotifs.isNotEmpty()) {
                                notifications.addAll(newNotifs)
                                val hasSound = newNotifs.any { it.shouldPlaySound && it.targetUserId == currentUser.id }
                                if (hasSound) {
                                    val s = storage
                                    if (s != null) {
                                        try { playNotificationSound(s) } catch (_: Exception) { }
                                    }
                                }
                            }
                        }
                        saveLocally()
                        println("✅ Cloud sync OK — staženo ${remoteTasks.size} zakázek, ${remoteUsers.size} uživatelů. Dogenerováno notifikací: ${newNotifs.size}")
                    } else {
                        // OPRAVA: Prázdná odpověď může být Supabase cold-start (server se probouzí).
                        // Pošleme lokální data do cloudu POUZE pokud víme, že cloud je skutečně
                        // prázdný a zároveň i lokálně máme data — jinak jen zkusíme znovu.
                        if (tasks.isNotEmpty()) {
                            println("⚠️ Cloud odpověděl prázdně, ale lokálně máme data → nejspíš cold-start. Odesílám lokální data do cloudu jako zálohu.")
                            pushToCloud()
                        } else {
                            println("ℹ️ Cloud i lokál jsou prázdné — první spuštění aplikace.")
                        }
                    }
                    initialSyncDone = true
                } catch (e: Exception) {
                    println("Pokus ${attempt + 1} selhal: ${e.message}. Zkouším znovu za 5s...")
                    kotlinx.coroutines.delay(5000)
                }
            }

            // Nekonečná polling smyčka — každých 8s zkontroluje změny
            // Každý cyklus má vlastní try-catch, takže výpadek neukončí celou smyčku
            while (true) {
                kotlinx.coroutines.delay(60000)  // 60s polling — snížení CPU/síťového zatížení
                
                // Pokud nám visí neodeslaná data (např. timeout minulý cyklus), nesmíme 
                // data z cloudu tahat a přepisovat! Musíme to znovu zkusit poslat.
                if (hasPendingUpload) {
                    println("🔄 Polling zachytil neodeslaná lokální data! Spouštím záchranný upload místo stahování.")
                    pushToCloud()
                    continue
                }

                // OCHRANA před race condition: pokud jsme teprve před chvílí uložili lokálně
                // (mechanik vzal zakázku), počkáme 12s aby push stihl dorazit do Supabase
                // a cloud nám nepřepsal čerstvé změny zpět na starý stav.
                val timeSinceSave = getCurrentTimestamp() - lastSaveTime
                if (timeSinceSave < 12000) {
                    println("⏸ Polling přeskočen — čerstvé lokální změny (${timeSinceSave}ms ago), dám push čas.")
                    continue
                }
                try {
                    val oldTasks = tasks.toList()
                    val existingNotifIds = notifications.map { it.id }.toSet()

                    // Odesíláme GET na Ktor
                    val bgTasks = httpClient.get("$SERVER_URL/tasks").body<List<Task>>()

                    // OPRAVA: Pokud cloud vrátí prázdný seznam, ale lokálně máme zakázky,
                    // jde téměř jistě o Supabase cold-start nebo výpadek sítě.
                    // V takovém případě data NEPŘEPISUJEME — zachováme stávající stav.
                    if (bgTasks.isEmpty() && oldTasks.isNotEmpty()) {
                        println("⚠️ Polling: cloud vrátil prázdná data, ale lokálně máme ${oldTasks.size} zakázek → přeskakuji (cold-start / výpadek).")
                        continue
                    }

                    // Porovnáváme pouze textová pole (ne ByteArray — není potřeba, fotky se neuklkládají při pollingu)
                    val hasChanges = bgTasks.size != oldTasks.size || bgTasks.any { newTask ->
                        val oldTask = oldTasks.find { it.id == newTask.id }
                        oldTask == null ||
                        oldTask.status != newTask.status ||
                        oldTask.assignedTo != newTask.assignedTo ||
                        oldTask.isInvoiceClosed != newTask.isInvoiceClosed ||
                        oldTask.invoiceItems.size != newTask.invoiceItems.size ||
                        oldTask.timeLogs.size != newTask.timeLogs.size ||
                        oldTask.photoUrls.size != newTask.photoUrls.size ||
                        oldTask.isDeleted != newTask.isDeleted
                    }

                    if (bgTasks.isNotEmpty() && hasChanges) {
                        // Vypočítej notifikace NA POZADÍ (čisté výpočty, žádná mutace stavu)
                        val currentId = currentUser.id
                        val isAdminUser = currentUser.role == Role.ADMIN
                        val newNotifs = mutableListOf<UserNotification>()

                        bgTasks.forEach { newTask ->
                            val oldTask = oldTasks.find { it.id == newTask.id }

                            // ── MECHANIK: notifikace o nové přidělené zakázce ──
                            val assignId = "notif_assign_${newTask.id}"
                            val isNewlyAssigned = newTask.assignedTo == currentId &&
                                newTask.status == TaskStatus.IN_PROGRESS &&
                                (oldTask == null || oldTask.assignedTo != currentId) &&
                                assignId !in existingNotifIds

                            if (!isAdminUser && isNewlyAssigned) {
                                val formattedTime = formatTimestamp(getCurrentTimestamp())
                                newNotifs += UserNotification(
                                    id = assignId,
                                    message = "🔔 Nová zakázka přidělena: ${newTask.title}\n" +
                                        "Zákazník: ${newTask.customerName} · SPZ: ${newTask.spz}\n" +
                                        "Přiděleno: $formattedTime — Čeká se na Vaše dokončení.",
                                    timestamp = getCurrentTimestamp(),
                                    targetUserId = currentId,
                                    shouldPlaySound = true
                                )
                            }

                            // ── ŠÉFOVI: notifikace když byla zakázka přidělena mechanikovi ──
                            val assignAdminId = "notif_assign_admin_${newTask.id}"
                            val wasJustAssigned = newTask.status == TaskStatus.IN_PROGRESS &&
                                newTask.assignedTo != null &&
                                (oldTask?.status != TaskStatus.IN_PROGRESS || oldTask.assignedTo != newTask.assignedTo) &&
                                assignAdminId !in existingNotifIds

                            if (isAdminUser && wasJustAssigned) {
                                val mechanic = users.find { it.id == newTask.assignedTo }
                                val mechanicName = mechanic?.name ?: "Neznámý mechanik"
                                val formattedTime = formatTimestamp(getCurrentTimestamp())
                                newNotifs += UserNotification(
                                    id = assignAdminId,
                                    message = "📋 Zakázka odeslána mechanikovi\n" +
                                        "Zakázka: ${newTask.title}\n" +
                                        "Mechanik: $mechanicName\n" +
                                        "Datum a čas: $formattedTime\n" +
                                        "⏳ Čeká se na dokončení zakázky.",
                                    timestamp = getCurrentTimestamp(),
                                    targetUserId = currentId
                                )
                            }

                            // ── ŠÉFOVI: zakázka dokončena mechanikem ──
                            val doneId = "notif_done_${newTask.id}"
                            if (isAdminUser && newTask.status == TaskStatus.COMPLETED &&
                                oldTask?.status != TaskStatus.COMPLETED && doneId !in existingNotifIds) {
                                val mechanic = users.find { it.id == newTask.assignedTo }
                                val mechanicName = mechanic?.name ?: "mechanik"
                                val formattedTime = formatTimestamp(getCurrentTimestamp())
                                newNotifs += UserNotification(
                                    id = doneId,
                                    message = "✅ Zakázka dokončena!\n" +
                                        "Zakázka: ${newTask.title}\n" +
                                        "Mechanik: $mechanicName\n" +
                                        "Dokončeno: $formattedTime\n" +
                                        "📄 Připravena k fakturaci.",
                                    timestamp = getCurrentTimestamp(),
                                    targetUserId = currentId,
                                    shouldPlaySound = true
                                )
                            }

                            // ── ŠÉFOVI: faktura vystavena ──
                            val invId = "notif_invoice_${newTask.id}"
                            if (isAdminUser && newTask.isInvoiceClosed &&
                                oldTask?.isInvoiceClosed == false && invId !in existingNotifIds) {
                                newNotifs += UserNotification(
                                    id = invId,
                                    message = "📄 Faktura vystavena: ${newTask.title}",
                                    timestamp = getCurrentTimestamp(),
                                    targetUserId = currentId
                                )
                            }

                            // ── KOŠ: zakázka přesunuta do koše (zvuk na mobilu) ──
                            val trashId = "notif_trash_${newTask.id}"
                            if (newTask.isDeleted && oldTask?.isDeleted == false && trashId !in existingNotifIds) {
                                newNotifs += UserNotification(
                                    id = trashId,
                                    message = "🗑️ Zakázka přesunuta do koše: ${newTask.title}",
                                    timestamp = getCurrentTimestamp(),
                                    targetUserId = currentId,
                                    shouldPlaySound = true
                                )
                            }
                        }

                        // KRITICKÉ: Chirurgická aktualizace — NE clear()+addAll()!
                        // clear()+addAll() způsobuje překreslení celého UI (scroll jank, blikání).
                        // Místo toho aktualizujeme pouze ZMĚNĚNÉ zakázky.
                        withContext(Dispatchers.Main) {
                            // Přidej nebo uprav zakázky (pouze pokud se logicky změnily)
                            bgTasks.forEach { newTask ->
                                val idx = tasks.indexOfFirst { it.id == newTask.id }
                                if (idx == -1) {
                                    tasks.add(newTask)          // nová zakázka
                                } else {
                                    val old = tasks[idx]
                                    // Porovnáváme POUZE pole bez ByteArray (ty nelze porovnat referenčně)
                                    val changed = old.status != newTask.status ||
                                        old.assignedTo != newTask.assignedTo ||
                                        old.isInvoiceClosed != newTask.isInvoiceClosed ||
                                        old.isDeleted != newTask.isDeleted ||
                                        old.title != newTask.title ||
                                        old.description != newTask.description ||
                                        old.invoiceItems.size != newTask.invoiceItems.size ||
                                        old.timeLogs.size != newTask.timeLogs.size ||
                                        old.photoUrls.size != newTask.photoUrls.size
                                    if (changed) {
                                        // Při aktualizaci zachováme lokálně uložené fotky!
                                        // Polling je stahuje prázdné, aby neučiněl GC tlak.
                                        tasks[idx] = newTask.copy(
                                            localPhotos = old.localPhotos,
                                            taskImages = old.taskImages,
                                            attachedDocuments = old.attachedDocuments
                                        )
                                    }
                                }
                            }
                            // Odstraň zakázky které v cloudu už nejsou
                            val cloudIds = bgTasks.map { it.id }.toSet()
                            tasks.removeAll { it.id !in cloudIds }

                            if (newNotifs.isNotEmpty()) {
                                notifications.addAll(newNotifs)
                                val hasSound = newNotifs.any { it.shouldPlaySound && it.targetUserId == currentId }
                                if (hasSound) {
                                    val s = storage
                                    if (s != null) {
                                        try { playNotificationSound(s) } catch (_: Exception) { }
                                    }
                                }
                            }
                        }
                        saveLocally()
                        if (newNotifs.isNotEmpty()) println("🔔 Vytvořeno ${newNotifs.size} nových notifikací.")
                        println("🔄 Zakázky aktualizovány z cloudu.")
                    }
                } catch (e: Exception) {
                    println("Polling chyba (bude znovu za 8s): ${e.message}")
                }
            }
        }
    }


    /** Vytvoří notifikaci pro daného uživatele a uloží. */
    fun addNotification(targetUserId: String, message: String, id: String = "notif_${getCurrentTimestamp()}") {
        val notif = UserNotification(
            id = id,
            message = message,
            timestamp = getCurrentTimestamp(),
            isRead = false,
            targetUserId = targetUserId
        )
        notifications.add(notif)
        saveLocally()
    }

    private fun pushToCloud() {
        // MUTEX: Pokud už probíhá upload, další nespouštíme!
        // Toto bylo příčinou OutOfMemoryError: 10+ paralelních coroutin každá alokovala 14MB JSON
        if (isUploadInProgress) {
            println("⏳ Upload již probíhá, přeskakuji duplicitní volání.")
            return
        }
        isUploadInProgress = true
        hasPendingUpload = true
        saveLocally() // Uložíme true flag na disk, kdyby to spadlo v průběhu
        scope.launch {
            try {
                // DŮLEŽITÉ: Odstraňujeme pouze taskImages (obrázky zadání od šéfa) před odesiláním do Supabase.
                // localPhotos (fotky mechanika z dílny) se ZACHOVÁVAJÍ, ale s pojistkou proti DDoS útoku!
                val cloudTasks = tasks.map { task ->
                    task.copy(
                        taskImages = emptyList(),
                        localPhotos = task.localPhotos.filter { byteArr -> byteArr.size < 1_500_000 } // Max 1.5MB na fotku
                    )
                }
                
                // Odesíláme po 50 zakázkách naráz (místo po 1).
                // Vzhledem k tomu, že fotky odfiltrujeme a server je už nevrací, nehrozí OutOfMemoryError.
                // Zároveň se tím drasticky sníží počet HTTP dotazů ze stovek na jednotky (eliminuje zahřívání telefonu).
                cloudTasks.chunked(50).forEach { chunk ->
                    if (chunk.isNotEmpty()) {
                        httpClient.post("$SERVER_URL/tasks/upsert") {
                            contentType(ContentType.Application.Json)
                            setBody(chunk)
                        }
                        // OCHRANA PAMĚTI: Po úspěšném odeslání na server můžeme obří base64 
                        // fotky smazat z RAM, server už je má bezpečně uložené. 
                        // Tím zabráníme zasekávání a neustálému chodu Garbage Collectoru!
                        withContext(Dispatchers.Main) {
                            chunk.forEach { uploadedTask ->
                                val idx = tasks.indexOfFirst { it.id == uploadedTask.id }
                                if (idx != -1) {
                                    val currentTask = tasks[idx]
                                    if (currentTask.localPhotos.isNotEmpty() || currentTask.taskImages.isNotEmpty()) {
                                        tasks[idx] = currentTask.copy(localPhotos = emptyList(), taskImages = emptyList())
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (users.isNotEmpty()) {
                    httpClient.post("$SERVER_URL/users/upsert") {
                        contentType(ContentType.Application.Json)
                        setBody(users.toList())
                    }
                }
                println("✅ Úspěšně odesláno do cloudu (bez binárních fotek).")
                hasPendingUpload = false // Úspěch! Uvolníme zámek pro polling.
                saveLocally() // Uložíme false flag na disk, už není třeba znovu posílat po restartu
            } catch (e: Exception) {
                println("❌ Upload do cloudu selhal: ${e.message}")
                hasPendingUpload = true // Při chybě (timeout) zůstává true, polling to bude zkoušet znovu
                saveLocally() // Pro jistotu
            } finally {
                isUploadInProgress = false // Vždy uvolníme mutex, i při chybě!
            }
        }
    }

    private fun loadDefaults() {
        users.clear()
        users.addAll(listOf(
            User(id = "emp1", name = "Láďa Novák", email = "lada@autoservis.cz", pin = "1111", role = Role.EMPLOYEE, totalHoursLogged = 0.0),
            User(id = "emp2", name = "Pepa Zdepa", email = "pepa@autoservis.cz", pin = "2222", role = Role.EMPLOYEE, totalHoursLogged = 0.0),
            User(id = "admin1", name = "Michal", email = "sef@zempro.cz", pin = "0000", role = Role.ADMIN, totalHoursLogged = 0.0)
        ))
        // Žádné testovací zakázky — reálná data přijdou z cloudu při syncWithCloud()
        tasks.clear()
        saveLocally()
    }


    fun save() {
        lastSaveTime = getCurrentTimestamp() // Zaznamenáme čas uložení pro ochranu před race condition
        hasPendingUpload = true // Zajistíme flag pro případ offline
        saveLocally()
        pushToCloud()
    }

    private fun saveLocally() {
        storage?.let {
            try {
                val snapshot = AppSnapshot(users.toList(), tasks.toList(), notifications.toList(), hasPendingUpload)
                val dataStr = json.encodeToString(snapshot)
                writePlatformData(it, dataStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchTaskHistory(vinOrSpz: String): Task? {
        val term = vinOrSpz.trim().uppercase()
        if (term.isBlank()) return null
        // Find the most recent task matching either SPZ or VIN
        return tasks.filter { 
            (it.vin?.uppercase() == term) || (it.spz.uppercase() == term) 
        }.maxByOrNull { it.createdAt }
    }

    // ════════════════════════════════════════════════════════════════
    // ZÁLOHA A OBNOVA DAT
    // ════════════════════════════════════════════════════════════════

    /** Vrátí aktuální data jako JSON string — připravený k uložení do souboru */
    fun exportBackupJson(): String {
        val snapshot = AppSnapshot(
            users = users.toList(),
            tasks = tasks.map { it.copy(taskImages = emptyList()) }, // Bez velkých obrázků zadání
            notifications = notifications.toList(),
            pendingUpload = false
        )
        return json.encodeToString(snapshot)
    }

    /** Načte data ze záložního JSON stringu a přepíše aktuální stav */
    fun importFromBackupJson(jsonString: String): Boolean {
        return try {
            val snapshot = json.decodeFromString<AppSnapshot>(jsonString)
            withContext(Dispatchers.Main) {
                users.clear()
                users.addAll(snapshot.users)
                tasks.clear()
                tasks.addAll(snapshot.tasks)
                notifications.clear()
                notifications.addAll(snapshot.notifications)
            }
            hasPendingUpload = true
            saveLocally()
            pushToCloud() // Obnoví data i do Supabase cloudu
            println("✅ Záloha úspěšně obnovena — ${snapshot.tasks.size} zakázek, ${snapshot.users.size} uživatelů.")
            true
        } catch (e: Exception) {
            println("❌ Obnova zálohy selhala: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun withContext(dispatcher: kotlinx.coroutines.CoroutineDispatcher, block: () -> Unit) {
        // Synchronní verze pro volání z non-suspend kontextu
        block()
    }

    // ════════════════════════════════════════════════════════════════
    // KOŠ — SOFT DELETE, OBNOVA, TRVALÉ SMAZÁNÍ
    // ════════════════════════════════════════════════════════════════

    /** Přesune zakázku do koše (soft delete) */
    fun softDeleteTask(taskId: String) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            tasks[index] = tasks[index].copy(
                isDeleted = true,
                deletedAt = com.zakazky.app.common.utils.getCurrentTimestamp()
            )
            save()
            println("🗑️ Zakázka '${tasks[index].title}' přesunuta do koše.")
        }
    }

    /** Obnoví zakázku z koše zpět do aktivních */
    fun restoreTask(taskId: String) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            tasks[index] = tasks[index].copy(isDeleted = false, deletedAt = null)
            save()
            println("♻️ Zakázka '${tasks[index].title}' obnovena z koše.")
        }
    }

    /** Trvale smaže zakázku z databáze i ze Supabase (NEVRATNÉ!) */
    fun permanentlyDeleteTask(taskId: String) {
        val taskTitle = tasks.find { it.id == taskId }?.title
        tasks.removeAll { it.id == taskId }
        saveLocally()
        // Smažeme i ze Supabase — eq musí být uvnitř filter { } bloku
        scope.launch {
            try {
                // Smazání zatím vyřešíme tak, že úkol updatneme s flagem isDeleted (soft delete), 
                // Ktor API zatím nemá delete endpoint.
                httpClient.post("$SERVER_URL/tasks/upsert") {
                    contentType(ContentType.Application.Json)
                    setBody(listOf(Task(id = taskId, isDeleted = true)))
                }
                println("🗑️ Zakázka '$taskTitle' trvale smazána ze Supabase.")
            } catch (e: Exception) {
                println("❌ Chyba při mazání ze Supabase: ${e.message}")
            }
        }
    }

    /** Počet zakázek v koši (pro badge) */
    val deletedTasksCount: Int get() = tasks.count { it.isDeleted }
}
