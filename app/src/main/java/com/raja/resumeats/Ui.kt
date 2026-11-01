package com.raja.resumeats

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Home, Fixes, Editor, Settings, Tailor, Tracker, Rewrite, Compare }

@Composable
fun App(vm: AppVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Home) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val t = withContext(Dispatchers.IO) { Importer.read(ctx, uri) }
                if (t.isBlank()) toast("No text found. Try a text-based PDF or DOCX.") else vm.importResume(t)
            } catch (e: Exception) { toast("Couldn't read that file") }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { Exporter.pdf(vm.resume, it) } }
                toast("PDF saved")
            } catch (e: Exception) { toast("Couldn't save the PDF") }
        }
    }
    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { Exporter.docx(vm.resume, it) } }
                toast("Word file saved")
            } catch (e: Exception) { toast("Couldn't save the Word file") }
        }
    }
    val openB = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val t = withContext(Dispatchers.IO) { Importer.read(ctx, uri) }
                if (t.isBlank()) toast("No text found in that file.") else vm.compareText = t
            } catch (e: Exception) { toast("Couldn't read that file") }
        }
    }
    val importFile = { open.launch(arrayOf("application/pdf", DOCX_MIME, "text/plain")) }

    if (screen != Screen.Home && screen != Screen.Tailor && screen != Screen.Tracker) BackHandler { screen = Screen.Home }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.Home -> HomeScreen(vm, { screen = it }, importFile, { savePdf.launch("Resume.pdf") }, { saveDocx.launch("Resume.docx") })
                Screen.Fixes -> FixesScreen(vm, { screen = it }, ::toast)
                Screen.Editor -> EditorScreen(vm) { screen = Screen.Home }
                Screen.Settings -> SettingsScreen(vm) { screen = Screen.Home }
                Screen.Tailor -> TailorScreen(vm)
                Screen.Tracker -> TrackerScreen(vm)
                Screen.Rewrite -> RewriteScreen(vm, { screen = it }, ::toast)
                Screen.Compare -> CompareScreen(vm, { screen = it }, { openB.launch(arrayOf("application/pdf", DOCX_MIME, "text/plain")) })
            }
        }
        if (screen == Screen.Home || screen == Screen.Tailor || screen == Screen.Tracker) {
            NavigationBar {
                NavigationBarItem(screen == Screen.Home, { screen = Screen.Home }, { Icon(Icons.Default.Home, null) }, label = { Text("Resume") })
                NavigationBarItem(screen == Screen.Tailor, { screen = Screen.Tailor }, { Icon(Icons.Default.Search, null) }, label = { Text("Job match") })
                NavigationBarItem(screen == Screen.Tracker, { screen = Screen.Tracker }, { Icon(Icons.Default.Menu, null) }, label = { Text("Tracker") })
            }
        }
    }
}

fun scoreColor(s: Int) = when { s >= 90 -> Color(0xFF3B6D11); s >= 75 -> Color(0xFF854F0B); else -> Color(0xFFA32D2D) }
fun scoreLabel(s: Int) = when { s >= 90 -> "Strong"; s >= 75 -> "Good"; s >= 60 -> "Needs work"; else -> "Weak" }

