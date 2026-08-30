package com.flame.doomclock

import android.app.Activity
import android.app.NotificationManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmAlertActivity : Activity() {

    companion object {
        private const val NOTIFICATION_ID = 0x00DC
    }

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var vibrator: Vibrator
    private var targetPackage: String? = null
    private var targetLabel: String? = null
    private lateinit var infoTv: TextView
    private lateinit var stopBtn: Button
    private lateinit var hintTv: TextView
    private lateinit var labelTv: TextView
    private lateinit var iconIv: ImageView
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = intent.getStringExtra("label") ?: "Alarm"
        targetPackage = intent.getStringExtra("targetPackage")
        targetLabel = intent.getStringExtra("targetLabel")

        // Turn the screen on and bring to foreground above the lockscreen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }

        buildUi(label)
        refreshState()
        startAlarmSound()
        startVibration()

        // Receiver runtime su ACTION_USER_PRESENT: cattura lo sblocco anche se l'activity
        // viene mandata in background (Android 12+ gesture manda in home).
        registerUnlockReceiver()
    }

    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, it: Intent) {
                if (it.action == Intent.ACTION_USER_PRESENT) {
                    refreshState()
                }
            }
        }
        try {
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        } catch (_: Exception) {
        }
    }

    private fun unregisterUnlockReceiver() {
        try { unlockReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        unlockReceiver = null
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    // Si chiama al cambio di stato lock/unlock e fa il refreshing della UI
    private fun refreshState() {
        val locked = isLocked()
        if (locked) {
            renderUnlockMessage()
        } else {
            renderStopScreen()
        }
        // Redraw the button highlight
        invalidateIfNeeded()
    }

    private val keyguardManager: KeyguardManager
        get() = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private fun isLocked(): Boolean {
        return keyguardManager.isKeyguardLocked
    }

    private fun appDisplayName(): String? {
        val pkg = targetPackage ?: return null
        if (pkg.isBlank()) return null
        val cached = targetLabel
        if (cached != null && cached.isNotBlank()) return cached
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    // STATE 1: phone locked -> just unlock message, no STOP button
    private fun renderUnlockMessage() {
        val name = appDisplayName()
        iconIv.setImageResource(R.drawable.ic_lock)
        infoTv.text = if (name != null)
            "Unlock your phone and open DoomClock to stop the alarm and open $name"
        else
            "Unlock your phone and open DoomClock to stop the alarm"

        stopBtn.visibility = View.GONE
        hintTv.text = "Phone locked"
        hintTv.visibility = View.VISIBLE
    }

    // STATE 2: unlocked -> the real STOP screen with a single button
    private fun renderStopScreen() {
        val name = appDisplayName()
        iconIv.setImageResource(R.drawable.ic_check)
        infoTv.text = if (name != null)
            "Press STOP and it opens $name"
        else
            "Press STOP to stop the alarm"

        stopBtn.visibility = View.VISIBLE
        hintTv.visibility = View.GONE

        // update the app name shown above the button
        labelTv.text = if (name != null) name else "Alarm"
    }

    private fun invalidateIfNeeded() {
        try {
            findViewById<View>(android.R.id.content)?.invalidate()
        } catch (_: Exception) {}
    }

    private fun buildUi(label: String) {
        // Sfondo con gradiente viola profondo (allineato al tema deepPurple dark dell'app)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(48), dp(32), dp(32))
        }
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF1B1525.toInt(), 0xFF3A1F5C.toInt(), 0xFF151029.toInt())
        )
        root.background = bg

        // Icona stato (lock / check)
        iconIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_lock)
        }
        val iconWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(80)
            )
            addView(iconIv, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))
        }

        // Spaziatori fissi
        fun spacer(h: Int) = Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, h)
        }

        // Big time
        val now = Calendar.getInstance()
        val timeStr = String.format(
            Locale.getDefault(),
            "%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE)
        )
        val dateStr = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
            .format(now.time).replaceFirstChar { it.uppercase() }

        val timeTv = TextView(this).apply {
            text = timeStr
            setTextColor(0xFFF5F0FF.toInt())
            textSize = 86f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
            letterSpacing = 0.02f
        }

        val dateTv = TextView(this).apply {
            text = dateStr
            setTextColor(0xFFB9A8F0.toInt())
            textSize = 18f
            letterSpacing = 0.06f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) }

        // Alarm name / app to open
        val lblTv = TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        labelTv = lblTv

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) }

        val appTv = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(0xFFC9BCF5.toInt())
            textSize = 16f
            setLineSpacing(0f, 1.2f)
        }
        infoTv = appTv

        val spacer3 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) }

        // STOP button (only visible when unlocked) - red rounded with shadow, centered
        val stopRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(72)
            )
        }
        val stop = Button(this).apply {
            text = "STOP"
            textSize = 24f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            val bg = GradientDrawable().apply {
                cornerRadius = dp(36).toFloat()
                setColor(0xFFE4484A.toInt())
            }
            background = bg
            isAllCaps = true
            elevation = dp(12).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(72))
        }
        stop.setOnClickListener { onStopPressed() }
        stopBtn = stop
        stopRow.addView(stop)

        // Hint (locked/unlocked)
        val hint = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(0xFF9E8FCC.toInt())
            textSize = 14f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        hintTv = hint
        hintTv.visibility = View.VISIBLE

        root.addView(iconWrap)
        root.addView(spacer(dp(24)))
        root.addView(timeTv)
        root.addView(dateTv)
        root.addView(spacer1)
        root.addView(lblTv)
        root.addView(spacer2)
        root.addView(appTv)
        root.addView(spacer3)
        root.addView(stopRow)
        root.addView(spacer(dp(16)))
        root.addView(hint)

        setContentView(root)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun onStopPressed() {
        // Remove the alarm notification
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        nm.cancel(1100)

        // Ferma suono e vibrazione
        stopAlarmSound()

        // Open the target app (works reliably when unlocked)
        val pkg = targetPackage
        if (pkg != null && pkg.isNotBlank()) {
            launchTarget(pkg)
        }
        finishAndRemoveTask()
    }

    private fun launchTarget(pkg: String) {
        try {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(launch)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterUnlockReceiver()
        stopAlarmSound()
    }

    private fun startAlarmSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmAlertActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: IOException) {
            try {
                val rUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    setDataSource(this@AlarmAlertActivity, rUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopAlarmSound() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        try { vibrator.cancel() } catch (_: Exception) {}
    }

    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 1000, 1000), 0)
        }
    }
}
