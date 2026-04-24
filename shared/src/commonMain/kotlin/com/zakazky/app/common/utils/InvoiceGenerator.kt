package com.zakazky.app.common.utils

import com.zakazky.app.common.models.Task

import org.jetbrains.compose.resources.ExperimentalResourceApi
import com.zakazky.app.common.utils.encodeBase64

@OptIn(ExperimentalResourceApi::class)
suspend fun generateInvoiceHtml(task: Task): String {
    val totalMaterialPrice = task.invoiceItems.sumOf { it.price }
    val totalWorkMechanic = task.mechanicWorkPrice
    val totalWorkElectric = task.electricWorkPrice
    val totalPrice = totalMaterialPrice + totalWorkMechanic + totalWorkElectric
    
    // Aktuální datum (Využití nativního expect/actual DateUtils přes JVM bez externí knihovny)
    val dateString = getCurrentDateString()

    // Ošetřené načtení loga (try/catch kdyby náhodou na nějaké platformě selhalo)
    var logoBase64 = ""
    try {
        val logoBytes = org.jetbrains.compose.resources.resource("drawable/app_logo.png").readBytes()
        logoBase64 = encodeBase64(logoBytes)
    } catch (e: Exception) {
        println("Chyba při načítání loga do faktury: ${e.message}")
    }
    
    val logoHtml = if (logoBase64.isNotEmpty()) {
        """<img src="data:image/png;base64,$logoBase64" width="70" height="70" style="border-radius: 50%; border: 2px solid #4CAF50;" />"""
    } else {
        """
        <svg width="200" height="40" viewBox="0 0 200 40" style="opacity: 0.85;">
            <path d="M10,35 Q50,5 120,10 T180,25" fill="none" stroke="#4CAF50" stroke-width="4" stroke-linecap="round"/>
            <ellipse cx="180" cy="25" rx="6" ry="4" fill="#2E7D32"/>
            <circle cx="185" cy="24" r="1.5" fill="#fff"/>
            <path d="M175,25 Q170,30 165,25" fill="none" stroke="#2E7D32" stroke-width="2"/>
            <line x1="184" y1="26" x2="190" y2="28" stroke="#2E7D32" stroke-width="1"/>
            <line x1="183" y1="27" x2="188" y2="31" stroke="#2E7D32" stroke-width="1"/>
        </svg>
        """
    }

    val builder = StringBuilder()
    builder.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    font-family: Arial, sans-serif;
                    margin: 0;
                    padding: 40px;
                    color: #000;
                    background: #fff;
                }
                @media screen and (max-width: 600px) {
                    body { padding: 15px; }
                    .header-right { order: -1; }
                }
                .invoice-container {
                    max-width: 800px;
                    margin: 0 auto;
                }
                .header {
                    display: flex;
                    justify-content: space-between;
                    flex-wrap: wrap;
                    gap: 15px;
                    margin-bottom: 15px;
                }
                .header-left {
                    width: 40%;
                    min-width: 270px;
                    flex: 1;
                }
                .header-right {
                    width: 45%;
                    min-width: 270px;
                    flex: 1;
                    text-align: right;
                    position: relative;
                }
                .company-name {
                    font-size: 38px;
                    font-weight: 900;
                    color: #2E7D32; /* Tmavě zelená */
                    font-style: italic;
                    letter-spacing: -1px;
                    margin-bottom: 0px;
                    text-shadow: 1px 1px 0px #A5D6A7;
                    white-space: nowrap;
                    text-align: right;
                    position: relative;
                    z-index: 2;
                }
                .company-sro {
                    font-size: 16px;
                    font-style: italic;
                    color: #1B5E20;
                    margin-left: 5px;
                }
                .company-sub {
                    font-size: 13px;
                    color: #111;
                    margin-bottom: 0;
                    font-weight: 900;
                }
                .form-group {
                    display: flex;
                    margin-bottom: 12px;
                    align-items: center;
                }
                .form-label {
                    width: 150px;
                    flex-shrink: 0;
                    font-size: 14px;
                    font-weight: bold;
                    text-transform: uppercase;
                }
                .form-value {
                    flex-grow: 1;
                    border: 1px solid #000;
                    padding: 6px 12px;
                    min-height: 20px;
                    font-size: 16px;
                }
                .consent {
                    font-size: 12px;
                    margin: 20px 0;
                    text-align: center;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-top: 20px;
                }
                th, td {
                    border: 1px solid #000;
                    padding: 8px 12px;
                    text-align: left;
                }
                th {
                    background-color: #f2f2f2;
                    text-align: center;
                    font-weight: bold;
                    text-transform: uppercase;
                }
                .col-price {
                    width: 150px;
                    text-align: right;
                }
                .total-row {
                    font-weight: bold;
                    background-color: #eaebec;
                }
                .footer {
                    display: flex;
                    justify-content: space-between;
                    margin-top: 40px;
                    font-size: 14px;
                    font-weight: bold;
                    text-transform: uppercase;
                }
                
                @media print {
                    @page { margin: 0; }
                    body {
                        padding: 1.0cm;
                    }
                    .invoice-container {
                        max-width: 100%;
                    }
                }
            </style>
        </head>
        <body>
            <div class="invoice-container">
                <div class="header">
                    <div class="header-left">
                        <div class="form-group">
                            <div class="form-label">Číslo objednávky</div>
                            <div class="form-value" style="width: 100px; flex-grow: 0;">${task.id}</div>
                        </div>
                        <div class="form-group">
                            <div class="form-label">Datum:</div>
                            <div class="form-value" style="width: 140px; flex-grow: 0; text-align:center;">$dateString</div>
                        </div>
                    </div>
                    <div class="header-right" style="display: flex; flex-direction: column; align-items: flex-end; text-align: right;">
                        <div style="display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-bottom: 5px;">
                            <div class="company-name" style="margin-top: 0; line-height: 1;">Zempro&nbsp;CZ <span class="company-sro">s.r.o.</span></div>
                            $logoHtml
                        </div>
                        <div class="company-sub" style="margin-top: 0;">AUTOSERVIS - PNEUSERVIS - SLUŽBY</div>
                        <div style="font-size: 12px; margin-top:2px;">Tel.: 725 625 174, 725 625 176</div>
                        <div style="font-size: 12px;">www.autoservis-zempro.cz</div>
                    </div>
                </div>

                <div class="form-group">
                    <div class="form-label">Zákazník:</div>
                    <div class="form-value" style="display: flex; flex-direction: column; justify-content: center;">
                        <div style="line-height: 1.2;">${task.customerName}</div>
                        ${if (task.customerAddress.isNotBlank()) "<div style='line-height: 1.2;'>${task.customerAddress}</div>" else ""}
                        ${if (task.customerPhone.isNotBlank() || task.customerEmail.isNotBlank()) {
                            "<div style='font-size: 13px; color: #555; margin-top: 2px; line-height: 1.2;'>${listOf(task.customerPhone, task.customerEmail).filter { it.isNotBlank() }.joinToString(", ")}</div>"
                        } else ""}
                    </div>
                </div>
                <div class="form-group">
                    <div class="form-label">Typ vozidla:</div>
                    <div class="form-value">${task.title}</div>
                </div>
                <div style="display: flex; flex-wrap: wrap; column-gap: 15px;">
                    <div class="form-group" style="flex: 1; min-width: 240px;">
                        <div class="form-label" style="white-space: nowrap;">Stav tachometru</div>
                        <div class="form-value" style="margin-right: 0;">${task.vehicleKm ?: ""}</div>
                    </div>
                    <div class="form-group" style="flex: 1; min-width: 150px;">
                        <div class="form-label" style="width: 60px; min-width: 60px;">SPZ</div>
                        <div class="form-value" style="text-align: center; font-weight: bold;">${task.spz}</div>
                    </div>
                </div>

                <div class="consent">
                    Zákazník souhlasí s provedením zkušební jízdy opravovaného vozu. Podpis: .....................................................
                </div>

                <table>
                    <tr>
                        <th>Materiál, Oprava</th>
                        <th class="col-price">Cena</th>
                    </tr>
    """.trimIndent())

    // Vložení materiálů (dynamicky dle reálného počtu, minimálně 1 řádek aby tabulka nebyla prázdná)
    val maxRows = maxOf(task.invoiceItems.size, 1)
    for (i in 0 until maxRows) {
        val item = task.invoiceItems.getOrNull(i)
        if (item != null) {
            builder.append("<tr><td>${item.name}</td><td class=\"col-price\">${item.price.toInt()} Kč</td></tr>\n")
        } else {
            builder.append("<tr><td style=\"color: transparent;\">.</td><td class=\"col-price\"></td></tr>\n")
        }
    }

    builder.append("""
                    <tr class="total-row">
                        <td style="text-align: right;">CELKEM ZA MATERIÁL</td>
                        <td class="col-price">${totalMaterialPrice.toInt()} Kč</td>
                    </tr>
                    <tr>
                        <td>PRÁCE MECHANICKÁ</td>
                        <td class="col-price">${totalWorkMechanic.toInt()} Kč</td>
                    </tr>
                    <tr>
                        <td>PRÁCE ELEKTRIKÁŘSKÁ</td>
                        <td class="col-price">${totalWorkElectric.toInt()} Kč</td>
                    </tr>
                    <tr class="total-row" style="border: 2px solid #000;">
                        <td style="text-align: right; border: 2px solid #000;">CELKEM</td>
                        <td class="col-price" style="border: 2px solid #000;">${totalPrice.toInt()} Kč</td>
                    </tr>
                </table>

                <div class="footer" style="align-items: flex-start;">
                    <div>Opravu převzal:</div>
                    <div style="text-align: center;">
                        <!-- QR Platba — Česká platební norma SPD, upravte IBAN v kódu InvoiceGenerator.kt -->
                        <img src="https://api.qrserver.com/v1/create-qr-code/?size=120x120&data=SPD*1.0*ACC:CZ0800000000123456789*AM:${totalPrice}*CC:CZK*MSG:FAKTURA%20${task.id}" style="border:1px solid #ccc;padding:5px;background:white;" alt="QR Platba" />
                        <div style="font-size: 11px; color: #4CAF50; margin-top:2px;">QR Platba (upravit IBAN: CZ0800000000123456789)</div>
                    </div>
                    <div style="margin-right: 70px;">Placeno dne:</div>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent())

    return builder.toString()
}

