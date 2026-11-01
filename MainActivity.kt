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
        for (i in 0 until records.length()) {
            val hive = records.getJSONObject(i)
            list.addView(Button(this).apply {
                text = "Ul " + hive.optString("number") + " | Matka: " + hive.optString("queenYear")
                setOnClickListener { edit(i) }
            })
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
