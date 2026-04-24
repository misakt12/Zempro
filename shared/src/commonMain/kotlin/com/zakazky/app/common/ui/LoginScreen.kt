package com.zakazky.app.common.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import com.zakazky.app.common.models.Role
import com.zakazky.app.common.models.User
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.theme.*
import com.zakazky.app.common.utils.decodeBase64
import com.zakazky.app.common.utils.toImageBitmap

@Composable
fun UserAvatar(user: User, size: androidx.compose.ui.unit.Dp, isBoss: Boolean = false) {
    val bgColor = if (isBoss) Indigo600 else Blue500
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (user.photoUrl != null) {
            val imageBitmap = remember(user.photoUrl) {
                try {
                    decodeBase64(user.photoUrl).toImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4).sp)
            }
        } else {
            Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4).sp)
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LoginScreen(onLoginSuccess: (User) -> Unit) {
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val activeUsers = AppDatabase.users.sortedByDescending { it.role == Role.ADMIN }

    // === Animace loga – bounce + fade in + pulzující záře ===
    var logoVisible by remember { mutableStateOf(false) }
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(700),
        label = "logoAlpha"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )
    LaunchedEffect(Unit) { logoVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Navy900, Color(0xFF0a0f1e), Navy900)
                )
            )
    ) {
        if (selectedUser == null) {
            // ====== VÝBĚR UŽIVATELE ======
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- LOGO ZEMPRO (animované) ---
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(logoScale)
                        .shadow(24.dp, CircleShape, spotColor = ZemproGreen.copy(alpha = glowAlpha))
                        .clip(CircleShape)
                        .border(2.dp, ZemproGreen.copy(alpha = glowAlpha), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource("drawable/app_logo.png"),
                        contentDescription = "Zempro logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(logoScale)
                            .graphicsLayer { alpha = logoAlpha }
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "Zempro CZ",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZemproGreen,
                    letterSpacing = 1.sp
                )
                Text(
                    "Přístupový systém",
                    fontSize = 14.sp,
                    color = Slate400,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Divider(
                    modifier = Modifier.width(60.dp),
                    color = ZemproGreen.copy(alpha = 0.5f),
                    thickness = 2.dp
                )
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "Vyberte svůj profil",
                    fontSize = 13.sp,
                    color = Slate400,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(activeUsers) { user ->
                        val isBoss = user.role == Role.ADMIN
                        val bgGradient = if (isBoss)
                            Brush.horizontalGradient(listOf(Indigo600.copy(alpha = 0.25f), Navy800))
                        else
                            Brush.horizontalGradient(listOf(Navy800, Navy800))

                        val borderColor = if (isBoss) ZemproGreen else Blue500.copy(alpha = 0.5f)

                        Row(
                            modifier = Modifier
                                .widthIn(max = 420.dp)
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = borderColor.copy(alpha = 0.3f))
                                .clip(RoundedCornerShape(20.dp))
                                .background(bgGradient)
                                .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
                                .clickable { selectedUser = user }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .border(2.dp, borderColor, CircleShape)
                                    .padding(3.dp)
                            ) {
                                UserAvatar(user = user, size = 60.dp, isBoss = isBoss)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                Text(
                                    if (isBoss) "👑 Správce systému" else "🔧 Mechanik",
                                    color = if (isBoss) ZemproGreen else Slate400,
                                    fontSize = 13.sp
                                )
                            }
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = borderColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

        } else {
            // ====== PIN OBRAZOVKA ======
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()                          // posune obsah nad klávesnici
                    .verticalScroll(rememberScrollState()) // umožní scroll pokud se nevejde
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    elevation = 16.dp,
                    backgroundColor = Navy800,
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Blue500.copy(alpha = 0.6f), Blue600.copy(alpha = 0.2f))),
                            RoundedCornerShape(28.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar s glowing okrajem
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .shadow(12.dp, CircleShape, spotColor = Blue500.copy(alpha = 0.5f))
                                .border(2.dp, Blue500, CircleShape)
                                .padding(3.dp)
                        ) {
                            UserAvatar(
                                user = selectedUser!!,
                                size = 84.dp,
                                isBoss = selectedUser!!.role == Role.ADMIN
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            selectedUser!!.name,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 24.sp
                        )
                        Text(
                            if (selectedUser!!.role == Role.ADMIN) "Správce systému" else "Mechanik",
                            color = Blue200,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // --- PIN POLE S VÝRAZNÝM OHRANIČENÍM ---
                        Text(
                            "Zadejte PIN kód",
                            color = Slate400,
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                if (it.length <= 4) {
                                    pinInput = it
                                    errorMessage = null
                                }
                            },
                            label = { Text("● ● ● ●", color = Slate400, letterSpacing = 4.sp) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                if (pinInput == selectedUser!!.pin) {
                                    onLoginSuccess(selectedUser!!)
                                } else {
                                    errorMessage = "❌ Nesprávný PIN, zkuste to znovu."
                                    pinInput = ""
                                }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Blue500.copy(alpha = 0.4f))
                                .onPreviewKeyEvent { 
                                    if ((it.key == Key.Enter || it.key == Key.NumPadEnter) && it.type == KeyEventType.KeyDown) {
                                        if (pinInput == selectedUser!!.pin) {
                                            onLoginSuccess(selectedUser!!)
                                        } else {
                                            errorMessage = "❌ Nesprávný PIN, zkuste to znovu."
                                            pinInput = ""
                                        }
                                        true
                                    } else false
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = Color.White,
                                cursorColor = Blue400,
                                focusedBorderColor = Blue500,
                                unfocusedBorderColor = Blue600.copy(alpha = 0.5f),
                                backgroundColor = Navy900
                            ),
                            singleLine = true
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                errorMessage!!,
                                color = Color(0xFFff6b6b),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // --- PŘIHLÁSIT TLAČÍTKO ---
                        Button(
                            onClick = {
                                if (pinInput == selectedUser!!.pin) {
                                    onLoginSuccess(selectedUser!!)
                                } else {
                                    errorMessage = "❌ Nesprávný PIN, zkuste to znovu."
                                    pinInput = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Blue600.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Blue600,
                                disabledBackgroundColor = Blue600.copy(alpha = 0.3f)
                            ),
                            enabled = pinInput.length == 4
                        ) {
                            Text("Přihlásit se", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = {
                                selectedUser = null
                                pinInput = ""
                                errorMessage = null
                            }
                        ) {
                            Text("← Zpět na výběr uživatele", color = Slate400, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
