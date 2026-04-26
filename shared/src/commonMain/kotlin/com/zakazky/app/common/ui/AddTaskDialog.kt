package com.zakazky.app.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zakazky.app.common.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.zakazky.app.common.models.CustomerProfile
import com.zakazky.app.common.models.Task

@Composable
fun AddTaskDialog(
    initialCustomer: CustomerProfile? = null,
    initialVehicle: Task? = null,
    onDismiss: () -> Unit,
    onTaskAdded: (title: String, brand: String, desc: String, cust: String, phone: String, email: String, address: String, spz: String, vin: String, empId: String?) -> Unit
) {
    val pBrand = initialVehicle?.brand ?: ""
    val pParts = initialVehicle?.title?.split(" - ") ?: emptyList()
    val pModel = if (initialVehicle != null && pParts.isNotEmpty()) pParts[0].split(" ").drop(1).joinToString(" ") else ""
    val pYear = if (initialVehicle != null && pParts.size >= 2) pParts[1] else ""
    val pEngine = if (initialVehicle != null && pParts.size >= 3) pParts[2] else ""
    val pDrivetrain = if (initialVehicle != null && pParts.size >= 4) pParts[3] else ""
    val pTransmission = if (initialVehicle != null && pParts.size >= 5) pParts[4] else ""

    var carBrand by remember { mutableStateOf(pBrand) }
    var carModel by remember { mutableStateOf(pModel) }
    var year by remember { mutableStateOf(pYear) }
    var engine by remember { mutableStateOf(pEngine) }
    var drivetrain by remember { mutableStateOf(pDrivetrain) }
    var transmission by remember { mutableStateOf(pTransmission) }
    var desc by remember { mutableStateOf("") }
    var cust by remember { mutableStateOf(initialCustomer?.name ?: initialVehicle?.customerName ?: "") }
    var custPhone by remember { mutableStateOf(initialCustomer?.phone ?: initialVehicle?.customerPhone ?: "") }
    var custEmail by remember { mutableStateOf(initialCustomer?.email ?: initialVehicle?.customerEmail ?: "") }
    var custAddress by remember { mutableStateOf(initialCustomer?.address ?: initialVehicle?.customerAddress ?: "") }
    var spz by remember { mutableStateOf(initialVehicle?.spz ?: "") }
    var vin by remember { mutableStateOf(initialVehicle?.vin ?: "") }
    var selectedEmpId by remember { mutableStateOf<String?>(null) }

    val brands = listOf("Škoda", "VW", "Audi", "BMW", "Ford", "Peugeot", "Renault", "Hyundai", "Kia", "Toyota", "Dacia", "Mercedes-Benz", "Ostatní")
    val currentYear = 2026
    val years = (currentYear downTo 1990).map { it.toString() }
    val drivetrains = listOf("Přední náhon", "Zadní náhon", "4x4 (AWD)")
    val transmissions = listOf("Manuální převodovka", "Automatická převodovka")

    val models = remember(carBrand) { getModelsForBrand(carBrand) }
    val engines = remember(carBrand, carModel, year) { suggestEngines(carBrand, carModel, year) }

    var disableAutoClear by remember { mutableStateOf(initialVehicle != null) }
    val focusManager = LocalFocusManager.current

    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (initialVehicle != null) {
            kotlinx.coroutines.delay(200)
            disableAutoClear = false
        }
    }

    LaunchedEffect(carBrand) {
        if (disableAutoClear) return@LaunchedEffect
        val newModels = getModelsForBrand(carBrand)
        if (newModels.isNotEmpty() && carModel.isNotBlank() && !newModels.contains(carModel)) {
            carModel = ""
            engine = ""
        }
    }

    val textFieldColors = TextFieldDefaults.outlinedTextFieldColors(
        textColor = Color.White,
        focusedBorderColor = Blue500,
        unfocusedBorderColor = Slate500
    )

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints {
            // Mobil (<600dp): celá obrazovka, 1 sloupec
            // PC/tablet (>=600dp): 75% šířky, 2 sloupce
            val isWide = maxWidth >= 600.dp

            Surface(
                modifier = if (isWide)
                    Modifier.fillMaxWidth(0.75f).fillMaxHeight(0.85f)
                else
                    // Na mobilu NEFIXUJEME výšku — dialog se přizpůsobí klávesnici
                    Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.95f).imePadding(),
                shape = if (isWide) RoundedCornerShape(24.dp) else RoundedCornerShape(16.dp),
                color = Navy800
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isWide) 20.dp else 16.dp)
                ) {
                    Text(
                        "Přidat novou zakázku",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // ─── Scrollovatelný obsah formuláře ───
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Značka + Model — vždy 2 sloupce (krátká slova)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f)) { EditableDropdownSelector("Značka", brands, carBrand) { carBrand = it } }
                            Box(Modifier.weight(1f)) { EditableDropdownSelector("Model", models, carModel) { carModel = it } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f)) { EditableDropdownSelector("Rok výroby", years, year) { year = it } }
                            Box(Modifier.weight(1f)) { EditableDropdownSelector("Motor", engines, engine) { engine = it } }
                        }

                        if (isWide) {
                            // PC: Pohon + Převodovka ve 2 sloupcích
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.weight(1f)) { EditableDropdownSelector("Pohon", drivetrains, drivetrain) { drivetrain = it } }
                                Box(Modifier.weight(1f)) { EditableDropdownSelector("Převodovka", transmissions, transmission) { transmission = it } }
                            }
                        } else {
                            // Mobil: každý dropdown plnou šířkou (aby se neskrácel text)
                            EditableDropdownSelector("Pohon", drivetrains, drivetrain) { drivetrain = it }
                            EditableDropdownSelector("Převodovka", transmissions, transmission) { transmission = it }
                        }

                        // Popis práce
                        OutlinedTextField(
                            value = desc, onValueChange = { desc = it },
                            label = { Text("Popis práce / Úkon", color = Slate400) },
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )

                        if (isWide) {
                            // PC — 2 sloupce
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = cust, onValueChange = { cust = it }, label = { Text("Zákazník", color = Slate400) }, modifier = Modifier.weight(1f), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                                OutlinedTextField(value = custPhone, onValueChange = { custPhone = it }, label = { Text("Telefon", color = Slate400) }, modifier = Modifier.weight(1f), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = custEmail, onValueChange = { custEmail = it }, label = { Text("E-mail", color = Slate400) }, modifier = Modifier.weight(1f), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                                OutlinedTextField(value = custAddress, onValueChange = { custAddress = it }, label = { Text("Adresa", color = Slate400) }, modifier = Modifier.weight(1f), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = spz, onValueChange = { spz = it }, label = { Text("SPZ", color = Slate400) }, modifier = Modifier.weight(1f), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                                OutlinedTextField(value = vin, onValueChange = { vin = it }, label = { Text("VIN (17 znaků)", color = Slate400) }, modifier = Modifier.weight(1f), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), singleLine = true)
                            }
                        } else {
                            // Mobil — 1 sloupec, plná šířka
                            OutlinedTextField(value = cust, onValueChange = { cust = it }, label = { Text("Zákazník", color = Slate400) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                            OutlinedTextField(value = custPhone, onValueChange = { custPhone = it }, label = { Text("Telefon", color = Slate400) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                            OutlinedTextField(value = custEmail, onValueChange = { custEmail = it }, label = { Text("E-mail", color = Slate400) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                            OutlinedTextField(value = custAddress, onValueChange = { custAddress = it }, label = { Text("Adresa", color = Slate400) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                            OutlinedTextField(value = spz, onValueChange = { spz = it }, label = { Text("SPZ", color = Slate400) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), singleLine = true)
                            OutlinedTextField(value = vin, onValueChange = { vin = it }, label = { Text("VIN (17 znaků)", color = Slate400) }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), singleLine = true)
                        }

                        // Tlačítko "Identifikovat"
                        Button(
                            onClick = {
                                val query = vin.ifBlank { spz }
                                if (query.isNotBlank()) {
                                    isSearching = true
                                    scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        try {
                                            val historyTask = com.zakazky.app.common.models.AppDatabase.searchTaskHistory(query)
                                            if (historyTask != null) {
                                                val cCust = historyTask.customerName
                                                val cPhone = historyTask.customerPhone
                                                val cEmail = historyTask.customerEmail
                                                val cAddress = historyTask.customerAddress
                                                val cSpz = historyTask.spz
                                                val cVin = historyTask.vin ?: vin
                                                val parts = historyTask.title.split(" - ")
                                                val hBrand = if (parts.isNotEmpty()) parts[0].split(" ").firstOrNull() ?: "" else ""
                                                val hModel = if (parts.isNotEmpty()) parts[0].split(" ").drop(1).joinToString(" ") else ""
                                                val hYear = if (parts.size >= 2) parts[1] else ""
                                                val hEngine = if (parts.size >= 3) parts[2] else ""
                                                val hDrivetrain = if (parts.size >= 4) parts[3] else ""
                                                val hTransmission = if (parts.size >= 5) parts[4] else ""
                                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    disableAutoClear = true
                                                    cust = cCust; custPhone = cPhone; custEmail = cEmail
                                                    custAddress = cAddress; spz = cSpz; vin = cVin
                                                    carBrand = hBrand; carModel = hModel; year = hYear
                                                    engine = hEngine; drivetrain = hDrivetrain; transmission = hTransmission
                                                    kotlinx.coroutines.delay(200)
                                                    disableAutoClear = false
                                                }
                                            } else if (vin.isNotBlank() && vin.length == 17) {
                                                val result = com.zakazky.app.common.utils.MdcrApi.getVehicleData(vin)
                                                if (result.isSuccess) {
                                                    val data = result.getOrNull()!!
                                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        disableAutoClear = true
                                                        carBrand = data.znacka; carModel = data.model
                                                        year = data.rok; engine = data.motor
                                                        kotlinx.coroutines.delay(200)
                                                        disableAutoClear = false
                                                    }
                                                } else {
                                                    println("MDCR API Error: ${result.exceptionOrNull()?.message}")
                                                }
                                            }
                                        } finally {
                                            withContext(kotlinx.coroutines.Dispatchers.Main) { isSearching = false }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Blue600),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("🔍 Identifikovat vůz a zákazníka", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } // End Scroll Column

                    Spacer(modifier = Modifier.height(16.dp))

                    // ─── Spodní tlačítka ───
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Zrušit — červené ohraničení
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedBorder.copy(width = 1.5.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = Color.Transparent,
                                contentColor = ErrorDark
                            )
                        ) {
                            Text("Zrušit", color = ErrorDark, fontWeight = FontWeight.Bold)
                        }

                        // Přidat zakázku — zelená
                        Button(
                            onClick = {
                                val partA = "$carBrand $carModel".trim()
                                val partBParts = listOf(year, engine, drivetrain, transmission).filter { it.isNotBlank() }
                                val partB = partBParts.joinToString(" - ")
                                var finalTitle = if (partA.isNotBlank() && partB.isNotBlank()) "$partA - $partB"
                                    else if (partA.isNotBlank()) partA else partB
                                if (finalTitle.isBlank()) finalTitle = "Nové vozidlo"
                                onTaskAdded(finalTitle, carBrand, desc, cust, custPhone, custEmail, custAddress, spz, vin, selectedEmpId)
                            },
                            modifier = Modifier.weight(2f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = SuccessDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Přidat zakázku", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } // end outer Column
            } // end Surface
        } // end BoxWithConstraints
    } // end Dialog
} // end AddTaskDialog


@Composable
fun EditableDropdownSelector(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected,
            onValueChange = {
                onSelected(it)
                if (options.isNotEmpty()) expanded = true
            },
            label = { Text(label, color = Slate400) },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Slate400, modifier = Modifier.clickable { expanded = true })
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Blue500, unfocusedBorderColor = Slate500),
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Navy800).heightIn(max = 250.dp)
        ) {
            val filteredOptions = if (selected.isBlank()) options else options.filter { it.contains(selected, ignoreCase = true) }
            val optionsToShow = if (filteredOptions.isEmpty()) options else filteredOptions
            optionsToShow.forEach { option ->
                DropdownMenuItem(onClick = {
                    onSelected(option)
                    expanded = false
                }) {
                    Text(option, color = Color.White)
                }
            }
        }
    }
}

