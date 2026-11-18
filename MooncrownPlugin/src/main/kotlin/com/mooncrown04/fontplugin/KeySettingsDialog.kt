package com.mooncrown04.fontplugin

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Her kumanda tusu icin ayri ac/kapat ve islem secimi.
 *
 * TV kumandasi icin tasarlandi: her kart odaklandiginda altin cerceveyle
 * vurgulanir, ustteki durum satiri o an hangi tusun secili oldugunu yazar.
 * XML kullanmaz; koyu, altin vurgulu bir panel cizer.
 */
object KeySettingsDialog {

    private const val GOLD = "#FDB912"
    private const val PANEL = "#10151A"
    private const val CARD = "#1A222A"
    private const val CARD_FOCUS = "#243040"
    private const val TEXT = "#F3F6F8"
    private const val MUTED = "#8FA3B0"
    private const val BORDER = "#33FDB912"
    private const val OFF = "#2A343E"
    private const val OFF_ACCENT = "#3B4753"

    fun show(context: Context) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(12))
            background = rounded(context, PANEL, BORDER, 20, 1)
        }

        val title = TextView(context).apply {
            text = "KUMANDA TUŞLARI"
            setTextColor(Color.parseColor(GOLD))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val subtitle = TextView(context).apply {
            text = "▲▼ tuşlarıyla gezin, OK ile aç/kapat veya işlem seç."
            setTextColor(Color.parseColor(MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(10))
        }
        root.addView(title)
        root.addView(subtitle)

        // O an odakta olan tus; TV'de "hangi satirdayim" sorusunu yanitlar.
        val status = TextView(context).apply {
            setTextColor(Color.parseColor("#FFE08A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(context, "#1F2A33", GOLD, 10, 1)
        }
        root.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        )

        fun updateStatus(spec: KeySpec?) {
            if (spec == null) {
                status.text = "▸ Odaklanmak için ▲▼ kullan"
                return
            }
            val enabled = RemoteShortcuts.isKeyEnabled(context, spec)
            status.text = "▸ ${spec.label}  •  " +
                RemoteShortcuts.action(context, spec).label +
                "  •  " + (if (enabled) "AÇIK" else "KAPALI")
        }
        updateStatus(null)

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isFocusable = false
            addView(
                list,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val maxListHeight = minOf(
            dp(400),
            (context.resources.displayMetrics.heightPixels * 0.54f).roundToInt()
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxListHeight)
        )

        val summary = TextView(context).apply {
            setTextColor(Color.parseColor(MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(2), dp(8), 0, dp(4))
        }
        root.addView(summary)

        var firstFocusable: View? = null

        fun refreshSummary() {
            val enabled = RemoteShortcuts.keys.count { RemoteShortcuts.isKeyEnabled(context, it) }
            summary.text = "$enabled / ${RemoteShortcuts.keys.size} tuş etkin • Değişiklikler anında kaydedilir"
        }

        fun rebuild() {
            list.removeAllViews()
            firstFocusable = null
            RemoteShortcuts.keys.forEach { spec ->
                val card = buildRow(
                    context = context,
                    spec = spec,
                    onFocus = { focused -> updateStatus(focused) },
                    onChanged = { refreshSummary() },
                )
                if (firstFocusable == null) firstFocusable = card.tag as? View
                list.addView(
                    card,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(9) }
                )
            }
            refreshSummary()
        }
        rebuild()

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        val reset = tweenButton(
            context,
            "Varsayılanlar",
            fill = "#1F2A33",
            textColor = GOLD,
        ) {
            RemoteShortcuts.resetBindings(context)
            rebuild()
            updateStatus(null)
        }
        val close = tweenButton(
            context,
            "Kapat",
            fill = GOLD,
            textColor = "#10151A",
        )
        footer.addView(reset)
        footer.addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
        )
        root.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()

        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (context.resources.displayMetrics.widthPixels * 0.94f).roundToInt()
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            firstFocusable?.post { firstFocusable?.requestFocus() }
        }
        dialog.show()
    }

    /** Karti ve ilk odaklanabilir ogreni dondurur (tag uzerinden). */
    private fun buildRow(
        context: Context,
        spec: KeySpec,
        onFocus: (KeySpec) -> Unit,
        onChanged: () -> Unit,
    ): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(context, CARD, BORDER, 14, 1)
            isFocusable = false
            isClickable = false
        }

        val accent = View(context).apply {
            background = rounded(context, OFF_ACCENT, null, 2, 0)
        }
        card.addView(
            accent,
            LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        card.addView(
            inner,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        labels.addView(
            TextView(context).apply {
                text = spec.label
                setTextColor(Color.parseColor(TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
            }
        )
        labels.addView(
            TextView(context).apply {
                text = "keyCode ${spec.code} • varsayılan: ${spec.defaultAction.label}"
                setTextColor(Color.parseColor(MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(0, dp(2), 0, 0)
            }
        )
        top.addView(
            labels,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val toggle = Button(context).apply {
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.06f
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        top.addView(toggle)
        inner.addView(
            top,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val actionButton = Button(context).apply {
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        inner.addView(
            actionButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        fun anyFocused(): Boolean = toggle.hasFocus() || actionButton.hasFocus()

        fun applyCardStyle() {
            val focused = anyFocused()
            val enabled = RemoteShortcuts.isKeyEnabled(context, spec)
            card.background = if (focused) {
                rounded(context, CARD_FOCUS, GOLD, 14, 2)
            } else {
                rounded(context, CARD, BORDER, 14, 1)
            }
            accent.background = rounded(
                context,
                when {
                    focused -> GOLD
                    enabled -> GOLD
                    else -> OFF_ACCENT
                },
                null,
                2,
                0,
            )
        }

        fun renderToggle() {
            val enabled = RemoteShortcuts.isKeyEnabled(context, spec)
            val focused = toggle.hasFocus()
            toggle.text = if (enabled) "AÇIK" else "KAPALI"
            toggle.setTextColor(
                when {
                    focused && enabled -> Color.BLACK
                    focused -> Color.WHITE
                    enabled -> Color.BLACK
                    else -> Color.parseColor(MUTED)
                }
            )
            toggle.background = when {
                focused && enabled -> rounded(context, "#FFD34D", "#FFFFFF", 10, 2)
                focused -> rounded(context, "#39485A", "#FFFFFF", 10, 2)
                enabled -> rounded(context, GOLD, null, 10, 0)
                else -> rounded(context, OFF, BORDER, 10, 1)
            }
        }

        fun renderAction() {
            val action = RemoteShortcuts.action(context, spec)
            val focused = actionButton.hasFocus()
            actionButton.text = "İşlem  ▸  ${action.label}"
            actionButton.setTextColor(
                if (focused) Color.parseColor("#FFE08A") else Color.parseColor(GOLD)
            )
            actionButton.background = if (focused) {
                rounded(context, "#3A2F14", GOLD, 10, 2)
            } else {
                rounded(context, "#222C35", BORDER, 10, 1)
            }
        }

        fun renderAll() {
            applyCardStyle()
            renderToggle()
            renderAction()
        }

        val focusWatcher = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onFocus(spec)
            renderAll()
        }

        toggle.setOnFocusChangeListener(focusWatcher)
        actionButton.setOnFocusChangeListener(focusWatcher)

        toggle.setOnClickListener {
            val next = !RemoteShortcuts.isKeyEnabled(context, spec)
            RemoteShortcuts.setKeyEnabled(context, spec, next)
            renderAll()
            onFocus(spec)
            onChanged()
        }

        actionButton.setOnClickListener {
            showActionPicker(context, spec) { picked ->
                onChanged()
                renderAction()
                actionButton.text = "İşlem  ▸  ${picked.label}"
                onFocus(spec)
            }
        }

        renderAll()

        // Ilk odaklanabilir ogreni kartin tag'ine koy; ana dongu ilk karta odaklanir.
        card.tag = toggle
        return card
    }

    private fun showActionPicker(
        context: Context,
        spec: KeySpec,
        onPick: (KeyAction) -> Unit,
    ) {
        val actions = KeyAction.entries
        val labels = actions.map { it.label }.toTypedArray()
        val selected = actions.indexOf(RemoteShortcuts.action(context, spec))

        AlertDialog.Builder(context)
            .setTitle("${spec.label} için işlem")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val action = actions[which]
                RemoteShortcuts.setAction(context, spec, action)
                onPick(action)
                dialog.dismiss()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    /** Odaklandiginda dolgusu degisen, TV'de secili oldugu belli olan dugme. */
    private fun tweenButton(
        context: Context,
        label: String,
        fill: String,
        textColor: String,
        onClick: (() -> Unit)? = null,
    ): Button {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val dark = fill != GOLD
        val button = Button(context).apply {
            text = label
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(textColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(20), dp(9), dp(20), dp(9))
            background = rounded(context, fill, if (dark) BORDER else null, 12, 1)
            setOnClickListener { onClick?.invoke() }
            setOnFocusChangeListener { view, hasFocus ->
                background = when {
                    hasFocus && dark -> rounded(context, "#3A2F14", GOLD, 12, 2)
                    hasFocus -> rounded(context, "#FFD34D", "#FFFFFF", 12, 2)
                    dark -> rounded(context, fill, BORDER, 12, 1)
                    else -> rounded(context, GOLD, null, 12, 0)
                }
                view.alpha = if (hasFocus) 1f else 0.92f
            }
        }
        return button
    }

    private fun rounded(
        context: Context,
        fill: String,
        stroke: String?,
        radiusDp: Int,
        strokeDp: Int,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.parseColor(fill))
        cornerRadius = (radiusDp * context.resources.displayMetrics.density)
        if (stroke != null) {
            setStroke(
                (strokeDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1),
                Color.parseColor(stroke),
            )
        }
    }
}
