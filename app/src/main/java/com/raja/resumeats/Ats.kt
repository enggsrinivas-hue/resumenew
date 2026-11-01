package com.raja.resumeats

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Kind { NAME, HEADING, BULLET, TEXT, BLANK }
data class Line(val kind: Kind, val text: String)
data class Issue(val id: String, val category: String, val title: String, val detail: String, val points: Int, val auto: Boolean = false)
data class Cat(val name: String, val got: Int, val max: Int)
data class AtsResult(val score: Int, val cats: List<Cat>, val issues: List<Issue>, val missingKeywords: List<String>)

object RoleKeywords {
    private val data: List<Triple<String, String, List<String>>> = listOf(
        Triple("IAM and security", "IAM Access Management Analyst", listOf("Access Management", "User Provisioning", "Deprovisioning", "Joiner Mover Leaver", "Access Reviews", "Recertification", "RBAC", "Active Directory", "Entra ID", "ServiceNow", "SSO", "MFA", "Least Privilege", "Access Requests", "Audit", "Compliance", "SOX")),
        Triple("IAM and security", "IAM Lead, Access Management", listOf("Access Management", "Team Lead", "Stakeholder Management", "Identity Governance", "Access Certification", "Policy", "Risk", "Audit", "Compliance", "Process Improvement", "Mentoring", "Escalation", "SLA", "Incident Management", "Roadmap", "ServiceNow", "RBAC", "Reporting")),
        Triple("IAM and security", "SailPoint Lead / Engineer", listOf("SailPoint", "IdentityIQ", "IdentityNow", "Identity Governance", "Workflows", "BeanShell", "Connectors", "Provisioning", "Certifications", "Lifecycle Events", "Rules", "Role Modeling", "Application Onboarding", "REST API", "SCIM", "Active Directory", "Leadership", "Integration")),
        Triple("IAM and security", "CyberArk / PAM Lead", listOf("CyberArk", "Privileged Access Management", "PAM", "Vault", "CPM", "PSM", "Safes", "Password Rotation", "Session Recording", "Just-in-Time Access", "Credential Management", "Account Onboarding", "Least Privilege", "Audit", "Compliance", "Leadership")),
        Triple("IAM and security", "IAM Engineer (Okta / Entra ID)", listOf("Okta", "Entra ID", "Azure AD", "SAML", "OAuth", "OIDC", "SCIM", "SSO", "MFA", "Conditional Access", "Federation", "Directory Services", "PowerShell", "REST API", "Automation")),
        Triple("IAM and security", "IAM Governance and Compliance Analyst", listOf("Access Certification", "SOX", "ISO 27001", "Audit", "Segregation of Duties", "Risk Assessment", "Controls", "Policies", "Evidence", "Remediation", "Governance", "Compliance", "Reporting")),
        Triple("IAM and security", "Cybersecurity Analyst", listOf("SIEM", "Splunk", "Incident Response", "Vulnerability Management", "Threat Detection", "Firewall", "NIST", "ISO 27001", "Risk Assessment", "Endpoint Security", "SOC", "Log Analysis", "Phishing", "Compliance"))
    )
    val families: Map<String, List<String>> = data.associate { it.second to it.third }
    val groups: Map<String, List<String>> = data.groupBy({ it.first }, { it.second })
}

object Resume {
    private val sections = mapOf(
        "summary" to listOf("summary", "professional summary", "profile", "objective", "career objective", "about me", "career summary"),
        "experience" to listOf("experience", "work experience", "professional experience", "employment history", "work history", "employment"),
        "education" to listOf("education", "academic background", "qualifications", "academics"),
        "skills" to listOf("skills", "technical skills", "key skills", "core competencies", "skills and tools"),
        "extras" to listOf("certifications", "projects", "achievements", "awards", "certificates")
    )
    const val BULLETS = "-•*▪–·● "

    fun headingKey(t: String): String? {
        val n = t.trim().trimEnd(':').lowercase()
        return sections.entries.firstOrNull { n in it.value }?.key
    }

    fun parse(text: String): List<Line> {
        var nameDone = false
        return text.lines().map { raw ->
            val t = raw.trim()
            when {
                t.isEmpty() -> Line(Kind.BLANK, "")
                headingKey(t) != null -> { nameDone = true; Line(Kind.HEADING, t.trimEnd(':').uppercase()) }
                !nameDone -> { nameDone = true; Line(Kind.NAME, t) }
                t.first() in BULLETS -> Line(Kind.BULLET, t.trimStart { it in BULLETS })
                else -> Line(Kind.TEXT, t)
            }
        }
    }
}

