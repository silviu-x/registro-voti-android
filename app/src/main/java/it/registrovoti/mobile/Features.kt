package it.registrovoti.mobile

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import org.json.JSONObject
import kotlin.math.*

@Composable
fun Confirm(title:String, cancel:()->Unit, confirm:()->Unit) {
    AlertDialog(onDismissRequest=cancel,title={Text(title)},confirmButton={TextButton(onClick=confirm){Text("Conferma")}},
        dismissButton={TextButton(onClick=cancel){Text("Annulla")}})
}

@Composable
fun Backup(book:Data, commit:Commit, report:(String)->Unit) {
    val resolver=LocalContext.current.contentResolver
    var pending by remember { mutableStateOf<Data?>(null) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) runCatching { requireNotNull(resolver.openOutputStream(uri,"wt")).bufferedWriter().use { it.write(JSONObject(book).toString(2)) } }
            .fold({report("Copia salvata")},{report("Esportazione fallita")})
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) runCatching { requireNotNull(resolver.openInputStream(uri)).bufferedReader().use { reader ->
            val buffer=CharArray(8192); val text=StringBuilder()
            while(true){val n=reader.read(buffer); if(n<0) break; text.append(buffer,0,n); require(text.length<=2000000)}
            readBackup(text.toString())
        } }.onSuccess {pending=it}.onFailure {report("Backup non valido")}
    }
    RegisterAction(onClick={save.launch("registro-voti-${LocalDate.now()}.json")}){Text("Scarica una copia JSON")}
    RegisterAction(onClick={open.launch(arrayOf("application/json","text/plain","application/octet-stream"))}){Text("Carica da un file")}
    pending?.let { replacement ->
        Confirm("Sostituire il registro con ${grades(replacement).size} voti?",{pending=null}) {
            pending=null; commit({replacement}){report(it ?: "Backup importato")}
        }
    }
}

@Composable
fun Target(rows:List<Map<*,*>>, names:List<String>, mode:Any?) {
    var name by rememberSaveable { mutableStateOf(names.firstOrNull().orEmpty()) }
    var goal by rememberSaveable { mutableStateOf("Materia") }
    var raw by rememberSaveable { mutableStateOf("7") }
    Text("Che voto mi serve?",style=MaterialTheme.typography.titleLarge)
    Choice(goal,listOf("Materia","Media generale")){goal=it}
    Choice(name,names){name=it}
    OutlinedTextField(raw,{raw=it},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Media da raggiungere")})
    val t=raw.replace(',','.').toDoubleOrNull()
    if(t==null || t !in 2.0..10.0){Text("Inserisci una media da 2 a 10");return}
    for(n in 1..3){
        val value=GradeMath.needed(rows,name,t,n,goal=="Media generale",mode!="all")
        val result=when {value<=2.000000001 -> "Basta il voto minimo";value>10.000000001 -> "Obiettivo irraggiungibile";else -> display(ceil(value*4-1e-9)/4)}
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Con $n ${if(n==1) "voto" else "voti uguali"}",Modifier.weight(1f))
            Text(result,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SchoolSettings(data:Data, cutoff:String, commit:Commit, report:(String)->Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable(cutoff) { mutableStateOf(cutoff) }
    var clear by remember { mutableStateOf(false) }
    fun save(change:Change){commit(change){report(it ?: "Impostazioni salvate")}}
    fun setting(key:String,value:String){
        save { current ->
            val settings=current.getValue("settings") as Map<*,*>
            val next=mapOf("cutoff" to settings["cutoff"].toString(), "mode" to settings["mode"].toString())+(key to value)
            current+("settings" to next)
        }
    }
    OutlinedTextField(date,{date=it},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),label={Text("Fine primo quadrimestre")},singleLine=true)
    RegisterAction(onClick={if(runCatching{LocalDate.parse(date)}.isSuccess) setting("cutoff",date) else report("Data non valida")}){Text("Salva data")}
    val modes=listOf("Media per materia","Media di tutti i voti")
    Choice(if((data["settings"] as? Map<*,*>)?.get("mode")=="all") modes[1] else modes[0],modes){setting("mode",if(it==modes[0]) "subjects" else "all")}
    OutlinedTextField(name,{name=it.take(40)},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),label={Text("Nuova materia")})
    RegisterAction(onClick={val newName=name.trim(); if(newName.isNotEmpty()) save {
        val names=subjects(it);require(names.size<60 && names.none{n->n.equals(newName,true)}){"Materia già presente o limite raggiunto"}
        it+("subjects" to (names+newName))
    }}){Text("Aggiungi materia")}
    val unused=subjects(data).filter { n->grades(data).none{it["subject"]==n} }
    if(unused.isNotEmpty()) Choice("Rimuovi materia senza voti",unused){n->save {
        require(grades(it).none{g->g["subject"]==n});it+("subjects" to subjects(it).filterNot{name->name==n})
    }}
    HorizontalDivider()
    Text("Backup e dati",style=MaterialTheme.typography.titleMedium)
    Backup(data,commit,report)
    RegisterAction(destructive=true, onClick={clear=true}){Text("Elimina tutti i voti")}
    if(clear) Confirm("Eliminare tutti i voti?",{clear=false}){clear=false;save {it+mapOf("grades" to emptyList<Data>(),"demo" to false)}}
}

@Composable
fun SubjectDetails(rows:List<Map<*,*>>) {
    if(rows.isEmpty()){Text("Nessun voto nel periodo");return}
    val ordered=rows.sortedWith(compareBy<Map<*,*>>{it["date"].toString()}.thenBy{(it["ts"] as? Number)?.toLong() ?: 0L})
    val values=ordered.map{(it["value"] as Number).toDouble()}
    HorizontalDivider()
    ValueLine("Migliore / peggiore", "${display(values.maxOrNull()!!)} / ${display(values.minOrNull()!!)}")
    ValueLine("Media ultimi ${minOf(3,rows.size)} voti", display(values.takeLast(3).average()))
    var point by remember(ordered) { mutableIntStateOf(values.lastIndex) }
    var sum = 0.0
    val means = values.mapIndexed { index, grade -> sum += grade; sum/(index+1) }
    GradeChart(means, ordered.map { it["date"].toString() },point,values) { point=it }
    Text("${ordered[point]["date"]} · Voto ${display(values[point])} · Media ${display(means[point])}",
        style=MaterialTheme.typography.bodySmall)
    ordered.groupBy {it["type"]}.forEach{(type,entries)->ValueLine("$type (${entries.size})",display(average(entries)))}
}
