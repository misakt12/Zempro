package com.zakazky.app.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.models.Task
import com.zakazky.app.common.theme.*
import com.zakazky.app.common.utils.formatTimestamp

@Composable
fun TrashScreen(
    tasks: androidx.compose.runtime.snapshots.SnapshotStateList<Task>
) {
    val deletedTasks = tasks.filter { it.isDeleted }.sortedByDescending { it.deletedAt ?: 0L }

    // Potvrzovací dialogy
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Hlavička ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "🗑️ Koš",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = Blue50
                )
                Text(
                    "${deletedTasks.size} ${if (deletedTasks.size == 1) "zakázka" else "zakázek"} v koši",
                    fontSize = 13.sp,
                    color = Slate400
                )
            }
            if (deletedTasks.isNotEmpty()) {
                TextButton(
                    onClick = { showClearAllConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorDark)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vyprázdnit koš", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Divider(color = Navy700, thickness = 1.dp)

        if (deletedTasks.isEmpty()) {
            // Prázdný stav
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗑️", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Koš je prázdný", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Blue200)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Smazané zakázky se zobrazí zde.\nMůžete je obnovit nebo trvale smazat.",
                        fontSize = 14.sp,
                        color = Slate400,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(deletedTasks, key = { it.id }) { task ->
                    TrashTaskCard(
                        task = task,
                        onRestore = { AppDatabase.restoreTask(task.id) },
                        onDeletePermanently = { taskToDelete = task }
                    )
                }
            }
        }
    }

    // ── Dialog: potvrzení trvalého smazání jedné zakázky ──
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            backgroundColor = Navy800,
            title = { Text("⚠️ Trvale smazat zakázku?", color = ErrorDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Zakázka \"${task.title}\" bude TRVALE a NEVRATNĚ smazána ze systému i z databáze.\n\nPokračovat?",
                    color = Blue200
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AppDatabase.permanentlyDeleteTask(task.id)
                    taskToDelete = null
                }) {
                    Text("Ano, smazat", color = ErrorDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Zrušit", color = Blue400)
                }
            }
        )
    }

    // ── Dialog: potvrzení vyprázdnění celého koše ──
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            backgroundColor = Navy800,
            title = { Text("⚠️ Vyprázdnit celý koš?", color = ErrorDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Všechny ${deletedTasks.size} zakázky budou TRVALE a NEVRATNĚ smazány ze systému i z databáze.\n\nTato akce je nevratná!",
                    color = Blue200
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deletedTasks.forEach { AppDatabase.permanentlyDeleteTask(it.id) }
                    showClearAllConfirm = false
                }) {
                    Text("Ano, vyprázdnit", color = ErrorDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Zrušit", color = Blue400)
                }
            }
        )
    }
}

@Composable
private fun TrashTaskCard(
    task: Task,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Navy700, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Navy800.copy(alpha = 0.7f),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Blue200  // trochu ztlumená barva = smazaná zakázka
                    )
                    Text(task.brand, fontSize = 13.sp, color = Slate400)
                }
                // Červený štítek
                Box(
                    modifier = Modifier
                        .background(ErrorDark.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, ErrorDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("V koši", color = ErrorDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(icon = Icons.Default.Person, text = task.customerName)
                InfoChip(icon = Icons.Default.Build, text = task.spz)
            }

            if (task.deletedAt != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Smazáno: ${formatTimestamp(task.deletedAt)}",
                    fontSize = 11.sp,
                    color = Slate400
                )
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = Navy700, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Tlačítko OBNOVIT ──
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessLight),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessLight)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Obnovit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                // ── Tlačítko TRVALE SMAZAT ──
                OutlinedButton(
                    onClick = onDeletePermanently,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorDark)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Smazat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
