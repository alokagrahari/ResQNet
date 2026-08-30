package com.resqnet.app

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.resqnet.app.ble.BleTestActivity
import com.resqnet.app.emergency.SosCoordinator
import com.resqnet.app.ui.ResQUi
import com.resqnet.app.ui.StatusTone
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var ui: ResQUi
    private lateinit var sosCoordinator: SosCoordinator
    private val prefs by lazy { getSharedPreferences("resqnet", Context.MODE_PRIVATE) }
    private val phoneUtil = PhoneNumberUtil.getInstance()
    private var selectedCountry = "IN"
    private var selectedDial = "+91"
    private var sosActive = false
    private var sosSeconds = 0
    private val sosHandler = Handler(Looper.getMainLooper())
    private var sosRunnable: Runnable? = null
    private var contacts = mutableListOf<Contact>()
    private var suppressNav = false
    private var currentNav = R.id.nav_home

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) refreshLocationForUi()
    }

    data class Contact(
        var name: String = "",
        var country: String = "",
        var dial: String = "+91",
        var phone: String = "",
        var relationship: String = ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ResQUi(this)
        sosCoordinator = SosCoordinator.get(this)
        contacts = loadContacts()
        showSplash()
    }

    private fun setPage(content: View, navId: Int? = currentNav, showNav: Boolean = true) {
        if (navId != null) currentNav = navId
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.background)
        }
        val holder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        holder.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(holder)
        if (showNav) {
            root.addView(buildBottomNav(currentNav))
        }
        setContentView(root)
    }

    private fun buildBottomNav(selected: Int): BottomNavigationView {
        return BottomNavigationView(this).apply {
            inflateMenu(R.menu.bottom_nav)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_bottom_nav)
            elevation = 0f
            itemIconTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.nav_item_color)
            itemTextColor = ContextCompat.getColorStateList(this@MainActivity, R.color.nav_item_color)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isItemActiveIndicatorEnabled = true
            suppressNav = true
            selectedItemId = selected
            suppressNav = false
            setOnItemSelectedListener { item ->
                if (suppressNav) return@setOnItemSelectedListener true
                when (item.itemId) {
                    R.id.nav_home -> showHome()
                    R.id.nav_emergency -> showEmergencySelection()
                    R.id.nav_contacts -> showContacts()
                    R.id.nav_profile -> showProfile()
                }
                true
            }
        }
    }

    private fun showSplash() {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ui.primary)
            setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32))
        }
        val logo = AppCompatImageView(this).apply {
            setImageResource(R.drawable.resqnet_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.cd_logo)
        }
        screen.addView(logo, LinearLayout.LayoutParams(ui.dp(120), ui.dp(120)))
        screen.addView(ui.titleLarge("ResQNet", ui.onPrimary).apply {
            gravity = Gravity.CENTER
            layoutParams = ui.lp(mt = 20)
        })
        screen.addView(ui.body(getString(R.string.tagline), ui.onPrimaryMuted).apply {
            gravity = Gravity.CENTER
            layoutParams = ui.lp(mt = 8)
        })
        setPage(screen, showNav = false)
        Handler(Looper.getMainLooper()).postDelayed({
            ensureLocationPermission()
            if (prefs.contains("name")) showHome() else showLogin()
        }, 1600)
    }

    private fun showLogin() {
        val outer = ui.screenColumn()
        outer.gravity = Gravity.CENTER_HORIZONTAL
        val logo = AppCompatImageView(this).apply {
            setImageResource(R.drawable.resqnet_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.cd_logo)
        }
        outer.addView(logo, LinearLayout.LayoutParams(ui.dp(88), ui.dp(88)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = ui.dp(16)
            bottomMargin = ui.dp(16)
        })
        outer.addView(ui.titleLarge("ResQNet", ui.primary).apply { gravity = Gravity.CENTER })
        outer.addView(ui.body(getString(R.string.purpose), ui.textSecondary).apply {
            gravity = Gravity.CENTER
            layoutParams = ui.lp(mt = 8, mb = 16)
        })

        val card = ui.card()
        val col = ui.cardColumn(card)
        val (nameWrap, name) = ui.labeledInput(
            "Name",
            "Name or username",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )
        name.imeOptions = EditorInfo.IME_ACTION_NEXT
        col.addView(nameWrap)

        col.addView(ui.caption("Phone number").apply { layoutParams = ui.lp(mb = 8) })
        val phoneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ui.lp(mb = 12)
        }
        val countryBtn = ui.secondaryButton("${flag(selectedCountry)}  $selectedDial").apply {
            textSize = 14f
            contentDescription = getString(R.string.cd_country)
            layoutParams = LinearLayout.LayoutParams(ui.dp(120), ui.dp(48)).apply {
                marginEnd = ui.dp(8)
            }
        }
        val phone = ui.textField("Phone number", InputType.TYPE_CLASS_PHONE)
        phone.imeOptions = EditorInfo.IME_ACTION_NEXT
        phoneRow.addView(countryBtn)
        phoneRow.addView(phone, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        col.addView(phoneRow)
        countryBtn.setOnClickListener { showCountryPicker(countryBtn) }

        val (emailWrap, email) = ui.labeledInput(
            "Email (optional)",
            "name@example.com",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        email.imeOptions = EditorInfo.IME_ACTION_DONE
        col.addView(emailWrap)

        val agree = CheckBox(this).apply {
            text = "I agree to the Terms & Privacy Policy"
            textSize = 14f
            setTextColor(ui.textPrimary)
            minHeight = ui.dp(48)
            layoutParams = ui.lp(mb = 8)
        }
        col.addView(agree)
        val err = ui.caption("", ui.error).apply { visibility = View.GONE }
        col.addView(err)
        val cont = ui.primaryButton("Continue")
        cont.layoutParams = ui.lp(height = ui.dp(48), mt = 8, mb = 0)
        col.addView(cont)
        outer.addView(card)

        cont.setOnClickListener {
            val msg = when {
                name.text.toString().trim().isEmpty() -> "Please enter your name or username."
                phone.text.toString().trim().isEmpty() -> "Enter a valid phone number."
                !validPhone(phone.text.toString(), selectedCountry) ->
                    "Enter a valid phone number for $selectedDial."
                !agree.isChecked -> "Please agree to the Terms & Privacy Policy."
                else -> ""
            }
            if (msg.isNotEmpty()) {
                err.text = msg
                err.visibility = View.VISIBLE
                return@setOnClickListener
            }
            hideKeyboard()
            prefs.edit()
                .putString("name", name.text.toString().trim())
                .putString("phone", "$selectedDial ${phone.text}")
                .putString("email", email.text.toString())
                .apply()
            showHome()
        }
        setPage(ui.scroll(outer), showNav = false)
    }

    private fun validPhone(raw: String, region: String): Boolean = try {
        val n = phoneUtil.parse(raw, region)
        phoneUtil.isValidNumber(n)
    } catch (_: Exception) {
        false
    }

    private fun flag(code: String): String {
        if (code.length != 2) return "•"
        return code.map { Character.toChars(127397 + it.code).concatToString() }.joinToString("")
    }

    private fun showCountryPicker(
        target: android.widget.Button,
        onPicked: ((String, String) -> Unit)? = null
    ) {
        val all = phoneUtil.supportedRegions.map { code ->
            val name = Locale("", code).displayCountry.ifBlank { code }
            Triple(code, name, "+${phoneUtil.getCountryCodeForRegion(code)}")
        }.sortedBy { it.second }
        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card)
        }
        box.addView(ui.titleMedium("Select country", ui.primary))
        val search = ui.textField("Search country or code", InputType.TYPE_CLASS_TEXT)
        search.layoutParams = ui.lp(mt = 12, mb = 12)
        box.addView(search)
        val list = ListView(this).apply {
            divider = null
            dividerHeight = 0
        }
        box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.72f).toInt()
        dialog.window?.setLayout(width, height)

        fun matching(q: String) = all.filter {
            it.second.contains(q, true) || it.first.contains(q, true) || it.third.contains(q)
        }
        fun refresh(q: String) {
            list.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                matching(q).map { "${flag(it.first)}  ${it.second}   ${it.third}" }
            )
        }
        refresh("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                refresh(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        list.setOnItemClickListener { _, _, pos, _ ->
            val data = matching(search.text.toString())
            val picked = data[pos]
            if (onPicked != null) onPicked(picked.first, picked.third)
            else {
                selectedCountry = picked.first
                selectedDial = picked.third
            }
            target.text = "${flag(picked.first)}  ${picked.third}"
            dialog.dismiss()
        }
    }

    private fun showHome() {
        val l = ui.screenColumn()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ui.lp(mb = 16)
        }
        val logo = AppCompatImageView(this).apply {
            setImageResource(R.drawable.resqnet_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.cd_logo)
        }
        top.addView(logo, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        val greetCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ui.lp(width = 0, weight = 1f, ms = 12)
        }
        greetCol.addView(ui.titleSmall("ResQNet", ui.primary))
        greetCol.addView(ui.caption("Emergency communication network"))
        top.addView(greetCol)
        top.addView(
            ui.iconButton(R.drawable.ic_person, getString(R.string.cd_profile)) { showProfile() }
        )
        l.addView(top)

        val name = prefs.getString("name", "User") ?: "User"
        l.addView(ui.titleLarge("Hello, $name", ui.primary))
        l.addView(ui.caption(timeGreeting()).apply { layoutParams = ui.lp(mt = 4, mb = 16) })

        l.addView(
            ui.statusCard(
                R.drawable.ic_mesh,
                "Mesh connected",
                "Nearby nodes can relay emergency alerts.",
                "Network connected  ·  GPS on  ·  Bluetooth on",
                StatusTone.SUCCESS
            )
        )

        l.addView(ui.sosButton { showEmergencySelection() })

        val voice = ui.secondaryButton("Voice command", R.drawable.ic_voice)
        voice.contentDescription = getString(R.string.cd_voice)
        voice.layoutParams = ui.lp(height = ui.dp(48), mb = 12)
        voice.setOnClickListener { showVoiceDialog() }
        l.addView(voice)

        l.addView(locationStatusCard())

        l.addView(ui.sectionTitle("Quick actions"))
        l.addView(
            ui.twoColumn(
                listOf(
                    ui.actionTile(R.drawable.ic_messages, "Messages") { showMessages() },
                    ui.actionTile(R.drawable.ic_map, "Map") { showTracking() },
                    ui.actionTile(R.drawable.ic_network, "Network status") { showNetwork() },
                    ui.actionTile(R.drawable.ic_nearby, "Nearby help") { showNearby() }
                )
            )
        )

        l.addView(ui.sectionTitle("Emergency types"))
        val types = listOf(
            "Medical" to R.drawable.ic_medical,
            "Accident" to R.drawable.ic_accident,
            "Fire" to R.drawable.ic_fire,
            "Crime" to R.drawable.ic_crime,
            "Disaster" to R.drawable.ic_disaster,
            "Child safety" to R.drawable.ic_child
        )
        l.addView(
            ui.twoColumn(
                types.map { (label, icon) ->
                    ui.actionTile(icon, label) { showEmergencySelection() }
                }
            )
        )

        l.addView(
            ui.statusCard(
                R.drawable.ic_monitor,
                "AI emergency detection",
                "Monitoring is active for this prototype.",
                "Detection confidence: 96%  ·  Voice / Manual SOS",
                StatusTone.SUCCESS
            )
        )

        val contactsCard = ui.card()
        val contactsCol = ui.cardColumn(contactsCard)
        contactsCol.addView(ui.listRow(R.drawable.ic_contacts, "Emergency contacts", null))
        if (contacts.isEmpty()) {
            contactsCol.addView(
                ui.caption("No emergency contacts yet. Add trusted contacts so they can be notified during an emergency.")
                    .apply { layoutParams = ui.lp(mt = 8, mb = 12) }
            )
        } else {
            contactsCol.addView(
                ui.caption("${contacts.size} trusted contact(s) saved.")
                    .apply { layoutParams = ui.lp(mt = 8, mb = 12) }
            )
        }
        val manage = ui.tonalButton("Manage contacts", R.drawable.ic_contacts)
        manage.layoutParams = ui.lp(height = ui.dp(48), mb = 0)
        manage.setOnClickListener { showContacts() }
        contactsCol.addView(manage)
        l.addView(contactsCard)

        setPage(ui.scroll(l), R.id.nav_home)
    }

    private fun timeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning. Stay safe."
            hour < 17 -> "Good afternoon. Stay safe."
            else -> "Good evening. Stay safe."
        }
    }

    private fun showEmergencySelection() {
        val l = ui.screenColumn()
        l.addView(ui.header("Emergency type", false) { showHome() })
        l.addView(ui.titleMedium("Select what is happening", ui.primary))
        l.addView(
            ui.body("We will only simulate the emergency workflow in this prototype.", ui.textSecondary)
                .apply { layoutParams = ui.lp(mt = 8, mb = 16) }
        )
        val types = listOf(
            "Medical" to R.drawable.ic_medical,
            "Accident" to R.drawable.ic_accident,
            "Fire" to R.drawable.ic_fire,
            "Crime" to R.drawable.ic_crime,
            "Disaster" to R.drawable.ic_disaster,
            "Other" to R.drawable.ic_other
        )
        types.forEachIndexed { index, (type, icon) ->
            val btn = ui.tonalButton(type, icon)
            btn.layoutParams = ui.lp(height = ui.dp(48), mb = if (index == types.lastIndex) 0 else 8)
            btn.setOnClickListener { showSOS(type) }
            l.addView(btn)
        }
        setPage(ui.scroll(l), R.id.nav_emergency)
    }

    private fun showSOS(type: String = "Medical") {
        sosActive = true
        sosSeconds = 0
        startTimer()
        val l = ui.screenColumn()
        l.addView(ui.header("Emergency SOS", true) { showHome() })

        val c = ui.card()
        val col = ui.cardColumn(c)
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(ui.icon(R.drawable.ic_sos, ui.emergency, 36, null).apply {
            layoutParams = LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        col.addView(ui.titleLarge("SOS active", ui.emergency).apply {
            gravity = Gravity.CENTER
            layoutParams = ui.lp(mt = 8)
        })
        col.addView(ui.emphasis("$type emergency", ui.primary).apply {
            gravity = Gravity.CENTER
            layoutParams = ui.lp(mt = 4)
        })
        val timer = ui.titleLarge("00:00", ui.emergency).apply {
            gravity = Gravity.CENTER
            textSize = 36f
            layoutParams = ui.lp(mt = 12)
        }
        col.addView(timer)
        col.addView(
            ui.caption("Emergency contacts notified · Services alerted · Live location sharing ready")
                .apply {
                    gravity = Gravity.CENTER
                    layoutParams = ui.lp(mt = 8, mb = 16)
                }
        )
        dispatchSos(type)
        val cancel = ui.destructiveButton("Cancel SOS", R.drawable.ic_close)
        cancel.layoutParams = ui.lp(height = ui.dp(48), mb = 0)
        cancel.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cancel SOS?")
                .setMessage("Are you sure you want to cancel this emergency event?")
                .setNegativeButton("Keep Active", null)
                .setPositiveButton("Cancel SOS") { _, _ ->
                    sosActive = false
                    stopTimer()
                    showHistory()
                }
                .show()
        }
        col.addView(cancel)
        l.addView(c)

        val info = ui.card()
        val infoCol = ui.cardColumn(info)
        infoCol.addView(ui.titleSmall("Emergency status"))
        listOf(
            "System ready",
            "Contacts notified",
            "Emergency services alerted",
            "Location sharing"
        ).forEach { item ->
            infoCol.addView(ui.listRow(R.drawable.ic_check, item, null, ui.success).apply {
                layoutParams = ui.lp(mt = 8)
            })
        }
        l.addView(info)

        val call = ui.emergencyButton("Call emergency services", R.drawable.ic_call)
        call.layoutParams = ui.lp(height = ui.dp(48), mb = 0)
        call.setOnClickListener {
            Toast.makeText(this, "Calling emergency services (prototype)", Toast.LENGTH_SHORT).show()
        }
        l.addView(call)
        setPage(ui.scroll(l), R.id.nav_emergency, showNav = false)

        val r = object : Runnable {
            override fun run() {
                if (sosActive) {
                    timer.text = String.format("%02d:%02d", sosSeconds / 60, sosSeconds % 60)
                    timer.postDelayed(this, 1000)
                }
            }
        }
        timer.post(r)
    }

    private fun startTimer() {
        stopTimer()
        sosRunnable = object : Runnable {
            override fun run() {
                if (sosActive) {
                    sosSeconds++
                    sosHandler.postDelayed(this, 1000)
                }
            }
        }
        sosHandler.postDelayed(sosRunnable!!, 1000)
    }

    private fun stopTimer() {
        sosRunnable?.let { sosHandler.removeCallbacks(it) }
    }

    private fun showMessages() {
        val l = ui.screenColumn()
        l.addView(ui.header("Messages", true) { showHome() })
        l.addView(
            ui.statusCard(
                R.drawable.ic_messages,
                "ResQNet Team",
                "Your emergency communication channel is ready.",
                tone = StatusTone.NEUTRAL
            )
        )
        l.addView(messageBubble("User A", "I need help near my location.", false))
        l.addView(messageBubble("ResQNet Team", "Support team acknowledged the alert.", true))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ui.lp(mt = 12)
        }
        val input = ui.textField(
            "Type a message",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        val send = ui.primaryButton("Send", R.drawable.ic_send)
        send.contentDescription = getString(R.string.cd_send_message)
        send.layoutParams = LinearLayout.LayoutParams(ui.dp(112), ui.dp(48)).apply {
            marginStart = ui.dp(8)
        }
        row.addView(input, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        row.addView(send)
        l.addView(row)
        send.setOnClickListener {
            if (input.text.isNotBlank()) {
                Toast.makeText(this, "Message sent (mock)", Toast.LENGTH_SHORT).show()
                input.text.clear()
            }
        }
        setPage(ui.scroll(l), R.id.nav_home)
    }

    private fun messageBubble(sender: String, body: String, outgoing: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (outgoing) Gravity.END else Gravity.START
            layoutParams = ui.lp(mb = 12)
            addView(ui.caption(sender).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = ui.dp(4) }
            })
            val bubble = ui.body(body, if (outgoing) ui.onPrimary else ui.textPrimary).apply {
                background = ContextCompat.getDrawable(
                    this@MainActivity,
                    if (outgoing) R.drawable.bg_bubble_out else R.drawable.bg_bubble_in
                )
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            }
            addView(
                bubble,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun showTracking() {
        val l = ui.screenColumn()
        l.addView(ui.header("Live tracking", true) { showHome() })
        val map = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_map)
            minimumHeight = ui.dp(320)
            layoutParams = ui.lp(mb = 12)
        }
        val markers = listOf(
            Triple("You", R.drawable.ic_you, ui.primary),
            Triple("Hospital", R.drawable.ic_hospital, ui.success),
            Triple("Police", R.drawable.ic_police, ui.primaryContainer),
            Triple("Fire", R.drawable.ic_fire, ui.emergency),
            Triple("Responder", R.drawable.ic_person, ui.success)
        )
        var y = 16
        markers.forEach { (label, icon, tint) ->
            val chip = mapChip(icon, label, tint)
            map.addView(chip, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ui.dp(40)
            ).apply {
                leftMargin = ui.dp(16)
                topMargin = ui.dp(y)
            })
            y += 48
        }
        l.addView(map)
        val c = ui.card()
        val col = ui.cardColumn(c)
        col.addView(ui.titleSmall("Emergency status"))
        col.addView(
            ui.body(
                "Current location: ${locationSummary()}\n" +
                    "Responder distance: 1.8 km\n" +
                    "Estimated arrival: 6 min\n" +
                    "Live tracking: ${if (sosActive) "ACTIVE" else "READY"}"
            ).apply { layoutParams = ui.lp(mt = 8, mb = 0) }
        )
        l.addView(c)
        setPage(ui.scroll(l), R.id.nav_home)
    }

    private fun mapChip(icon: Int, label: String, tint: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card)
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
            addView(ui.icon(icon, tint, 18, null))
            addView(ui.caption(label, ui.textPrimary).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = ui.dp(8) }
            })
        }
    }

    private fun showNetwork() {
        val l = ui.screenColumn()
        l.addView(ui.header("Network status", true) { showHome() })
        l.addView(
            ui.statusCard(
                R.drawable.ic_mesh,
                "Network connected",
                "This prototype shows a ready network path for alerts.",
                "Status: Connected",
                StatusTone.SUCCESS
            )
        )
        l.addView(
            ui.statusCard(
                R.drawable.ic_devices,
                "Nearby devices",
                "Devices discovered on the local mesh.",
                "3 nearby",
                StatusTone.SUCCESS
            )
        )
        l.addView(
            ui.statusCard(
                R.drawable.ic_bluetooth,
                "Bluetooth",
                "Wireless radio used for nearby node discovery.",
                "ON",
                StatusTone.SUCCESS
            )
        )
        val bleTest = ui.primaryButton("Open BLE test", R.drawable.ic_bluetooth)
        bleTest.layoutParams = ui.lp(height = ui.dp(48), mb = 12)
        bleTest.setOnClickListener {
            startActivity(Intent(this, BleTestActivity::class.java))
        }
        l.addView(bleTest)
        l.addView(
            ui.statusCard(
                R.drawable.ic_gps,
                "GPS",
                "Location services are available for this session.",
                "ON",
                StatusTone.SUCCESS
            )
        )
        l.addView(
            ui.statusCard(
                R.drawable.ic_signal,
                "Connection quality",
                "Signal strength is sufficient for emergency alerts.",
                "Excellent",
                StatusTone.SUCCESS
            )
        )
        setPage(ui.scroll(l), R.id.nav_home)
    }

    private fun showContacts() {
        val l = ui.screenColumn()
        l.addView(ui.header("Emergency contacts", false) { showHome() })
        l.addView(
            ui.caption("Your contacts start empty. Only contacts you manually enter are saved.")
                .apply { layoutParams = ui.lp(mb = 12) }
        )
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun redraw() {
            holder.removeAllViews()
            if (contacts.isEmpty()) {
                holder.addView(
                    ui.emptyState(
                        R.drawable.ic_contacts,
                        getString(R.string.empty_contacts_title),
                        getString(R.string.empty_contacts_body),
                        getString(R.string.add_emergency_contact)
                    ) {
                        contacts.add(Contact())
                        saveContacts()
                        redraw()
                    }
                )
            } else {
                contacts.forEachIndexed { i, c -> holder.addView(contactEditor(i, c, ::redraw)) }
                if (contacts.size < 5) {
                    val add = ui.secondaryButton(getString(R.string.add_emergency_contact), R.drawable.ic_add)
                    add.contentDescription = getString(R.string.cd_add_contact)
                    add.layoutParams = ui.lp(height = ui.dp(48), mb = 0)
                    add.setOnClickListener {
                        contacts.add(Contact())
                        saveContacts()
                        redraw()
                    }
                    holder.addView(add)
                } else {
                    holder.addView(
                        ui.statusCard(
                            R.drawable.ic_check,
                            "Contact limit reached",
                            "Maximum 5 emergency contacts allowed.",
                            tone = StatusTone.SUCCESS
                        )
                    )
                }
            }
        }
        l.addView(holder)
        redraw()
        setPage(ui.scroll(l), R.id.nav_contacts)
    }

    private fun contactEditor(i: Int, c: Contact, redraw: () -> Unit): View {
        val box = ui.card()
        val col = ui.cardColumn(box)
        col.addView(ui.caption("Emergency contact ${i + 1}").apply { layoutParams = ui.lp(mb = 8) })
        col.addView(ui.titleSmall(c.name.ifBlank { "New contact" }).apply {
            layoutParams = ui.lp(mb = 12)
        })
        val (nameWrap, name) = ui.labeledInput(
            "Contact name",
            "Full name",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            c.name
        )
        col.addView(nameWrap)

        val region = c.country.ifBlank { selectedCountry }
        val dial = c.dial.ifBlank { selectedDial }
        col.addView(ui.caption("Country code").apply { layoutParams = ui.lp(mb = 8) })
        val country = ui.secondaryButton("${flag(region)}  $dial").apply {
            textSize = 14f
            contentDescription = getString(R.string.cd_country)
            layoutParams = ui.lp(height = ui.dp(48), mb = 12)
        }
        col.addView(country)
        country.setOnClickListener {
            showCountryPicker(country) { code, newDial ->
                c.country = code
                c.dial = newDial
            }
        }

        val (phoneWrap, ph) = ui.labeledInput(
            "Phone number",
            "Phone number",
            InputType.TYPE_CLASS_PHONE,
            c.phone
        )
        col.addView(phoneWrap)

        col.addView(ui.caption("Relationship").apply { layoutParams = ui.lp(mb = 8) })
        val rel = Spinner(this)
        val rels = arrayOf("Select relationship", "Parent", "Sibling", "Friend", "Partner", "Guardian", "Other")
        rel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rels)
        val selectedRel = rels.indexOf(c.relationship).let { if (it >= 0) it else 0 }
        rel.setSelection(selectedRel)
        rel.background = null
        rel.minimumHeight = ui.dp(48)
        rel.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        val relWrap = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_input)
            layoutParams = ui.lp(height = ui.dp(48), mb = 12)
            addView(rel, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        col.addView(relWrap)

        val err = ui.caption("", ui.error).apply {
            visibility = View.GONE
            layoutParams = ui.lp(mb = 8)
        }
        col.addView(err)

        val save = ui.primaryButton("Save contact")
        val del = ui.destructiveButton("Remove", R.drawable.ic_delete)
        del.contentDescription = getString(R.string.cd_remove_contact)
        save.setOnClickListener {
            val enteredName = name.text.toString().trim()
            val enteredPhone = ph.text.toString().trim()
            val regionCode = c.country.ifBlank { selectedCountry }
            val msg = when {
                enteredName.isEmpty() -> "Enter the contact's name."
                enteredPhone.isEmpty() -> "Enter a valid phone number."
                !validPhone(enteredPhone, regionCode) ->
                    "Enter a valid phone number for ${c.dial.ifBlank { selectedDial }}."
                else -> ""
            }
            if (msg.isNotEmpty()) {
                err.text = msg
                err.visibility = View.VISIBLE
                return@setOnClickListener
            }
            c.name = enteredName
            c.phone = enteredPhone
            c.relationship = rel.selectedItem.toString().let { if (it == "Select relationship") "" else it }
            saveContacts()
            Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show()
            redraw()
        }
        del.setOnClickListener {
            contacts.removeAt(i)
            saveContacts()
            redraw()
        }
        col.addView(save)
        col.addView(del.apply { layoutParams = ui.lp(height = ui.dp(48), mb = 0) })
        return box
    }

    private fun showNearby() {
        val l = ui.screenColumn()
        l.addView(ui.header("Nearby help", true) { showHome() })
        val places = listOf(
            NearbyPlace("Ambulance", "1.8 km", "6 min", R.drawable.ic_hospital),
            NearbyPlace("Police station", "2.4 km", "8 min", R.drawable.ic_police),
            NearbyPlace("Fire station", "3.1 km", "10 min", R.drawable.ic_fire),
            NearbyPlace("Nearby hospital", "2.0 km", "7 min", R.drawable.ic_hospital)
        )
        places.forEach { place ->
            val c = ui.card()
            val col = ui.cardColumn(c)
            col.addView(ui.listRow(place.icon, place.title, "${place.distance}  ·  ${place.eta}"))
            val b = ui.primaryButton("Call / navigate", R.drawable.ic_call)
            b.layoutParams = ui.lp(height = ui.dp(48), mt = 12, mb = 0)
            b.setOnClickListener {
                Toast.makeText(this, "${place.title} (prototype)", Toast.LENGTH_SHORT).show()
            }
            col.addView(b)
            l.addView(c)
        }
        setPage(ui.scroll(l), R.id.nav_home)
    }

    private data class NearbyPlace(val title: String, val distance: String, val eta: String, val icon: Int)

    private fun showHistory() {
        val l = ui.screenColumn()
        l.addView(ui.header("Emergency history", true) { showHome() })
        l.addView(
            ui.statusCard(
                R.drawable.ic_history,
                "Medical / SOS",
                "Today  ·  ${if (sosActive) "Active" else "Cancelled"}  ·  Location shared",
                tone = if (sosActive) StatusTone.EMERGENCY else StatusTone.NEUTRAL
            )
        )
        setPage(ui.scroll(l), R.id.nav_home)
    }

    private fun showNotifications() {
        val l = ui.screenColumn()
        l.addView(ui.header("Notifications", true) { showProfile() })
        listOf(
            "SOS alert triggered",
            "Emergency contact notified",
            "Emergency service assigned",
            "Location shared"
        ).forEach { item ->
            val c = ui.card()
            ui.cardColumn(c).addView(ui.listRow(R.drawable.ic_notifications, item))
            l.addView(c)
        }
        setPage(ui.scroll(l), R.id.nav_profile)
    }

    private fun showProfile() {
        val l = ui.screenColumn()
        l.addView(ui.header("User profile", false) { showHome() })
        val name = prefs.getString("name", "User") ?: "User"
        val phone = prefs.getString("phone", "") ?: ""
        val c = ui.card()
        val col = ui.cardColumn(c)
        col.addView(ui.avatar(name))
        col.addView(ui.titleLarge(name, ui.primary).apply {
            gravity = Gravity.CENTER
            layoutParams = ui.lp(mt = 12)
        })
        col.addView(ui.caption(phone).apply {
            gravity = Gravity.CENTER
            layoutParams = ui.lp(mt = 4, mb = 16)
        })
        col.addView(ui.listRow(R.drawable.ic_contacts, "Emergency contacts", "${contacts.size} saved"))
        col.addView(ui.listRow(R.drawable.ic_location, "Location permission", "Ready"))
        col.addView(ui.listRow(R.drawable.ic_notifications, "Notification permission", "Ready"))
        col.addView(ui.listRow(R.drawable.ic_voice, "Voice command permission", "API-ready"))
        l.addView(c)

        val n = ui.primaryButton("Notifications", R.drawable.ic_notifications)
        n.contentDescription = getString(R.string.cd_notifications)
        n.setOnClickListener { showNotifications() }
        l.addView(n)
        val con = ui.tonalButton("Emergency contacts", R.drawable.ic_contacts)
        con.setOnClickListener { showContacts() }
        l.addView(con)
        val logout = ui.destructiveButton("Log out", R.drawable.ic_logout)
        logout.layoutParams = ui.lp(height = ui.dp(48), mb = 0)
        logout.setOnClickListener {
            prefs.edit().clear().apply()
            showLogin()
        }
        l.addView(logout)
        setPage(ui.scroll(l), R.id.nav_profile)
    }

    private fun showVoiceDialog() {
        val d = Dialog(this)
        val c = ui.card()
        c.layoutParams = ui.lp(mb = 0)
        val col = ui.cardColumn(c)
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(ui.toneBadge(R.drawable.ic_voice, StatusTone.NEUTRAL).apply {
            layoutParams = LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        col.addView(ui.titleMedium("Listening…", ui.primary).apply {
            gravity = Gravity.CENTER
            layoutParams = ui.lp(mt = 12)
        })
        col.addView(
            ui.body("User command: “I need an ambulance”", ui.textSecondary).apply {
                gravity = Gravity.CENTER
                layoutParams = ui.lp(mt = 8)
            }
        )
        col.addView(
            ui.emphasis("Emergency detected: Medical", ui.emergency).apply {
                gravity = Gravity.CENTER
                layoutParams = ui.lp(mt = 16)
            }
        )
        col.addView(ui.caption("AI confidence: 96%").apply { gravity = Gravity.CENTER })
        col.addView(
            ui.emphasis("Ambulance alert ready", ui.success).apply {
                gravity = Gravity.CENTER
                layoutParams = ui.lp(mt = 8, mb = 16)
            }
        )
        val ok = ui.primaryButton("Done")
        ok.layoutParams = ui.lp(height = ui.dp(48), mb = 0)
        ok.setOnClickListener { d.dismiss() }
        col.addView(ok)
        d.setContentView(c)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.show()
        val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        d.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun saveContacts() {
        val a = JSONArray()
        contacts.forEach { c ->
            a.put(
                JSONObject()
                    .put("name", c.name)
                    .put("country", c.country)
                    .put("dial", c.dial)
                    .put("phone", c.phone)
                    .put("relationship", c.relationship)
            )
        }
        prefs.edit().putString("contacts", a.toString()).apply()
    }

    private fun loadContacts(): MutableList<Contact> {
        val out = mutableListOf<Contact>()
        try {
            val a = JSONArray(prefs.getString("contacts", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(
                    Contact(
                        o.optString("name"),
                        o.optString("country"),
                        o.optString("dial", "+91"),
                        o.optString("phone"),
                        o.optString("relationship")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun ensureLocationPermission() {
        if (sosCoordinator.hasLocationPermission()) {
            refreshLocationForUi()
            return
        }
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun refreshLocationForUi() {
        sosCoordinator.refreshLocation {
            runOnUiThread {
                if (currentNav == R.id.nav_home && !sosActive) showHome()
            }
        }
    }

    private fun locationStatusCard(): View {
        val fix = sosCoordinator.lastFix
        return if (fix != null) {
            ui.statusCard(
                R.drawable.ic_location,
                "Location available",
                formatCoordinate(fix.latitude, fix.longitude),
                "Accuracy ±${fix.accuracy.toInt()} m  ·  GPS on",
                StatusTone.SUCCESS
            )
        } else {
            ui.statusCard(
                R.drawable.ic_location,
                "Location",
                if (sosCoordinator.hasLocationPermission()) {
                    "Waiting for GPS fix"
                } else {
                    "Location permission needed"
                },
                "GPS",
                StatusTone.NEUTRAL
            )
        }
    }

    private fun locationSummary(): String {
        val fix = sosCoordinator.lastFix
        return if (fix != null) formatCoordinate(fix.latitude, fix.longitude)
        else "Waiting for GPS"
    }

    private fun formatCoordinate(latitude: Double, longitude: Double): String {
        val ns = if (latitude >= 0) "N" else "S"
        val ew = if (longitude >= 0) "E" else "W"
        return "${"%.4f".format(kotlin.math.abs(latitude))}° $ns, ${"%.4f".format(kotlin.math.abs(longitude))}° $ew"
    }

    private fun dispatchSos(type: String) {
        sosCoordinator.sendSos(type) { packet, sos ->
            runOnUiThread {
                if (packet != null && sos != null) {
                    Toast.makeText(
                        this,
                        "SOS queued on mesh (${packet.messageId.take(8)})",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "SOS started. Location not available yet.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showHome()
    }
}
