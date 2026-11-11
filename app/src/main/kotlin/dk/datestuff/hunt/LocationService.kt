package dk.datestuff.hunt

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Foreground service: tracks the phone while the hunt runs, forwards fixes to the server. */
class LocationService : Service() {
    private var lastPosted: LatLng? = null
    private var lastPostedAt = 0L
    private val cb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            val loc = r.lastLocation ?: return
            val l = LatLng(loc.latitude, loc.longitude, loc.accuracy, if (loc.hasBearing()) loc.bearing else null)
            HuntRepo.onLocation(l)
            val now = System.currentTimeMillis()
            val prev = lastPosted
            val moved = prev == null || Geo.distance(prev.lat, prev.lng, l.lat, l.lng) >= 15
            if (moved || now - lastPostedAt >= 10_000) {
                lastPosted = l; lastPostedAt = now
                HuntRepo.postLocation(l)
            }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        HuntRepo.init(this)
        startInForeground()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).setMinUpdateIntervalMillis(1500L).build()
            @Suppress("MissingPermission")
            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(req, cb, mainLooper)
        } else stopSelf()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Field operation", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Field Agent active")
            .setContentText("Mission Control is tracking your position")
            .setContentIntent(pi).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(cb)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "field_op"
        fun start(c: Context) {
            if (ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            runCatching { ContextCompat.startForegroundService(c, Intent(c, LocationService::class.java)) }
        }
        fun stop(c: Context) { c.stopService(Intent(c, LocationService::class.java)) }
    }
}
