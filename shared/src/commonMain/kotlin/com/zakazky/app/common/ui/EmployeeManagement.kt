package com.zakazky.app.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.zakazky.app.common.models.Role
import com.zakazky.app.common.models.User
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.theme.*
import com.zakazky.app.common.utils.encodeBase64
import com.zakazky.app.common.utils.decodeBase64
import com.zakazky.app.common.utils.rememberMediaManager
import com.zakazky.app.common.utils.toImageBitmap

@Composable
fun EmployeeManagement() {
    var showAddDialog by remember { mutableStateOf(false) }
    var showMediaDialog by remember { mutableStateOf(false) }
    
    var editingUser by remember { mutableStateOf<User?>(null) }
    var newUserName by remember { mutableStateOf("") }
    var newUserEmail by remember { mutableStateOf("") }
    var newUserPhone by remember { mutableStateOf("") }
    var newUserPin by remember { mutableStateOf("") }
    var newUserPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }
    
    val mockUsers = AppDatabase.users
    
    val mediaManager = rememberMediaManager { newImageBytes ->
        if (newImageBytes != null) {
            newUserPhotoBytes = newImageBytes
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isDesktop = maxWidth > 800.dp
        
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Zaměstnanci a Historie", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = Blue50)
                Button(
                    onClick = { 
                        editingUser = null
                        newUserName = ""
                        newUserEmail = ""
                        newUserPhone = ""
                        newUserPin = ""
                        newUserPhotoBytes = null
                        showAddDialog = true 
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Blue600),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Přidat", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            
            val sortedUsers = mockUsers.sortedByDescending { it.role == Role.ADMIN }
            val activeUsers = sortedUsers.filter { it.isActive }
            val historyUsers = sortedUsers.filter { !it.isActive }

            val onEditUser = { user: User ->
                editingUser = user
                newUserName = user.name
                newUserEmail = user.email
                newUserPhone = user.phone
                newUserPin = user.pin
                newUserPhotoBytes = user.photoUrl?.let { decodeBase64(it) }
                showAddDialog = true
            }

            val onToggleStatus = { user: User ->
                val idx = AppDatabase.users.indexOfFirst { it.id == user.id }
                if (idx != -1) {
                    AppDatabase.users[idx] = user.copy(isActive = !user.isActive)
                    AppDatabase.save()
                }
            }

            if (isDesktop) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text("Aktuální zaměstnanci", color = Blue200, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(activeUsers, key = { it.id }) { user -> EmployeeCard(user, { onEditUser(user) }, { onToggleStatus(user) }) }
                        }
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text("Bývalí zaměstnanci (Historie)", color = Slate400, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(historyUsers, key = { it.id }) { user -> EmployeeCard(user, { onEditUser(user) }, { onToggleStatus(user) }) }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Text("Aktuální zaměstnanci", color = Blue200, fontWeight = FontWeight.Bold) }
                    items(activeUsers, key = { it.id }) { user -> EmployeeCard(user, { onEditUser(user) }, { onToggleStatus(user) }) }
                    if (historyUsers.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item { Text("Historie", color = Slate400, fontWeight = FontWeight.Bold) }
                        items(historyUsers, key = { it.id }) { user -> EmployeeCard(user, { onEditUser(user) }, { onToggleStatus(user) }) }
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        val focusManager = LocalFocusManager.current
        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                editingUser = null
            },
            // OPRAVA: Zabrní náhodnému zavření kliknutím mimo dialog (double-click na fotku, focus ztráta)
            properties = DialogProperties(dismissOnClickOutside = false),
            backgroundColor = Navy800,
            shape = RoundedCornerShape(24.dp),
            title = null,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(if (editingUser != null) "Upravit zaměstnance" else "Nový zaměstnanec", color = Blue50, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
                    
                    Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Navy700).clickable { showMediaDialog = true }, contentAlignment = Alignment.Center) {
                        if (newUserPhotoBytes != null) {
                            val bmp = remember(newUserPhotoBytes) {
                                try { newUserPhotoBytes!!.toImageBitmap() } catch(e: Exception) { null }
                            }
                            if (bmp != null) {
                                Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Blue500)
                                Text("Foto", color = Slate400, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Tab = přechod na další kolonku
                    OutlinedTextField(
                        value = newUserName, onValueChange = { newUserName = it },
                        label = { Text("Jméno", color = Blue200) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Blue50, focusedBorderColor = Blue500, unfocusedBorderColor = Navy700),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newUserEmail, onValueChange = { newUserEmail = it },
                        label = { Text("Email", color = Blue200) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Blue50, focusedBorderColor = Blue500, unfocusedBorderColor = Navy700),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newUserPhone, onValueChange = { newUserPhone = it },
                        label = { Text("Telefon", color = Blue200) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Blue50, focusedBorderColor = Blue500, unfocusedBorderColor = Navy700),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newUserPin, onValueChange = { newUserPin = it },
                        label = { Text("PIN (např. 1234)", color = Blue200) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Blue50, focusedBorderColor = Blue500, unfocusedBorderColor = Navy700),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newUserName.isNotBlank() && newUserPin.isNotBlank()) {
                        val base64Str = if (newUserPhotoBytes != null) encodeBase64(newUserPhotoBytes!!) else editingUser?.photoUrl
                        
                        if (editingUser != null) {
                            val index = AppDatabase.users.indexOfFirst { it.id == editingUser!!.id }
                            if (index != -1) {
                                AppDatabase.users[index] = editingUser!!.copy(
                                    name = newUserName,
                                    email = newUserEmail,
                                    phone = newUserPhone,
                                    pin = newUserPin,
                                    photoUrl = base64Str
                                )
                            }
                        } else {
                            val newId = if (AppDatabase.users.isEmpty()) "emp1" else "emp${(AppDatabase.users.maxOfOrNull { it.id.removePrefix("emp").toIntOrNull() ?: 0 } ?: 0) + 1}"
                            AppDatabase.users.add(User(
                                id = newId, 
                                name = newUserName, 
                                email = newUserEmail, 
                                phone = newUserPhone,
                                pin = newUserPin,
                                photoUrl = base64Str
                            ))
                        }
                        AppDatabase.save()
                        
                        // Reset forms
                        newUserName = ""
                        newUserEmail = ""
                        newUserPhone = ""
                        newUserPin = ""
                        newUserPhotoBytes = null
                        editingUser = null
                        showAddDialog = false
                    }
                }) { Text("Uložit", color = Blue500, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false
                    editingUser = null
                    newUserName = ""
                    newUserEmail = ""
                    newUserPhone = ""
                    newUserPin = ""
                    newUserPhotoBytes = null
                }) { Text("Zrušit", color = Blue200) }
            }
        )
    }
    
    if (showMediaDialog) {
        AlertDialog(
            onDismissRequest = { showMediaDialog = false },
            backgroundColor = Navy800,
            contentColor = Blue50,
            title = { Text("Přidat profilovou fotku", fontWeight = FontWeight.Bold, color = Blue50) },
            text = { Text("Vyfoťte zaměstnance fotoaparátem, nebo vyberte hotovou fotku z galerie.", color = Blue200) },
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
}

@Composable
fun EmployeeCard(
    user: User,
    onEdit: () -> Unit,
    onToggleStatus: () -> Unit
) {
    val isBoss = user.role == Role.ADMIN
    val bgColor = if (isBoss) Indigo600.copy(alpha = 0.1f) else Navy800
    val borderColor = if (isBoss) Indigo600 else Navy700
    val alpha = if (user.isActive) 1f else 0.5f

    Card(
        modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(16.dp), spotColor = borderColor.copy(alpha=0.6f)),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = bgColor,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.border(2.dp, borderColor.copy(alpha=alpha), CircleShape).padding(2.dp)) {
                UserAvatar(user = user, size = 56.dp, isBoss = isBoss)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Blue50.copy(alpha=alpha))
                Text(user.email, color = Blue200.copy(alpha=alpha), fontSize = 14.sp)
                if (user.phone.isNotBlank()) Text("Tel: ${user.phone}", color = Blue200.copy(alpha=alpha), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Odpracováno: ${user.totalHoursLogged} h", fontWeight = FontWeight.Medium, color = Blue400.copy(alpha=alpha), fontSize = 13.sp)
                Text("Přihlašovací PIN: ${user.pin}", fontWeight = FontWeight.Medium, color = Slate300.copy(alpha=alpha), fontSize = 13.sp)
            }
            
            Row {
                if (user.isActive) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Upravit", tint = Blue400)
                    }
                }
                if (!isBoss) {
                    IconButton(onClick = onToggleStatus) {
                        if (user.isActive) {
                            Icon(Icons.Default.Delete, contentDescription = "Ukončit", tint = ErrorDark)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Obnovit", tint = SuccessLight)
                        }
                    }
                }
            }
        }
    }
}