fun getModelsForBrand(brand: String): List<String> {
    return when (brand) {
        "Škoda" -> listOf("Octavia", "Octavia Combi", "Superb", "Superb Combi", "Fabia", "Kodiaq", "Karoq", "Kamiq", "Scala", "Yeti", "Citigo", "Enyaq")
        "VW" -> listOf("Passat", "Passat Variant", "Golf", "Golf Variant", "Tiguan", "Touran", "Polo", "Touareg", "Caddy", "Transporter", "Arteon", "Multivan")
        "Audi" -> listOf("A3", "A4", "A4 Avant", "A6", "A6 Avant", "A8", "Q3", "Q5", "Q7", "Q8", "e-tron")
        "BMW" -> listOf("Řada 1", "Řada 3", "Řada 3 Touring", "Řada 5", "Řada 5 Touring", "Řada 7", "X1", "X3", "X5", "X6", "X7")
        "Ford" -> listOf("Focus", "Mondeo", "Fiesta", "Kuga", "Puma", "Mustang", "Custom", "Transit", "S-Max", "C-Max")
        "Peugeot" -> listOf("208", "308", "508", "2008", "3008", "5008", "Rifter", "Boxer", "Partner")
        "Renault" -> listOf("Clio", "Megane", "Captur", "Trafic", "Master", "Kangoo", "Arkana")
        "Hyundai" -> listOf("i20", "i30", "i30 Kombi", "Tucson", "Kona", "Santa Fe", "Ioniq 5")
        "Kia" -> listOf("Ceed", "Ceed SW", "Sportage", "Sorento", "Rio", "Niro", "EV6")
        "Toyota" -> listOf("Yaris", "Corolla", "Corolla Touring Sports", "RAV4", "C-HR", "Camry", "Hilux", "Proace")
        "Dacia" -> listOf("Duster", "Sandero", "Logan", "Jogger", "Spring")
        "Mercedes-Benz" -> listOf("Třída A", "Třída C", "Třída E", "Třída S", "GLC", "GLE", "Vito", "Sprinter")
        else -> emptyList()
    }
}

