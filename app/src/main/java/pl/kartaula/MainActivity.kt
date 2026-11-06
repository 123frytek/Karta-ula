package pl.kartaula

import android.app.Activity
import android.os.Bundle
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val store by lazy { getSharedPreferences("pasieka", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private var records = JSONArray()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        records = try { JSONArray(store.getString("ule", "[]")) } catch (_: Exception) { JSONArray() }
        home()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun background(color: Int, border: Int? = null): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(12).toFloat()
            if (border != null) setStroke(dp(1), border)
        }
    }

    private fun style(view: android.view.View) {
        val ink = android.graphics.Color.rgb(24, 48, 39)
        val green = android.graphics.Color.rgb(35, 70, 56)
        val gold = android.graphics.Color.rgb(255, 200, 87)
        val white = android.graphics.Color.WHITE
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        view.layoutParams = params
        when (view) {
            is Button -> {
                view.isAllCaps = false
                view.textSize = 18f
                view.typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
                view.minHeight = dp(60)
                view.setPadding(dp(16), dp(14), dp(16), dp(14))
                view.backgroundTintList = null
                val primary = view.text.toString() in listOf("Dodaj ul", "Zapisz w telefonie")
                val hive = view.text.toString().startsWith("Ul ")
                view.setTextColor(if (primary) white else ink)
                view.background = background(if (primary) green else if (hive) gold else white, green)
                if (hive) {
                    view.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    view.textSize = 22f
                }
            }
            is EditText -> {
                view.textSize = 20f
                view.setTextColor(ink)
                view.setHintTextColor(android.graphics.Color.DKGRAY)
                view.minHeight = dp(56)
                view.setPadding(dp(14), dp(12), dp(14), dp(12))
                view.backgroundTintList = null
                view.background = background(white, green)
            }
            is TextView -> {
                view.setTextColor(ink)
                view.textSize = 17f
                view.typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
                view.setPadding(0, dp(6), 0, dp(4))
            }
        }
    }

    private fun layout(title: String): LinearLayout {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
            setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: android.view.View?, child: android.view.View?) {
                    if (child != null) style(child)
                }
                override fun onChildViewRemoved(parent: android.view.View?, child: android.view.View?) {}
            })
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        body.addView(header)
        val heading = TextView(this).apply { text = title; setTextColor(android.graphics.Color.rgb(24, 48, 39)) }
        header.addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (title == "Karta ula") {
            header.addView(Button(this).apply {
                text = "⋮"
                textSize = 30f
                contentDescription = "Menu kopii zapasowej"
                setTextColor(android.graphics.Color.rgb(24, 48, 39))
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                setOnClickListener { anchor ->
                    PopupMenu(this@MainActivity, anchor).apply {
                        menu.add(0, 1, 0, "Eksportuj kopię zapasową")
                        menu.add(0, 2, 1, "Przywróć kopię zapasową")
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                1 -> exportBackup()
                                2 -> importBackup()
                            }
                            true
                        }
                        show()
                    }
                }
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
        }
        heading.textSize = 32f
        heading.setTypeface(android.graphics.Typeface.create("sans-serif", 0), android.graphics.Typeface.BOLD)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(246, 247, 239))
            addView(body)
        })
        window.statusBarColor = android.graphics.Color.rgb(35, 70, 56)
        window.navigationBarColor = android.graphics.Color.rgb(35, 70, 56)
        return body
    }

    private fun home() {
        list = layout("Karta ula")
        list.addView(TextView(this).apply { text = "Dane zapisane w telefonie. Wysyłanie do karty przez Wi-Fi Pasieka." })
        list.addView(Button(this).apply {
            text = "Dodaj ul"
            setOnClickListener { edit(-1) }
        })
        for (i in 0 until records.length()) {
            val hive = records.getJSONObject(i)
            val kg = hive.optDouble("weightKg", Double.NaN)
            val base = hive.optDouble("tareKg", Double.NaN)
            val weight = if (kg.isFinite() && base.isFinite())
                String.format(java.util.Locale.getDefault(), "%+.2f kg", kg - base) else "-- kg"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = background(android.graphics.Color.rgb(255, 200, 87),
                    android.graphics.Color.rgb(35, 70, 56))
                setPadding(dp(16), dp(14), dp(10), dp(14))
                minimumHeight = dp(90)
            }
            row.addView(TextView(this).apply {
                text = "Ul " + hive.optString("number") + "    Przyrost: " + weight +
                    "\nMatka: " + queenInfo(hive).let { (breed, year) ->
                        listOf(breed, year).filter { it.isNotBlank() }
                            .joinToString(" • ").ifBlank { "Brak danych" }
                    }
                textSize = 22f
                typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
                setTextColor(android.graphics.Color.rgb(24, 48, 39))
                setOnClickListener { edit(i) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "T"
                textSize = 24f
                isAllCaps = false
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(android.graphics.Color.WHITE)
                backgroundTintList = null
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.rgb(190, 35, 42))
                }
                contentDescription = "Taruj wagę ula " + hive.optString("number")
                setOnClickListener { confirmTare(i) }
            }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
            list.addView(row)
        }
    }

    private fun measurementDate(timestamp: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))

    private fun saveWeightRecord(index: Int, updated: JSONObject): Boolean {
        val next = JSONArray(records.toString())
        next.put(index, updated)
        if (!store.edit().putString("ule", next.toString()).commit()) {
            Toast.makeText(this, "Nie udało się zapisać danych wagi", Toast.LENGTH_LONG).show()
            return false
        }
        records = next
        return true
    }

    // Punkt wejścia dla zweryfikowanych odczytów z modułu komunikacji.
    // Wywołanie wyłącznie w wątku interfejsu, po sprawdzeniu tożsamości ula.
    private fun recordWeight(hiveId: String, kg: Double, measuredAt: Long, sampleId: String) {
        require(kg.isFinite() && kg >= 0.0 && measuredAt > 0 && sampleId.isNotBlank())
        val index = (0 until records.length()).firstOrNull {
            records.getJSONObject(it).optString("id") == hiveId
        } ?: throw IllegalArgumentException("Nieznany ul")
        val updated = JSONObject(records.getJSONObject(index).toString())
        val history = updated.optJSONArray("weightHistory") ?: JSONArray()
        for (i in 0 until history.length()) {
            if (history.getJSONObject(i).optString("sampleId") == sampleId) return
        }
        history.put(JSONObject().put("type", "measurement").put("sampleId", sampleId)
            .put("measuredAt", measuredAt).put("receivedAt", System.currentTimeMillis())
            .put("timezone", java.util.TimeZone.getDefault().id).put("kg", kg))
        updated.put("weightHistory", history)
        if (measuredAt >= updated.optLong("weightMeasuredAt", 0)) {
            updated.put("weightKg", kg).put("weightMeasuredAt", measuredAt)
        }
        if (saveWeightRecord(index, updated)) home()
    }

    private fun confirmTare(index: Int) {
        val hive = records.getJSONObject(index)
        val kg = hive.optDouble("weightKg", Double.NaN)
        val measuredAt = hive.optLong("weightMeasuredAt", 0)
        if (!kg.isFinite() || kg < 0.0 || measuredAt <= 0) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Brak pomiaru do tarowania")
                .setMessage("Tara wymaga odczytu z wagi tego ula. Nie ustawiono fikcyjnego zera. Odbiór pomiarów z ESP32 wymaga podłączenia.")
                .setPositiveButton("OK", null).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Tarować ul " + hive.optString("number") + "?")
            .setMessage("Ustawić punkt odniesienia na podstawie odczytu z " +
                measurementDate(measuredAt) + "? Sprawdź datę pomiaru. Historia nie zostanie usunięta.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Taruj") { _, _ ->
                val updated = JSONObject(records.getJSONObject(index).toString())
                val timestamp = System.currentTimeMillis()
                val history = updated.optJSONArray("weightHistory") ?: JSONArray()
                history.put(JSONObject().put("type", "tare").put("at", timestamp)
                    .put("timezone", java.util.TimeZone.getDefault().id)
                    .put("kg", kg).put("measurementAt", measuredAt))
                updated.put("tareKg", kg).put("tareAt", timestamp).put("weightHistory", history)
                if (saveWeightRecord(index, updated)) home()
            }.show()
    }

    private var wifiBusy = false

    private fun wifiRequest(network: android.net.Network, path: String, payload: String? = null): JSONObject {
        val connection = network.openConnection(java.net.URL("http://192.168.4.1" + path))
            as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            if (payload != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = payload.toByteArray(Charsets.UTF_8)
                require(bytes.size < 8192) { "Dane ula są zbyt duże dla karty" }
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            require(code in 200..299) { "Karta zgłosiła błąd HTTP " + code }
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 16384) { "Odpowiedź karty jest zbyt duża" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    private fun wifiWork(work: () -> Unit) {
        if (wifiBusy) return
        wifiBusy = true
        Thread {
            try {
                work()
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Nie udało się połączyć")
                            .setMessage((e.message ?: "Błąd sieci") +
                                "\nPołącz telefon z Wi-Fi Pasieka i zaakceptuj brak internetu. Jeśli wysłanie przerwano, sprawdź zapis na karcie.")
                            .setPositiveButton("OK", null).show()
                    }
                }
            } finally {
                runOnUiThread { wifiBusy = false }
            }
        }.start()
    }

    private fun sendHive(hive: JSONObject) {
        val snapshot = JSONObject(hive.toString())
        wifiWork {
            val manager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networks = manager.allNetworks.filter {
                manager.getNetworkCapabilities(it)?.hasTransport(
                    android.net.NetworkCapabilities.TRANSPORT_WIFI
                ) == true
            }
            require(networks.size == 1) { "Brak jednoznacznego połączenia Wi-Fi. Połącz się z siecią Pasieka." }
            val network = networks.single()
            val info = wifiRequest(network, "/api/info")
            require(info.optString("protocol") == "pasieka-v1") { "Nieobsługiwana karta" }
            val cardId = info.getString("cardId")
            require(cardId.matches(Regex("[0-9A-F]{12}"))) { "Nieprawidłowy identyfikator karty" }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Wysłać zapisane dane?")
                        .setMessage("Ul: " + snapshot.optString("number") +
                            "\nKarta: " + cardId +
                            "\nDane na tej karcie zostaną zastąpione. Niezapisane zmiany formularza nie są wysyłane.")
                        .setNegativeButton("Anuluj", null)
                        .setPositiveButton("Wyślij") { _, _ ->
                            wifiWork {
                                val check = wifiRequest(network, "/api/info")
                                require(check.optString("cardId") == cardId &&
                                    check.optString("protocol") == "pasieka-v1") { "Zmieniła się karta odbiorcy" }
                                val payload = JSONObject().put("cardId", cardId).put("hive", snapshot)
                                val answer = wifiRequest(network, "/api/hive", payload.toString())
                                require(answer.optBoolean("saved")) { "Karta nie potwierdziła zapisu" }
                                val stored = wifiRequest(network, "/api/hive")
                                require(stored.optString("id") == snapshot.optString("id") &&
                                    stored.optString("number") == snapshot.optString("number")) {
                                    "Nie potwierdzono odczytu wysłanego ula"
                                }
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) {
                                        android.app.AlertDialog.Builder(this)
                                            .setTitle("Dane zapisane na karcie")
                                            .setMessage("Ul " + snapshot.optString("number") +
                                                "\nKarta: " + cardId +
                                                "\nZapis i odczyt potwierdzone. E-papier nie jest jeszcze obsługiwany.")
                                            .setPositiveButton("OK", null).show()
                                    }
                                }
                            }
                        }.show()
                }
            }
        }
    }

    private fun queenInfo(hive: JSONObject): Pair<String, String> {
        val legacy = hive.optString("queenYear").trim()
        if (hive.has("queenBreed")) return Pair(hive.optString("queenBreed"), legacy)
        val match = Regex("\\b(?:19|20)[0-9]{2}\\b").find(legacy)
        val year = match?.value ?: ""
        val breed = if (match != null) legacy.removeRange(match.range).trim() else legacy
        return Pair(breed, year)
    }

    private fun exportBackup() {
        startActivityForResult(android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_TITLE, "karta-ula-kopia.json")
        }, 100)
    }

    private fun importBackup() {
        startActivityForResult(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }, 101)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 100) {
                val backup = JSONObject().put("format", "karta-ula").put("version", 1).put("hives", records)
                val stream = contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("Nie można otworzyć pliku")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(backup.toString(2)) }
                Toast.makeText(this, "Zapisano kopię zapasową", Toast.LENGTH_LONG).show()
            } else if (requestCode == 101) {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Nie można otworzyć kopii")
                val bytes = stream.use {
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = it.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > 2_000_000) throw IllegalArgumentException("Kopia jest zbyt duża")
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
                val backup = JSONObject(String(bytes, Charsets.UTF_8))
                require(backup.getString("format") == "karta-ula" && backup.getInt("version") == 1) {
                    "Nieobsługiwany format kopii"
                }
                val imported = backup.getJSONArray("hives")
                val numbers = mutableSetOf<String>()
                val ids = mutableSetOf<String>()
                val keys = listOf("number", "queenYear", "queenMark", "queenOrigin", "introduced",
                    "inspection", "notes", "harvest", "feeding", "treatments", "todo", "id")
                for (i in 0 until imported.length()) {
                    val hive = imported.getJSONObject(i)
                    keys.forEach { key ->
                        require(hive.has(key) && hive.get(key) is String) { "Nieprawidłowe pole: " + key }
                    }
                    require(hive.getString("number").isNotBlank() && numbers.add(hive.getString("number"))) {
                        "Pusty lub powtórzony numer ula"
                    }
                    require(hive.getString("id").isNotBlank() && ids.add(hive.getString("id"))) {
                        "Pusty lub powtórzony identyfikator"
                    }
                }
                android.app.AlertDialog.Builder(this)
                    .setTitle("Przywrócić kopię?")
                    .setMessage("Ta operacja zastąpi wszystkie dane w aplikacji. Liczba uli w kopii: " + imported.length() +
                        ". Przed zastąpieniem wykonaj eksport obecnych danych.")
                    .setNegativeButton("Anuluj", null)
                    .setPositiveButton("Zastąp dane") { _, _ ->
                        if (store.edit().putString("ule", imported.toString()).commit()) {
                            records = imported
                            home()
                            Toast.makeText(this, "Przywrócono kopię", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Błąd zapisu. Dane nie zostały zastąpione.", Toast.LENGTH_LONG).show()
                        }
                    }.show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Błąd kopii: " + (e.message ?: "Nieznany błąd"), Toast.LENGTH_LONG).show()
        }
    }

    private fun edit(index: Int) {
        val hive = if (index >= 0) records.getJSONObject(index) else JSONObject()
        val body = layout(if (index < 0) "Nowy ul" else "UL NR " + hive.optString("number"))
        val fields = linkedMapOf<String, EditText>()
        val labels = linkedMapOf(
            "number" to "Numer ula",
            "queenBreed" to "Rasa / linia matki",
            "queenYear" to "Rocznik matki (np. 2025)",
            "queenMark" to "Oznaczenie matki",
            "queenOrigin" to "Pochodzenie matki",
            "introduced" to "Data poddania (RRRR-MM-DD)",
            "inspection" to "Data przeglądu (RRRR-MM-DD)",
            "notes" to "Stan rodziny i uwagi",
            "harvest" to "Miodobrania: data i masa w kg",
            "feeding" to "Karmienie: data, rodzaj i ilość",
            "treatments" to "Zabiegi: data i opis",
            "todo" to "Do wykonania"
        )
        labels.forEach { (key, label) ->
            body.addView(TextView(this).apply { text = label })
            val input = EditText(this).apply {
                val queen = queenInfo(hive)
                setText(when (key) {
                    "queenBreed" -> queen.first
                    "queenYear" -> queen.second
                    else -> hive.optString(key)
                })
                if (key == "queenYear") {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    filters = arrayOf(android.text.InputFilter.LengthFilter(4))
                }
                minHeight = 64
            }
            fields[key] = input
            body.addView(input)
        }
        body.addView(Button(this).apply {
            text = "Zapisz w telefonie"
            setOnClickListener {
                val number = fields.getValue("number").text.toString().trim()
                if (number.isEmpty()) {
                    fields.getValue("number").error = "Podaj numer ula"
                    return@setOnClickListener
                }
                for (i in 0 until records.length()) {
                    if (i != index && records.getJSONObject(i).optString("number") == number) {
                        fields.getValue("number").error = "Ten numer ula już istnieje"
                        return@setOnClickListener
                    }
                }
                val year = fields.getValue("queenYear").text.toString().trim()
                if (year.isNotEmpty() && !Regex("(19|20)[0-9]{2}").matches(year)) {
                    fields.getValue("queenYear").error = "Podaj czterocyfrowy rok, np. 2025, lub pozostaw puste"
                    return@setOnClickListener
                }
                val updated = JSONObject(hive.toString())
                if (!updated.has("queenBreed")) updated.put("queenLegacy", hive.optString("queenYear"))
                fields.forEach { (key, input) -> updated.put(key, input.text.toString().trim()) }
                if (!updated.has("id")) updated.put("id", java.util.UUID.randomUUID().toString())
                val next = JSONArray(records.toString())
                if (index < 0) next.put(updated) else next.put(index, updated)
                if (store.edit().putString("ule", next.toString()).commit()) {
                    records = next
                    home()
                } else {
                    Toast.makeText(this@MainActivity, "Nie udało się zapisać danych", Toast.LENGTH_LONG).show()
                }
            }
        })
        if (index >= 0) {
            body.addView(Button(this).apply {
                text = "Wyślij zapisane dane na kartę Wi-Fi"
                setOnClickListener { sendHive(records.getJSONObject(index)) }
            })
            body.addView(TextView(this).apply {
                text = "Przed wysłaniem zapisz zmiany i otwórz ul ponownie. Telefon połącz z siecią Pasieka."
            })
        }
        body.addView(Button(this).apply { text = "Anuluj"; setOnClickListener { home() } })
    }
}
