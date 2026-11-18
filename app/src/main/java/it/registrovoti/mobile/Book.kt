package it.registrovoti.mobile

import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

val defaults = listOf("Italiano","Storia","Inglese","Matematica","Informatica","Sistemi e reti","TPSIT","Telecomunicazioni","Scienze motorie")
val types = listOf("Scritto","Orale","Pratico","Laboratorio")

fun parseGrade(raw: String): Pair<Double, String>? {
    val s = raw.trim().replace(Regex("\\s+"), "").replace(',', '.').replace('−','-').replace('–','-')
    val slash = Regex("""(\d{1,2})/(\d{1,2})""").matchEntire(s)
    if (slash != null) {
        val (a,b) = slash.destructured
        val x = a.toInt()
        val y = b.toInt()
        val n = (x+y)/2.0
        return if (abs(x-y)==1 && n in 2.0..10.0) n to "${minOf(x,y)}/${maxOf(x,y)}" else null
    }
    val match = Regex("""(\d{1,2})(?:(\.\d{1,2})|([+½-]))?""").matchEntire(s) ?: return null
    val (whole,decimal,mark) = match.destructured
    val n = (whole+decimal).toDouble() + when(mark) { "+" -> 0.25; "-" -> -0.25; "½" -> 0.5; else -> 0.0 }
    val label = if (decimal.isNotEmpty() && n % 1 == 0.5) "${n.toInt()}½" else s.replace('.',',')
    return if (n in 2.0..10.0) n to label else null
}

fun grades(data: Map<String,Any>): List<Map<*,*>> =
    (data["grades"] as? List<*>)?.filterIsInstance<Map<*,*>>().orEmpty()

fun subjects(data: Map<String,Any>) =
    (data["subjects"] as? List<*>)?.filterIsInstance<String>() ?: defaults

typealias Data = Map<String,Any>
typealias Change = (Data) -> Data
typealias Commit = (Change, (String?) -> Unit) -> Unit

fun emptyBook():Data {
    val today = LocalDate.now()
    val end = today.year + if(today.monthValue>=9) 1 else 0
    return mapOf("v" to 1,"savedAt" to 0L,"demo" to false,"subjects" to defaults,"grades" to emptyList<Data>(),
        "settings" to mapOf("cutoff" to "$end-01-31","mode" to "subjects"))
}

fun changeGrade(old: Map<*,*>?, name:String, score:Pair<Double,String>, day:LocalDate, type:String, note:String):Change {
    val id=old?.get("id") ?: UUID.randomUUID().toString()
    return { book ->
        val names=subjects(book).toMutableList()
        val canonical=names.firstOrNull { it.equals(name,true) } ?: name.also { names.add(it) }
        val original=grades(book)
        require(old==null || original.any { it==old }) { "Il voto è cambiato. Riaprilo." }
        val entries=original.filterNot { it["id"]==id || (book["demo"]==true && it["demo"]==true) }.toMutableList()
        require(names.size<=60 && entries.size<500) { "Limite di materie o voti raggiunto" }
        entries.add(mapOf("id" to id,"subject" to canonical,"value" to score.first,"label" to score.second,
            "date" to day.toString(),"type" to type,"note" to note,"ts" to (old?.get("ts") ?: System.currentTimeMillis())))
        book+mapOf("subjects" to names,"grades" to entries,"demo" to false)
    }
}

private fun unpack(value:Any):Any = when(value) {
    is org.json.JSONObject -> value.keys().asSequence().associateWith { unpack(value.get(it)) }
    is org.json.JSONArray -> (0 until value.length()).map { unpack(value.get(it)) }
    else -> value
}
fun readBackup(text:String):Data {
    val json=org.json.JSONObject(text)
    require(json.getInt("v")==1)
    val book=json.keys().asSequence().associateWith { unpack(json.get(it)) }
    val names=book["subjects"] as List<*>
    val entries=book["grades"] as List<*>
    require(names.size<=60 && names.all { it is String && it.isNotBlank() } && entries.size<=500 && book["demo"] is Boolean)
    val settings=book["settings"] as Map<*,*>
    LocalDate.parse(settings["cutoff"] as String)
    require(settings["mode"] in listOf("subjects","all"))
    val ids=mutableSetOf<String>()
    entries.forEach {
        val g=it as Map<*,*>
        require(ids.add(g["id"] as String) && g["subject"] in names && (g["value"] as Number).toDouble() in 2.0..10.0)
        LocalDate.parse(g["date"] as String)
    }
    return book.filterKeys { it in emptyBook().keys }
}
