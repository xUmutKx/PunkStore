package com.punkstore

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date

/**
 * The home-screen widgets of the store, in two looks each: Steam (dark blue, the colours of the store) and Material (the phone's own Material You colours).
 * Three kinds: updates waiting, a counter of the downloads of my apps, and the list of my GitHub apps that have an APK release.
 * The numbers come from what the store saved the last time it looked (see [PunkWidgets.save]).
 */
abstract class PunkWidget(private val kind: String, private val material: Boolean) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { m.updateAppWidget(it, PunkWidgets.views(c, kind, material)) } }
}

class UpdatesWidget : PunkWidget("updates", false)
class UpdatesMaterialWidget : PunkWidget("updates", true)
class DownloadsWidget : PunkWidget("downloads", false)
class DownloadsMaterialWidget : PunkWidget("downloads", true)
class ReposWidget : PunkWidget("repos", false)
class ReposMaterialWidget : PunkWidget("repos", true)

object PunkWidgets {
    private fun p(c: Context) = c.getSharedPreferences("punk", Context.MODE_PRIVATE)

    /** Keeps what the widgets show: how many downloads my GitHub apps have, and their names with the version of the latest release. */
    fun save(c: Context, mine: List<AppItem>) {
        val dl = mine.sumOf { a -> a.installs.filter { it.isDigit() }.toLongOrNull() ?: 0L }
        p(c).edit().putLong("gh_downloads", dl).putInt("gh_apps", mine.size)
            .putString("gh_list", mine.sortedByDescending { it.updated }.joinToString("\n") { it.name + "\t" + it.versionName }).apply()
        refresh(c)
    }

    private fun open(c: Context) = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun views(c: Context, kind: String, material: Boolean): RemoteViews {
        val sp = p(c)
        val bg = if (material) R.drawable.widget_bg_material else R.drawable.widget_bg_steam
        val text = if (material) c.getColor(R.color.wm_text) else Color.WHITE
        val sub = if (material) c.getColor(R.color.wm_sub) else 0xFF8F98A0.toInt()
        val accent = if (material) c.getColor(R.color.wm_accent) else 0xFF66C0F4.toInt()
        if (kind == "repos") {
            val v = RemoteViews(c.packageName, R.layout.widget_repos)
            v.setInt(R.id.wr_root, "setBackgroundResource", bg)
            v.setTextColor(R.id.wr_title, accent)
            v.setTextViewText(R.id.wr_title, "by UmutK")
            val rows = (sp.getString("gh_list", "") ?: "").lines().filter { it.isNotBlank() }
            intArrayOf(R.id.wr_1, R.id.wr_2, R.id.wr_3, R.id.wr_4, R.id.wr_5).forEachIndexed { i, id ->
                val r = rows.getOrNull(i)?.split('\t')
                v.setTextColor(id, text)
                v.setTextViewText(id, if (r == null) (if (i == 0) t("Henüz bir şey yok", "Nothing yet") else "") else r[0] + "   " + r.getOrElse(1) { "" })
            }
            v.setOnClickPendingIntent(R.id.wr_root, open(c))
            return v
        }
        val v = RemoteViews(c.packageName, R.layout.widget_updates)
        v.setInt(R.id.wu_root, "setBackgroundResource", bg)
        v.setTextColor(R.id.wu_title, accent); v.setTextColor(R.id.wu_count, text); v.setTextColor(R.id.wu_sub, sub)
        if (kind == "downloads") {
            v.setTextViewText(R.id.wu_title, "by UmutK")
            v.setTextViewText(R.id.wu_count, java.text.NumberFormat.getInstance().format(sp.getLong("gh_downloads", 0L)))
            v.setTextViewText(R.id.wu_sub, t("indirme · ${sp.getInt("gh_apps", 0)} uygulama", "downloads · ${sp.getInt("gh_apps", 0)} apps"))
        } else {
            val n = sp.getInt("updates_n", 0)
            val last = sp.getLong("lastCheck", 0L)
            v.setTextViewText(R.id.wu_title, "Punk Store")
            v.setTextViewText(R.id.wu_count, n.toString())
            v.setTextViewText(R.id.wu_sub, (if (n == 1) t("güncelleme bekliyor", "update waiting") else t("güncelleme bekliyor", "updates waiting")) +
                (if (last > 0) " · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(last)) else ""))
        }
        v.setOnClickPendingIntent(R.id.wu_root, open(c))
        return v
    }

    /** Draws every placed widget of every kind again (call after the numbers change). */
    fun refresh(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        listOf(UpdatesWidget::class.java to ("updates" to false), UpdatesMaterialWidget::class.java to ("updates" to true),
            DownloadsWidget::class.java to ("downloads" to false), DownloadsMaterialWidget::class.java to ("downloads" to true),
            ReposWidget::class.java to ("repos" to false), ReposMaterialWidget::class.java to ("repos" to true)).forEach { (cls, k) ->
            m.getAppWidgetIds(ComponentName(c, cls)).forEach { m.updateAppWidget(it, views(c, k.first, k.second)) }
        }
    }
}
