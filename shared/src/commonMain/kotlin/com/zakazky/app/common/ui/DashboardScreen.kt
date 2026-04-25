package com.zakazky.app.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakazky.app.common.models.Task
import com.zakazky.app.common.models.TaskStatus
import com.zakazky.app.common.models.displayName
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.theme.*

// Datová třída pro položky bottom nav šéfa
private data class AdminNavItem(
    val title: String,
    val icon: ImageVector,
    val badge: Int = 0
)

@Composable
fun DashboardScreen(
    isAdmin: Boolean,
    isDesktopMode: Boolean = false,
    desktopSelectedTab: Int = 0,
    onDesktopTabSelected: ((Int) -> Unit)? = null,
    onTaskClick: (Task) -> Unit,
    onProfileClick: () -> Unit,
    onAddTaskClick: () -> Unit
) {
    var localSelectedTab by remember { mutableStateOf(0) }
    val selectedTab = if (isDesktopMode) desktopSelectedTab else localSelectedTab
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val currentUserId = AppDatabase.currentUser.id
    val tasks = AppDatabase.tasks

    // Badge: počet dokončených ale nezafakturovaných zakázek (pro šéfa na Historii)
    val completedCountToReview = tasks.count { it.status == TaskStatus.COMPLETED && !it.isInvoiceClosed && !it.isDeleted }
    val deletedCount = AppDatabase.deletedTasksCount

    // ── Navigační položky pro admin bottom bar ──
    val adminNavItems = listOf(
        AdminNavItem("Nástěnka", Icons.Default.Home),
        AdminNavItem("Historie", Icons.Default.DateRange, badge = completedCountToReview),
        AdminNavItem("Zákazníci", Icons.Default.Person),
        AdminNavItem("Faktury", Icons.Default.List),
        AdminNavItem("Zaměstnanci", Icons.Default.Settings),
        AdminNavItem("Koš", Icons.Default.Delete, badge = deletedCount)
    )

    // Data pro prefill okna Nové zakázky
    var taskDialogCustomer by remember { mutableStateOf<com.zakazky.app.common.models.CustomerProfile?>(null) }
    var taskDialogVehicle by remember { mutableStateOf<com.zakazky.app.common.models.Task?>(null) }

    // ── Mechanic tab títoly — Faktury je jen pro šéfa, mechanik ji nevidí
    val mechanicTabTitles = listOf("Nástěnka", "Historie")

    // TopAppBar je společná pro všechny (mobil)
    val topBar: @Composable () -> Unit = {
        if (!isDesktopMode) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd)))
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "Zempro CZ — Správa zakázek",
                            fontWeight = FontWeight.ExtraBold,
                            color = ZemproGreen,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                    },
                    actions = {
                        val currentId = AppDatabase.currentUser.id
                        val myNotifications = AppDatabase.notifications.filter {
                            it.targetUserId.isEmpty() || it.targetUserId == currentId
                        }
                        val unreadCount = myNotifications.count { !it.isRead }
                        IconButton(onClick = { showNotificationsDialog = true }) {
                            Box {
                                Icon(Icons.Default.Notifications, contentDescription = "Upozornění", tint = Blue50)
                                if (unreadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(ErrorDark)
                                    )
                                }
                            }
                        }
                        // Profil tlačítko jen pro mechanika (šéf má v bottom baru Zaměstnanci + profil v TopBar)
                        if (!isAdmin) {
                            IconButton(onClick = onProfileClick) {
                                Icon(Icons.Default.Person, contentDescription = "Profil", tint = Blue50)
                            }
                        } else {
                            // Šéf má profil tlačítko v Top baru
                            IconButton(onClick = onProfileClick) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, ZemproGreen.copy(alpha = 0.7f), CircleShape)
                                ) {
                                    UserAvatar(
                                        user = AppDatabase.currentUser,
                                        size = 32.dp,
                                        isBoss = true
                                    )
                                }
                            }
                        }
                    },
                    backgroundColor = Color.Transparent,
                    elevation = 0.dp
                )
            }
        }
    }

    // ════════════════════════════════════════════════
    // ŠÉFOVO MOBILNÍ ROZHRANÍ — Bottom Navigation Bar
    // ════════════════════════════════════════════════
    if (isAdmin && !isDesktopMode) {
        Scaffold(
            topBar = topBar,
            bottomBar = {
                // Glassmorphism bottom navigation bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(PrimaryGradientStart.copy(alpha = 0.97f), Navy900.copy(alpha = 0.97f))
                            )
                        )
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        adminNavItems.forEachIndexed { index, item ->
                            val isSelected = selectedTab == index
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { localSelectedTab = index }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Aktivní indikátor — tečka nahoře
                                Box(
                                    modifier = Modifier
                                        .width(if (isSelected) 20.dp else 0.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelected) ZemproGreen else Color.Transparent)
                                )
                                Spacer(Modifier.height(4.dp))
                                // Ikona s badgem
                                Box {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = if (isSelected) Color.White else Blue200,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    // Badge (číslo zakázek čekajících na fakturaci)
                                    if (item.badge > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(ErrorDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (item.badge > 9) "9+" else item.badge.toString(),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.title,
                                    color = if (isSelected) Color.White else Blue200,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Navy900)
                    .padding(paddingValues)
            ) {
                AdminContent(
                    selectedTab = selectedTab,
                    tasks = tasks,
                    currentUserId = currentUserId,
                    isAdmin = true,
                    onTaskClick = onTaskClick,
                    onShowAddDialog = { showAddTaskDialog = true },
                    onPrefillAddDialog = { cust, veh -> 
                        taskDialogCustomer = cust
                        taskDialogVehicle = veh
                        showAddTaskDialog = true
                    }
                )
            }
        }

    // ════════════════════════════════════════════════
    // MECHANIKOVO ROZHRANÍ — Top Tabs (beze změny)
    // ════════════════════════════════════════════════
    } else if (!isAdmin && !isDesktopMode) {
        Scaffold(topBar = topBar) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Navy900)
                    .padding(paddingValues)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    backgroundColor = Navy800,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Blue500,
                            height = 3.dp
                        )
                    }
                ) {
                    mechanicTabTitles.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { localSelectedTab = index }) {
                            Text(
                                title,
                                modifier = Modifier.padding(vertical = 16.dp),
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == index) Blue50 else Slate500,
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                AdminContent(
                    selectedTab = selectedTab,
                    tasks = tasks,
                    currentUserId = currentUserId,
                    isAdmin = false,
                    onTaskClick = onTaskClick,
                    onShowAddDialog = {},
                    onPrefillAddDialog = { _, _ -> }
                )
            }
        }

    // ════════════════════════════════════════════════
    // DESKTOP REŽIM — předává tab index nahoru
    // ════════════════════════════════════════════════
    } else {
        // Desktop: DashboardScreen slouží jen jako obsah (sidebar je v App.kt)
        Scaffold(backgroundColor = Navy900) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Navy900)
                    .padding(paddingValues)
            ) {
                AdminContent(
                    selectedTab = selectedTab,
                    tasks = tasks,
                    currentUserId = currentUserId,
                    isAdmin = isAdmin,
                    onTaskClick = onTaskClick,
                    onShowAddDialog = { showAddTaskDialog = true },
                    onPrefillAddDialog = { cust, veh -> 
                        taskDialogCustomer = cust
                        taskDialogVehicle = veh
                        showAddTaskDialog = true
                    }
                )
            }
        }
    }

    // ── Dialogy ──
    if (showAddTaskDialog) {
        AddTaskDialog(
            initialCustomer = taskDialogCustomer,
            initialVehicle = taskDialogVehicle,
            onDismiss = { 
                showAddTaskDialog = false
                taskDialogCustomer = null
                taskDialogVehicle = null
            },
            onTaskAdded = { title, brand, desc, cust, phone, email, address, spz, vin, empId ->
                val newId = (AppDatabase.tasks.size + 1).toString()
                val now = com.zakazky.app.common.utils.getCurrentTimestamp()
                AppDatabase.tasks.add(
                    Task(
                        id = newId,
                        title = title,
                        brand = brand,
                        description = desc,
                        customerName = cust,
                        customerPhone = phone,
                        customerEmail = email,
                        customerAddress = address,
                        spz = spz,
                        vin = vin,
                        status = if (empId != null) TaskStatus.IN_PROGRESS else TaskStatus.AVAILABLE,
                        assignedTo = empId,
                        createdBy = currentUserId,
                        createdAt = now,
                        startedAt = if (empId != null) now else null
                    )
                )
                
                // NOTIFIKACE: Pokud byla zakázka ihned po vytvoření přidělena mechanikovi
                if (empId != null) {
                    AppDatabase.notifications.add(
                        com.zakazky.app.common.models.UserNotification(
                            id = "n_${com.zakazky.app.common.utils.getCurrentTimestamp()}",
                            message = "Byla vám vytvořena a rovnou přidělena zakázka: $title",
                            timestamp = com.zakazky.app.common.utils.getCurrentTimestamp(),
                            isRead = false,
                            targetUserId = empId
                        )
                    )
                }

                AppDatabase.save()
                showAddTaskDialog = false
                taskDialogCustomer = null
                taskDialogVehicle = null
            }
        )
    }

    if (showNotificationsDialog) {
        val currentId = AppDatabase.currentUser.id
        val myNotifications = AppDatabase.notifications.filter {
            it.targetUserId.isEmpty() || it.targetUserId == currentId
        }

        // Pomocná funkce: označí notifikace jako přečtené BEZ modifikace listu při iteraci
        fun markAllAsRead() {
            val updated = AppDatabase.notifications.map { notif ->
                if (!notif.isRead && (notif.targetUserId.isEmpty() || notif.targetUserId == currentId)) {
                    notif.copy(isRead = true)
                } else notif
            }
            AppDatabase.notifications.clear()
            AppDatabase.notifications.addAll(updated)
            AppDatabase.save()
        }

        AlertDialog(
            onDismissRequest = {
                showNotificationsDialog = false
                markAllAsRead()
            },
            backgroundColor = Navy800,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Upozornění", color = Blue50, fontWeight = FontWeight.Bold) },
            text = {
                if (myNotifications.isEmpty()) {
                    Text("Zatím nemáte žádná upozornění.", color = Blue200)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(myNotifications.reversed()) { notif ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (notif.isRead) Navy700 else Navy600,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(notif.message, color = Blue50, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationsDialog = false
                    markAllAsRead()
                }) {
                    Text("Zavřít", color = Blue500, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

}

// ════════════════════════════════════════════════════
// Sdílený obsah pro admin i mechanika — zobrazí správnou sekci
// ════════════════════════════════════════════════════
@Composable
private fun AdminContent(
    selectedTab: Int,
    tasks: androidx.compose.runtime.snapshots.SnapshotStateList<Task>,
    currentUserId: String,
    isAdmin: Boolean,
    onTaskClick: (Task) -> Unit,
    onShowAddDialog: () -> Unit,
    onPrefillAddDialog: (com.zakazky.app.common.models.CustomerProfile?, com.zakazky.app.common.models.Task?) -> Unit
) {
    if (selectedTab == 0 || selectedTab == 1 || selectedTab == 3) {
        val filteredTasks by remember(selectedTab, currentUserId, isAdmin) {
            derivedStateOf {
                tasks.filter { task ->
                    val isForMe = isAdmin || task.status == TaskStatus.AVAILABLE || task.assignedTo == currentUserId
                    val isCorrectTab = when (selectedTab) {
                        // Tab 0 a 1: smazané zakázky nezobrazujeme
                        0 -> task.status != TaskStatus.COMPLETED && !task.isDeleted
                        1 -> if (isAdmin) task.status == TaskStatus.COMPLETED && !task.isInvoiceClosed && !task.isDeleted
                             else task.status == TaskStatus.COMPLETED && !task.isDeleted
                        // Tab 3 (Faktury): ZOBRAZUJEME i smazané — budou označeny červeným štítkem
                        3 -> task.isInvoiceClosed
                        else -> false
                    }
                    isForMe && isCorrectTab
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            val titleText = when (selectedTab) {
                0 -> "Aktuální zakázky"
                1 -> if (isAdmin) "Historie nezfakturovaných zakázek" else "Moje dokončené zakázky"
                3 -> "Databáze faktur"
                else -> ""
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(titleText, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = Blue50)
                if (isAdmin && selectedTab == 0) {
                    Button(
                        onClick = onShowAddDialog,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Blue600),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Přidat", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    TaskCard(task = task, onClick = { onTaskClick(task) }, isAdmin = isAdmin)
                }
            }
        }
    } else if (isAdmin && selectedTab == 2) {
        // Zakázníci: smazané zakázky NEzobrazujeme
        val nonDeletedTasks = androidx.compose.runtime.snapshots.SnapshotStateList<Task>().also { list ->
            list.addAll(tasks.filter { !it.isDeleted })
        }
        CustomersScreen(tasks = nonDeletedTasks, onPrefillAddDialog = onPrefillAddDialog, onTaskClick = onTaskClick)
    } else if (isAdmin && selectedTab == 4) {
        EmployeeManagement()
    } else if (isAdmin && selectedTab == 5) {
        // ── KOŠ ──
        TrashScreen(tasks = tasks)
    }
}



@Composable
fun TaskCard(task: Task, onClick: () -> Unit, isAdmin: Boolean) {
    val statusColor = when (task.status) {
        TaskStatus.AVAILABLE -> SuccessLight
        TaskStatus.IN_PROGRESS -> Blue400
        TaskStatus.COMPLETED -> Slate300
    }

    // Jméno přiděleného mechanika (pokud existuje)
    val assignedMechanicName = remember(task.assignedTo) {
        task.assignedTo?.let { id -> AppDatabase.users.find { it.id == id }?.name }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).border(1.dp, Navy700, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = if (task.isDeleted) ErrorDark.copy(alpha = 0.08f) else Navy800,
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Badge "Přiděleno – Jméno" nahoře (zobrazí se jen když je zakázka IN_PROGRESS a má mechanika)
            if (!task.isDeleted && task.status == TaskStatus.IN_PROGRESS && assignedMechanicName != null) {
                Box(
                    modifier = Modifier
                        .background(Blue500.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(1.dp, Blue500.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "👷 Přiděleno – $assignedMechanicName",
                        color = Blue200,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = if (task.isDeleted) Blue200 else Blue50)
                if (task.isDeleted) {
                    // Červený štítek "Připraveno k vymazání"
                    Box(
                        modifier = Modifier
                            .background(ErrorDark.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .border(1.dp, ErrorDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🗑️ Připraveno k vymazání", color = ErrorDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(modifier = Modifier.background(statusColor.copy(alpha=0.2f), RoundedCornerShape(8.dp)).border(1.dp, statusColor, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(task.status.displayName, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(icon = Icons.Default.Person, text = task.customerName)
                InfoChip(icon = Icons.Default.Settings, text = task.spz)
            }
        }
    }
}
