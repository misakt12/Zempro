@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.zakazky.app.common.utils

import kotlinx.cinterop.*
import platform.AudioToolbox.*
import platform.darwin.*
import platform.Foundation.*
import platform.MessageUI.*
import platform.UIKit.*
import platform.WebKit.*
import platform.posix.memcpy

actual class PlatformStorage

private fun getFilePath(): String {
    val documentDirectory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String ?: ""
    return "$documentDirectory/zempro_database.json"
}

actual fun writePlatformData(storage: PlatformStorage, json: String) {
    try {
        val filePath = getFilePath()
        val nsString = json as NSString
        nsString.writeToFile(filePath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun readPlatformData(storage: PlatformStorage): String? {
    try {
        val filePath = getFilePath()
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(filePath)) {
            val nsString = NSString.stringWithContentsOfFile(filePath, encoding = NSUTF8StringEncoding, error = null)
            return nsString as? String
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

actual fun encodeBase64(byteArray: ByteArray): String {
    if (byteArray.isEmpty()) return ""
    val nsData = byteArray.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = byteArray.size.toULong())
    }
    return nsData.base64EncodedStringWithOptions(0u)
}

actual fun decodeBase64(string: String): ByteArray {
    if (string.isEmpty()) return ByteArray(0)
    val nsData = NSData.create(base64Encoding = string) ?: return ByteArray(0)
    val length = nsData.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), nsData.bytes, nsData.length.toULong())
    }
    return bytes
}

actual fun printInvoice(htmlContent: String, taskName: String) {
    try {
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return

        // Vytvoříme WKWebView, načteme HTML a pak spustíme AirPrint dialog
        val webView = WKWebView(frame = rootVC.view.bounds)
        webView.loadHTMLString(htmlContent, baseURL = null)

        // Malá prodleva, aby se HTML stihlo vyrenderovat před tiskem
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, 800_000_000L), // 0.8s
            dispatch_get_main_queue()
        ) {
            val printController = UIPrintInteractionController.sharedPrintController
            val printInfo = UIPrintInfo.printInfo()
            printInfo.outputType = UIPrintInfoOutputGeneral
            printInfo.jobName = taskName
            printController.printInfo = printInfo
            printController.printFormatter = webView.viewPrintFormatter()
            printController.presentAnimated(true, completionHandler = null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun openEmailClient(to: String, subject: String, body: String, attachmentHtml: String?, taskName: String) {
    try {
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return

        if (MFMailComposeViewController.canSendMail()) {
            val mailVC = MFMailComposeViewController()
            mailVC.setToRecipients(listOf(to))
            mailVC.setSubject(subject)
            mailVC.setMessageBody(body, isHTML = false)

            // Přiložíme fakturu jako HTML soubor
            if (attachmentHtml != null) {
                val nsData = (attachmentHtml as NSString).dataUsingEncoding(NSUTF8StringEncoding)
                if (nsData != null) {
                    val safeFileName = taskName.replace("/", "-").replace(":", "-")
                    mailVC.addAttachmentData(
                        attachment = nsData,
                        mimeType = "text/html",
                        fileName = "faktura_$safeFileName.html"
                    )
                }
            }

            // Zavíracím tlačítkem dismiss
            mailVC.mailComposeDelegate = IosMailDelegate.shared
            rootVC.presentViewController(mailVC, animated = true, completion = null)
        } else {
            // Mail.app není nastaven — otevřeme mailto: link jako fallback
            val encodedSubject = subject.replace(" ", "%20")
            val mailtoUrl = NSURL(string = "mailto:$to?subject=$encodedSubject")
            if (mailtoUrl != null && UIApplication.sharedApplication.canOpenURL(mailtoUrl)) {
                UIApplication.sharedApplication.openURL(mailtoUrl)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun getCurrentTimestamp(): Long {
    return NSDate().timeIntervalSince1970.toLong() * 1000L
}

actual fun formatTimestamp(timestamp: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0)
    val formatter = NSDateFormatter().apply {
        dateFormat = "dd.MM. yyyy HH:mm"
        locale = NSLocale.currentLocale
    }
    return formatter.stringFromDate(date)
}

/**
 * Přehraje systémový zvuk upozornění iOS.
 * kSystemSoundID_Vibrate = 4095 (vibrace), 1007 = klasický iOS "ping" tón.
 */
actual fun playNotificationSound(storage: PlatformStorage) {
    // 1007 = SMS zvuk (krátký, nenápadný — vhodný pro notifikaci v autoservisu)
    AudioServicesPlaySystemSound(1007u)
}

// ── Mail delegate — zavře MFMailComposeViewController po odeslání/zrušení ────
class IosMailDelegate : NSObject(), MFMailComposeViewControllerDelegateProtocol {
    companion object { val shared = IosMailDelegate() }

    override fun mailComposeController(
        controller: MFMailComposeViewController,
        didFinishWithResult: MFMailComposeResult,
        error: NSError?
    ) {
        controller.dismissViewControllerAnimated(true, completion = null)
    }
}