fun generateInvoiceCsv(task: Task): String {
    val totalMaterialPrice = task.invoiceItems.sumOf { it.price }
    val totalWorkMechanic = task.mechanicWorkPrice
    val totalWorkElectric = task.electricWorkPrice
    val totalPrice = totalMaterialPrice + totalWorkMechanic + totalWorkElectric
    val dateString = getCurrentDateString()

    val builder = StringBuilder()
    builder.append("""
        <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
        <head>
            <meta charset="utf-8">
        </head>
        <body>
            <table border="1" cellpadding="5" cellspacing="0" width="1800">
                <tr>
                    <td width="250" bgcolor="#eaebec"><b>Číslo objednávky</b></td>
                    <td width="250" bgcolor="#eaebec"><b>Datum</b></td>
                    <td width="400" bgcolor="#eaebec"><b>Zákazník</b></td>
                    <td width="500" bgcolor="#eaebec"><b>Vůz</b></td>
                    <td width="200" bgcolor="#eaebec"><b>SPZ</b></td>
                    <td width="200" bgcolor="#eaebec"><b>Stav KM</b></td>
                </tr>
                <tr>
                    <td>${task.id}</td>
                    <td style="mso-number-format:'\@'">$dateString</td>
                    <td>${task.customerName}</td>
                    <td>${task.title}</td>
                    <td style="mso-number-format:'\@'">${task.spz}</td>
                    <td style="mso-number-format:'\@'">${task.vehicleKm ?: ""}</td>
                </tr>
                <tr><td colspan="6" height="20"></td></tr>
                
                <tr>
                    <td colspan="2" bgcolor="#eaebec"><b>Materiál / Oprava</b></td>
                    <td bgcolor="#eaebec" align="right"><b>Cena (Kč)</b></td>
                    <td colspan="3"></td>
                </tr>
    """.trimIndent())
    
    task.invoiceItems.forEach { item ->
        builder.append("<tr><td colspan=\"2\">${item.name}</td><td align=\"right\">${item.price.toInt()}</td><td colspan=\"3\"></td></tr>\n")
    }
    
    builder.append("""
                <tr><td colspan="6" height="10"></td></tr>
                <tr><td colspan="2" bgcolor="#f9fafb"><b>CELKEM ZA MATERIÁL</b></td><td bgcolor="#f9fafb" align="right"><b>${totalMaterialPrice.toInt()}</b></td><td colspan="3"></td></tr>
                <tr><td colspan="2" bgcolor="#f9fafb"><b>PRÁCE MECHANICKÁ</b></td><td bgcolor="#f9fafb" align="right"><b>${totalWorkMechanic.toInt()}</b></td><td colspan="3"></td></tr>
                <tr><td colspan="2" bgcolor="#f9fafb"><b>PRÁCE ELEKTRIKÁŘSKÁ</b></td><td bgcolor="#f9fafb" align="right"><b>${totalWorkElectric.toInt()}</b></td><td colspan="3"></td></tr>
                <tr><td colspan="2" bgcolor="#d1d5db"><b>CELKEM</b></td><td bgcolor="#d1d5db" align="right"><b>${totalPrice.toInt()}</b></td><td colspan="3"></td></tr>
            </table>
        </body>
        </html>
    """.trimIndent())
    
    return builder.toString()
}
