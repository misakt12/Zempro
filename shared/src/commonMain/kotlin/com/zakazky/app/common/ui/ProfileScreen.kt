package com.zakazky.app.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.models.Role
import com.zakazky.app.common.theme.*
import com.zakazky.app.common.utils.encodeBase64
import com.zakazky.app.common.utils.exportBackup
import com.zakazky.app.common.utils.importBackup
import com.zakazky.app.common.utils.rememberMediaManager
import com.zakazky.app.common.utils.getCurrentTimestamp
import com.zakazky.app.common.utils.formatTimestamp

@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val currentUser = AppDatabase.currentUser

    // ── State proměnné (všechny na úrovni composable — bez podmínek!) ──
    var name by remember { mutableStateOf(currentUser.name) }
    var email by remember { mutableStateOf(currentUser.email) }
    var phone by remember { mutableStateOf(currentUser.phone) }
    var pin by remember { mutableStateOf(currentUser.pin) }
    var showMediaDialog by remember { mutableStateOf(false) }
    var newUserPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val mediaManager = rememberMediaManager { newImageBytes ->
        if (newImageBytes != null) {
            newUserPhotoBytes = newImageBytes
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Navy900)) {

        // ── TopAppBar ──
        Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd)))) {
            TopAppBar(
                title = { Text("Můj Profil", fontWeight = FontWeight.Bold, color = Blue50) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zpět", tint = Blue50)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Odhlásit", tint = Blue50)
                    }
                },
                backgroundColor = Color.Transparent,
                elevation = 0.dp
            )
        }

        // ── Scrollovatelný obsah ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            val previewUser = currentUser.copy(
                photoUrl = if (newUserPhotoBytes != null) encodeBase64(newUserPhotoBytes!!) else currentUser.photoUrl
            )

            Box(modifier = Modifier.clickable { showMediaDialog = true }, contentAlignment = Alignment.BottomEnd) {
                UserAvatar(user = previewUser, size = 100.dp, isBoss = currentUser.role == Role.ADMIN)
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = 4.dp)
                        .clip(CircleShape)
                        .background(Blue600)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(currentUser.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Blue50)
            Text(if (currentUser.role == Role.ADMIN) "Šéf" else "Mechanik", fontSize = 16.sp, color = Blue200)

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Jméno", color = Blue200) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Blue50, focusedBorderColor = Blue500, unfocusedBorderColor = Navy700),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail", color = Blue200) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Blue50, focusedBorderColor = Blue500, unfocusedBorderColor = Navy700),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Telefon", color = Blue200) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Blue50, focusedBorderColor = Blue500, unfocusedBorderColor = Navy700),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("Přihlašovací PIN", color = Blue200) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Blue50, focusedBorderColor = Blue500, unfocusedBorderColor = Navy700),
                modifier = Modifier.fillMaxWidth(),
                enabled = currentUser.role == Role.ADMIN
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val index = AppDatabase.users.indexOfFirst { it.id == currentUser.id }
                    if (index != -1 && name.isNotBlank() && pin.isNotBlank()) {
                        val base64Str = if (newUserPhotoBytes != null) encodeBase64(newUserPhotoBytes!!) else currentUser.photoUrl

                        if (currentUser.role == Role.EMPLOYEE &&
                            (name != currentUser.name || email != currentUser.email || phone != currentUser.phone || newUserPhotoBytes != null)
                        ) {
                            AppDatabase.notifications.add(
                                com.zakazky.app.common.models.UserNotification(
                                    id = "notif${AppDatabase.notifications.size + 1}",
                                    message = "Mechanik ${currentUser.name} si upravil profil.",
                                    timestamp = 0L
                                )
                            )
                        }

                        val updatedUser = currentUser.copy(name = name, email = email, phone = phone, pin = pin, photoUrl = base64Str)
                        AppDatabase.users[index] = updatedUser
                        AppDatabase.currentUser = updatedUser
                        AppDatabase.save()
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Blue600)
            ) {
                Text("Uložit změny", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // ════════════════════════════════════════════
            // ZÁLOHA A OBNOVA DAT — jen pro šéfa
            // ════════════════════════════════════════════
            if (currentUser.role == Role.ADMIN) {
                Spacer(modifier = Modifier.height(32.dp))
                Divider(color = Navy700, thickness = 1.dp)
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Záloha a Obnova Dat",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue200,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Záloha uloží všechny zakázky a uživatele do JSON souboru na vašem zařízení.",
                    fontSize = 12.sp,
                    color = Slate400,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tlačítko EXPORT zálohy
                Button(
                    onClick = {
                        val json = AppDatabase.exportBackupJson()
                        val date = formatTimestamp(getCurrentTimestamp())
                            .replace(". ", "-").replace(":", "-").replace(" ", "_")
                        val fileName = "zempro_zaloha_$date.json"
                        exportBackup(json, fileName)
                        backupMessage = "✅ Záloha exportována"
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = SuccessLight.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Exportovat zálohu (JSON)", color = SuccessLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tlačítko IMPORT zálohy
                Button(
                    onClick = { showImportConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = ErrorDark.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorDark, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Obnovit ze zálohy (JSON)", color = ErrorDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                if (backupMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(backupMessage!!, color = SuccessLight, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
            // ── konec scrollovatelného obsahu ──
        }
    }

    // ── Dialogy (mimo Column, uvnitř composable funkce) ──

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            backgroundColor = Navy800,
            title = { Text("⚠️ Obnovit ze zálohy?", color = ErrorDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Tato akce přepíše VŠECHNA aktuální data (zakázky, uživatele) daty ze zálohy.\n\nPokračovat?",
                    color = Blue200
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importBackup { jsonString ->
                        if (jsonString != null) {
                            val ok = AppDatabase.importFromBackupJson(jsonString)
                            backupMessage = if (ok) "✅ Záloha úspěšně obnovena!" else "❌ Obnova selhala — neplatný soubor"
                        }
                    }
                }) {
                    Text("Ano, obnovit", color = ErrorDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text("Zrušit", color = Blue400)
                }
            }
        )
    }

    if (showMediaDialog) {
        AlertDialog(
            onDismissRequest = { showMediaDialog = false },
            backgroundColor = Navy800,
            contentColor = Blue50,
            title = { Text("Změnit profilovou fotku", fontWeight = FontWeight.Bold, color = Blue50) },
            text = { Text("Vyfoťte se fotoaparátem, nebo vyberte fotku z galerie.", color = Blue200) },
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
