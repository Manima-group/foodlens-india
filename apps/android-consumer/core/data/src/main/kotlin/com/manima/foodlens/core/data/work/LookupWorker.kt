package com.manima.foodlens.core.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.manima.foodlens.core.data.ProductRepository
import com.manima.foodlens.core.data.R
import com.manima.foodlens.core.database.PendingDao
import com.manima.foodlens.core.datastore.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Resolves barcodes scanned while offline; notifies only if the user turned notifications on (SPEC §10.5). */
@HiltWorker
class LookupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pending: PendingDao,
    private val products: ProductRepository,
    private val settings: SettingsStore
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        var resolved = 0
        var remaining = 0
        for (lookup in pending.lookups()) {
            if (products.resolvePending(lookup.gtin14)) resolved++ else remaining++
        }
        if (resolved > 0 && settings.current().notifyLookups) notify(resolved)
        return if (remaining > 0) Result.retry() else Result.success()
    }

    private fun notify(count: Int) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notification_channel_lookups),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launch?.let {
            android.app.PendingIntent.getActivity(context, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notification_lookups_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notification_lookups_text, count, count))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked above for API 33+; not required below
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val NAME = "pending-lookups"
        private const val CHANNEL = "lookups"
        private const val NOTIFICATION_ID = 1001
    }
}
