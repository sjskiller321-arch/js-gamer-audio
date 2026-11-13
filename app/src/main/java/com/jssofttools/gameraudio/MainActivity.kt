package com.jssofttools.gameraudio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val bg = Color.parseColor("#0B1220")
    private val cardBg = Color.parseColor("#111B2E")
    private val cyan = Color.parseColor("#22D3EE")
    private val txt = Color.parseColor("#E6EDF7")
    private val sub = Color.parseColor("#8FA3BF")

    private var boostGain = 300
    private var bass = 0
    private var foot = false

    private lateinit var boostBtn: Button
    private lateinit var voiceBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun card(root: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = GradientDrawable().apply { setColor(cardBg); cornerRadius = dp(16).toFloat() }
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        root.addView(c, lp)
        return c
    }

    private fun button(t: String): Button = Button(this).apply {
        text = t; setTextColor(bg); isAllCaps = false; textSize = 16f
        setTypeface(null, Typeface.BOLD)
        background = GradientDrawable().apply { setColor(cyan); cornerRadius = dp(12).toFloat() }
    }

    private fun slider(parent: LinearLayout, title: String, min: Int, max: Int, init: Int,
                       fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val t = label("$title: ${fmt(init)}", 14f, txt)
        val s = SeekBar(this).apply {
            this.max = max - min; progress = init - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + min; t.text = "$title: ${fmt(v)}"; onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        parent.addView(t); parent.addView(s)
    }

    private fun toggle(parent: LinearLayout, t: String, init: Boolean, on: (Boolean) -> Unit) {
        parent.addView(Switch(this).apply {
            text = t; setTextColor(txt); isChecked = init; textSize = 14f
            setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, c -> on(c) }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(label("JS Gamer Audio", 26f, cyan, true))
        root.addView(label("Sound booster + voice changer for mobile gamers", 13f, sub))

        // ---- Game sound booster ----
        val c1 = card(root)
        c1.addView(label("Game Sound Booster", 18f, txt, true))
        slider(c1, "Boost", 100, 1000, boostGain, { "$it%" }) { boostGain = it; pushBoost() }
        slider(c1, "Bass", 0, 1000, bass, { "${it / 10}%" }) { bass = it; pushBoost() }
        toggle(c1, "Footsteps / clarity mode (lifts high-mids)", foot) { foot = it; pushBoost() }
        boostBtn = button("Start boost")
        boostBtn.setOnClickListener {
            if (BoostService.running) stopService(Intent(this, BoostService::class.java))
            else startForegroundService(boostIntent())
            boostBtn.postDelayed({ refresh() }, 300)
        }
        c1.addView(boostBtn, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        val maxVol = button("Set phone volume to max")
        maxVol.setOnClickListener {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
        }
        c1.addView(maxVol, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        // ---- Voice studio ----
        val c2 = card(root)
        c2.addView(label("Voice Studio (live mic)", 18f, txt, true))
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            Voice.values().map { it.label })
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                VoiceService.voice = Voice.values()[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        c2.addView(label("Voice", 14f, sub)); c2.addView(sp)
        slider(c2, "Mic boost", 100, 500, VoiceService.gainPct, { "$it%" }) { VoiceService.gainPct = it }
        toggle(c2, "Echo", VoiceService.echo) { VoiceService.echo = it }
        toggle(c2, "Noise cleaner + auto gain (applies on start)", VoiceService.noise) { VoiceService.noise = it }
        voiceBtn = button("Start live voice")
        voiceBtn.setOnClickListener {
            if (VoiceService.running) {
                stopService(Intent(this, VoiceService::class.java))
                voiceBtn.postDelayed({ refresh() }, 300)
            } else startVoice()
        }
        c2.addView(voiceBtn, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        c2.addView(label("Use headphones, otherwise you will get feedback squeal.", 12f, sub))

        root.addView(label("High boost can damage hearing and speakers. Raise it slowly.", 12f, sub))
        root.addView(label("Chip-X  |  JS SoftTools  |  support.jssofttoolsproducts@gmail.com", 11f, sub))

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8)
        refresh()
    }

    private fun boostIntent() = Intent(this, BoostService::class.java)
        .putExtra("gain", boostGain).putExtra("bass", bass).putExtra("foot", foot)

    private fun pushBoost() { if (BoostService.running) startService(boostIntent()) }

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7); return
        }
        startForegroundService(Intent(this, VoiceService::class.java))
        voiceBtn.postDelayed({ refresh() }, 300)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 7 && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) startVoice()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        boostBtn.text = if (BoostService.running) "Stop boost" else "Start boost"
        voiceBtn.text = if (VoiceService.running) "Stop live voice" else "Start live voice"
    }
}
