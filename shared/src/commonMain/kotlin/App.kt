import androidx.compose.runtime.*
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.models.Role
import com.zakazky.app.common.models.Task
import com.zakazky.app.common.models.TaskStatus
import com.zakazky.app.common.models.TimeLog
import com.zakazky.app.common.models.ReworkLog
import com.zakazky.app.common.theme.ZakazkyTheme
import com.zakazky.app.common.ui.DashboardScreen
import com.zakazky.app.common.ui.LoginScreen
import com.zakazky.app.common.ui.TaskDetailScreen
import com.zakazky.app.common.ui.ReworkDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.Image

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
fun App() {
    ZakazkyTheme {
        // Navy900 pozadí sahá za status bar — bez bílého pruhu nahoře
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(com.zakazky.app.common.theme.Navy900)
        ) {
        var isLoggedIn by remember { mutableStateOf(false) }
        var isAdmin by remember { mutableStateOf(true) }
        var selectedTask by remember { mutableStateOf<Task?>(null) }
        var showReworkDialog by remember { mutableStateOf(false) }
        var showProfileScreen by remember { mutableStateOf(false) }

        if (!isLoggedIn) {
            LoginScreen(
                onLoginSuccess = { loggedInUser ->
                    AppDatabase.currentUser = loggedInUser
                    isAdmin = loggedInUser.role == Role.ADMIN
                    isLoggedIn = true
                }
            )
        } else {
            var desktopSelectedTab by remember { mutableStateOf(0) }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isDesktop = maxWidth > 960.dp  // 960dp = nad iPhone landscape, pod iPad
                val contentView: @Composable () -> Unit = {
                    if (selectedTask != null) {
            TaskDetailScreen(
                task = selectedTask!!,
                isAdmin = isAdmin,
                onBack = { selectedTask = null },
                onTakeTask = {
                    val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                    if (index != -1) {
                        val updatedTask = selectedTask!!.copy(
                            assignedTo = AppDatabase.currentUser.id,
                            status = TaskStatus.IN_PROGRESS,
                            startedAt = com.zakazky.app.common.utils.getCurrentTimestamp()
                        )
                        AppDatabase.tasks[index] = updatedTask
                        AppDatabase.save()
                        selectedTask = updatedTask
                    }
                },
                onAssignTask = { employeeId ->
                    val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                    if (index != -1) {
                        val updatedTask = selectedTask!!.copy(
                            assignedTo = employeeId,
                            status = TaskStatus.IN_PROGRESS,
                            startedAt = com.zakazky.app.common.utils.getCurrentTimestamp()
                        )
                        AppDatabase.tasks[index] = updatedTask
                        AppDatabase.save()
                        selectedTask = updatedTask
                    }
                },
                onCancelTask = {
                    val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                    if (index != -1) {
                        val updatedTask = selectedTask!!.copy(
                            assignedTo = null,
                            status = TaskStatus.AVAILABLE
                        )
                        AppDatabase.tasks[index] = updatedTask
                        AppDatabase.save()
                    }
                    selectedTask = null 
                },
                onSaveProgress = { hoursStr, elecHoursStr, kmStr, newPhotos, newItems, mechStr, elecStr, mechHourlyRateStr, elecHourlyRateStr -> 
                    val hours = hoursStr.toDoubleOrNull() ?: 0.0
                    val elecHours = elecHoursStr.toDoubleOrNull() ?: 0.0
                    val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                    if (index != -1) {
                        val currentUser = AppDatabase.currentUser
                        val updatedTask = selectedTask!!.copy(
                            status = TaskStatus.IN_PROGRESS,
                            localPhotos = newPhotos,
                            invoiceItems = newItems,
                            mechanicWorkPrice = mechStr.toDoubleOrNull() ?: selectedTask!!.mechanicWorkPrice,
                            electricWorkPrice = elecStr.toDoubleOrNull() ?: selectedTask!!.electricWorkPrice,
                            mechanicHourlyRate = mechHourlyRateStr.toDoubleOrNull() ?: selectedTask!!.mechanicHourlyRate,
                            electricHourlyRate = elecHourlyRateStr.toDoubleOrNull() ?: selectedTask!!.electricHourlyRate,
                            timeLogs = if (hours > 0) selectedTask!!.timeLogs + TimeLog(
                                id = "log${selectedTask!!.timeLogs.size + 1}",
                                employeeId = currentUser.id,
                                employeeName = currentUser.name,
                                hours = hours
                            ) else selectedTask!!.timeLogs,
                            electricTimeLogs = if (elecHours > 0) selectedTask!!.electricTimeLogs + TimeLog(
                                id = "elog${selectedTask!!.electricTimeLogs.size + 1}",
                                employeeId = currentUser.id,
                                employeeName = currentUser.name,
                                hours = elecHours
                            ) else selectedTask!!.electricTimeLogs,
                            vehicleKm = kmStr.toIntOrNull() ?: selectedTask!!.vehicleKm
                        )
                        AppDatabase.tasks[index] = updatedTask
                        
                        val userIndex = AppDatabase.users.indexOfFirst { it.id == currentUser.id }
                        if (userIndex != -1 && hours > 0) {
                            val u = AppDatabase.users[userIndex]
                            AppDatabase.users[userIndex] = u.copy(totalHoursLogged = u.totalHoursLogged + hours)
                            AppDatabase.currentUser = AppDatabase.users[userIndex]
                        }
                        AppDatabase.save()
                    }
                    selectedTask = null 
                },
                onGenerateInvoice = { hoursStr, elecHoursStr, kmStr, newPhotos, newItems, mechStr, elecStr, mechHourlyRateStr, elecHourlyRateStr -> 
                    val hours = hoursStr.toDoubleOrNull() ?: 0.0
                    val elecHours = elecHoursStr.toDoubleOrNull() ?: 0.0
                    val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                    if (index != -1) {
                        val currentUser = AppDatabase.currentUser
                        val updatedTask = selectedTask!!.copy(
                            isInvoiceClosed = true,
                            localPhotos = newPhotos,
                            invoiceItems = newItems,
                            mechanicWorkPrice = mechStr.toDoubleOrNull() ?: selectedTask!!.mechanicWorkPrice,
                            electricWorkPrice = elecStr.toDoubleOrNull() ?: selectedTask!!.electricWorkPrice,
                            mechanicHourlyRate = mechHourlyRateStr.toDoubleOrNull() ?: selectedTask!!.mechanicHourlyRate,
                            electricHourlyRate = elecHourlyRateStr.toDoubleOrNull() ?: selectedTask!!.electricHourlyRate,
                            timeLogs = if (hours > 0) selectedTask!!.timeLogs + TimeLog(
                                id = "log${selectedTask!!.timeLogs.size + 1}",
                                employeeId = currentUser.id,
                                employeeName = currentUser.name,
                                hours = hours
                            ) else selectedTask!!.timeLogs,
                            electricTimeLogs = if (elecHours > 0) selectedTask!!.electricTimeLogs + TimeLog(
                                id = "elog${selectedTask!!.electricTimeLogs.size + 1}",
                                employeeId = currentUser.id,
                                employeeName = currentUser.name,
                                hours = elecHours
                            ) else selectedTask!!.electricTimeLogs,
                            vehicleKm = kmStr.toIntOrNull() ?: selectedTask!!.vehicleKm
                        )
                        AppDatabase.tasks[index] = updatedTask
                        
                        val userIndex = AppDatabase.users.indexOfFirst { it.id == currentUser.id }
                        if (userIndex != -1 && hours > 0) {
                            val u = AppDatabase.users[userIndex]
                            AppDatabase.users[userIndex] = u.copy(totalHoursLogged = u.totalHoursLogged + hours)
                            AppDatabase.currentUser = AppDatabase.users[userIndex]
                        }
                        AppDatabase.save()
                    }
                    selectedTask = null 
                },
                onComplete = { hoursStr, elecHoursStr, kmStr, newPhotos, newItems, mechStr, elecStr, mechHourlyRateStr, elecHourlyRateStr -> 
                    val hours = hoursStr.toDoubleOrNull() ?: 0.0
                    val elecHours = elecHoursStr.toDoubleOrNull() ?: 0.0
                    val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                    if (index != -1) {
                        val currentUser = AppDatabase.currentUser
                        val updatedTask = selectedTask!!.copy(
                            status = TaskStatus.COMPLETED,
                            completedAt = com.zakazky.app.common.utils.getCurrentTimestamp(),
                            localPhotos = newPhotos,
                            invoiceItems = newItems,
                            mechanicWorkPrice = mechStr.toDoubleOrNull() ?: selectedTask!!.mechanicWorkPrice,
                            electricWorkPrice = elecStr.toDoubleOrNull() ?: selectedTask!!.electricWorkPrice,
                            mechanicHourlyRate = mechHourlyRateStr.toDoubleOrNull() ?: selectedTask!!.mechanicHourlyRate,
                            electricHourlyRate = elecHourlyRateStr.toDoubleOrNull() ?: selectedTask!!.electricHourlyRate,
                            timeLogs = if (hours > 0) selectedTask!!.timeLogs + TimeLog(
                                id = "log${selectedTask!!.timeLogs.size + 1}",
                                employeeId = currentUser.id,
                                employeeName = currentUser.name,
                                hours = hours
                            ) else selectedTask!!.timeLogs,
                            electricTimeLogs = if (elecHours > 0) selectedTask!!.electricTimeLogs + TimeLog(
                                id = "elog${selectedTask!!.electricTimeLogs.size + 1}",
                                employeeId = currentUser.id,
                                employeeName = currentUser.name,
                                hours = elecHours
                            ) else selectedTask!!.electricTimeLogs,
                            vehicleKm = kmStr.toIntOrNull() ?: selectedTask!!.vehicleKm
                        )
                        AppDatabase.tasks[index] = updatedTask
                        
                        val userIndex = AppDatabase.users.indexOfFirst { it.id == currentUser.id }
                        if (userIndex != -1 && hours > 0) {
                            val u = AppDatabase.users[userIndex]
                            AppDatabase.users[userIndex] = u.copy(totalHoursLogged = u.totalHoursLogged + hours)
                            AppDatabase.currentUser = AppDatabase.users[userIndex]
                        }
                        AppDatabase.save()
                    }
                    selectedTask = null 
                },
                onReworkClick = { showReworkDialog = true },
                onDocumentAttached = { newDoc ->
                    val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                    if (index != -1) {
                        val updatedTask = selectedTask!!.copy(
                            attachedDocuments = selectedTask!!.attachedDocuments + newDoc
                        )
                        AppDatabase.tasks[index] = updatedTask
                        AppDatabase.save()
                        selectedTask = updatedTask
                    }
                },
                onTaskImageAttached = { newBytes ->
                    val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                    if (index != -1) {
                        val updatedTask = selectedTask!!.copy(
                            taskImages = selectedTask!!.taskImages + newBytes
                        )
                        AppDatabase.tasks[index] = updatedTask
                        AppDatabase.save()
                        selectedTask = updatedTask
                    }
                }
            )
            
            if (showReworkDialog) {
                ReworkDialog(
                    onDismiss = { showReworkDialog = false },
                    onSubmit = { solverId, solverHours, guiltyId, penaltyHours, note ->
                        val index = AppDatabase.tasks.indexOfFirst { it.id == selectedTask!!.id }
                        if (index != -1) {
                            val solver = AppDatabase.users.find { it.id == solverId }
                            val guilty = AppDatabase.users.find { it.id == guiltyId }
                            
                            if (solver != null && guilty != null) {
                                val reworkLog = ReworkLog(
                                    id = "rework${selectedTask!!.reworks.size + 1}",
                                    solverId = solverId,
                                    solverName = solver.name,
                                    solverHours = solverHours,
                                    guiltyId = guiltyId,
                                    guiltyName = guilty.name,
                                    penaltyHours = penaltyHours,
                                    note = note
                                )
                                val updatedTask = selectedTask!!.copy(
                                    reworks = selectedTask!!.reworks + reworkLog
                                )
                                AppDatabase.tasks[index] = updatedTask
                                selectedTask = updatedTask
                                
                                val solverIndex = AppDatabase.users.indexOfFirst { it.id == solverId }
                                if (solverIndex != -1) {
                                    val u = AppDatabase.users[solverIndex]
                                    AppDatabase.users[solverIndex] = u.copy(totalHoursLogged = u.totalHoursLogged + solverHours)
                                }
                                val guiltyIndex = AppDatabase.users.indexOfFirst { it.id == guiltyId }
                                if (guiltyIndex != -1) {
                                    val u = AppDatabase.users[guiltyIndex]
                                    AppDatabase.users[guiltyIndex] = u.copy(totalHoursLogged = u.totalHoursLogged - penaltyHours) 
                                }
                                AppDatabase.save()
                            }
                        }
                        showReworkDialog = false
                    }
                )
            }
                    } else if (showProfileScreen) {
                        com.zakazky.app.common.ui.ProfileScreen(
                            onBack = { showProfileScreen = false },
                            onLogout = {
                                showProfileScreen = false
                                isLoggedIn = false
                                AppDatabase.currentUser = com.zakazky.app.common.models.User(role = Role.EMPLOYEE)
                            }
                        )
                    } else {
                        DashboardScreen(
                            isAdmin = isAdmin,
                            isDesktopMode = isDesktop,
                            desktopSelectedTab = desktopSelectedTab,
                            onDesktopTabSelected = { desktopSelectedTab = it },
                            onTaskClick = { task -> selectedTask = task },
                            onProfileClick = { showProfileScreen = true },
                            onAddTaskClick = { /* Handled in DashboardScreen */ }
                        )
                    }
                } // end contentView

                if (isDesktop) {
                    // Sidebar state — expanded nebo collapsed (jen ikony)
                    var sidebarExpanded by remember { mutableStateOf(true) }
                    val sidebarWidth by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (sidebarExpanded) 260.dp else 68.dp,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        ),
                        label = "sidebarWidth"
                    )
                    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (sidebarExpanded) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "contentAlpha"
                    )

                    // Nav položky s ikonami
                    data class NavItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
                    val navItems = if (isAdmin) listOf(
                        NavItem("Nástěnka", androidx.compose.material.icons.Icons.Default.Home),
                        NavItem("Historie", androidx.compose.material.icons.Icons.Default.DateRange),
                        NavItem("Zákazníci", androidx.compose.material.icons.Icons.Default.Person),
                        NavItem("Faktury", androidx.compose.material.icons.Icons.Default.List),
                        NavItem("Zaměstnanci", androidx.compose.material.icons.Icons.Default.Settings),
                        NavItem("Koš", androidx.compose.material.icons.Icons.Default.Delete)
                    ) else listOf(
                        NavItem("Nástěnka", androidx.compose.material.icons.Icons.Default.Home),
                        NavItem("Historie", androidx.compose.material.icons.Icons.Default.DateRange)
                    )

                    Row(modifier = Modifier.fillMaxSize().background(com.zakazky.app.common.theme.Navy900)) {
                        // ═══ LEVÝ SIDEBAR ═══
                        Column(
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight()
                                .background(
                                    Brush.verticalGradient(listOf(
                                        com.zakazky.app.common.theme.PrimaryGradientStart,
                                        com.zakazky.app.common.theme.PrimaryGradientEnd
                                    ))
                                )
                        ) {
                            // ── HEADER: logo + hamburger ──
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (sidebarExpanded) {
                                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                        Text(
                                            "Zempro CZ",
                                            color = com.zakazky.app.common.theme.ZemproGreen,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            "Správa zakázek",
                                            color = com.zakazky.app.common.theme.Blue200,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                // Hamburger tlačítko
                                IconButton(
                                    onClick = { sidebarExpanded = !sidebarExpanded },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(com.zakazky.app.common.theme.Navy900.copy(alpha = 0.3f))
                                ) {
                                    Icon(
                                        imageVector = if (sidebarExpanded)
                                            androidx.compose.material.icons.Icons.Default.ArrowBack
                                        else
                                            androidx.compose.material.icons.Icons.Default.ArrowForward,
                                        contentDescription = "Rozbalit/sbalit menu",
                                        tint = com.zakazky.app.common.theme.Blue50,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // ── LOGO (jen když je rozbaleno) ──
                            if (sidebarExpanded) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = sidebarExpanded,
                                    enter = androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.fadeOut()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Image(
                                            painter = org.jetbrains.compose.resources.painterResource("drawable/app_logo.png"),
                                            contentDescription = "Zempro Logo",
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, com.zakazky.app.common.theme.ZemproGreen.copy(alpha = 0.6f), CircleShape)
                                        )
                                    }
                                }
                            }

                            // ── ODDĚLOVAČ ──
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = if (sidebarExpanded) 16.dp else 8.dp)
                                    .height(1.dp)
                                    .background(com.zakazky.app.common.theme.Blue500.copy(alpha = 0.2f))
                            )
                            Spacer(Modifier.height(12.dp))

                            // ── NAVIGAČNÍ POLOŽKY ──
                            navItems.forEachIndexed { index, item ->
                                val isSelected = (desktopSelectedTab == index && selectedTask == null && !showProfileScreen)
                                val itemBg = if (isSelected)
                                    com.zakazky.app.common.theme.Blue600.copy(alpha = 0.35f)
                                else
                                    Color.Transparent
                                val indicatorColor = if (isSelected)
                                    com.zakazky.app.common.theme.ZemproGreen
                                else
                                    Color.Transparent

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(itemBg)
                                        .clickable {
                                            desktopSelectedTab = index
                                            selectedTask = null
                                            showProfileScreen = false
                                        }
                                        .padding(horizontal = if (sidebarExpanded) 12.dp else 0.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = if (sidebarExpanded) Arrangement.Start else Arrangement.Center
                                ) {
                                    // Barevný indikátor (levá čára)
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(indicatorColor)
                                    )
                                    Spacer(Modifier.width(if (sidebarExpanded) 10.dp else 0.dp))
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = if (isSelected) Color.White else com.zakazky.app.common.theme.Blue200,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (sidebarExpanded) {
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            item.title,
                                            color = if (isSelected) Color.White else com.zakazky.app.common.theme.Blue50,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 15.sp,
                                            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                                        )
                                        
                                        // Odznak pro Historii (Dokončené, nevyfakturované zakázky)
                                        if (item.title == "Historie" && isAdmin) {
                                            val completedTasksCount = AppDatabase.tasks.count { it.status == com.zakazky.app.common.models.TaskStatus.COMPLETED && !it.isInvoiceClosed && !it.isDeleted }
                                            if (completedTasksCount > 0) {
                                                Spacer(Modifier.weight(1f))
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(com.zakazky.app.common.theme.ErrorDark),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (completedTasksCount > 9) "9+" else completedTasksCount.toString(),
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                        }
                                        // Odznak pro Koš (počet smazaných zakázek)
                                        if (item.title == "Koš" && isAdmin) {
                                            val deletedCount = AppDatabase.deletedTasksCount
                                            if (deletedCount > 0) {
                                                Spacer(Modifier.weight(1f))
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(com.zakazky.app.common.theme.ErrorDark),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (deletedCount > 9) "9+" else deletedCount.toString(),
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                        }
                                    } else {
                                        // Sbalený sidebar — jen tečka (bez čísla)
                                        if (item.title == "Historie" && isAdmin) {
                                            val completedTasksCount = AppDatabase.tasks.count { it.status == com.zakazky.app.common.models.TaskStatus.COMPLETED && !it.isInvoiceClosed && !it.isDeleted }
                                            if (completedTasksCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.Top)
                                                        .offset(x = (-4).dp, y = (-4).dp)
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(com.zakazky.app.common.theme.ErrorDark)
                                                )
                                            }
                                        }
                                        if (item.title == "Koš" && isAdmin) {
                                            val deletedCount = AppDatabase.deletedTasksCount
                                            if (deletedCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.Top)
                                                        .offset(x = (-4).dp, y = (-4).dp)
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(com.zakazky.app.common.theme.ErrorDark)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }

                            Spacer(Modifier.weight(1f))

                            // ── ODDĚLOVAČ PŘED PROFILEM ──
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = if (sidebarExpanded) 16.dp else 8.dp)
                                    .height(1.dp)
                                    .background(com.zakazky.app.common.theme.Blue500.copy(alpha = 0.2f))
                            )
                            Spacer(Modifier.height(8.dp))

                            // ── PROFIL TLAČÍTKO ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (showProfileScreen)
                                            com.zakazky.app.common.theme.Blue600.copy(alpha = 0.35f)
                                        else Color.Transparent
                                    )
                                    .clickable { showProfileScreen = true; selectedTask = null }
                                    .padding(horizontal = if (sidebarExpanded) 12.dp else 0.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (sidebarExpanded) Arrangement.Start else Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .border(2.dp, com.zakazky.app.common.theme.Blue500.copy(alpha = 0.5f), CircleShape)
                                        .padding(2.dp)
                                ) {
                                    com.zakazky.app.common.ui.UserAvatar(
                                        user = AppDatabase.currentUser,
                                        size = 32.dp,
                                        isBoss = isAdmin
                                    )
                                }
                                if (sidebarExpanded) {
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
                                        Text(
                                            AppDatabase.currentUser.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            if (isAdmin) "Administrátor" else "Zaměstnanec",
                                            color = com.zakazky.app.common.theme.Blue200,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // ═══ HLAVNÍ OBSAH ═══
                        Box(modifier = Modifier.weight(1f)) {
                            contentView()
                        }
                    }
                } else {
                    contentView()
                }
            } // close BoxWithConstraints
        } // close else (isLoggedIn)
        } // close Navy900 Box
    } // close ZakazkyTheme
} // close App()

expect fun getPlatformName(): String
