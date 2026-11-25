package it.registrovoti.mobile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

private data class Draft(val subject:String,val raw:String,val day:String,val type:String)

private val aliasGroups=listOf(
    listOf("Italiano","Lingua e letteratura italiana","Letteratura italiana","Lingua italiana"),
    listOf("Scienze motorie","Scienze motorie e sportive","Educazione fisica","Motoria"),
    listOf("Inglese","Lingua inglese","Lingua straniera inglese","Lingua e cultura inglese"),
    listOf("TPSIT","Tecnologie e progettazione di sistemi informatici e di telecomunicazioni","Tecnologie e progettazione di sistemi informatici e telecomunicazioni"),
    listOf("Educazione civica","Ed civica")
)
private fun fold(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD)
    .replace(Regex("""\p{M}+"""),"").lowercase().replace(Regex("[^a-z0-9]+")," ").trim()
private val aliases=aliasGroups.flatMap { group->group.map { fold(it).replace(" ","") to group.first() } }.toMap()
private fun subjectKey(s:String)=fold(aliases[fold(s).replace(" ","")] ?: s)
private fun resolveSubject(s:String,names:List<String>):String {
    val preferred=aliases[fold(s).replace(" ","")] ?: s.trim()
    return names.firstOrNull { fold(it)==fold(preferred) } ?: names.firstOrNull { subjectKey(it)==subjectKey(s) } ?: preferred
}

private data class GradeKey(val subject:String,val score:Double,val day:String,val type:String)
private fun gradeKey(name:String,score:Double,day:String,type:String)=
    GradeKey(subjectKey(name),score,day,type)

private fun duplicateReasons(book:Data,rows:List<Draft>):List<String> {
    val existing=grades(book).filterNot { book["demo"]==true && it["demo"]==true }.mapNotNull {
        val value=(it["value"] as? Number)?.toDouble() ?: return@mapNotNull null
        gradeKey(it["subject"].toString(),value,it["date"].toString(),it["type"].toString())
    }.toSet()
    val seen=mutableSetOf<GradeKey>()
    return rows.map { r ->
        val score=parseGrade(r.raw)?.first ?: return@map ""
        if(r.subject.isBlank() || r.day.isBlank()) return@map ""
        val key=gradeKey(r.subject,score,r.day,r.type)
        when {
            key in existing -> "Già nel registro"
            !seen.add(key) -> "Ripetuto nello screenshot"
            else -> ""
        }
    }
}

private fun screenshotRows(result:Text,names:List<String>):List<Draft> {
    // Rejoin text columns which ML Kit may return as separate blocks.
    val visualRows=mutableListOf<MutableList<Text.Line>>()
    val lines=result.textBlocks.flatMap { it.lines }.filter { it.boundingBox!=null }
        .sortedBy { it.boundingBox!!.centerY() }
    for(line in lines) {
        val box=line.boundingBox!!
        val previous=visualRows.lastOrNull()
        val anchor=previous?.first()?.boundingBox
        if(previous!=null && anchor!=null && abs(box.centerY()-anchor.centerY())<=minOf(box.height(),anchor.height())/2)
            previous.add(line)
        else visualRows.add(mutableListOf(line))
    }
    val resultRows=mutableListOf<Draft>()
    val dates=datePattern
    var subject=""
    val orderedNames=names.sortedByDescending { it.length }
    for(row in visualRows) {
        val line=row.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text }
        val date=dates.find(line)
        val day=isoGradeDate(date)
        val words=dates.replace(line," ").replace(Regex("""\b\d{1,2}:\d{2}\b""")," ").split(Regex("""[\s|;]+"""))
        val type=types.firstOrNull { line.contains(it,true) }
        val known=orderedNames.firstOrNull { line.contains(it,true) }
        val guess=words.filter { it.isNotBlank() && parseGrade(it)==null && !it.equals(type,true) }.joinToString(" ")
        val resolved=resolveSubject(guess,names)
        subject=resolved.takeIf { it!=guess || it in names } ?: known ?: guess.takeIf { it.matches(Regex("""[\p{L}][\p{L} ’'-]{1,39}""")) } ?: subject
        words.filter { parseGrade(it)!=null }.forEach { resultRows.add(Draft(subject,it,day,type ?: types.first())) }
    }
    return resultRows
}

