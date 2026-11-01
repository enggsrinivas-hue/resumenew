package com.raja.resumeats

import android.content.Context
import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Entity
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val company: String,
    val role: String,
    val status: String = "Applied",
    val updated: Long = System.currentTimeMillis()
)

@Dao
interface JobDao {
    @Query("SELECT * FROM Job ORDER BY updated DESC")
    fun all(): Flow<List<Job>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(j: Job)

    @Delete
    suspend fun delete(j: Job)
}

@Database(entities = [Job::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun jobs(): JobDao
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("resume", Context.MODE_PRIVATE)
    private val secure by lazy {
        EncryptedSharedPreferences.create(
            ctx, "secure",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    var resume: String
        get() = sp.getString("resume", "") ?: ""
        set(v) { sp.edit().putString("resume", v).apply() }
    var family: String
        get() = sp.getString("family", RoleKeywords.families.keys.first()) ?: RoleKeywords.families.keys.first()
        set(v) { sp.edit().putString("family", v).apply() }
    var baseline: Int
        get() = sp.getInt("baseline", -1)
        set(v) { sp.edit().putInt("baseline", v).apply() }
    var apiKey: String
        get() = try { secure.getString("key", "") ?: "" } catch (e: Exception) { "" }
        set(v) { try { secure.edit().putString("key", v).apply() } catch (e: Exception) { } }
}

object Ai {
    fun ask(key: String, prompt: String, maxTokens: Int = 1000, model: String = "claude-haiku-5-5"): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.setRequestProperty("content-type", "application/json")
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 120000
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        if (code !in 200..299) error("AI request failed ($code). Check your API key.")
        val txt = c.inputStream.bufferedReader().readText()
        val arr = JSONObject(txt).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("type") == "text") sb.append(o.getString("text"))
        }
        return sb.toString().trim()
    }

    fun rewrite(key: String, bullet: String): String {
        val prompt = "Rewrite this resume bullet so it is ATS-friendly. Start with a strong past-tense action verb, keep it under 25 words, " +
            "and keep every fact exactly as given. Do not invent numbers, tools or results. If a metric would help, add a placeholder like [X%] " +
            "or [number] for the person to fill in. Return only the bullet text.\n\nBullet: $bullet"
        return ask(key, prompt, 200).trimStart('-', '•', ' ')
    }

    fun rewriteResume(key: String, role: String, resume: String, answers: String, hint: String): String {
        val prompt = "You are an expert resume writer for ATS systems. Target role: $role.\n\n" +
            "Rules:\n" +
            "- Use ONLY facts found in the source material below. Never invent employers, dates, tools, certifications, degrees, numbers or results.\n" +
            "- Where a number or result would strengthen a bullet but is not given, write a placeholder like [X%] or [number]. Never guess.\n" +
            "- Start every bullet with a strong action verb (past tense, present tense for the current job). No first-person words.\n" +
            "- Work in keywords that fit the role ONLY when the source material supports them.\n" +
            "- Plain text only: single column, no markdown, no tables, no icons.\n" +
            "- Format: first line is the person's name, second line is contact details separated by ' | ' (only those given). " +
            "Then these headings in capitals, each on its own line: SUMMARY, EXPERIENCE, EDUCATION, SKILLS, CERTIFICATIONS (skip a section if the source has no facts for it). " +
            "Under EXPERIENCE write each job as 'Job title, Company, dates' followed by bullets that start with '- '. SKILLS is one comma-separated line.\n" +
            "- Return only the resume.\n\n" +
            (if (hint.isNotBlank()) "Extra guidance: $hint\n\n" else "") +
            (if (resume.isNotBlank()) "SOURCE RESUME:\n$resume\n\n" else "") +
            (if (answers.isNotBlank()) "EXTRA DETAILS FROM THE PERSON:\n$answers\n" else "")
        val raw = ask(key, prompt, 3500, "claude-sonnet-5-5")
        return raw.replace("```", "").replace("**", "").lines().joinToString("\n") { it.trimEnd().trimStart('#', ' ') }.trim()
    }
}

class AppVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val db = Room.databaseBuilder(app, Db::class.java, "jobs.db").build()

    var resume by mutableStateOf(store.resume); private set
    var family by mutableStateOf(store.family); private set
    var baseline by mutableStateOf(store.baseline); private set
    var apiKey by mutableStateOf(store.apiKey); private set
    var jd by mutableStateOf("")
    var compareText by mutableStateOf("")
    var rewriteHint by mutableStateOf("")

    val jobs = db.jobs().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val result: AtsResult? by derivedStateOf { if (resume.isBlank()) null else AtsEngine.analyze(resume, family) }

    fun updateResume(t: String) { resume = t; store.resume = t }
    fun importResume(t: String) {
        updateResume(t)
        baseline = AtsEngine.analyze(t, family).score
        store.baseline = baseline
    }
    fun updateFamily(f: String) { family = f; store.family = f }
    fun updateKey(k: String) { apiKey = k.trim(); store.apiKey = k.trim() }
    fun saveJob(j: Job) { viewModelScope.launch { db.jobs().save(j.copy(updated = System.currentTimeMillis())) } }
    fun deleteJob(j: Job) { viewModelScope.launch { db.jobs().delete(j) } }
}
