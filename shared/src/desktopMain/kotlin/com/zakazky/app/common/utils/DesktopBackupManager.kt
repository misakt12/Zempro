package com.zakazky.app.common.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════
// RUČNÍ EXPORT / IMPORT (přes dialog)
// ════════════════════════════════════════════════════════════

actual fun exportBackup(jsonData: String, fileName: String) {
    try {
        val chooser = JFileChooser().apply {
            dialogTitle = "Uložit zálohu Zempro"
            fileFilter = FileNameExtensionFilter("Záloha Zempro (*.json)", "json")
            selectedFile = File(fileName)
        }
        val result = chooser.showSaveDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            var file = chooser.selectedFile
            if (!file.name.endsWith(".json", ignoreCase = true)) {
                file = File(file.parentFile, "${file.name}.json")
            }
            file.writeText(jsonData, Charsets.UTF_8)
            JOptionPane.showMessageDialog(
                null,
                "✅ Záloha úspěšně uložena:\n${file.absolutePath}",
                "Záloha uložena",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(
            null,
            "❌ Chyba při ukládání zálohy:\n${e.message}",
            "Chyba zálohy",
            JOptionPane.ERROR_MESSAGE
        )
        e.printStackTrace()
    }
}

actual fun importBackup(onResult: (String?) -> Unit) {
    try {
        val chooser = JFileChooser().apply {
            dialogTitle = "Načíst zálohu Zempro"
            fileFilter = FileNameExtensionFilter("Záloha Zempro (*.json)", "json")
        }
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val json = chooser.selectedFile.readText(Charsets.UTF_8)
            onResult(json)
        } else {
            onResult(null)
        }
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(
            null,
            "❌ Chyba při načítání zálohy:\n${e.message}",
            "Chyba obnovy",
            JOptionPane.ERROR_MESSAGE
        )
        e.printStackTrace()
        onResult(null)
    }
}

// ════════════════════════════════════════════════════════════
// AUTOMATICKÉ DENNÍ ZÁLOHOVÁNÍ (jen PC)
// ════════════════════════════════════════════════════════════

/** Složka pro automatické zálohy: Dokumenty\Zempro Zálohy\ */
private val autoBackupDir: File by lazy {
    val docs = File(System.getProperty("user.home"), "Documents")
    File(docs, "Zempro Zálohy").also { it.mkdirs() }
}

private val latestFile   get() = File(autoBackupDir, "zaloha_latest.json")
private val previousFile get() = File(autoBackupDir, "zaloha_predposledni.json")

/** Vrátí datum poslední zálohy z lastModified souboru (nebo null pokud neexistuje) */
private fun lastBackupDate(): String? {
    if (!latestFile.exists()) return null
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(latestFile.lastModified()))
}

/** Vrátí dnešní datum ve formátu yyyy-MM-dd */
private fun todayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

/** Vrátí klíč aktuálního měsíce ve formátu yyyy-MM */
private fun currentMonthKey(): String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

/** Provede zálohu: rotace latest→previous, vytvoří nový latest, případně i měsíční */
private fun performAutoBackup(jsonData: String) {
    try {
        // 1. Rotace: previous = latest
        if (latestFile.exists()) {
            latestFile.copyTo(previousFile, overwrite = true)
            println("🔄 Záloha rotována: latest → předposlední")
        }

        // 2. Nový latest
        latestFile.writeText(jsonData, Charsets.UTF_8)
        println("✅ Automatická záloha uložena: ${latestFile.absolutePath}")

        // 3. Měsíční záloha (jen pokud ještě neexistuje pro tento měsíc)
        val monthKey = currentMonthKey()
        val monthlyFile = File(autoBackupDir, "zaloha_mesicni_$monthKey.json")
        if (!monthlyFile.exists()) {
            monthlyFile.writeText(jsonData, Charsets.UTF_8)
            println("📅 Měsíční záloha vytvořena: ${monthlyFile.name}")
        }
    } catch (e: Exception) {
        println("❌ Automatická záloha selhala: ${e.message}")
        e.printStackTrace()
    }
}

actual fun startAutoBackup(getJsonData: () -> String) {
    CoroutineScope(Dispatchers.IO).launch {
        // Čekáme 15s po startu — data se načtou z cloudu
        delay(15_000)

        // Okamžitá kontrola při startu aplikace
        if (lastBackupDate() != todayDate()) {
            println("📦 Spouštím automatickou zálohu při startu (dnešní ještě nebyla)...")
            performAutoBackup(getJsonData())
        } else {
            println("✅ Dnešní automatická záloha již existuje (${lastBackupDate()}), přeskakuji.")
        }

        // Každou hodinu znovu zkontrolujeme (pro případ přeběhnutí půlnoci)
        while (true) {
            delay(60 * 60_000L) // 1 hodina
            if (lastBackupDate() != todayDate()) {
                println("🕛 Půlnoc přešla — vytvářím novou denní zálohu...")
                performAutoBackup(getJsonData())
            }
        }
    }
}