private val datePattern=Regex("""(?<!\d)(\d{1,2})\s*[/.-]\s*(\d{1,2})\s*[/.-]\s*(\d{4}|\d{2})(?!\d)""")
private fun isoGradeDate(match:MatchResult?):String {
    if(match==null)return ""
    val (day,month,year)=match.destructured
    val fullYear=if(year.length==2) 2000+year.toInt() else year.toInt()
    return runCatching { LocalDate.of(fullYear,month.toInt(),day.toInt()).toString() }.getOrDefault("")
}
private fun structuredRows(bitmap:android.graphics.Bitmap,text:Text,names:List<String>,templates:List<BadgeLayout.Glyph>):List<Draft>{
    val w=bitmap.width
    val pixels=IntArray(w*bitmap.height)
    bitmap.getPixels(pixels,0,w,0,0,w,bitmap.height)
    val regions=BadgeLayout.detect(pixels,w,bitmap.height)
    if(regions.isEmpty())return screenshotRows(text,names).map { it.copy(subject=resolveSubject(it.subject,names)) }
    val elements=text.textBlocks.flatMap { block->block.lines.flatMap { it.elements } }.filter { it.boundingBox!=null }
    val drafts=mutableListOf<Draft>()
    var dated=false
    for(row in regions){
        val details=elements.filter {
            val b=it.boundingBox!!
            b.centerY() in row.top..row.bottom && b.centerX() in row.start..row.end
        }
        val dateElement=details.firstOrNull { datePattern.containsMatchIn(it.text) }
        val dateLine=details.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text }
        val date=datePattern.find(dateElement?.text ?: dateLine) ?: continue
        dated=true
        val day=isoGradeDate(date)
        val boundary=dateElement?.boundingBox
        val mobile=row.r-row.l>w*.09
        val words=details.filter {
            val b=it.boundingBox!!
            if(boundary!=null && mobile)b.centerY()<boundary.top
            else if(boundary!=null)b.centerX()<boundary.left
            else if(!mobile)b.centerX()<w*.25
            else !datePattern.containsMatchIn(it.text) && it.text.any(Char::isLetter) && it.text==it.text.uppercase()
        }
        val lines=mutableListOf<MutableList<Text.Element>>()
        for(word in words.sortedBy { it.boundingBox!!.centerY() }){
            val last=lines.lastOrNull()
            val box=word.boundingBox!!
            if(last!=null && abs(box.centerY()-last.first().boundingBox!!.centerY())<box.height()*.55)last.add(word)
            else lines.add(mutableListOf(word))
        }
        val name=lines.joinToString(" ") { line->line.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text } }
        val raw=BadgeLayout.match(pixels,w,row,templates) ?: elements.filter {
            val b=it.boundingBox!!
            b.centerX() in row.l..row.r && b.centerY() in row.t..row.b
        }.sortedBy { it.boundingBox!!.left }.joinToString("") { it.text }
        if(name.isBlank() || parseGrade(raw)==null)continue
        val type=types.firstOrNull { dateLine.contains(it,true) } ?: types.first()
        drafts.add(Draft(resolveSubject(name,names),raw,day,type))
    }
    return if(dated) drafts else screenshotRows(text,names).map { it.copy(subject=resolveSubject(it.subject,names)) }
}

