package com.didicalc.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.didicalc.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); Toast.makeText(this, "Busca DiDi Calc y activala", Toast.LENGTH_LONG).show() }
        binding.btnOverlay.setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        binding.btnToggleService.setOnClickListener {
            if (!isAccessibilityEnabled()) { Toast.makeText(this, "Primero activa el Servicio de Accesibilidad", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Primero activa el permiso de superposicion", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            Toast.makeText(this, "Todo listo. Abre DiDi Conductor.", Toast.LENGTH_LONG).show(); finish()
        }
    }
    override fun onResume() {
        super.onResume()
        val accessOk = isAccessibilityEnabled(); val overlayOk = Settings.canDrawOverlays(this)
        binding.statusAccessibility.text = if (accessOk) "Activado" else "Pendiente"
        binding.statusAccessibility.setTextColor(getColor(if (accessOk) R.color.green else R.color.red))
        binding.statusOverlay.text = if (overlayOk) "Activado" else "Pendiente"
        binding.statusOverlay.setTextColor(getColor(if (overlayOk) R.color.green else R.color.red))
        binding.btnToggleService.isEnabled = accessOk && overlayOk
        binding.btnToggleService.alpha = if (accessOk && overlayOk) 1f else 0.4f
    }
    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
