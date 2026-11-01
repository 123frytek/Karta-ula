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

    private fun layout(title: String): LinearLayout {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        body.addView(TextView(this).apply { text = title; textSize = 26f })
        setContentView(ScrollView(this).apply { addView(body) })
        return body
    }

    private fun home() {
        list = layout("Karta ula")
        list.addView(TextView(this).apply { text = "Dane lokalne. Bluetooth nie jest jeszcze dostępny." })
        list.addView(Button(this).apply {
            text = "Dodaj ul"
            setOnClickListener { edit(-1) }
        })
        list.addView(Button(this).apply {
            text = "Eksportuj kopię zapasową"
            setOnClickListener { exportBackup() }
        })
        list.addView(Button(this).apply {
            text = "Przywróć kopię zapasową"
            setOnClickListener { importBackup() }
        })
        for (i in 0 until records.length()) {
            val hive = records.getJSONObject(i)
            list.addView(Button(this).apply {
                text = "Ul " + hive.optString("number") + " | Matka: " + hive.optString("queenYear")
                setOnClickListener { edit(i) }
            })
        }
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
            "queenYear" to "Rocznik matki",
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
                setText(hive.optString(key))
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
                val updated = JSONObject(hive.toString())
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
        body.addView(Button(this).apply { text = "Anuluj"; setOnClickListener { home() } })
    }
}