@Composable
internal fun ScreenshotDialog(book:Data,commit:Commit,close:()->Unit,done:(Int)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val rows=remember { mutableStateListOf<Draft>() }
    val reasons=duplicateReasons(book,rows)
    val visible=rows.indices.filter { reasons[it].isEmpty() }
    val fresh=visible.size
    var emptyPopup by remember { mutableStateOf(false) }
    LaunchedEffect(reasons) {
        if(reasons.any { it.isNotEmpty() } && duplicateReasons(book,rows)==reasons){
            for(i in reasons.indices.reversed()) if(reasons[i].isNotEmpty())rows.removeAt(i)
            if(rows.isEmpty())emptyPopup=true
        }
    }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Scegli una schermata ritagliata sui voti. La lettura avviene sul dispositivo.") }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            rows.clear()
            message="Lettura dello screenshot…"
            try {
                val bitmap=withContext(Dispatchers.IO) {
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
                    val options=android.graphics.BitmapFactory.Options().apply {
                        while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize.coerceAtLeast(1)>4096)
                            inSampleSize=inSampleSize.coerceAtLeast(1)*2
                    }
                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
                        ?: error("Immagine non leggibile")
                }
                val input=InputImage.fromBitmap(bitmap,0)
                val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val task=try { reader.process(input) } catch(e:Exception) { reader.close();throw e }
                task.addOnSuccessListener { text ->
                    scope.launch {
                        try {
                            val found=withContext(Dispatchers.Default) {
                                val json=org.json.JSONArray(context.resources.openRawResource(R.raw.grade_glyphs).bufferedReader().use { it.readText() })
                                val templates=(0 until json.length()).map { index->json.getJSONObject(index).let {
                                    BadgeLayout.Glyph(it.getString("raw"),it.getString("bits"),it.getDouble("ratio"))
                                } }
                                structuredRows(bitmap,text,subjects(book),templates)
                            }
                            message=when {
                                found.isEmpty() -> { emptyPopup=true; "Puoi scegliere un altro screenshot." }
                                found.size>500 -> "Troppi voti. Ritaglia una porzione più piccola."
                                else -> {
                                    rows.addAll(found)
                                    "Controlla le date. Se una data è vuota, inserisci quella riportata nel registro."
                                }
                            }
                        } catch(e:Exception){message="Immagine non leggibile."} finally {busy=false}
                    }
                }.addOnFailureListener { busy=false;message="Lettura non riuscita. Prova un altro screenshot." }
                 .addOnCompleteListener { reader.close() }
            } catch(e:Exception) { busy=false;message="Immagine non leggibile. Scegli uno screenshot PNG o JPEG." }
        }
    }
    AlertDialog(onDismissRequest={if(!saving) close()},title={Text("Importa da screenshot")},text={
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {
                Text(message)
                Button(enabled=!busy && !saving,onClick={picker.launch("image/*")},
                    modifier=Modifier.fillMaxWidth().padding(vertical=8.dp).heightIn(min=54.dp)) {
                    Text("Scegli uno screenshot",style=MaterialTheme.typography.titleMedium)
                }
            }
            itemsIndexed(visible) { position,i ->
                val r=rows.getOrNull(i) ?: return@itemsIndexed
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    Text("Voto ${position+1}",style=MaterialTheme.typography.titleSmall)
                    OutlinedTextField(r.subject,{rows[i]=r.copy(subject=it.take(40))},enabled=!saving,label={Text("Materia")},singleLine=true)
                    OutlinedTextField(r.raw,{rows[i]=r.copy(raw=it.take(6))},enabled=!saving,label={Text("Voto")},singleLine=true)
                    OutlinedTextField(r.day,{rows[i]=r.copy(day=it)},enabled=!saving,label={Text("Data (AAAA-MM-GG)")},singleLine=true)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(enabled=!saving,onClick={expanded=true}) { Text("Prova · ${r.type} ▾") }
                        DropdownMenu(expanded,{expanded=false}) {
                            types.forEach { t->DropdownMenuItem(text={Text(t)},onClick={rows[i]=r.copy(type=t);expanded=false}) }
                        }
                    }
                    TextButton(enabled=!saving,onClick={rows.removeAt(i)}) { Text("Escludi") }
                }
            }
        }
    },dismissButton={TextButton(enabled=!saving,onClick=close){Text("Chiudi")}},confirmButton={
        TextButton(enabled=!busy && !saving && fresh>0,onClick={
            val batch=visible.map { rows[it] }
            val changes=mutableListOf<Change>()
            for((i,r) in batch.withIndex()) {
                val score=parseGrade(r.raw)
                val day=runCatching { LocalDate.parse(r.day) }.getOrNull()
                if(r.subject.isBlank() || score==null || day==null) {
                    message="Controlla materia, voto e data nella riga ${i+1}."
                    return@TextButton
                }
                changes.add(changeGrade(null,resolveSubject(r.subject,subjects(book)),score,day,r.type,""))
            }
            saving=true
            var added=0
            commit({current->
                val duplicates=duplicateReasons(current,batch)
                added=duplicates.count { it.isEmpty() }
                require(added>0) { "Tutti i voti sono già presenti." }
                changes.foldIndexed(current) { index,updated,change ->
                    if(duplicates[index].isEmpty()) change(updated) else updated
                }
            }) { error ->
                saving=false
                if(error==null) done(added) else if(error=="Tutti i voti sono già presenti.") {rows.clear();emptyPopup=true} else message=error
            }
        }) { Text(if(saving) "Salvataggio…" else if(rows.isNotEmpty() && fresh==0) "Nessun nuovo voto" else "Importa ($fresh)") }
    })
    if(emptyPopup) AlertDialog(onDismissRequest={emptyPopup=false},title={Text("Nessun nuovo voto")},
        text={Text("Non è stato trovato nessun nuovo voto nello screenshot.")},
        confirmButton={TextButton(onClick={emptyPopup=false}){Text("OK")}})

}