@Composable
fun ScoreRing(score: Int) {
    val color = scoreColor(score)
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 14.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(Color.Gray.copy(alpha = 0.25f), -90f, 360f, false, tl, sz, style = Stroke(sw))
            drawArc(color, -90f, 360f * score / 100f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontSize = 38.sp, fontWeight = FontWeight.Medium)
            Text("out of 100", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HomeScreen(vm: AppVm, go: (Screen) -> Unit, onImport: () -> Unit, onPdf: () -> Unit, onDocx: () -> Unit) {
    val r = vm.result
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Resume check", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { go(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
        }
        var pick by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) { Text("Target role: ${vm.family}") }
        if (pick) RolePickerDialog(vm.family, { vm.updateFamily(it); pick = false }, { pick = false })
        if (r == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Start your first check", style = MaterialTheme.typography.titleMedium)
                    Text("Import a PDF or Word resume, or paste the text. Everything is checked on your phone.")
                    Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import resume") }
                    OutlinedButton(onClick = { go(Screen.Editor) }, modifier = Modifier.fillMaxWidth()) { Text("Paste text") }
                    OutlinedButton(onClick = { go(Screen.Rewrite) }, modifier = Modifier.fillMaxWidth()) { Text("Build with AI") }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScoreRing(r.score)
                Text(scoreLabel(r.score), color = scoreColor(r.score), fontWeight = FontWeight.Medium)
                if (vm.baseline >= 0 && vm.baseline != r.score) Text("Started at ${vm.baseline}, now ${r.score}", style = MaterialTheme.typography.bodySmall)
            }
            r.cats.forEach { c ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${c.got}/${c.max}", style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(progress = { c.got / c.max.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            }
            Button(onClick = { go(Screen.Fixes) }, modifier = Modifier.fillMaxWidth()) { Text("See ${r.issues.size} fixes") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { go(Screen.Rewrite) }, modifier = Modifier.weight(1f)) { Text("AI rewrite") }
                OutlinedButton(onClick = { go(Screen.Compare) }, modifier = Modifier.weight(1f)) { Text("Compare") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPdf, modifier = Modifier.weight(1f)) { Text("Export PDF") }
                OutlinedButton(onClick = onDocx, modifier = Modifier.weight(1f)) { Text("Export Word") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { go(Screen.Editor) }, modifier = Modifier.weight(1f)) { Text("Edit text") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import another") }
            }
        }
    }
}

@Composable
fun FixesScreen(vm: AppVm, go: (Screen) -> Unit, toast: (String) -> Unit) {
    val r = vm.result
    val scope = rememberCoroutineScope()
    var showKw by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<Pair<String, String>?>(null) }
    if (r == null) { Text("Import a resume first.", Modifier.padding(16.dp)); return }
    val weakBullets = Resume.parse(vm.resume).filter { it.kind == Kind.BULLET && it.text.none { c -> c.isDigit() } }.map { it.text }.take(6)
    val total = r.issues.sumOf { it.points }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { go(Screen.Home) }) { Text("Back") }
                Text("Fixes", style = MaterialTheme.typography.titleLarge)
            }
            if (r.issues.isEmpty()) Text("Nothing left to fix. Your score is ${r.score}.")
            else Text("Fix everything below to gain up to $total points. Score now: ${r.score}.", style = MaterialTheme.typography.bodyMedium)
        }
        items(r.issues) { i ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(i.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("+${i.points}", color = scoreColor(60))
                    }
                    Text(i.detail, style = MaterialTheme.typography.bodySmall)
                    when {
                        i.id == "weak" && i.auto -> OutlinedButton(onClick = {
                            val (t, n) = AtsEngine.fixWeak(vm.resume)
                            vm.updateResume(t); toast("Fixed $n lines")
                        }) { Text("Apply fix") }
                        i.id == "kw" -> OutlinedButton(onClick = { showKw = true }) { Text("Choose skills") }
                        i.id == "quant" || i.id == "verbs" -> {}
                        else -> OutlinedButton(onClick = { go(Screen.Editor) }) { Text("Edit resume") }
                    }
                }
            }
        }
        if (weakBullets.isNotEmpty()) {
            item {
                Text("Bullets without numbers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    if (vm.apiKey.isBlank()) "Add your Claude API key in Settings to get AI rewrites. You can also edit them by hand."
                    else "AI keeps your facts and adds [placeholders] where a number would help. Fill them in with your real figures.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(weakBullets) { b ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(b, style = MaterialTheme.typography.bodyMedium)
                        if (vm.apiKey.isNotBlank()) OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    val s = withContext(Dispatchers.IO) { Ai.rewrite(vm.apiKey, b) }
                                    suggestion = b to s
                                } catch (e: Exception) { toast(e.message ?: "AI request failed") }
                                busy = false
                            }
                        }) { Text(if (busy) "Working…" else "Improve with AI") }
                    }
                }
            }
        }
        item { Box(Modifier.size(16.dp)) }
    }

    if (showKw) KeywordDialog(r.missingKeywords, { picked ->
        vm.updateResume(AtsEngine.addSkills(vm.resume, picked)); showKw = false
    }, { showKw = false })

    suggestion?.let { (old, new) ->
        AlertDialog(
            onDismissRequest = { suggestion = null },
            title = { Text("Suggested rewrite") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Before: $old", style = MaterialTheme.typography.bodySmall); Text(new) } },
            confirmButton = { TextButton(onClick = { vm.updateResume(vm.resume.replaceFirst(old, new)); suggestion = null }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { suggestion = null }) { Text("Skip") } }
        )
    }
}

