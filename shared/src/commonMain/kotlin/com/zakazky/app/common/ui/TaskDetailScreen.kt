package com.zakazky.app.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.ExperimentalComposeUiApi
import kotlinx.coroutines.launch
import com.zakazky.app.common.models.Task
import com.zakazky.app.common.models.TaskStatus
import com.zakazky.app.common.models.InvoiceItem
import com.zakazky.app.common.models.AttachedDocument
import com.zakazky.app.common.theme.*
import com.zakazky.app.common.utils.generateInvoiceHtml
import com.zakazky.app.common.utils.rememberExportHelper
import com.zakazky.app.common.utils.rememberMediaManager
import com.zakazky.app.common.utils.rememberDocumentManager
import com.zakazky.app.common.utils.rememberDocumentViewer
import com.zakazky.app.common.utils.toImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.request.get
import io.ktor.client.call.body
import io.ktor.client.statement.readBytes

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, onClick: ((ByteArray) -> Unit)? = null) {
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        try {
            // Android a PC používají OkHttp, které nesnáší mezery v URL. iOS Darwin to řeší automaticky.
            val safeUrl = url.replace(" ", "%20")
            val fullUrl = if (safeUrl.startsWith("http")) safeUrl else "http://194.182.79.72:8080/$safeUrl"
            imageBytes = com.zakazky.app.common.models.AppDatabase.httpClient.get(fullUrl).readBytes()
        } catch (e: Exception) {
            hasError = true
            println("❌ Chyba stahování fotky ($url): ${e.message}")
        }
    }

    if (imageBytes != null) {
        val bitmap = remember(imageBytes) { try { imageBytes!!.toImageBitmap() } catch(e:Exception){null} }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = if (onClick != null) modifier.clickable { onClick(imageBytes!!) } else modifier
            )
        } else {
            Box(modifier = modifier.background(com.zakazky.app.common.theme.Navy700), contentAlignment = Alignment.Center) {
                Text("Chyba", color = com.zakazky.app.common.theme.ErrorDark)
            }
        }
    } else if (hasError) {
        Box(modifier = modifier.background(com.zakazky.app.common.theme.Navy700), contentAlignment = Alignment.Center) {
            Text("Nelze", color = com.zakazky.app.common.theme.Slate400, fontSize = 12.sp)
        }
    } else {
        Box(modifier = modifier.background(com.zakazky.app.common.theme.Navy800), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = com.zakazky.app.common.theme.Blue500, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TaskDetailScreen(
    task: Task,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onTakeTask: () -> Unit,
    onAssignTask: (employeeId: String) -> Unit,
    onCancelTask: () -> Unit,
    onSaveProgress: (hours: String, elecHours: String, km: String, newPhotos: List<ByteArray>, items: List<InvoiceItem>, mechPrice: String, elecPrice: String, mechHourlyRate: String, elecHourlyRate: String) -> Unit,
    onGenerateInvoice: (hours: String, elecHours: String, km: String, newPhotos: List<ByteArray>, items: List<InvoiceItem>, mechPrice: String, elecPrice: String, mechHourlyRate: String, elecHourlyRate: String) -> Unit,
    onComplete: (hours: String, elecHours: String, km: String, newPhotos: List<ByteArray>, items: List<InvoiceItem>, mechPrice: String, elecPrice: String, mechHourlyRate: String, elecHourlyRate: String) -> Unit,
    onReworkClick: () -> Unit,
    onDocumentAttached: (AttachedDocument) -> Unit,
    onTaskImageAttached: (ByteArray) -> Unit
) {
    var hoursInput by remember { mutableStateOf("") }
    var electricHoursInput by remember { mutableStateOf("") }
    var kmInput by remember { mutableStateOf(task.vehicleKm?.toString() ?: "") }
    var currentPhotos by remember { mutableStateOf(task.localPhotos) }
    
    // Nové stavy pro rozkliknutí (zoom) fotky a pro editaci odpracovaných hodin šéfem
    var zoomedImageBase64 by remember { mutableStateOf<ByteArray?>(null) }
    
    var bossMechanicHoursInput by remember(task.timeLogs) { 
        val sum = task.timeLogs.sumOf { it.hours }
        mutableStateOf(if (sum > 0) sum.toString() else "") 
    }
    var bossElectricHoursInput by remember(task.electricTimeLogs) { 
        val sum = task.electricTimeLogs.sumOf { it.hours }
        mutableStateOf(if (sum > 0) sum.toString() else "") 
    }
    
    var currentInvoiceItems by remember { mutableStateOf(task.invoiceItems) }
    var mechanicWorkPriceInput by remember { mutableStateOf(if (task.mechanicWorkPrice > 0) task.mechanicWorkPrice.toString() else "") }
    var electricWorkPriceInput by remember { mutableStateOf(if (task.electricWorkPrice > 0) task.electricWorkPrice.toString() else "") }
    // Hodinová sazba mechanika — průhledný výpočet pro šéfa
    var mechanicHourlyRateInput by remember { mutableStateOf(if (task.mechanicHourlyRate > 0) task.mechanicHourlyRate.toString() else "") }
    var electricHourlyRateInput by remember { mutableStateOf(if (task.electricHourlyRate > 0) task.electricHourlyRate.toString() else "") }
    var showFormatDialog by remember { mutableStateOf(false) }
    var closeTaskOnExport by remember { mutableStateOf(false) }
    
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var newItemPrice by remember { mutableStateOf("") }
    
    var itemToEdit by remember { mutableStateOf<InvoiceItem?>(null) }
    var editItemName by remember { mutableStateOf("") }
    var editItemPrice by remember { mutableStateOf("") }
    
    LaunchedEffect(itemToEdit) {
        if (itemToEdit != null) {
            editItemName = itemToEdit!!.name
            editItemPrice = if (itemToEdit!!.price > 0.0) itemToEdit!!.price.toString() else ""
        }
    }
    
    var showMediaDialog by remember { mutableStateOf(false) }
    // Stav pro dialog mazání do koše — musí být na úrovni composable (ne uvnitř if)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val exportHelper = rememberExportHelper()
    val scope = rememberCoroutineScope()
    
    val mediaManager = rememberMediaManager { newImageBytes ->
        if (newImageBytes != null) {
            currentPhotos = currentPhotos + newImageBytes
        }
    }
    
    val docManager = rememberDocumentManager { name, bytes -> 
        val newDoc = AttachedDocument(id = "doc_${task.attachedDocuments.size + 1}", name = name, data = bytes)
        onDocumentAttached(newDoc)
    }

    val taskImageManager = rememberMediaManager { newImageBytes ->
        if (newImageBytes != null) {
            onTaskImageAttached(newImageBytes)
        }
    }

    val docViewer = rememberDocumentViewer()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd)))
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                TopAppBar(
                    title = {
                        Text("Detail Zakázky", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zpět", tint = Color.White)
                        }
                    },
                    actions = {
                        // Tlačítko "Přesunout do koše" — jen pro šéfa
                        if (isAdmin) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Přesunout do koše", tint = ErrorDark)
                            }
                        }
                    },
                    backgroundColor = Color.Transparent,
                    contentColor = Color.White,
                    elevation = 0.dp
                )
            }
        }
    ) { paddingValues ->
        LaunchedEffect(task.id) {
            if (!isAdmin && task.readAt == null) {
                // Notifikace, že si mechanik zakázku otevřel
                val index = com.zakazky.app.common.models.AppDatabase.tasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    val updatedTask = task.copy(readAt = com.zakazky.app.common.utils.getCurrentTimestamp())
                    com.zakazky.app.common.models.AppDatabase.tasks[index] = updatedTask
                    com.zakazky.app.common.models.AppDatabase.save()
                }
            }
        }

        BoxWithConstraints(modifier = Modifier
            .fillMaxSize()
            .background(Navy900)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            }
        ) {
            val isDesktopLayout = maxWidth > 800.dp
            
            val leftColumnContent = @Composable {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(paddingValues).navigationBarsPadding()
                ) {
            // Task Header Info
            Surface(
                modifier = Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), spotColor = Color.Black.copy(alpha = 0.5f)),
                color = Navy800,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = task.title, style = MaterialTheme.typography.h1.copy(fontSize = 28.sp), color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoChip(Icons.Default.Person, task.customerName.ifEmpty { "Neznámý zákazník" })
                        InfoChip(Icons.Default.Info, task.spz.ifEmpty { "Bez SPZ" })
                    }
                    if (task.customerPhone.isNotBlank() || task.customerEmail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (task.customerPhone.isNotBlank()) InfoChip(Icons.Default.Call, task.customerPhone)
                            if (task.customerEmail.isNotBlank()) InfoChip(Icons.Default.Email, task.customerEmail)
                        }
                    }
                    if (task.customerAddress.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoChip(Icons.Default.LocationOn, task.customerAddress)
                    }
                    if (task.brand.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoChip(Icons.Default.Info, "Vůz: ${task.brand}")
                    }
                    if (task.vin.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoChip(Icons.Default.Settings, "VIN: ${task.vin}")
                    }
                    if (task.assignedTo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val assignedUser = com.zakazky.app.common.models.AppDatabase.users.find { it.id == task.assignedTo }
                        val assigneeName = assignedUser?.name ?: "Neznámý mechanik"
                        InfoChip(Icons.Default.Person, "Zpracovává: $assigneeName")
                        
                        if (isAdmin) {
                            if (task.readAt != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val readDate = com.zakazky.app.common.utils.formatTimestamp(task.readAt)
                                Text("Přečteno: $readDate", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                            if (task.startedAt != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val startedDate = com.zakazky.app.common.utils.formatTimestamp(task.startedAt)
                                Text("Odesláno mechanikovi: $startedDate", color = Blue400, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                            }
                            if (task.completedAt != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val completedDate = com.zakazky.app.common.utils.formatTimestamp(task.completedAt)
                                Text("Odesláno zpět šéfovi: $completedDate", color = com.zakazky.app.common.theme.SuccessLight, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Zadání od šéfa:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.body1,
                        color = Slate300
                    )
                    
                    if (task.attachedDocuments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Připojené dokumenty:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            task.attachedDocuments.forEach { doc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Navy900, RoundedCornerShape(8.dp))
                                        .border(1.dp, Navy700, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { docViewer.viewDocument(doc.name, doc.data) }
                                        .padding(12.dp), 
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Blue500)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(doc.name, color = Color.White, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { docManager.pickDocument() },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue400, backgroundColor = Navy800)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Připojit soubor (Word, PDF...)")
                        }
                    }

                    val remoteTaskImages = task.photoUrls.filter { it.contains("_task_") }
                    if (task.taskImages.isNotEmpty() || remoteTaskImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Obrázky zadání (od šéfa):", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(task.taskImages) { byteArr ->
                                val imageBitmap = remember(byteArr) { try { byteArr.toImageBitmap() } catch(e:Exception){null} }
                                if (imageBitmap != null) {
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.height(150.dp).width(150.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Navy600, RoundedCornerShape(8.dp)).clickable { zoomedImageBase64 = byteArr }
                                    )
                                }
                            }
                            items(remoteTaskImages) { url ->
                                RemoteImage(
                                    url = url,
                                    modifier = Modifier.height(150.dp).width(150.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Navy600, RoundedCornerShape(8.dp)),
                                    onClick = { zoomedImageBase64 = it }
                                )
                            }
                        }
                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { taskImageManager.pickImage() },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue400, backgroundColor = Navy800)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Přidat další obrázek")
                            }
                        }
                    } else if (isAdmin) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Navy900)
                                .border(1.dp, Navy700, RoundedCornerShape(16.dp))
                                .clickable { taskImageManager.pickImage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Slate400, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Přidat obrázek zadání (Screenshot)", color = Slate400, fontSize = 14.sp)
                            }
                        }
                    }

                    if (task.reworks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Zaznamenané reklamace:", fontWeight = FontWeight.Bold, color = Color(0xFFFCA5A5), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            task.reworks.forEach { rework ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, ErrorDark, RoundedCornerShape(8.dp)).background(Navy900, RoundedCornerShape(8.dp)).padding(12.dp)
                                ) {
                                    Text("Opravoval: ${rework.solverName} (+${rework.solverHours}h)", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text("Zavinil: ${rework.guiltyName} (-${rework.penaltyHours}h)", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                                    if (rework.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Důvod: ${rework.note}", style = MaterialTheme.typography.caption, color = Slate300)
                                    }
                                }
                            }
                        }
                    }
                } // closes Column 170
            } // closes Surface 165
        } // closes LeftColumn wrapper
    } // end of val leftColumnContent
                
            val rightColumnContent = @Composable {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(paddingValues).navigationBarsPadding()
                ) {
                    if (task.timeLogs.isNotEmpty() || task.electricTimeLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Historie práce na zakázce:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            task.timeLogs.forEach { log ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Navy900, RoundedCornerShape(8.dp)).border(1.dp, Navy700, RoundedCornerShape(8.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mechanik: ${log.employeeName}", fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.weight(1f))
                                    Text("${log.hours} h", fontWeight = FontWeight.Bold, color = Blue500)
                                }
                            }
                            task.electricTimeLogs.forEach { log ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Navy900, RoundedCornerShape(8.dp)).border(1.dp, Navy700, RoundedCornerShape(8.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Elektrikář: ${log.employeeName}", fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.weight(1f))
                                    Text("${log.hours} h", fontWeight = FontWeight.Bold, color = com.zakazky.app.common.theme.WarningLight)
                                }
                            }
                            val totalM = task.timeLogs.sumOf { it.hours }
                            val totalE = task.electricTimeLogs.sumOf { it.hours }
                            if (totalM > 0) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) { Text("Odpracováno celkem (Mechanik): $totalM h", fontWeight = FontWeight.ExtraBold, color = Color.White) }
                            if (totalE > 0) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) { Text("Odpracováno celkem (Elektrikář): $totalE h", fontWeight = FontWeight.ExtraBold, color = Color.White) }
                        }
                    }
            Spacer(modifier = Modifier.height(24.dp))

            // Mechanic Input Form, Take Task Button, or Admin Invoice Review
            if (task.assignedTo == null && task.status == TaskStatus.AVAILABLE) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Button(
                        onClick = onTakeTask,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Blue600)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vzít si tuto zakázku", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    if (isAdmin) {
                        var expandedAssign by remember { mutableStateOf(false) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedAssign = true },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp).padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue400, backgroundColor = Navy800)
                            ) {
                                Text("Předat zakázku přidělením...", fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(
                                expanded = expandedAssign,
                                onDismissRequest = { expandedAssign = false },
                                modifier = Modifier.background(Navy800)
                            ) {
                                val mechanics = com.zakazky.app.common.models.AppDatabase.users.filter { it.role == com.zakazky.app.common.models.Role.EMPLOYEE }
                                mechanics.forEach { mech ->
                                    DropdownMenuItem(onClick = {
                                        expandedAssign = false
                                        onAssignTask(mech.id)
                                    }) {
                                        Text(mech.name, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (task.status == TaskStatus.IN_PROGRESS && isAdmin) {
                // ════════════════════════════════════════════════════════
                // ŠÉF: Zakázka probíhá — read-only přehled stavu
                // ════════════════════════════════════════════════════════
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                    // STATUS BANNER
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = 0.dp,
                        backgroundColor = Blue900.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Blue400)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Zakázka probíhá — čeká se na dokončení",
                                    color = Blue200,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))

                            // Kdo pracuje
                            val mechanic = com.zakazky.app.common.models.AppDatabase.users
                                .find { it.id == task.assignedTo }
                            val mechanicName = mechanic?.name ?: "Neznámý mechanik"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Mechanik: ", color = Slate400, fontSize = 13.sp)
                                Text(mechanicName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            if (task.startedAt != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Odesláno mechanikovi: ", color = Slate400, fontSize = 13.sp)
                                    Text(com.zakazky.app.common.utils.formatTimestamp(task.startedAt), color = Color.White, fontSize = 13.sp)
                                }
                            }

                            if (task.readAt != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessLight, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Přečteno mechanikem: ", color = Slate400, fontSize = 13.sp)
                                    Text(com.zakazky.app.common.utils.formatTimestamp(task.readAt), color = SuccessLight, fontSize = 13.sp)
                                }
                            }

                            // Odpracované hodiny ze záznamů
                            if (task.timeLogs.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                val totalHours = task.timeLogs.sumOf { it.hours }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Odpracováno: ", color = Slate400, fontSize = 13.sp)
                                    Text("$totalHours hodin", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // KM z tachometru
                            if (task.vehicleKm != null && task.vehicleKm > 0) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Tachometr: ", color = Slate400, fontSize = 13.sp)
                                    Text("${task.vehicleKm} km", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // FOTKY OD MECHANIKA (localPhotos)
                    val remoteMechanicImages = task.photoUrls.filter { !it.contains("_task_") }
                    if (task.localPhotos.isNotEmpty() || remoteMechanicImages.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Text("📷 Fotky z dílny (mechanik):", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(task.localPhotos) { byteArr ->
                                val imageBitmap = remember(byteArr) {
                                    try { byteArr.toImageBitmap() } catch (e: Exception) { null }
                                }
                                if (imageBitmap != null) {
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, Navy600, RoundedCornerShape(12.dp))
                                            .clickable { zoomedImageBase64 = byteArr }
                                    )
                                }
                            }
                            items(remoteMechanicImages) { url ->
                                RemoteImage(
                                    url = url,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Navy600, RoundedCornerShape(12.dp)),
                                    onClick = { zoomedImageBase64 = it }
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Navy800)
                                .border(1.dp, Navy700, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "⏳ Mechanik ještě nepřidal fotky z dílny",
                                color = Slate400,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // MATERIÁL (přehled co mechanik přidal)
                    if (task.invoiceItems.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Text("🔧 Použitý materiál:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = 0.dp,
                            backgroundColor = Navy800,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                task.invoiceItems.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.name, color = Slate300)
                                        Text(
                                            if (item.price > 0) "${item.price} Kč" else "—",
                                            color = if (item.price > 0) Color.White else Slate400,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }

            } else if (task.status == TaskStatus.IN_PROGRESS && !isAdmin) {
                // ════════════════════════════════════════════════════════
                // MECHANIK: editovatelný formulář na práci
                // ════════════════════════════════════════════════════════
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Vaše práce na zakázce", style = MaterialTheme.typography.h6, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = 2.dp,
                        backgroundColor = Navy800,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = hoursInput,
                                onValueChange = { hoursInput = it },
                                label = { Text("Odpracovaný čas mechanika (hodiny)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = Slate400) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = electricHoursInput,
                                onValueChange = { electricHoursInput = it },
                                label = { Text("Odpracovaný čas elektrikáře (hodiny)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = Slate400) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = kmInput,
                                onValueChange = { kmInput = it },
                                label = { Text("Aktuální stav tachometru (KM)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Slate400) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            if (currentPhotos.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(currentPhotos) { byteArr ->
                                        val imageBitmap = remember(byteArr) {
                                            try { byteArr.toImageBitmap() } catch (e: Exception) { null }
                                        }
                                        if (imageBitmap != null) {
                                            Image(
                                                bitmap = imageBitmap,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Navy600, RoundedCornerShape(8.dp)).clickable { zoomedImageBase64 = byteArr }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            OutlinedButton(
                                onClick = { showMediaDialog = true },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp).padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue600)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Přidat fotku z dílny (stav vozu)")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // MATERIÁL pro mechanika
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = 2.dp,
                        backgroundColor = Navy800,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Materiál a Cena Opravy", style = MaterialTheme.typography.h6, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))

                            if (currentInvoiceItems.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    currentInvoiceItems.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Navy900).border(1.dp, Navy700, RoundedCornerShape(8.dp)).clickable { itemToEdit = item }.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.name, color = Slate300, fontWeight = FontWeight.Medium)
                                            val priceText = if (item.price > 0.0) "${item.price} Kč" else "Cena k doplnění"
                                            Text(priceText, color = if (item.price > 0.0) Color.White else Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            OutlinedButton(
                                onClick = { showAddItemDialog = true },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessDark)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Přidat spotřebovaný materiál")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Tlačítka mechanika
                    Button(
                        onClick = { onSaveProgress(hoursInput, electricHoursInput, kmInput, currentPhotos, currentInvoiceItems, "", "", mechanicHourlyRateInput, electricHourlyRateInput) },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Blue600)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uložit mezikrok a pokračovat později", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onComplete(hoursInput, electricHoursInput, kmInput, currentPhotos, currentInvoiceItems, "", "", mechanicHourlyRateInput, electricHourlyRateInput) },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = SuccessDark)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("✅ Odeslat šéfovi k fakturaci (Dokončit)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = onCancelTask,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Storno: Smazat přidělení a vrátit nevyřešené", color = Slate400, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

            } else if (task.status == TaskStatus.COMPLETED && isAdmin) {
                // ════════════════════════════════════════════════════════
                // ŠÉF: Zakázka dokončena mechanikem — fakturace
                // ════════════════════════════════════════════════════════
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Kontrola a Vyúčtování (Před fakturací)", style = MaterialTheme.typography.h6, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Fotky od mechanika
                    val remoteMechanicImagesCompleted = task.photoUrls.filter { !it.contains("_task_") }
                    if (task.localPhotos.isNotEmpty() || remoteMechanicImagesCompleted.isNotEmpty()) {
                        Text("📷 Fotky z dílny (od mechanika):", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(task.localPhotos) { byteArr ->
                                val imageBitmap = remember(byteArr) {
                                    try { byteArr.toImageBitmap() } catch (e: Exception) { null }
                                }
                                if (imageBitmap != null) {
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, Navy600, RoundedCornerShape(12.dp))
                                            .clickable { zoomedImageBase64 = byteArr }
                                    )
                                }
                            }
                            items(remoteMechanicImagesCompleted) { url ->
                                RemoteImage(
                                    url = url,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Navy600, RoundedCornerShape(12.dp)),
                                    onClick = { zoomedImageBase64 = it }
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = 2.dp,
                        backgroundColor = Navy800,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = bossMechanicHoursInput,
                                onValueChange = { bossMechanicHoursInput = it },
                                label = { Text("Odpracovaný čas mechanika (hodiny)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = Slate400) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = com.zakazky.app.common.theme.ZemproGreen, unfocusedBorderColor = Slate500)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = bossElectricHoursInput,
                                onValueChange = { bossElectricHoursInput = it },
                                label = { Text("Odpracovaný čas elektrikáře (hodiny)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = Slate400) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = com.zakazky.app.common.theme.ZemproGreen, unfocusedBorderColor = Slate500)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = kmInput,
                                onValueChange = { kmInput = it },
                                label = { Text("Aktuální stav tachometru (KM)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Slate400) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Materiál — přehled + šéfův zásah
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = 2.dp,
                        backgroundColor = Navy800,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Materiál a Cena Opravy", style = MaterialTheme.typography.h6, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))

                            if (currentInvoiceItems.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    currentInvoiceItems.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Navy900).border(1.dp, Navy700, RoundedCornerShape(8.dp)).clickable { itemToEdit = item }.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.name, color = Slate300, fontWeight = FontWeight.Medium)
                                            val priceText = if (item.price > 0.0) "${item.price} Kč" else "Cena k doplnění"
                                            Text(priceText, color = if (item.price > 0.0) Color.White else Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            OutlinedButton(
                                onClick = { showAddItemDialog = true },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessDark)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Přidat/upravit materiál")
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Divider(color = Navy700)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Vyúčtování práce (Pouze pro Šéfa)", fontSize = 14.sp, color = Slate400, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))

                            // ── HODINOVÁ SAZBA + AUTOMATICKÝ VÝPOČET ──
                            val totalHoursForCalc = bossMechanicHoursInput.toDoubleOrNull() ?: 0.0
                            OutlinedTextField(
                                value = mechanicHourlyRateInput,
                                onValueChange = { v ->
                                    mechanicHourlyRateInput = v
                                    // Automaticky předvyplnit cenu práce z výpočtu
                                    val rate = v.toDoubleOrNull()
                                    if (rate != null && totalHoursForCalc > 0) {
                                        mechanicWorkPriceInput = (totalHoursForCalc * rate).toInt().toString()
                                    }
                                },
                                label = { Text("Hodinová sazba mechanika (Kč/hod)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Slate400) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = com.zakazky.app.common.theme.ZemproGreen, unfocusedBorderColor = Slate500)
                            )

                            // Zobrazení auto-výpočtu
                            val calcRate = mechanicHourlyRateInput.toDoubleOrNull()
                            if (calcRate != null && totalHoursForCalc > 0) {
                                val calculatedPrice = (totalHoursForCalc * calcRate).toInt()
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.zakazky.app.common.theme.SuccessDark.copy(alpha = 0.15f))
                                        .border(1.dp, com.zakazky.app.common.theme.SuccessDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = com.zakazky.app.common.theme.SuccessLight, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${totalHoursForCalc}h × ${calcRate.toInt()} Kč/hod = $calculatedPrice Kč → automaticky vloženo do faktury",
                                        color = com.zakazky.app.common.theme.SuccessLight,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            } else if (totalHoursForCalc > 0 && calcRate == null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Zadejte hodinovou sazbu pro automatický výpočet (odpracováno: ${totalHoursForCalc}h)",
                                    color = Slate400, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }

                            // ── HODINOVÁ SAZBA ELEKTRIKÁŘE + AUTOMATICKÝ VÝPOČET ──
                            val totalElectricHoursForCalc = bossElectricHoursInput.toDoubleOrNull() ?: 0.0
                            OutlinedTextField(
                                value = electricHourlyRateInput,
                                onValueChange = { v ->
                                    electricHourlyRateInput = v
                                    // Automaticky předvyplnit cenu práce z výpočtu
                                    val rate = v.toDoubleOrNull()
                                    if (rate != null && totalElectricHoursForCalc > 0) {
                                        electricWorkPriceInput = (totalElectricHoursForCalc * rate).toInt().toString()
                                    }
                                },
                                label = { Text("Hodinová sazba elektrikáře (Kč/hod)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Slate400) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = com.zakazky.app.common.theme.ZemproGreen, unfocusedBorderColor = Slate500)
                            )

                            // Zobrazení auto-výpočtu pro elektrikáře
                            val calcElecRate = electricHourlyRateInput.toDoubleOrNull()
                            if (calcElecRate != null && totalElectricHoursForCalc > 0) {
                                val calculatedElecPrice = (totalElectricHoursForCalc * calcElecRate).toInt()
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.zakazky.app.common.theme.SuccessDark.copy(alpha = 0.15f))
                                        .border(1.dp, com.zakazky.app.common.theme.SuccessDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = com.zakazky.app.common.theme.SuccessLight, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${totalElectricHoursForCalc}h × ${calcElecRate.toInt()} Kč/hod = $calculatedElecPrice Kč → automaticky vloženo do faktury (elektrika)",
                                        color = com.zakazky.app.common.theme.SuccessLight,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            } else if (totalElectricHoursForCalc > 0 && calcElecRate == null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Zadejte hodinovou sazbu elektrikáře pro automatický výpočet (odpracováno: ${totalElectricHoursForCalc}h)",
                                    color = Slate400, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = mechanicWorkPriceInput,
                                onValueChange = { mechanicWorkPriceInput = it },
                                label = { Text("Práce mechanická (Kč)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = electricWorkPriceInput,
                                onValueChange = { electricWorkPriceInput = it },
                                label = { Text("Práce elektrikářská (Kč)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Fakturační tlačítka
                    if (!task.isInvoiceClosed) {
                        Button(
                            onClick = { closeTaskOnExport = true; showFormatDialog = true },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = SuccessDark)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uzavřít zakázku a uložit/tisknout fakturu", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { closeTaskOnExport = false; showFormatDialog = true },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Blue600)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uložit / Tisknout kopii faktury", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    val invoiceHtml = generateInvoiceHtml(task)
                                    com.zakazky.app.common.utils.openEmailClient(
                                        to = if (task.customerName.contains("@")) task.customerName else "",
                                        subject = "Faktura Zempro CZ - ${task.title}",
                                        body = "Dobrý den,\n\npřipravili jsme pro Vás fakturu k zakázce za Vaše vozidlo.\n\nS pozdravem,\nTým Zempro CZ",
                                        attachmentHtml = invoiceHtml,
                                        taskName = task.title
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Navy600)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Poslat e-mailem", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onReworkClick,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = ErrorDark)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vyřešit reklamaci a penalizovat", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

            } else if (task.status == TaskStatus.COMPLETED && !isAdmin) {

                Column(modifier = Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessLight, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tato zakázka byla úspěšně dokončena. Čeká se na kontrolu šéfa a fakturaci.", color = Slate300, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }

            // Universal bottom spacer
            Spacer(modifier = Modifier.height(40.dp))
            }
            } // close rightColumnContent
            if (isDesktopLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .imePadding(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        leftColumnContent()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        rightColumnContent()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                ) {
                    leftColumnContent()
                    rightColumnContent()
                }
            }
        } // close BoxWithConstraints
    }
    
    if (showMediaDialog) {
        AlertDialog(
            onDismissRequest = { showMediaDialog = false },
            backgroundColor = Navy800,
            contentColor = Color.White,
            title = { Text("Přidat fotografii", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Chcete pořídit novou fotku kamerou, nebo vybrat existující z galerie?") },
            confirmButton = {
                TextButton(onClick = { 
                    showMediaDialog = false
                    mediaManager.takePicture()
                }) {
                    Text("Fotoaparát", color = Blue500, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showMediaDialog = false
                    mediaManager.pickImage()
                }) {
                    Text("Z Galerie", color = Blue500)
                }
            }
        )
    }
    
    if (showAddItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            modifier = Modifier.imePadding(),
            backgroundColor = Navy800,
            contentColor = Color.White,
            title = { Text("Přidat Materiál", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("Název dílu (např. fridex)", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newItemPrice,
                        onValueChange = { newItemPrice = it },
                        label = { Text("Cena v Kč (Pokud nevíš, nechej prázdné)", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (newItemName.isNotBlank()) {
                        val parsedPrice = newItemPrice.toDoubleOrNull() ?: 0.0
                        currentInvoiceItems = currentInvoiceItems + InvoiceItem(
                            id = "item_${currentInvoiceItems.size + 1}",
                            name = newItemName,
                            price = parsedPrice
                        )
                        newItemName = ""
                        newItemPrice = ""
                        showAddItemDialog = false
                    }
                }) {
                    Text("Přidat do zakázky", color = SuccessLight, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Zrušit", color = Slate400)
                }
            }
        )
    }

    if (itemToEdit != null) {
        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            modifier = Modifier.imePadding(),
            backgroundColor = Navy800,
            contentColor = Color.White,
            title = { Text("Upravit položku", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editItemName,
                        onValueChange = { editItemName = it },
                        label = { Text("Název dílu", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editItemPrice,
                        onValueChange = { editItemPrice = it },
                        label = { Text("Cena v Kč (Pokud nevíš, nechej prázdné)", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (editItemName.isNotBlank()) {
                        val parsedPrice = editItemPrice.toDoubleOrNull() ?: 0.0
                        val updatedList = currentInvoiceItems.map { if (it.id == itemToEdit!!.id) it.copy(name = editItemName, price = parsedPrice) else it }
                        currentInvoiceItems = updatedList
                        itemToEdit = null
                    }
                }) {
                    Text("Uložit změny", color = SuccessLight, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { 
                        currentInvoiceItems = currentInvoiceItems.filter { it.id != itemToEdit!!.id }
                        itemToEdit = null 
                    }) {
                        Text("Smazat položku", color = ErrorDark)
                    }
                    TextButton(onClick = { itemToEdit = null }) {
                        Text("Zrušit", color = Slate400)
                    }
                }
            }
        )
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp),
            backgroundColor = Navy800,
            contentColor = Color.White,
            title = { Text("Vyberte formát pro uložení", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Fakturu si můžete uložit pro úpravy v Excelu, otevření jako webovou stránku, nebo tisk do PDF.", color = Slate300) },
            buttons = {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            showFormatDialog = false
                            val updatedTask = task.copy(
                                invoiceItems = currentInvoiceItems,
                                mechanicWorkPrice = mechanicWorkPriceInput.toDoubleOrNull() ?: task.mechanicWorkPrice,
                                electricWorkPrice = electricWorkPriceInput.toDoubleOrNull() ?: task.electricWorkPrice,
                                vehicleKm = kmInput.toIntOrNull() ?: task.vehicleKm
                            )
                            val xlsData = com.zakazky.app.common.utils.generateInvoiceCsv(updatedTask)
                            exportHelper.exportDocument(xlsData, "Faktura_${task.id}_${task.customerName.replace(" ", "_")}", "xls", "application/vnd.ms-excel")
                            if (closeTaskOnExport) onGenerateInvoice(hoursInput, electricHoursInput, kmInput, currentPhotos, currentInvoiceItems, mechanicWorkPriceInput, electricWorkPriceInput, mechanicHourlyRateInput, electricHourlyRateInput)
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = SuccessDark),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("📊 Uložit do tabulky (Excel XLS)", color = Color.White, fontWeight = FontWeight.Bold) }
                    
                    Button(
                        onClick = {
                            showFormatDialog = false
                            scope.launch {
                                val updatedTask = task.copy(
                                    invoiceItems = currentInvoiceItems,
                                    mechanicWorkPrice = mechanicWorkPriceInput.toDoubleOrNull() ?: task.mechanicWorkPrice,
                                    electricWorkPrice = electricWorkPriceInput.toDoubleOrNull() ?: task.electricWorkPrice,
                                    vehicleKm = kmInput.toIntOrNull() ?: task.vehicleKm
                                )
                                val html = com.zakazky.app.common.utils.generateInvoiceHtml(updatedTask)
                                exportHelper.exportDocument(html, "Faktura_${task.id}_${task.customerName.replace(" ", "_")}", "html", "text/html")
                                if (closeTaskOnExport) onGenerateInvoice(hoursInput, electricHoursInput, kmInput, currentPhotos, currentInvoiceItems, mechanicWorkPriceInput, electricWorkPriceInput, mechanicHourlyRateInput, electricHourlyRateInput)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Blue600),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("🌐 Uložit jako HTML (Web/Záloha)", color = Color.White, fontWeight = FontWeight.Bold) }

                    Button(
                        onClick = {
                            showFormatDialog = false
                            scope.launch {
                                val updatedTask = task.copy(
                                    invoiceItems = currentInvoiceItems,
                                    mechanicWorkPrice = mechanicWorkPriceInput.toDoubleOrNull() ?: task.mechanicWorkPrice,
                                    electricWorkPrice = electricWorkPriceInput.toDoubleOrNull() ?: task.electricWorkPrice,
                                    vehicleKm = kmInput.toIntOrNull() ?: task.vehicleKm
                                )
                                val html = com.zakazky.app.common.utils.generateInvoiceHtml(updatedTask)
                                exportHelper.exportDocument(html, "Faktura_${task.id}_${task.customerName.replace(" ", "_")}", "pdf", "application/pdf")
                                if (closeTaskOnExport) onGenerateInvoice(hoursInput, electricHoursInput, kmInput, currentPhotos, currentInvoiceItems, mechanicWorkPriceInput, electricWorkPriceInput, mechanicHourlyRateInput, electricHourlyRateInput)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Navy600),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("🖨️ Uložit přímo jako PDF", color = Color.White, fontWeight = FontWeight.Bold) }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showFormatDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Zrušit", color = Slate400, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    // Zobrazení detailního overlaye pro zvětšené fotky
    androidx.compose.animation.AnimatedVisibility(
        visible = zoomedImageBase64 != null,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { zoomedImageBase64 = null }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            zoomedImageBase64?.let { byteArr ->
                val bigImage = remember(byteArr) {
                    try { byteArr.toImageBitmap() } catch (e: Exception) { null }
                }
                if (bigImage != null) {
                    Image(
                        bitmap = bigImage,
                        contentDescription = "Zvětšená fotka",
                        modifier = Modifier
                            .fillMaxSize(0.9f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            
            // Tlačítko pro zavření fotky
            IconButton(
                onClick = { zoomedImageBase64 = null },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Color.White)
            }
        }
    }

    // ── Dialog: potvrzení přesunutí do koše ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            backgroundColor = com.zakazky.app.common.theme.Navy800,
            title = { Text("Přesunout do koše?", color = ErrorDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Zakázka \"${task.title}\" bude přesunuta do koše.\nMůžete ji kdykoli obnovit v záložce Koš.",
                    color = com.zakazky.app.common.theme.Blue200
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    com.zakazky.app.common.models.AppDatabase.softDeleteTask(task.id)
                    onBack()
                }) {
                    Text("Přesunout do koše", color = ErrorDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Zrušit", color = com.zakazky.app.common.theme.Blue400)
                }
            }
        )
    }
}

@Composable
fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Navy700, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// Temporary Slate500/400 colors if not in Theme
val Slate400 = Color(0xFF94a3b8)
val Slate500 = Color(0xFF64748b)
