package com.didicalc.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {
    companion object {
        const val ACTION_UPDATE = "com.didicalc.app.OVERLAY_UPDATE"
        const val EXTRA_PXKM = "pxkm"; const val EXTRA_KM_PICKUP = "km_pickup"
        const val EXTRA_KM_TRIP = "km_trip"; const val EXTRA_PRICE = "price"
        const val CHANNEL_ID = "didi_calc_overlay"
    }
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var tvPxkm: TextView; private lateinit var tvVerdict: TextView
    private lateinit var tvDetails: TextView; private lateinit var tvWaiting: TextView
    private lateinit var contentGroup: View
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f
    private var isDragging = false; private var isCollapsed = false
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            if (intent.hasExtra(EXTRA_PXKM)) updateUI(intent.getDoubleExtra(EXTRA_PXKM,0.0),intent.getDoubleExtra(EXTRA_KM_PICKUP,0.0),intent.getDoubleExtra(EXTRA_KM_TRIP,0.0),intent.getDoubleExtra(EXTRA_PRICE,0.0))
            else showWaiting()
        }
    }
    override fun onCreate() {
        super.onCreate(); createNotificationChannel(); startForeground(1, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay(); registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE), RECEIVER_NOT_EXPORTED)
    }
    private fun setupOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        tvPxkm = overlayView.findViewById(R.id.tv_pxkm); tvVerdict = overlayView.findViewById(R.id.tv_verdict)
        tvDetails = overlayView.findViewById(R.id.tv_details); tvWaiting = overlayView.findViewById(R.id.tv_waiting)
        contentGroup = overlayView.findViewById(R.id.content_group)
        params = WindowManager.LayoutParams(dpToPx(200), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = dpToPx(8); y = dpToPx(80) }
        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialX=params.x; initialY=params.y; initialTouchX=event.rawX; initialTouchY=event.rawY; isDragging=false; true }
                MotionEvent.ACTION_MOVE -> { val dx=event.rawX-initialTouchX; val dy=event.rawY-initialTouchY; if(!isDragging&&(abs(dx)>8||abs(dy)>8)) isDragging=true; if(isDragging){params.x=(initialX-dx).toInt();params.y=(initialY+dy).toInt();windowManager.updateViewLayout(overlayView,params)}; true }
                MotionEvent.ACTION_UP -> { if(!isDragging) toggleCollapse(); true }
                else -> false
            }
        }
        showWaiting(); windowManager.addView(overlayView, params)
    }
    private fun toggleCollapse() {
        isCollapsed = !isCollapsed
        if (isCollapsed) { contentGroup.visibility=View.GONE; tvWaiting.visibility=View.GONE; params.width=dpToPx(60) }
        else { params.width=dpToPx(200); if(tvPxkm.text.isNotEmpty()){contentGroup.visibility=View.VISIBLE;tvWaiting.visibility=View.GONE} else showWaiting() }
        windowManager.updateViewLayout(overlayView, params)
    }
    private fun updateUI(pxkm: Double, kmPickup: Double, kmTrip: Double, price: Double) {
        tvPxkm.text = "$${pxkm.roundToInt()}"
        val isLong = kmTrip >= DiDiAccessibilityService.LONG_TRIP_KM
        val (verdictText, bgColor, textColor) = when {
            pxkm >= 700 -> Triple("Excelente", Color.parseColor("#14532d"), Color.parseColor("#4ade80"))
            pxkm >= DiDiAccessibilityService.MIN_PRICE_PER_KM -> Triple("Acepta", Color.parseColor("#14532d"), Color.parseColor("#86efac"))
            pxkm >= DiDiAccessibilityService.EXCEPTION_MIN && isLong -> Triple("Largo, evalua", Color.parseColor("#713f12"), Color.parseColor("#fbbf24"))
            else -> Triple("Rechaza", Color.parseColor("#7f1d1d"), Color.parseColor("#fca5a5"))
        }
        tvVerdict.text = verdictText; tvVerdict.setTextColor(textColor)
        overlayView.findViewById<View>(R.id.verdict_bg).setBackgroundColor(bgColor)
        tvDetails.text = "${"%.1f".format(kmPickup)}+${"%.1f".format(kmTrip)}=${"%.1f".format(kmPickup+kmTrip)}km"
        if (!isCollapsed) { contentGroup.visibility=View.VISIBLE; tvWaiting.visibility=View.GONE }
    }
    private fun showWaiting() { if(!isCollapsed){contentGroup.visibility=View.GONE;tvWaiting.visibility=View.VISIBLE} }
    override fun onDestroy() { super.onDestroy(); unregisterReceiver(updateReceiver); if(::overlayView.isInitialized) windowManager.removeView(overlayView) }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createNotificationChannel() { val ch=NotificationChannel(CHANNEL_ID,"DiDi Calc",NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(ch) }
    private fun buildNotification() = NotificationCompat.Builder(this,CHANNEL_ID).setContentTitle("DiDi Calc activo").setContentText("Calculando precio/km").setSmallIcon(android.R.drawable.ic_menu_compass).setPriority(NotificationCompat.PRIORITY_LOW).build()
    private fun dpToPx(dp: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,dp.toFloat(),resources.displayMetrics).toInt()
}