@Composable
fun KeywordDialog(missing: List<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add skills you really have") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (missing.isEmpty()) Text("No missing keywords for this role.")
                missing.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(k in picked, { if (it) picked.add(k) else picked.remove(k) })
                        Text(k)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(picked.toList()) }) { Text("Add to skills") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditorScreen(vm: AppVm, onDone: () -> Unit) {
    var t by remember { mutableStateOf(vm.resume) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Edit resume text", style = MaterialTheme.typography.titleLarge)
        Text("Use plain headings like EXPERIENCE, EDUCATION, SKILLS and start bullets with a dash.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(t, { t = it }, Modifier.weight(1f).fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = {
                if (vm.resume.isBlank()) vm.importResume(t) else vm.updateResume(t)
                onDone()
            }, modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppVm, onDone: () -> Unit) {
    var key by remember { mutableStateOf(vm.apiKey) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text("Claude API key (optional). Used only for AI bullet rewrites. Stored encrypted on this phone and sent only to Anthropic.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Button(onClick = { vm.updateKey(key); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
fun TailorScreen(vm: AppVm) {
    val hasJd = vm.jd.isNotBlank() && vm.resume.isNotBlank()
    val m = if (hasJd) JobMatch.match(vm.resume, vm.jd) else null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Job match", style = MaterialTheme.typography.titleLarge)
        Text("Optional. Paste a job post to see how well your resume matches its keywords.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(vm.jd, { vm.jd = it }, Modifier.fillMaxWidth().height(220.dp), label = { Text("Job description") })
        if (vm.resume.isBlank()) Text("Import your resume first.")
        if (m != null) {
            Text("${m.first}% match", style = MaterialTheme.typography.headlineSmall, color = scoreColor(m.first))
            if (m.second.isNotEmpty()) {
                Text("Keywords from the job that your resume doesn't have:", fontWeight = FontWeight.Medium)
                Text(m.second.joinToString(", "))
                Text("Add only what you really have. Don't copy skills you can't back up in an interview.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TrackerScreen(vm: AppVm) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Applications", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { adding = true }) { Text("Add") }
        }
        if (jobs.isEmpty()) Text("No applications yet. Add the first one you send.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(jobs) { j ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(j.company, fontWeight = FontWeight.Medium)
                                Text(j.role, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.deleteJob(j) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Applied", "Interview", "Offer", "Rejected").forEach { s ->
                                FilterChip(j.status == s, { vm.saveJob(j.copy(status = s)) }, { Text(s) })
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        var company by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add application") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(company, { company = it }, label = { Text("Company") }, singleLine = true)
                    OutlinedTextField(role, { role = it }, label = { Text("Role") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (company.isNotBlank()) { vm.saveJob(Job(company = company.trim(), role = role.trim())); adding = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun RolePickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose your target role") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search, e.g. SailPoint") }, singleLine = true)
                LazyColumn(Modifier.height(320.dp)) {
                    RoleKeywords.groups.forEach { (g, roles) ->
                        val shown = roles.filter { r ->
                            q.isBlank() || r.contains(q, true) || g.contains(q, true) ||
                                (RoleKeywords.families[r] ?: emptyList()).any { k -> k.contains(q, true) }
                        }
                        if (shown.isNotEmpty()) {
                            item { Text(g, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                            items(shown) { r ->
                                TextButton(onClick = { onPick(r) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(r, fontWeight = if (r == current) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
