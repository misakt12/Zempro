package com.zakazky.app.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakazky.app.common.models.CustomerProfile
import com.zakazky.app.common.models.Task
import com.zakazky.app.common.theme.*
import com.zakazky.app.common.utils.formatTimestamp

@Composable
fun CustomersScreen(
    tasks: List<Task>,
    onPrefillAddDialog: (CustomerProfile?, Task?) -> Unit,
    onTaskClick: (Task) -> Unit
) {
    // 1. Grouping to extract customers dynamically
    val profiles = remember(tasks) {
        val groups = tasks.groupBy { it.customerName.normalizeForIdentifier() }.filter { it.key.isNotEmpty() }
        groups.map { (_, userTasks) ->
            // use the most recent valid details
            val sortedByTime = userTasks.sortedByDescending { it.createdAt }
            val latestWithPhone = sortedByTime.firstOrNull { it.customerPhone.isNotBlank() } ?: userTasks.first()
            val latestWithEmail = sortedByTime.firstOrNull { it.customerEmail.isNotBlank() } ?: userTasks.first()
            val latestWithAddr = sortedByTime.firstOrNull { it.customerAddress.isNotBlank() } ?: userTasks.first()
            
            // Heuristika pro jméno: zkusit najít variantu s diakritikou (česky správně), jinak vzít nejnovější
            val hasDiacritics = { str: String -> str.any { it in "áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ" } }
            val bestName = sortedByTime.map { it.customerName.trim() }
                .firstOrNull { hasDiacritics(it) } ?: sortedByTime.first().customerName.trim()
            
            // Build garage: group by VIN or SPZ
            val garageGroups = sortedByTime.groupBy { task ->
               val vinRaw = task.vin ?: ""
               if (vinRaw.isNotBlank()) vinRaw else task.spz
            }.filter { it.key.isNotBlank() }
            
            val garage = garageGroups.values.map { it.first() }

            CustomerProfile(
                name = bestName,
                phone = latestWithPhone.customerPhone,
                email = latestWithEmail.customerEmail,
                address = latestWithAddr.customerAddress,
                historyTasks = sortedByTime,
                garageVehicles = garage
            )
        }.sortedBy { it.name }
    }

    var selectedProfile by remember { mutableStateOf<CustomerProfile?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredProfiles = remember(profiles, searchQuery) {
        if (searchQuery.isBlank()) profiles else profiles.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.phone.contains(searchQuery, ignoreCase = true) ||
            it.email.contains(searchQuery, ignoreCase = true) ||
            it.garageVehicles.any { v -> v.spz.contains(searchQuery, ignoreCase=true) || v.vin?.contains(searchQuery, ignoreCase=true) == true }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Navy900)) {
        val isWide = maxWidth > 800.dp
        
        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Levé menu - seznam zákazníků
                Column(modifier = Modifier.width(350.dp).fillMaxHeight().border(1.dp, Navy700).padding(16.dp)) {
                    CustomerSearchBox(searchQuery) { searchQuery = it }
                    Spacer(Modifier.height(16.dp))
                    CustomerList(filteredProfiles, selectedProfile) { selectedProfile = it }
                }
                
                // Pravá část - Detail zákazníka
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    if (selectedProfile != null) {
                        CustomerDetail(
                            profile = selectedProfile!!,
                            onClose = { selectedProfile = null },
                            isWide = true,
                            onAddCar = { onPrefillAddDialog(selectedProfile, null) },
                            onNewTaskForCar = { car -> onPrefillAddDialog(selectedProfile, car) },
                            onTaskClick = onTaskClick
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = Slate500)
                            Spacer(Modifier.height(16.dp))
                            Text("Vyberte zákazníka ze seznamu vlevo", color = Slate400)
                        }
                    }
                }
            }
        } else {
            // Úzké zobrazení (mobil)
            if (selectedProfile == null) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    CustomerSearchBox(searchQuery) { searchQuery = it }
                    Spacer(Modifier.height(16.dp))
                    CustomerList(filteredProfiles, null) { selectedProfile = it }
                }
            } else {
                CustomerDetail(
                    profile = selectedProfile!!,
                    onClose = { selectedProfile = null },
                    isWide = false,
                    onAddCar = { onPrefillAddDialog(selectedProfile, null) },
                    onNewTaskForCar = { car -> onPrefillAddDialog(selectedProfile, car) },
                    onTaskClick = onTaskClick
                )
            }
        }
    }
}

