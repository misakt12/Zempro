package com.zakazky.app.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.theme.*

@Composable
fun ReworkDialog(
    onDismiss: () -> Unit,
    onSubmit: (solverId: String, solverHours: Double, guiltyId: String, penaltyHours: Double, note: String) -> Unit
) {
    var solverId by remember { mutableStateOf("") }
    var solverHours by remember { mutableStateOf("") }
    var guiltyId by remember { mutableStateOf("") }
    var penaltyHours by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = Navy800,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Ohlásit Reklamaci", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kdo opravu vyřešil (dostane hodiny)?", color = Slate400)
                OutlinedTextField(value = solverId, onValueChange = { solverId = it }, label = { Text("ID zaměstnance (dočasně takto)", color=Slate400) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500))
                OutlinedTextField(value = solverHours, onValueChange = { solverHours = it }, label = { Text("Kolik hodin to vzalo", color=Slate400) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Kdo reklamaci zavinil (strhnou se hodiny)?", color = ErrorDark, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = guiltyId, onValueChange = { guiltyId = it }, label = { Text("ID zaměstnance", color=Slate400) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500))
                OutlinedTextField(value = penaltyHours, onValueChange = { penaltyHours = it }, label = { Text("Kolik strhnout", color=Slate400) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500))
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                if (solverId.isNotBlank() && guiltyId.isNotBlank()) {
                    onSubmit(solverId, solverHours.toDoubleOrNull()?:0.0, guiltyId, penaltyHours.toDoubleOrNull()?:0.0, note)
                }
            }) { Text("Uložit reklamaci", color = ErrorDark, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušit", color = Slate400) }
        }
    )
}
