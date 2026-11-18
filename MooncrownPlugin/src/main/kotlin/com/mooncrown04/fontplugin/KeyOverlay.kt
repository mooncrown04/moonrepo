package com.mooncrown04.fontplugin

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Bölüm değiştirme geri bildirimi.
 *
 * Kumandadan önceki/sonraki bölüme geçildiğinde ekranın üstünde kısa süreli
 * bir banner gösterir. Banner odak almaz, tıklanamaz; böylece kumanda ile
 * gezinmeyi ve oynatıcıyı etkilemez.
 */
object KeyOverlay {

    private const val TAG = "FontPlugin"
    private const val GOLD = "#FDB912"
    private const val BG = "#F2141A20"
    private const val BORDER = "#66FDB912"

    private val handler = Handler(Looper.getMainLooper())

    private val banners = WeakHashMap<Activity, View>()
    private val hiders = WeakHashMap<Activity, Runnable>()

    /**
     * [directionLabel] hemen gösterilir ("ÖNCEKİ BÖLÜM" gibi), [titleProvider]
     * ise host bölümü değiştirdikten sonra çağrılır; uzun sürerse başlık
     * "Bölüm değiştirildi" olarak kalır.
     */
    fun episodeChange(activity: Activity, directionLabel: String, titleProvider: () -> String?) {
        val (banner, titleView) = show(activity, directionLabel, "değiştiriliyor…", isPlaceholder = true)

        handler.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            if (banners[activity] !== banner) return@postDelayed
            val title = runCatching { titleProvider() }.getOrNull()?.trim()
            titleView.text = if (title.isNullOrEmpty()) "Bölüm değiştirildi" else title
            titleView.setTextColor(Color.parseColor("#F3F6F8"))
        }, 650)
    }

    /** Sabit metinli banner; ornegin son bolumdeyken "Son bölümdesin". */
    fun message(activity: Activity, label: String, title: String) {
        show(activity, label, title, isPlaceholder = false)
    }

    fun forget(activity: Activity) {
        hiders.remove(activity)?.let { handler.removeCallbacks(it) }
        banners.remove(activity)?.let { banner ->
            runCatching { (banner.parent as? ViewGroup)?.removeView(banner) }
        }
    }

    private fun show(
        activity: Activity,
        label: String,
        title: String,
        isPlaceholder: Boolean,
    ): Pair<View, TextView> {
        val decor = activity.window?.decorView as? ViewGroup
            ?: return Pair(View(activity), TextView(activity))

        forget(activity)

        val accent = View(activity).apply {
            background = rounded(activity, GOLD, 3)
        }

        val labelView = TextView(activity).apply {
            text = label
            setTextColor(Color.parseColor(GOLD))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.14f
        }

        val titleView = TextView(activity).apply {
            text = title
            setTextColor(Color.parseColor(if (isPlaceholder) "#8FA3B0" else "#F3F6F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (activity.resources.displayMetrics.widthPixels * 0.68f).roundToInt()
            setPadding(0, dp(activity, 2), 0, 0)
        }

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(labelView)
            addView(titleView)
        }

        val banner = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 18), dp(activity, 10))
            background = roundedPanel(activity)
            addView(
                accent,
                LinearLayout.LayoutParams(dp(activity, 3), ViewGroup.LayoutParams.MATCH_PARENT)
                    .apply { rightMargin = dp(activity, 12) }
            )
            addView(column)
            isFocusable = false
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(activity, 36)
        }

        return try {
            decor.addView(banner, params)
            banners[activity] = banner

            banner.alpha = 0f
            banner.translationY = -dp(activity, 8).toFloat()
            banner.animate().alpha(1f).translationY(0f).setDuration(150).start()

            val hide = Runnable {
                if (banners[activity] !== banner) return@Runnable
                banner.animate().alpha(0f).setDuration(220)
                    .withEndAction {
                        runCatching { (banner.parent as? ViewGroup)?.removeView(banner) }
                        if (banners[activity] === banner) banners.remove(activity)
                    }
                    .start()
            }
            hiders[activity] = hide
            handler.postDelayed(hide, 2600)

            Pair(banner, titleView)
        } catch (t: Throwable) {
            Log.w(TAG, "Overlay could not be shown", t)
            Pair(banner, titleView)
        }
    }

    private fun roundedPanel(activity: Activity): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.parseColor(BG))
        cornerRadius = dp(activity, 14).toFloat()
        setStroke(dp(activity, 1), Color.parseColor(BORDER))
    }

    private fun rounded(activity: Activity, fill: String, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fill))
            cornerRadius = dp(activity, radiusDp).toFloat()
        }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
