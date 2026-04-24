package com.zakazky.app.common.utils

/** Expect rozhraní pro zálohu a obnovu dat — implementace se liší pro Desktop a Android */
expect fun exportBackup(jsonData: String, fileName: String)
expect fun importBackup(onResult: (String?) -> Unit)

/**
 * Spustí automatické zálohovací plánování na pozadí.
 * Na PC: vytvoří zálohu při startu (pokud dnes ještě nebyla) a každou hodinu kontroluje.
 * Na Androidu: nedělá nic (záloha je jen na PC).
 */
expect fun startAutoBackup(getJsonData: () -> String)