fun suggestEngines(brand: String, model: String, yearStr: String): List<String> {
    val year = yearStr.toIntOrNull() ?: 2026
    val isVAG = brand in listOf("Škoda", "VW", "Audi")
    val engines = mutableListOf<String>()

    if (isVAG) {
        if (year < 2010) {
            engines.addAll(listOf("1.9 TDI", "2.0 TDI (PD)", "1.4 MPI", "1.6 MPI", "1.8T"))
        } else if (year in 2010..2015) {
            engines.addAll(listOf("1.6 TDI", "2.0 TDI (CR)", "1.2 TSI", "1.4 TSI", "1.8 TSI", "3.6 FSI"))
        } else {
            engines.addAll(listOf("2.0 TDI", "1.5 TSI", "1.0 TSI", "2.0 TSI", "1.4 TSI iV (PHEV)", "Elektro"))
        }
    } else if (brand == "BMW") {
        engines.addAll(listOf("18d", "20d", "30d", "20i", "30i", "40i", "M50i", "Plug-in Hybrid"))
    } else if (brand == "Ford") {
        engines.addAll(listOf("1.0 EcoBoost", "1.5 EcoBoost", "1.5 TDCi", "2.0 TDCi", "2.0 EcoBlue"))
    } else if (brand in listOf("Hyundai", "Kia")) {
        engines.addAll(listOf("1.0 T-GDi", "1.4 MPi", "1.5 T-GDi", "1.6 CRDi", "1.6 GDi", "1.6 T-GDi"))
    } else if (brand == "Toyota") {
        engines.addAll(listOf("1.5 Hybrid", "1.8 Hybrid", "2.0 Hybrid", "2.5 Hybrid", "1.5 VVT-iE"))
    } else if (brand == "Peugeot" || brand == "Renault" || brand == "Dacia") {
        engines.addAll(listOf("1.2 PureTech", "1.5 BlueHDi", "1.6 BlueHDi", "0.9 TCe", "1.0 TCe", "1.2 TCe", "1.5 dCi", "LPG"))
    }

    if (engines.isEmpty() || brand == "Ostatní") {
        engines.addAll(listOf("Nafta", "Benzín", "LPG", "Elektro", "Hybrid"))
    }
    return engines.distinct()
}
