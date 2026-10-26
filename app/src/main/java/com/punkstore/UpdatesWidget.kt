package com.punkstore

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date

/** Home-screen widget: how many updates wait (store catalogues, my GitHub apps and Pulse included), and when the store last looked. A tap opens the store. */
class UpdatesWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { m.updateAppWidget(it, views(c)) } }

    companion object {
        private fun views(c: Context): RemoteViews {
            val p = c.getSharedPreferences("punk", Context.MODE_PRIVATE)
            val n = p.getInt("updates_n", 0)
            val last = p.getLong("lastCheck", 0L)
            val v = RemoteViews(c.packageName, R.layout.widget_updates)
            v.setTextViewText(R.id.wu_count, n.toString())
            val what = if (n == 1) t("güncelleme bekliyor", "update waiting") else t("güncelleme bekliyor", "updates waiting")
            v.setTextViewText(R.id.wu_sub, what + (if (last > 0) " · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(last)) else ""))
            v.setOnClickPendingIntent(R.id.wu_root, PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }

        /** Draws every placed widget again (call after the count changes). */
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, UpdatesWidget::class.java)).forEach { m.updateAppWidget(it, views(c)) }
        }
    }
}
