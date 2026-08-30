package com.flame.doomclock

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "doomclock/native"
        private const val REQ_EXACT_ALARM = 1001
        private const val REQ_POST_NOTIF = 1002
        private const val REQ_FULL_SCREEN = 1003
    }

    private var pendingLambdaResult: MethodChannel.Result? = null
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "scheduleAlarm" -> {
                    val ms = call.argument<Long>("triggerAtMs")!!
                    val label = call.argument<String>("label") ?: "Alarm"
                    val fullScreen = call.argument<Boolean>("fullScreen") ?: true
                    val targetPackage = call.argument<String>("targetPackage")
                    val targetLabel = call.argument<String>("targetLabel")
                    scheduleAlarm(ms, label, fullScreen, targetPackage, targetLabel, result)
                }
                "cancelAlarm" -> {
                    AlarmScheduler.cancelAlarm(applicationContext)
                    result.success(null)
                }
                "hasExactAlarmPermission" -> {
                    result.success(AlarmScheduler.canScheduleExactAlarms(applicationContext))
                }
                "requestExactAlarmPermission" -> {
                    pendingResult = result
                    requestExactAlarmPermission()
                }
                "requestNotificationPermission" -> {
                    requestNotificationPermission(result)
                }
                "hasFullScreenIntentPermission" -> {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    result.success(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            nm.canUseFullScreenIntent()
                        } else true
                    )
                }
                "openFullScreenSettings" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${packageName}"))
                        try {
                            startActivity(intent)
                        } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${packageName}")))
                        }
                    }
                    result.success(null)
                }
                "pickApp" -> {
                    pendingLambdaResult = result
                    pickApp()
                }
                "openApp" -> {
                    val pkg = call.argument<String>("package")!!
                    openApp(pkg, result)
                }
                "getAlarmRingtone" -> {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    result.success(uri.toString())
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun scheduleAlarm(triggerAtMs: Long, label: String, fullScreen: Boolean, targetPackage: String?, targetLabel: String?, result: MethodChannel.Result) {
        if (!AlarmScheduler.canScheduleExactAlarms(applicationContext)) {
            result.error("NO_EXACT_ALARM", "Exact alarm permission not granted", null)
            return
        }
        AlarmScheduler.scheduleAlarm(applicationContext, triggerAtMs, label, fullScreen, targetPackage, targetLabel)
        result.success(null)
    }

    private fun requestExactAlarmPermission() {
        if (AlarmScheduler.canScheduleExactAlarms(applicationContext)) {
            pendingResult?.success(true)
            pendingResult = null
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${packageName}"))
            startActivityForResult(intent, REQ_EXACT_ALARM)
        } else {
            pendingResult?.success(true)
            pendingResult = null
        }
    }

    private fun requestNotificationPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIF)
                pendingResult = result
                return
            }
            result.success(true)
        } else {
            result.success(true)
        }
    }

    private fun pickApp() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        val packages = resolveInfos
            .map { ri -> ri.activityInfo.packageName }
            .distinct()
            .sorted()
        val labels = resolveInfos.associate { ri ->
            ri.activityInfo.packageName to
                packageManager.getApplicationLabel(ri.activityInfo.applicationInfo).toString()
        }
        val pkgArray = packages.toTypedArray()
        val labelArray = packages.map { labels[it] ?: it }.toTypedArray()
        pendingLambdaResult?.success(mapOf(
            "packages" to pkgArray.toList(),
            "labels" to labelArray.toList()
        ))
        pendingLambdaResult = null
    }

    private fun openApp(packageName: String, result: MethodChannel.Result) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(intent)
                result.success(true)
            } else {
                result.error("APP_NOT_FOUND", "App non trovata: $packageName", null)
            }
        } catch (e: Exception) {
            result.error("OPEN_FAILED", e.message, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_EXACT_ALARM) {
            pendingResult?.success(AlarmScheduler.canScheduleExactAlarms(applicationContext))
            pendingResult = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_POST_NOTIF) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingResult?.success(granted)
            pendingResult = null
        }
    }
}
