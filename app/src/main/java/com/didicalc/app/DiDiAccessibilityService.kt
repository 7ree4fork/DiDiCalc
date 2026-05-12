package com.didicalc.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DiDiAccessibilityService : AccessibilityService() {
    companion object {
        const val MIN_PRICE_PER_KM = 550.0
        const val LONG_TRIP_KM = 8.0
        const val EXCEPTION_MIN = 450.0
    }
    private var lastResult = ""
    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundService(Intent(this, OverlayService::class.java))
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val root = rootInActiveWindow ?: return
        val allText = extractAllText(root)
        root.recycle()
        parseAndBroadcast(allText)
    }
    private fun extractAllText(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        traverseNode(node, texts)
        return texts
    }
    private fun traverseNode(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) texts.add(text)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, texts)
            child.recycle()
        }
    }
    private fun parseAndBroadcast(texts: List<String>) {
        val fullText = texts.joinToString(" | ")
        val kmPattern = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE)
        val kmMatches = kmPattern.findAll(fullText).map { it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0 }.filter { it > 0.0 && it < 100.0 }.toList()
        val pricePattern = Regex("""\$?\s*(\d{1,2}[.,]\d{3}|\d{4,6})""")
        val priceMatches = pricePattern.findAll(fullText).mapNotNull { it.groupValues[1].replace(".", "").replace(",", "").toDoubleOrNull() }.filter { it in 800.0..99999.0 }.toList()
        if (kmMatches.size < 2 || priceMatches.isEmpty()) { sendToOverlay(null, null, null, null); return }
        val kmPickup = kmMatches[0]; val kmTrip = kmMatches[1]
        val totalKm = kmPickup + kmTrip
        val price = priceMatches.maxOrNull() ?: return
        val pxkm = price / totalKm
        val resultKey = "$kmPickup|$kmTrip|$price"
        if (resultKey == lastResult) return
        lastResult = resultKey
        sendToOverlay(kmPickup, kmTrip, price, pxkm)
    }
    private fun sendToOverlay(kmPickup: Double?, kmTrip: Double?, price: Double?, pxkm: Double?) {
        val intent = Intent(OverlayService.ACTION_UPDATE).apply {
            setPackage(packageName)
            if (pxkm != null) {
                putExtra(OverlayService.EXTRA_PXKM, pxkm)
                putExtra(OverlayService.EXTRA_KM_PICKUP, kmPickup ?: 0.0)
                putExtra(OverlayService.EXTRA_KM_TRIP, kmTrip ?: 0.0)
                putExtra(OverlayService.EXTRA_PRICE, price ?: 0.0)
            }
        }
        sendBroadcast(intent)
    }
    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); stopService(Intent(this, OverlayService::class.java)) }
}