@Composable
fun CustomerSearchBox(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Hledat zákazníka...", color = Slate500) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.White,
            backgroundColor = Navy800,
            cursorColor = Blue500,
            focusedBorderColor = Blue500,
            unfocusedBorderColor = Navy700
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
fun CustomerList(profiles: List<CustomerProfile>, selected: CustomerProfile?, onSelect: (CustomerProfile) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(profiles, key = { "${it.name}_${it.hashCode()}" }) { profile ->
            val isSelected = selected == profile
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(profile) }.border(1.dp, Navy700, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = if (isSelected) Blue600.copy(alpha=0.3f) else Navy800,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Blue500) else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    if (profile.phone.isNotBlank()) {
                        Text(profile.phone, color = Slate400, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoChip(icon = Icons.Default.DateRange, text = "${profile.historyTasks.size} zakázek")
                        InfoChip(icon = Icons.Default.Build, text = "${profile.garageVehicles.size} aut")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetail(
    profile: CustomerProfile,
    onClose: () -> Unit,
    isWide: Boolean,
    onAddCar: () -> Unit,
    onNewTaskForCar: (Task) -> Unit,
    onTaskClick: (Task) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy900)
            .verticalScroll(scrollState)
    ) {
        // Hlavička
        Row(
            modifier = Modifier.fillMaxWidth().background(Navy800).padding(if (isWide) 24.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isWide) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Zpět", tint = Blue50) }
                Spacer(Modifier.width(8.dp))
            }
            Box(modifier = Modifier.size(56.dp).background(Blue600, CircleShape), contentAlignment = Alignment.Center) {
                val initial = profile.name.take(1).uppercase()
                Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (profile.phone.isNotBlank()) ContactItem(Icons.Default.Phone, profile.phone)
                    if (profile.email.isNotBlank()) ContactItem(Icons.Default.Email, profile.email)
                }
                if (profile.address.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    ContactItem(Icons.Default.LocationOn, profile.address)
                }
            }
            if (isWide) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = Slate400) }
            }
        }

        // Garáž
        val pad = if (isWide) 24.dp else 16.dp
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Garáž", color = Blue50, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Button(
                onClick = onAddCar,
                colors = ButtonDefaults.buttonColors(backgroundColor = ZemproGreen),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Přijel s novým", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        profile.garageVehicles.forEach { vehicle ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad).padding(bottom = 12.dp)
                    .border(1.dp, Navy700, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = Navy800
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        val brandAndModel = "${vehicle.brand} ${vehicle.title.split(" - ").firstOrNull() ?: ""}".trim()
                        Text(brandAndModel.takeIf { it.isNotBlank() } ?: "Vozidlo bez modelu", color = Blue50, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (vehicle.spz.isNotBlank()) InfoChip(Icons.Default.Info, vehicle.spz)
                            if (vehicle.vin?.isNotBlank() == true) InfoChip(Icons.Default.Settings, vehicle.vin)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { onNewTaskForCar(vehicle) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Blue600),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Nová zakázka", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Historie
        Spacer(Modifier.height(24.dp))
        Divider(color = Navy700, modifier = Modifier.padding(horizontal = pad))
        Spacer(Modifier.height(24.dp))
        Text("Historie zakázek", color = Blue50, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = pad))
        Spacer(Modifier.height(12.dp))

        profile.historyTasks.forEach { task ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad).padding(bottom = 8.dp)
                    .clickable { onTaskClick(task) },
                shape = RoundedCornerShape(8.dp),
                backgroundColor = Navy800
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.title, color = Blue50, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(formatTimestamp(task.createdAt), color = Slate400, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.background(
                        if (task.isInvoiceClosed) SuccessLight.copy(alpha = 0.2f) else Navy700,
                        RoundedCornerShape(4.dp)
                    ).padding(6.dp)) {
                        Text(
                            if (task.isInvoiceClosed) "Zaplaceno" else "Otevřeno",
                            color = if (task.isInvoiceClosed) SuccessLight else Slate300,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}


@Composable
fun ContactItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Slate400, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Slate300, fontSize = 13.sp)
    }
}

private fun String.normalizeForIdentifier(): String {
    val lower = this.lowercase()
    var normalized = ""
    for (c in lower) {
        val replacement = when (c) {
            'á' -> 'a'
            'č' -> 'c'
            'ď' -> 'd'
            'é', 'ě' -> 'e'
            'í' -> 'i'
            'ň' -> 'n'
            'ó' -> 'o'
            'ř' -> 'r'
            'š' -> 's'
            'ť' -> 't'
            'ú', 'ů' -> 'u'
            'ý' -> 'y'
            'ž' -> 'z'
            else -> c
        }
        normalized += replacement
    }
    // odstraníme mezery a jiné non-alphanumeric znaky
    return normalized.replace(Regex("[^a-z0-9]"), "")
}
