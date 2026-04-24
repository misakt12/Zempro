package com.zakazky.app.common.utils

import androidx.compose.runtime.Composable
import com.zakazky.app.common.models.Task
import com.zakazky.app.common.models.displayName

interface ExportHelper {
    fun exportDocument(content: String, defaultName: String, extension: String, mimeType: String)
}

@Composable
expect fun rememberExportHelper(): ExportHelper

fun generateExcelXmlString(tasks: List<Task>): String {
    val builder = StringBuilder()
    builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    builder.append("<?mso-application progid=\"Excel.Sheet\"?>\n")
    builder.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
    builder.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n")
    builder.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n")
    builder.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
    builder.append(" xmlns:html=\"http://www.w3.org/TR/REC-html40\">\n")
    
    // Styly pro hlavičku (Tučné, šedé pozadí)
    builder.append(" <Styles>\n")
    builder.append("  <Style ss:ID=\"Header\">\n")
    builder.append("   <Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>\n")
    builder.append("   <Interior ss:Color=\"#4285F4\" ss:Pattern=\"Solid\"/>\n")
    builder.append("  </Style>\n")
    builder.append(" </Styles>\n")

    builder.append(" <Worksheet ss:Name=\"Zakazky\">\n")
    builder.append("  <Table>\n")
    
    // Natvrdo nadefinované šířky pro každý ze 12 sloupců (aby text nebyl uříznutý)
    builder.append("   <Column ss:Width=\"40\"/>\n")
    builder.append("   <Column ss:Width=\"130\"/>\n")
    builder.append("   <Column ss:Width=\"80\"/>\n")
    builder.append("   <Column ss:Width=\"150\"/>\n")
    builder.append("   <Column ss:Width=\"150\"/>\n")
    builder.append("   <Column ss:Width=\"250\"/>\n")
    builder.append("   <Column ss:Width=\"90\"/>\n")
    builder.append("   <Column ss:Width=\"80\"/>\n")
    builder.append("   <Column ss:Width=\"90\"/>\n")
    builder.append("   <Column ss:Width=\"80\"/>\n")
    builder.append("   <Column ss:Width=\"180\"/>\n")
    builder.append("   <Column ss:Width=\"250\"/>\n")

    // Hlavička Tabulky
    builder.append("   <Row ss:StyleID=\"Header\">\n")
    val headers = listOf("ID", "Zákazník", "SPZ", "VIN", "Název vozu", "Zadávací popis", "Status", "Stav Tacho", "Přiřazeno", "Odpracováno (h)", "Záznamy o Práci", "Reklamace")
    headers.forEach { h ->
        builder.append("    <Cell><Data ss:Type=\"String\">$h</Data></Cell>\n")
    }
    builder.append("   </Row>\n")
    
    // Data (řádky zakázek)
    tasks.forEach { task ->
        val totalHours = task.timeLogs.sumOf { it.hours }
        val workLogs = task.timeLogs.joinToString(", ") { "${it.employeeName}: ${it.hours}h" }
        val reworkLogs = task.reworks.joinToString("; ") { "Opravil ${it.solverName} (+${it.solverHours}h), Zavinil ${it.guiltyName} (-${it.penaltyHours}h)" }
        
        fun escapeXml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        
        builder.append("   <Row>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.id)}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.customerName)}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.spz)}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.vin)}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.title)}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.description)}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.status.displayName)}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.vehicleKm?.toString() ?: "")}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(task.assignedTo ?: "")}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"Number\">$totalHours</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(workLogs)}</Data></Cell>\n")
        builder.append("    <Cell><Data ss:Type=\"String\">${escapeXml(reworkLogs)}</Data></Cell>\n")
        builder.append("   </Row>\n")
    }
    
    builder.append("  </Table>\n")
    builder.append(" </Worksheet>\n")
    builder.append("</Workbook>\n")
    
    return builder.toString()
}
