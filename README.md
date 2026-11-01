# Resume ATS

Android app that scores your resume for ATS (applicant tracking systems), shows exactly what to fix, and exports a clean PDF or Word file.

## Features
- Import PDF, DOCX or pasted text (all processing on the phone)
- ATS score out of 100 with 5 categories, no job description needed
- Fix list with points per fix: auto-fix weak phrases, add missing keywords, edit by hand
- Optional AI bullet rewrites (your own Claude API key, stored encrypted). It never invents numbers; it adds [placeholders]
- AI full rewrite: "Polish mine" or "Build from basics" (asks a few questions, uses only your facts)
- Compare two resumes: scores, keyword gaps, what to improve, then rewrite using the gaps
- Optional job match: paste a job post to see missing keywords
- Application tracker: Applied, Interview, Offer, Rejected
- Export ATS-safe single-column PDF and Word (.docx)

## Build with GitHub
1. Create a new GitHub repo and upload this folder's contents.
2. Open the Actions tab. The "Build APK" workflow runs on every push to main.
3. Download the `resume-ats-debug-apk` artifact and install it on your phone.
4. For a signed release: add secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, then push a tag like `v1.0`.