object AtsEngine {
    private val strongVerbs = setOf(
        "led", "built", "designed", "developed", "implemented", "managed", "created", "reduced", "increased", "improved",
        "automated", "delivered", "analyzed", "analysed", "streamlined", "launched", "optimized", "coordinated", "resolved",
        "maintained", "deployed", "migrated", "configured", "monitored", "tested", "reviewed", "trained", "drove", "achieved",
        "established", "executed", "integrated", "secured", "audited", "documented", "supported", "handled", "owned", "produced",
        "negotiated", "generated", "enhanced", "simplified", "standardized", "administered", "onboarded", "provisioned", "validated"
    )
    private val weak = listOf(
        "responsible for" to "Managed", "worked on" to "Delivered", "helped with" to "Supported",
        "duties included" to "Handled", "was involved in" to "Contributed to", "assisted in" to "Supported", "in charge of" to "Led"
    )

    fun hasWord(text: String, k: String) =
        Regex("(?<![A-Za-z0-9])" + Regex.escape(k) + "(?![A-Za-z0-9])", RegexOption.IGNORE_CASE).containsMatchIn(text)

    fun analyze(text: String, family: String): AtsResult {
        val lines = Resume.parse(text)
        val issues = mutableListOf<Issue>()
        val words = Regex("\\S+").findAll(text).count()

        // Sections (20)
        val heads = lines.filter { it.kind == Kind.HEADING }.mapNotNull { Resume.headingKey(it.text) }.toSet()
        var sec = 0
        listOf(Triple("experience", "Experience", 6), Triple("education", "Education", 4), Triple("skills", "Skills", 5), Triple("summary", "Summary", 5)).forEach { (k, n, p) ->
            if (k in heads) {
                sec += p
            } else {
                issues.add(Issue("sec_$k", "Sections", "Add a $n section", "Use the plain heading \"$n\" so the ATS can find it.", p))
            }
        }

        // Format and contact (15)
        var fmt = 0
        if (Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+").containsMatchIn(text)) fmt += 4 else issues += Issue("email", "Format", "Add your email", "ATS and recruiters need a plain-text email at the top.", 4)
        if (Regex("\\+?\\d[\\d\\s\\-().]{8,}\\d").containsMatchIn(text)) fmt += 4 else issues += Issue("phone", "Format", "Add your phone number", "Put it in the first lines, as plain text.", 4)
        if (text.contains("linkedin.com", true)) fmt += 3 else issues += Issue("linkedin", "Format", "Add your LinkedIn link", "Most ATS profiles read this field.", 3)
        val odd = text.count { it.code > 127 && it !in "•–—‘’“”·▪●…" }
        if (odd < 3) fmt += 2 else issues += Issue("symbols", "Format", "Remove special symbols", "$odd unusual characters found. Icons and fancy symbols can break ATS parsing.", 2)
        if (lines.any { it.kind == Kind.BULLET }) fmt += 2 else issues += Issue("bullets", "Format", "Use bullet points", "List your work as short bullets under each job.", 2)
        if (Regex("\\[[^\\]]*\\]").containsMatchIn(text)) {
            fmt = max(0, fmt - 5)
            issues += Issue("placeholder", "Format", "Fill in the [placeholders]", "Replace every [X] with your real number before you apply.", 5)
        }

        // Keywords (25)
        val list = RoleKeywords.families[family] ?: RoleKeywords.families.values.first()
        val found = list.filter { hasWord(text, it) }
        val missing = list - found.toSet()
        val target = min(10, list.size)
        val kw = min(25, found.size * 25 / target)
        if (kw < 25) issues += Issue("kw", "Keywords", "Add missing keywords", "Found ${found.size} of ${list.size} keywords for $family. Add only the ones you really have.", 25 - kw)

        // Impact (25)
        val bullets = lines.filter { it.kind == Kind.BULLET }.map { it.text }
        var imp = 0
        if (bullets.isNotEmpty()) {
            val withNum = bullets.count { b -> b.any { it.isDigit() } }
            val q = min(12, (withNum.toDouble() / bullets.size / 0.5 * 12).roundToInt())
            imp += q
            if (q < 12) issues += Issue("quant", "Impact", "Add numbers to your bullets", "${bullets.size - withNum} of ${bullets.size} bullets have no number, %, or amount. Aim for half or more.", 12 - q)
            val verbOk = bullets.count { b -> val w = b.split(" ").first().lowercase().trim(',', '.'); w in strongVerbs || (w.endsWith("ed") && w.length > 4) }
            val v = min(8, (verbOk.toDouble() / bullets.size / 0.8 * 8).roundToInt())
            imp += v
            if (v < 8) issues += Issue("verbs", "Impact", "Start bullets with action verbs", "${bullets.size - verbOk} bullets don't start with a strong past-tense verb like Built, Reduced, Led.", 8 - v)
        } else {
            issues += Issue("quant", "Impact", "Add numbers to your bullets", "Add bullets with results: %, amounts, team size, time saved.", 12)
            issues += Issue("verbs", "Impact", "Start bullets with action verbs", "Begin each bullet with Built, Reduced, Led, and so on.", 8)
        }
        val weakCount = lines.count { l -> weak.any { l.text.contains(it.first, true) } }
        val w = if (weakCount == 0) 5 else max(0, 5 - weakCount)
        imp += w
        if (w < 5) {
            val canFix = fixWeak(text).second > 0
            issues += Issue("weak", "Impact", "Replace weak phrases", "$weakCount lines use phrases like \"responsible for\" or \"worked on\".", 5 - w, auto = canFix)
        }

        // Length and readability (15)
        var len = 0
        val lp = when (words) { in 350..900 -> 8; in 250..349, in 901..1100 -> 5; else -> 2 }
        len += lp
        if (lp < 8) issues += Issue("length", "Length", if (words < 350) "Resume is too short" else "Resume is too long", "$words words. Best range is 350 to 900.", 8 - lp)
        if (Regex("\\b(I|my|me)\\b").containsMatchIn(text)) issues += Issue("pronoun", "Length", "Remove I, my, me", "Write bullets without first-person words.", 4) else len += 4
        val longLines = lines.count { (it.kind == Kind.BULLET || it.kind == Kind.TEXT) && it.text.split(" ").size > 40 }
        if (longLines == 0) len += 3 else issues += Issue("long", "Length", "Shorten long lines", "$longLines lines are over 40 words. Keep bullets to one or two lines.", 3)

        val cats = listOf(Cat("Sections and headings", sec, 20), Cat("Format and contact", fmt, 15), Cat("Keywords", kw, 25), Cat("Impact and numbers", imp, 25), Cat("Length and readability", len, 15))
        return AtsResult(cats.sumOf { it.got }, cats, issues.sortedByDescending { it.points }, missing)
    }

    fun fixWeak(text: String): Pair<String, Int> {
        var n = 0
        val out = text.lines().map { raw ->
            val lead = raw.takeWhile { it in Resume.BULLETS || it.isWhitespace() }
            val body = raw.substring(lead.length)
            val hit = weak.firstOrNull { body.startsWith(it.first, ignoreCase = true) }
            if (hit == null) raw else {
                val rest = body.substring(hit.first.length).trimStart()
                val next = rest.takeWhile { it.isLetter() }.lowercase()
                if (rest.isEmpty() || next.endsWith("ing")) raw else { n++; lead + hit.second + " " + rest }
            }
        }
        return out.joinToString("\n") to n
    }

    fun addSkills(text: String, skills: List<String>): String {
        if (skills.isEmpty()) return text
        val ls = text.lines().toMutableList()
        val idx = ls.indexOfFirst { Resume.headingKey(it) == "skills" }
        val line = skills.joinToString(", ")
        if (idx >= 0) ls.add(idx + 1, line) else { ls.add(""); ls.add("SKILLS"); ls.add(line) }
        return ls.joinToString("\n")
    }
}

object JobMatch {
    private val stop = ("the and for with you your our are will have has from that this their they them who what when where into over such than then also can may must should about across able within including " +
        "work working team teams role job position company years year experience strong good great skills ability knowledge responsibilities requirements required preferred plus etc more other using use used new help make").split(" ").toSet()

    fun keywords(jd: String): List<String> =
        Regex("[A-Za-z][A-Za-z0-9+#./-]{2,}").findAll(jd.lowercase()).map { it.value.trim('.', '/', '-') }
            .filter { it.length > 2 && it !in stop }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(25).map { it.key }

    fun match(resume: String, jd: String): Pair<Int, List<String>> {
        val kws = keywords(jd)
        if (kws.isEmpty()) return 0 to emptyList()
        val r = resume.lowercase()
        val found = kws.filter { Regex("(?<![a-z0-9])" + Regex.escape(it) + "(?![a-z0-9])").containsMatchIn(r) }
        return (found.size * 100 / kws.size) to (kws - found.toSet())
    }
}
