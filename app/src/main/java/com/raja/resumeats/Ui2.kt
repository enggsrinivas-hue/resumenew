package com.raja.resumeats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RewriteScreen(vm: AppVm, go: (Screen) -> Unit, toast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val hasResume = vm.resume.isNotBlank()
    var basics by remember { mutableStateOf(!hasResume) }
    val qs = listOf(
        "Your name, phone, email, LinkedIn",
        "Your last or current job: title, company, dates",
        "What you handled day to day (rough notes are fine)",
        "Tools and systems you use",
        "Education and certifications"
    )
    val answers = remember { mutableStateListOf("", "", "", "", "") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    val after = remember(result, vm.family) { if (result.isBlank()) null else AtsEngine.analyze(result, vm.family).score }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.rewriteHint = ""; go(Screen.Home) }) { Text("Back") }
            Text("Rewrite resume", style = MaterialTheme.typography.titleLarge)
        }
        if (vm.apiKey.isBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI rewrite needs your Claude API key.")
                    Button(onClick = { go(Screen.Settings) }) { Text("Open settings") }
                }
            }
        } else {
            if (vm.rewriteHint.isNotBlank()) Text("Using the gaps from your comparison.", style = MaterialTheme.typography.bodySmall)
            if (hasResume) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!basics, { basics = false }, { Text("Polish mine") })
                    FilterChip(basics, { basics = true }, { Text("Build from basics") })
                }
            }
            Text(
                if (basics) "Answer what you can. Rough notes are fine. The AI uses only what you write."
                else "The AI rewrites your resume using only the facts already in it.",
                style = MaterialTheme.typography.bodySmall
            )
            if (basics) {
                qs.forEachIndexed { i, q ->
                    OutlinedTextField(answers[i], { answers[i] = it }, Modifier.fillMaxWidth(), label = { Text(q) }, minLines = if (i == 2) 3 else 1)
                }
            }
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (basics && !hasResume && answers[1].isBlank() && answers[2].isBlank()) {
                        toast("Add your last job or what you handled.")
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                val ans = if (basics) qs.indices.filter { answers[it].isNotBlank() }.joinToString("\n") { "${qs[it]}: ${answers[it]}" } else ""
                                result = withContext(Dispatchers.IO) { Ai.rewriteResume(vm.apiKey, vm.family, vm.resume, ans, vm.rewriteHint) }
                            } catch (e: Exception) {
                                toast(e.message ?: "AI request failed")
                            }
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Writing… this can take a minute" else "Write my resume") }

            if (after != null) {
                val before = vm.result?.score
                Text(
                    if (before != null) "Score: $before → $after" else "Score: $after",
                    style = MaterialTheme.typography.titleMedium, color = scoreColor(after)
                )
                val ph = Regex("\\[[^\\]]*\\]").findAll(result).count()
                if (ph > 0) Text("$ph placeholders to fill with your real numbers. The score goes up once you do.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(result, { result = it }, Modifier.fillMaxWidth().height(340.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { result = "" }, modifier = Modifier.weight(1f)) { Text("Discard") }
                    Button(onClick = {
                        if (vm.resume.isBlank()) vm.importResume(result) else vm.updateResume(result)
                        vm.rewriteHint = ""
                        toast("Resume updated. Fill in any [placeholders].")
                        go(Screen.Home)
                    }, modifier = Modifier.weight(1f)) { Text("Use this resume") }
                }
            }
        }
    }
}

private fun numStat(text: String): Pair<Int, Int> {
    val b = Resume.parse(text).filter { it.kind == Kind.BULLET }
    return b.count { l -> l.text.any { it.isDigit() } } to b.size
}

@Composable
fun CompareScreen(vm: AppVm, go: (Screen) -> Unit, onImportB: () -> Unit) {
    var pasting by remember { mutableStateOf(false) }
    val a = vm.result
    val b = remember(vm.compareText, vm.family) { if (vm.compareText.isBlank()) null else AtsEngine.analyze(vm.compareText, vm.family) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { go(Screen.Home) }) { Text("Back") }
            Text("Compare", style = MaterialTheme.typography.titleLarge)
        }
        if (a == null) {
            Text("Import your own resume on the Resume tab first. It becomes Resume A.")
        } else {
            Text("Resume A is your current resume. Add Resume B, for example a stronger one from a colleague or a sample.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportB, modifier = Modifier.weight(1f)) { Text(if (b == null) "Import Resume B" else "Change Resume B") }
                OutlinedButton(onClick = { pasting = !pasting }, modifier = Modifier.weight(1f)) { Text("Paste text") }
            }
            if (pasting) OutlinedTextField(vm.compareText, { vm.compareText = it }, Modifier.fillMaxWidth().height(200.dp), label = { Text("Resume B text") })
            if (b != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Resume A (yours)", style = MaterialTheme.typography.labelSmall)
                            Text("${a.score}", style = MaterialTheme.typography.headlineMedium, color = scoreColor(a.score))
                        }
                    }
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Resume B", style = MaterialTheme.typography.labelSmall)
                            Text("${b.score}", style = MaterialTheme.typography.headlineMedium, color = scoreColor(b.score))
                        }
                    }
                }
                a.cats.zip(b.cats).forEach { (ca, cb) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ca.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${ca.got} vs ${cb.got}", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(progress = { ca.got / ca.max.toFloat() }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF854F0B))
                        LinearProgressIndicator(progress = { cb.got / cb.max.toFloat() }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF3B6D11))
                    }
                }
                Text("Bar colors: brown is A, green is B.", style = MaterialTheme.typography.labelSmall)

                val kws = RoleKeywords.families[vm.family] ?: emptyList()
                val gaps = kws.filter { AtsEngine.hasWord(vm.compareText, it) && !AtsEngine.hasWord(vm.resume, it) }
                Text("Resume B has these, yours doesn't", fontWeight = FontWeight.Medium)
                Text(if (gaps.isEmpty()) "No role keywords missing from yours." else gaps.joinToString(", "))

                val na = numStat(vm.resume)
                val nb = numStat(vm.compareText)
                val ra = if (na.second == 0) 0.0 else na.first.toDouble() / na.second
                val rb = if (nb.second == 0) 0.0 else nb.first.toDouble() / nb.second
                val tips = mutableListOf<String>()
                a.cats.zip(b.cats).forEach { (ca, cb) ->
                    if (cb.got - ca.got >= 3) tips.add("${ca.name}: B scores ${cb.got}, you score ${ca.got}.")
                }
                if (rb > ra + 0.1) tips.add("B has numbers in ${nb.first} of ${nb.second} bullets. Yours has them in ${na.first} of ${na.second}. Add results to your strongest bullets first.")
                if (gaps.isNotEmpty()) tips.add("Add the missing keywords only if you really have those skills.")
                if (tips.isEmpty()) tips.add("Your resume already scores as well as B on these checks.")

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("What to improve in A", fontWeight = FontWeight.Medium)
                        tips.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Button(onClick = {
                    vm.rewriteHint = "Compared with a stronger resume, these gaps were found: " + tips.joinToString(" ") +
                        " Fix them only where the source resume's facts support it. Do not copy anything else from the other resume."
                    go(Screen.Rewrite)
                }, modifier = Modifier.fillMaxWidth()) { Text("Improve mine using these gaps") }
            }
        }
    }
}
