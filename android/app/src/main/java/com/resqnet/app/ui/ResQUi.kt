package com.resqnet.app.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.resqnet.app.R

enum class StatusTone { SUCCESS, WARNING, ERROR, NEUTRAL, EMERGENCY }

class ResQUi(private val activity: Activity) {
    val primary = color(R.color.primary)
    val primaryContainer = color(R.color.primary_container)
    val onPrimary = color(R.color.on_primary)
    val onPrimaryMuted = color(R.color.on_primary_muted)
    val background = color(R.color.background)
    val surface = color(R.color.surface)
    val surfaceVariant = color(R.color.surface_variant)
    val textPrimary = color(R.color.text_primary)
    val textSecondary = color(R.color.text_secondary)
    val outline = color(R.color.outline)
    val success = color(R.color.success)
    val warning = color(R.color.warning)
    val error = color(R.color.error)
    val emergency = color(R.color.emergency)
    val onEmergency = color(R.color.on_emergency)

    fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        v.toFloat(),
        activity.resources.displayMetrics
    ).toInt()

    fun color(id: Int) = ContextCompat.getColor(activity, id)

    fun lp(
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        weight: Float = 0f,
        mt: Int = 0,
        mb: Int = 0,
        ms: Int = 0,
        me: Int = 0
    ) = LinearLayout.LayoutParams(width, height, weight).apply {
        setMargins(dp(ms), dp(mt), dp(me), dp(mb))
    }

    fun screenColumn(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(this@ResQUi.background)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    fun scroll(content: View): ScrollView = ScrollView(activity).apply {
        isFillViewport = true
        setBackgroundColor(this@ResQUi.background)
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    fun titleLarge(text: String, color: Int = textPrimary) = label(text, 28f, true, color)
    fun titleMedium(text: String, color: Int = textPrimary) = label(text, 18f, true, color)
    fun titleSmall(text: String, color: Int = textPrimary) = label(text, 16f, true, color)
    fun body(text: String, color: Int = textPrimary) = label(text, 14f, false, color)
    fun caption(text: String, color: Int = textSecondary) = label(text, 12f, false, color)
    fun emphasis(text: String, color: Int = textPrimary) = label(text, 16f, true, color)

    private fun label(text: String, sizeSp: Float, medium: Boolean, color: Int): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            typeface = if (medium) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            } else {
                Typeface.SANS_SERIF
            }
            setLineSpacing(dp(2).toFloat(), 1f)
        }
    }

    fun icon(res: Int, tint: Int, sizeDp: Int = 24, description: String? = null): AppCompatImageView {
        return AppCompatImageView(activity).apply {
            setImageResource(res)
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            importantForAccessibility = if (description == null) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            contentDescription = description
            layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    fun iconButton(res: Int, description: String, tint: Int = primary, onClick: () -> Unit): AppCompatImageButton {
        return AppCompatImageButton(activity).apply {
            setImageResource(res)
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))
            contentDescription = description
            background = selectableBackground()
            val size = dp(48)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }
    }

    fun card(): MaterialCardView = MaterialCardView(activity).apply {
        radius = dp(16).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(surface)
        strokeWidth = 0
        preventCornerOverlap = true
        useCompatPadding = false
        layoutParams = lp(mb = 12)
        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        addView(inner, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        tag = inner
    }

    fun cardColumn(card: MaterialCardView): LinearLayout = card.tag as LinearLayout

    fun header(title: String, showBack: Boolean, onBack: () -> Unit): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(mb = 16)
            minimumHeight = dp(48)
            if (showBack) {
                addView(iconButton(R.drawable.ic_back, activity.getString(R.string.cd_back), primary, onBack))
            }
            val titleView = titleMedium(title, primary).apply {
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(titleView, lp(width = 0, weight = 1f, ms = if (showBack) 8 else 0))
        }
    }

    fun primaryButton(text: String, icon: Int? = null): MaterialButton =
        styledButton(text, primaryContainer, onPrimary, icon)

    fun secondaryButton(text: String, icon: Int? = null): MaterialButton =
        MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            isAllCaps = false
            cornerRadius = dp(12)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            minimumHeight = dp(48)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(primary)
            strokeColor = ColorStateList.valueOf(outline)
            strokeWidth = dp(1)
            if (icon != null) {
                setIconResource(icon)
                iconTint = ColorStateList.valueOf(primary)
                iconPadding = dp(8)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            }
            layoutParams = lp(height = dp(48), mb = 8)
        }

    fun tonalButton(text: String, icon: Int? = null): MaterialButton =
        styledButton(text, surfaceVariant, primary, icon)

    fun emergencyButton(text: String, icon: Int? = null): MaterialButton =
        styledButton(text, emergency, onEmergency, icon)

    fun destructiveButton(text: String, icon: Int? = null): MaterialButton =
        secondaryButton(text, icon).apply {
            setTextColor(this@ResQUi.error)
            strokeColor = ColorStateList.valueOf(outline)
            if (icon != null) iconTint = ColorStateList.valueOf(this@ResQUi.error)
        }

    private fun styledButton(text: String, bg: Int, fg: Int, icon: Int?): MaterialButton {
        return MaterialButton(activity).apply {
            this.text = text
            isAllCaps = false
            cornerRadius = dp(12)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            minimumHeight = dp(48)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            backgroundTintList = ColorStateList.valueOf(bg)
            setTextColor(fg)
            rippleColor = ColorStateList.valueOf(0x33FFFFFF)
            if (icon != null) {
                setIconResource(icon)
                iconTint = ColorStateList.valueOf(fg)
                iconPadding = dp(8)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            }
            layoutParams = lp(height = dp(48), mb = 8)
        }
    }

    fun sosButton(onClick: () -> Unit): LinearLayout {
        val bg = ContextCompat.getDrawable(activity, R.drawable.bg_sos)
        val ripple = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ripple
            isClickable = true
            isFocusable = true
            contentDescription = activity.getString(R.string.cd_sos)
            minimumHeight = dp(96)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = lp(mb = 12)
            addView(icon(R.drawable.ic_sos, onEmergency, 28, null))
            addView(label(activity.getString(R.string.sos_label), 28f, true, onEmergency).apply {
                gravity = Gravity.CENTER
                layoutParams = lp(mt = 4)
            })
            addView(caption(activity.getString(R.string.sos_support), onEmergency).apply {
                gravity = Gravity.CENTER
                alpha = 0.92f
            })
            setOnClickListener { onClick() }
        }
    }

    fun statusCard(
        iconRes: Int,
        title: String,
        explanation: String,
        secondary: String? = null,
        tone: StatusTone = StatusTone.SUCCESS
    ): MaterialCardView {
        val card = card()
        val col = cardColumn(card)
        col.removeAllViews()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(toneBadge(iconRes, tone))
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(width = 0, weight = 1f, ms = 12)
        }
        texts.addView(titleSmall(title, toneColor(tone)))
        texts.addView(caption(explanation).apply { layoutParams = lp(mt = 4) })
        if (!secondary.isNullOrBlank()) {
            texts.addView(caption(secondary).apply { layoutParams = lp(mt = 4) })
        }
        row.addView(texts)
        col.addView(row)
        return card
    }

    fun emptyState(
        iconRes: Int,
        title: String,
        body: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ): MaterialCardView {
        val card = card()
        val col = cardColumn(card)
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(toneBadge(iconRes, StatusTone.NEUTRAL).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        col.addView(titleSmall(title).apply {
            gravity = Gravity.CENTER
            layoutParams = lp(mt = 12)
        })
        col.addView(body(body, textSecondary).apply {
            gravity = Gravity.CENTER
            layoutParams = lp(mt = 8)
        })
        if (actionLabel != null && onAction != null) {
            col.addView(primaryButton(actionLabel, R.drawable.ic_add).apply {
                layoutParams = lp(mt = 16, mb = 0)
                setOnClickListener { onAction() }
            })
        }
        return card
    }

    fun actionTile(iconRes: Int, label: String, onClick: () -> Unit): MaterialCardView {
        return MaterialCardView(activity).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(surface)
            strokeWidth = 0
            isClickable = true
            isFocusable = true
            rippleColor = ColorStateList.valueOf(surfaceVariant)
            setOnClickListener { onClick() }
            val inner = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(16), dp(12), dp(16))
                minimumHeight = dp(88)
                addView(icon(iconRes, primary, 24, null))
                addView(body(label, primary).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    layoutParams = lp(mt = 8, mb = 0)
                })
            }
            addView(inner, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    fun twoColumn(items: List<View>): GridLayout {
        return GridLayout(activity).apply {
            columnCount = 2
            layoutParams = lp(mb = 12)
            items.forEachIndexed { index, child ->
                val col = index % 2
                val row = index / 2
                addView(child, GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(
                        if (col == 0) 0 else dp(4),
                        if (row == 0) 0 else dp(8),
                        if (col == 1) 0 else dp(4),
                        0
                    )
                })
            }
        }
    }

    fun labeledInput(label: String, hint: String, inputType: Int, value: String = ""): Pair<LinearLayout, EditText> {
        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(mb = 12)
        }
        wrap.addView(caption(label).apply { layoutParams = lp(mb = 8) })
        val field = textField(hint, inputType, value)
        wrap.addView(field, lp(height = dp(48), mb = 0))
        return wrap to field
    }

    fun textField(hint: String, inputType: Int, value: String = ""): EditText {
        return EditText(activity).apply {
            this.hint = hint
            this.inputType = inputType
            setText(value)
            setSingleLine()
            minHeight = dp(48)
            textSize = 14f
            setTextColor(textPrimary)
            setHintTextColor(textSecondary)
            background = ContextCompat.getDrawable(activity, R.drawable.bg_input)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
    }

    fun sectionTitle(text: String): TextView = titleMedium(text).apply {
        layoutParams = lp(mt = 4, mb = 8)
    }

    fun listRow(iconRes: Int, title: String, subtitle: String? = null, tint: Int = primary): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            addView(toneBadge(iconRes, StatusTone.NEUTRAL, tint))
            val texts = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = lp(width = 0, weight = 1f, ms = 12)
            }
            texts.addView(titleSmall(title))
            if (!subtitle.isNullOrBlank()) {
                texts.addView(caption(subtitle).apply { layoutParams = lp(mt = 4) })
            }
            addView(texts)
        }
    }

    fun avatar(initial: String, sizeDp: Int = 72): FrameLayout {
        return FrameLayout(activity).apply {
            background = ContextCompat.getDrawable(activity, R.drawable.bg_avatar_fill)
            val size = dp(sizeDp)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
            val letter = titleLarge(initial.take(1).uppercase(), onPrimary).apply {
                gravity = Gravity.CENTER
            }
            addView(letter, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    fun toneBadge(iconRes: Int, tone: StatusTone, tintOverride: Int? = null): FrameLayout {
        val bgRes = when (tone) {
            StatusTone.SUCCESS -> R.drawable.bg_status_success
            StatusTone.WARNING -> R.drawable.bg_status_warning
            StatusTone.ERROR, StatusTone.EMERGENCY -> R.drawable.bg_status_error
            StatusTone.NEUTRAL -> R.drawable.bg_status_neutral
        }
        val tint = tintOverride ?: toneColor(tone)
        return FrameLayout(activity).apply {
            background = ContextCompat.getDrawable(activity, bgRes)
            val size = dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size)
            val iv = icon(iconRes, tint, 22, null)
            addView(iv, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
    }

    fun toneColor(tone: StatusTone): Int = when (tone) {
        StatusTone.SUCCESS -> success
        StatusTone.WARNING -> warning
        StatusTone.ERROR, StatusTone.EMERGENCY -> emergency
        StatusTone.NEUTRAL -> primary
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable? {
        val value = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        return ContextCompat.getDrawable(activity, value.resourceId)
    }
}
