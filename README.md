# Saathi

An on-screen helper for parents who are new to smartphones. It speaks Hindi (Hinglish), English or Kannada, chosen first in setup. A small character sits on top of apps, reads the screen of allowed apps, points at the exact button and speaks one step at a time. **It never taps, types or swipes for her.**

Built with **Gemini (`google-genai`, through Vertex AI by default or an AI Studio key) on Google Cloud Run + Firestore** and a native Kotlin Android client (`applicationId = "in.getsaathi.app"`, `namespace = "com.saathi.app"`, Android 8.0+, zero third-party Android runtime libraries, zero client-side Gemini API keys, and a release-signed APK of about 165 KB / AAB of about 315 KB).

**Status (2026-11-27):** Cloud Run + Firestore backend deployed at `https://saathi-api-744486955078.asia-south1.run.app` (revision `saathi-api-00019-qwf`), with 3-tier routing (`--model auto`). On that revision the 89-case eval scored 100% intent, element, slot and guide-routing accuracy, 100% valid JSON and 0 safety violations, with replies at p50 1.5 s and p95 7.1 s (4.4 s and 8.6 s earlier the same day). 164 on-device unit tests and 36 backend tests pass. The code has later changes that are not deployed or measured yet: screenshot turns start on Flash-Lite, recovery and safety turns use LOW thinking, Aadhaar or PAN alone is no longer a safety word, request size limits, scope and Play Protect refusals, the web page and form rules, server-side masking of her words, and the BANKING HELP rules. On the phone: the two-try cap on automatic steps, masking of dictated names, addresses and IDs, and the bank-pause card with a voice-only question. **For a hackathon on 2026-11-27, the backend runs on a temporary AI Studio key; switch back to Vertex before the account is deleted** (see [Temporary AI Studio key](#temporary-ai-studio-key)). See [the test checklist](docs/TESTING.md) and [eval suite](eval/README.md).

## Install on a phone

There are three ways. Sending the APK as a file works on many phones, but two Android checks can get in the way.

| Way | Good for | Play Protect block | "Restricted setting" lock |
| --- | --- | --- | --- |
| Send the APK (WhatsApp, Drive, browser) | Quick tests on a few phones | Possible | Yes, cleared once |
| USB with `adb install` | Your own test phones | No | No |
| Play Store internal testing link | Sharing with testers, up to 100 | No | No |

### Sending the APK as a file

This worked on a OnePlus phone. On the Vivo test phone it was blocked. Two separate checks can stop it:

1. **Play Protect may refuse the install.** In India, Google's enhanced fraud protection blocks some apps installed from WhatsApp, a browser or a file manager when they ask for accessibility or notification access, which Saathi does. Whether it fires depends on the phone, whether Play Protect is on and which app opens the APK. If it fires, use USB or the internal testing link instead. Don't ask her to turn Play Protect off.
2. **Android 13 and later lock the accessibility switch** for any app installed from a file, even when the install worked. Clear it once: try to turn on Saathi in **Settings → Accessibility** so Android blocks it, then go to **App info → ⋮ (top right) → Allow restricted settings**, and turn Saathi on again.

**OnePlus** (OxygenOS) and some other phones don't show that ⋮ option. Connect the phone over USB and run:

```sh
adb shell appops set in.getsaathi.app ACCESS_RESTRICTED_SETTINGS allow
```

Then turn on Saathi in **Settings → Accessibility** as usual. If the switch still won't turn on, enable it directly. This replaces the list of enabled accessibility services, so check that list first and add any other services (such as TalkBack) back to the command, separated by `:`:

```sh
adb shell settings get secure enabled_accessibility_services
adb shell settings put secure enabled_accessibility_services in.getsaathi.app/com.saathi.app.SaathiAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

### Turning on Saathi in Accessibility

Every "Saathi chalu karein" button opens **Settings → Accessibility** and speaks one line. The line names the row she has to find, in the words her phone's Settings use. `AccessibilitySetupGuide` chooses it from the phone's Settings language and its maker:

| Settings language | Phone | She hears |
| --- | --- | --- |
| English | Samsung | "Installed apps", then Saathi, then "Allow" |
| English | Xiaomi, Redmi, Poco, Oppo, OnePlus, Realme, Vivo, iQOO | "Downloaded apps", then Saathi, then "Allow" |
| English | Pixel and others | "Downloaded apps" or "Saathi", then "Allow" |
| Hindi | Samsung | "इंस्टॉल किए गए ऐप्स", then "अनुमति दें" |
| Hindi | Others | "डाउनलोड किए गए ऐप्स", then "अनुमति दें" |
| Any other (Tamil, Marathi, Bengali…) | Any | Go down the list, find the Saathi icon, turn it on, confirm below |

Settings is also opened with `:settings:fragment_args_key` pointing at Saathi's service, so phones that support it highlight the Saathi row. Most phones list Saathi one page further in, under "Installed apps" or "Downloaded apps", so the highlight often doesn't appear and the spoken line is what guides her.

### USB (`adb install`)

1. Turn on Developer options on the phone. Go to **Settings → About phone** and tap **Build number** or **Software version** 7–10 times. On Vivo, it may be under **About phone → Version**.
2. In **Developer options**, turn on **USB debugging**. TalkBack has its own screen called "Developer settings", which is not this one. If you see an **Install via USB** option, turn that on too.
3. Connect the phone with a data cable and tap **Allow** on the phone.
4. Install:
   ```sh
   adb install -r dist/saathi-v0.1.1.apk
   ```
5. Open **Saathi** and work through the setup screen. Most steps show a tick when finished.

### Play Store internal testing & policy compliance

Upload `app/build/outputs/bundle/release/app-release.aab` to an **internal testing** track in Play Console and add the testers' Google accounts. They install from the opt-in link like any Play app, so neither check above applies.
- **AccessibilityService Prominent Disclosure (`Lines.Disclosure`, Step 2 in `MainActivity`):** Displays an uncollapsed disclosure card in Hindi, English or Kannada before Android Accessibility Settings ever opens, with explicit **Agree & Open Settings** (`accessibilityDisclosureAccepted = true`) and **Decline (Use voice only)** (`accessibilityDeclined = true`) buttons. `android:isAccessibilityTool` is omitted from `accessibility_service_config.xml` because Saathi is a general senior/digital-literacy helper rather than an assistive disability tool.
- **Exact Alarms (`SCHEDULE_EXACT_ALARM`):** Uses `SCHEDULE_EXACT_ALARM` (no restricted `USE_EXACT_ALARM`), with `AlarmManager.canScheduleExactAlarms()` and a graceful `setAndAllowWhileIdle()` fallback in `ReminderScheduler`.
- **Battery Optimization (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`):** Directs the user to the system battery optimization list without requesting the restricted `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest permission.

## Build (Android Client & Cloud Run Backend)

### 1. Android APK & App Bundle (`.aab`)
1. Copy `secrets.properties.template` to `secrets.properties` and set:
   ```properties
   BACKEND_URL=https://saathi-api-744486955078.asia-south1.run.app
   CLIENT_SECRET=<your-shared-client-secret>
   EVAL_SECRET=<eval-runner-only-secret>
   KEYSTORE_FILE=upload-keystore.jks
   KEYSTORE_PASSWORD=<your-keystore-password>
   KEY_ALIAS=saathi-upload
   KEY_PASSWORD=<your-key-password>
   ```
2. Build and run unit tests:
   ```sh
   ./gradlew test assembleRelease bundleRelease
   ```

The release-signed APK is written to `app/build/outputs/apk/release/app-release.apk` (copy it to `dist/saathi-v0.1.1.apk` to share), and the Play Store App Bundle signed with `upload-keystore.jks` is written to `app/build/outputs/bundle/release/app-release.aab` (`dist/saathi-v0.1.1.aab`).

`app/build.gradle.kts` reads `BACKEND_URL` and `CLIENT_SECRET` from `secrets.properties` into `BuildConfig.BACKEND_URL` and `BuildConfig.CLIENT_SECRET`. **No Gemini API key is ever embedded in the Android app or entered by the user.** `EVAL_SECRET` is read only by `eval/run_eval.py`, never by the app.

The client secret is built into the APK, so anyone with the file can extract it. It is not real protection, only a first gate. The limits below cap what a leaked secret can cost. Play Integrity is planned before public launch. The secrets were last rotated on 2026-11-22. To rotate without breaking installed copies, see `SAATHI_CLIENT_SECRET_PREV` below.

### 2. Cloud Run + Vertex AI + Firestore Backend (`backend/`)
The backend proxy in `backend/main.py` runs on Google Cloud Run. By default it calls Gemini through Vertex AI with the service's own credentials (`genai.Client(vertexai=True, project=..., location="global")`). If `GEMINI_API_KEY` is set on the service, it uses that AI Studio key instead:

```sh
cd backend
gcloud run deploy saathi-api \
  --source . \
  --project=saathi-514208 \
  --region=asia-south1 \
  --max-instances=2
```

Env vars on the service: `SAATHI_CLIENT_SECRET` (same as `CLIENT_SECRET`), `EVAL_SECRET`, `GLOBAL_DAILY_CAP` (2000) and `IP_BURST_PER_MINUTE` (30). They persist across deploys. Optional:
- `SAATHI_CLIENT_SECRET_PREV`: the previous client secret, still accepted during a rotation.
- `GEMINI_API_KEY`: use an AI Studio key instead of Vertex AI.
- `TIER2_MODEL` and `TIER3_MODEL`: override `gemini-3.5-flash-lite` and `gemini-4-hydrogen`.
- `FORCE_HYDROGEN=true`: send every turn to Tier 3.

To rotate the client secret without breaking installed copies: set `SAATHI_CLIENT_SECRET_PREV` to the old value and `SAATHI_CLIENT_SECRET` to the new one with `--update-env-vars`, put the new one in `secrets.properties`, rebuild and ship the app, and remove `SAATHI_CLIENT_SECRET_PREV` once phones have updated.

#### Temporary AI Studio key

On 2026-11-27 the service was switched to a hackathon AI Studio key for about two days. The key lives only on the Cloud Run service, never in the repo or the app. The account is deleted the day after the hackathon, and the key with it, so switch back before then:

```sh
gcloud run services update saathi-api --project=saathi-514208 --region=asia-south1 \
  --remove-env-vars=GEMINI_API_KEY,GLOBAL_DAILY_CAP
```

Then run `/usr/bin/python3 -I eval/run_eval.py --limit 5 --model auto` to check that Vertex answers. If you set `TIER2_MODEL` or `TIER3_MODEL` for the hackathon, remove them in the same command.

Keep `--max-instances=2`. The per-IP burst limit is counted in each instance's memory, so the real limit is at most twice the setting.

Backend tests: `cd backend && python -m unittest test_main` (needs the packages in `requirements.txt`, plus `httpx` for FastAPI's test client: `pip install httpx`). Oversized requests are refused with `HTTP 422` before any check: `prompt` over 12,000 characters, `jpeg_base64` over 350,000, `model` over 64, and on `/v1/report` `last_utterance` or `last_reply` over 500 and `current_app`, `target_label` or `reason` over 128.

What the backend enforces on every request:
- **Shared Secret Header Guard:** Verifies `X-Saathi-Client-Secret` against `SAATHI_CLIENT_SECRET`, and `SAATHI_CLIENT_SECRET_PREV` when set, using constant-time `hmac.compare_digest` against both.
- **Eval runner secret:** a request with a valid `X-Saathi-Eval-Secret` (`EVAL_SECRET`) skips the per-IP and per-device limits but still counts toward the global cap. No `device_id` is exempt; `eval_bot` was removed as a bypass.
- **Per-IP burst limit:** 30 requests a minute per IP, rejected with `HTTP 429`. It is per minute, not per day, because Jio and Airtel put many phones behind one IP. **Known gap:** it reads the *first* `X-Forwarded-For` address, which the caller can set, so a caller with the secret can bypass it (confirmed live on 2026-11-23). The fix, reading the address Cloud Run adds, is not done yet. The global daily cap still holds.
- **Global daily cap:** `global_usage/{YYYY-MM-DD}` in Firestore counts every request from every device. Above 2,000 a day, all requests get `HTTP 429` until midnight UTC.
- **One Firestore read per turn:** the device and global counters are read together in one `get_all`, a full one is refused with `HTTP 429`, and both are then increased in one batched write in the background while Gemini answers. Requests arriving at the same moment can go a few over a limit.
- **No prompt or reply text in logs:** `saathi_audit_trace` and `saathi_guide_step_telemetry` carry only structured fields (`package_name`, `model_used`/`model_version`, latency, `used_screenshot`, `is_reprompt`, token counts, `prompt_hash`). A flagged output's words are stored only in its `flagged_outputs` document, not in logs.
- **Firestore Daily Quota Guard:** Counts `daily_usage/{device_id}_{YYYY-MM-DD}.count` (where `device_id` is an anonymous SHA-256 hash of `ANDROID_ID`) and rejects requests above **200 turns/day** with `HTTP 429`.
- **Prompt order and thinking:**
  - The static `SYSTEM_PROMPT` always goes inline as `system_instruction`, before any turn data, so Gemini's implicit prefix caching can match it. Explicit `cachedContents` was removed on 2026-11-27: it returned 404 on the AI Studio key, Gemini 4 doesn't allow it, and the prompt is near the minimum cache size.
  - `gemini-4-hydrogen` always runs at `thinking_level="LOW"`, safety turns included. MEDIUM took 9–10 s in production, past the app's 6-second timeout. `gemini-3.5-flash-lite` runs at `"MINIMAL"`.
  - `max_output_tokens`, which includes thinking: 1024 at MINIMAL (Kannada and Devanagari need more tokens per word), 2048 on Hydrogen.
- **3-Tier Edge-to-Cloud Triage Routing (`model="auto"` + `FORCE_HYDROGEN` kill-switch):**
  - **Tier 1 (On-Device Edge, `<50 ms`, ₹0 cost):**
    - `core/LocalCommandParser.kt` deterministically handles unambiguous Romanized Hindi and Devanagari list reads (`list_read`), list clears (`list_clear`), explicit-time alarms (`alarm_set`), and explicit-time reminders (`reminder_set`) offline.
    - `Assistant.tryLocalDomMatch()` and `core/Guides.kt` answer simple UI lookups (`where is`, `find`, `click`, `tap`, `kahan hai`, `dabao`) and prepared WhatsApp flows directly on the phone with zero network calls.
    - OTP, PIN and ID warnings are spoken on the phone and nothing is sent (see [Privacy and safety](#privacy-and-safety-enforced-in-code)).
  - **Tier 2 (`gemini-3.5-flash-lite`, `"MINIMAL"`):** routine screen steps (with or without a screenshot), live-information questions with Google Search, and text turns (lists, reminders, alarms, chat, clarifications). Invalid JSON, `confidence < 0.75` or no clear target on a screen step escalates once to Tier 3 at LOW.
  - **Tier 3 (`gemini-4-hydrogen`, `"LOW"`):** safety turns, checked first (banking apps, unreadable or blocked screens, a `bank_text_help` goal, scam words in her words, or a high-signal scam phrase on the screen), wrong-turn recovery, her repeat questions ("kahan", "nahi dikh raha"), WhatsApp privacy modes, and every turn when `FORCE_HYDROGEN=true`.
  - **Safety check, split in two:** her words are checked for OTP, PIN, CVV, password, remote-access apps, collect requests, KYC, bank names, `[number]` and similar. Everyday words (share, link, code, unknown, remote, Aadhaar, PAN) count only inside a scam phrase, such as "link aaya" or "aadhar otp maang raha". The screen is checked only for high-signal phrases (AnyDesk, "Enter UPI PIN", "Requesting [number]", "collect request", "KYC expired", "account blocked"), never for buttons such as Share or masked prices, which every shopping app shows.
- **Live information with Google Search:** a question about prices, weather, news, scores, petrol, gold or holidays, with no screen, gets the Google Search tool (`LIVE_INFO_REGEX`). The audit log records `used_search` and `search_query_count`.
- **Structured Audit Logging & One-Tap Flagging (`/v1/ask` & `/v1/report`):**
  - Every `/v1/ask` turn generates a UUID4 `trace_id`, returns it to the app, and emits a structured `saathi_audit_trace` JSON event to Cloud Logging for replay.
  - Tapping **"Galat hai / Report"** on Saathi's overlay (or saying *"galat tha"*) sends `trace_id` and masked turn context to `POST /v1/report`, writing a review document to Firestore `flagged_outputs/{trace_id}` (`owner: "bhavyta"`, `status: "needs_review"`) and emitting a `WARNING` `saathi_output_flagged` event to Cloud Logging.

## Languages

Setup starts by asking her language: **हिन्दी, English or ಕನ್ನಡ**. Each is written in its own script, and the phone's own language is suggested first. Telugu, Tamil, Marathi, Bengali, Gujarati, Malayalam and Punjabi are listed as "coming soon" and can't be chosen. Saathi never speaks half-Hindi to someone who asked for another language. "🌐 Change language" on the setup screen brings the choice back.

The choice (`core/Language.kt`, `Prefs.language`) sets all of these:
- **Listening and the speech pack:** the recognizer, and the automatic pack download, use `hi-IN`, `en-IN` or `kn-IN`.
- **The voice:** the speech engine voice for that language.
- **Fixed lines and buttons:** every line in `core/Lines.kt`, the panel buttons, notifications and the setup steps.
- **WhatsApp guides:** every step, and where a button is ("neeche right mein", "at the bottom right", "ಕೆಳಗೆ ಬಲಭಾಗದಲ್ಲಿ").
- **The Accessibility setup line:** labels still follow the phone's Settings language.
- **Words she says on the phone:** yes/no, stop, again, slower, wrong, "done", and the guide router.
- **Privacy:** Kannada number words and digits (೦–೯) are masked, and OTP, PIN, password and code in Kannada trigger the warning.
- **AI replies:** the prompt has a `Her language:` line and a LANGUAGE section in the system instruction.

**Kannada is a draft.** It must be checked by a native speaker before wide use, above all the OTP, PIN and scam warnings. `LanguageTest` checks that every line exists in every ready language, in its own script, under 15 words, and never says "tap" or "click".

To add a language: give it `ready = true` in `Language`, add its version to `Lang.pick` and every line in `Lines`, then its guide steps, places, words and privacy words. Add eval cases tagged `language`, get a native speaker to check it, then run the phone test.

## Questions and live information

Saathi also answers everyday questions, within rules in the prompt's QUESTIONS SHE ASKS section:
- **What a word or button means, or a simple fact:** one short `chat` sentence.
- **How or where to do something on the phone** ("photo kaise bhejte hain", "order kahan dikhega"): never a chat answer. Saathi guides step by step.
- **Live information** (prices, weather, news): answered only from a Google Search made in that turn, starting "Google ke hisaab se". Without a search, Saathi says it can't check right now, never a guess. In a test on 2026-11-27 these took 2.2–2.4 s on Flash-Lite against about 7 s on Hydrogen, with the same answers.
- **Health:** general information only. Never a dose, a diagnosis or advice to change a medicine: "Doctor se poochiye".
- **Money:** no advice on investing, loans, shares, banks, schemes or policies.
- **Safety rules come first.**

Eval cases `info-01` to `info-03` check the petrol price, a paracetamol dose and gold investing.

### Banking questions

The prompt's BANKING HELP section, added on 2026-11-27:
- **No searches for banking.** Saathi never searches and never suggests Google or YouTube for a banking, UPI or card problem, because fake customer-care numbers hide in search ads and video comments. The backend also never turns on Google Search for a safety turn, even one that asks about a rate.
- **No screen sharing,** and no other screen-reading app, inside a banking or UPI app.
- **A general how-to** ("GPay mein balance kaise dekhein?") gets 1–2 calm sentences, without asking for the screen.
- **Her own account problem** (an error, a blocked card, a failed payment, or steps she can't follow): ask someone she trusts at home, visit the branch, or call only the number printed on the back of her ATM card or in her passbook. Never search for a customer care number.

## Web pages and forms

Chrome and apps that show a website (such as myaadhaar.uidai.gov.in) get three extra rules in GUIDE STEPS:
- **A tutorial or info popup** ("How it works", "Using your document"): buttons drawn in its pictures aren't real, so Saathi points at the close (X) button.
- **A 404 or error page:** go back, or open the site's home page, said once. Never "refresh" again and again.
- **A form with several boxes:** Saathi never points at the boxes. When the form first appears she hears *"Aap yeh form khud bhar lijiye, agar kisi box mein dikkat aaye toh mujhe dabakar pooch lena."* If she asks, Saathi explains one box per reply, reminds her to type it herself and not say private details aloud, and waits for "ho gaya".

A web page's text often keeps changing by itself, which used to start a new automatic step on every change. Since 2026-11-27 an automatic step gets two tries on the same page (`core/AutoStall`). After that Saathi says once *"Agla button saaf nahi dikh raha. Khud aage badhiye, ya madad ke liye mujhe dabaiye."* and waits. A step with no button on purpose, such as the form line, also waits, so her typing doesn't start new steps. Steps start again when she speaks, changes app, or reaches a clearly new page (less than 30% of the text the same).

## How it works

1. She taps the face and asks out loud, or types.
2. Her words go to Gemini **without the screen**. Lists, reminders, chat and live questions never send the screen. A first question about how or where to do something on the phone ("GPay mein bank kaise jodun?") goes with the screen straight away, after the same payment, secret and scam checks, and never in WhatsApp, so Saathi points in one call instead of two (`TurnPolicy.isScreenHelpQuestion`).
3. If she needs help on the phone, Saathi reads the allowed app into a numbered list of elements and masks private values. In WhatsApp it asks first, and then sends only known buttons (see [WhatsApp](#whatsapp-ask-first)).
4. Each request carries a small state object (her goal, the last step, the screen's app and whether it changed, a pending question, the last thing saved) and the last 4 turns of masked chat. Gemini replies with one JSON object: an intent, a confidence, the line to say, the element number to point at, and a `state_patch` with only the fields that changed.
5. The app checks the reply. An invalid reply, or an element number that isn't on the screen list, gets one retry with the reason. A list or reminder change with confidence below 0.6 is not saved. Saathi asks "Maine suna: … Sahi hai?" first.
6. The character moves beside the target, a ring and arrow highlight it, and the line is spoken briskly at `1.08f` (1 short sentence, max 8–10 Hindi words). The panel shrinks to a slim strip (and moves to the top whenever the target is in the lower 60% of the screen, or can be dragged up and down by hand) so the app stays visible. Tapping the slim strip expands it and starts listening immediately.
7. She does the action herself. When the screen changes (or hands off across apps, e.g., Play Store → Flipkart), Saathi waits for the new screen to settle (650 ms after her own tap, 750 ms otherwise, and 500 ms more while a spinner or skeleton shows), shows `"Dekh rahi hoon..."` on the banner, reads the screen again, and automatically gives the next step without waiting for her to say *"Ab?"*. Calls are at least 800 ms apart. A `6-second` timeout watchdog (8 seconds for a live-information question with no screen) automatically retries once on slow network/LLM calls. If the retry also fails on her own question she hears *"Ek second, main phir se dekh rahi hoon"*; on an automatic step Saathi stops trying that page and waits for her (see [Web pages and forms](#web-pages-and-forms)). Completing a sub-goal (such as tapping "Install" or "Open") transitions the overlay into an active ready/listening state instead of closing it.

| File | Job |
| --- | --- |
| `SaathiAccessibilityService` | Read-only access and overlay host. `allowedRoot()` is the only way to reach screen content. Stops pending setup TTS and speaks *"Saathi chalu ho gaya hai"* on connect, clearing the bank-pause flag and notification; steps out (`disableSelf()`) when a strict banking app opens, after speaking and posting the bank-safety notification |
| `Assistant` | The guide loop (650/750 ms screen-settle wait, `"Dekh rahi hoon..."` in-flight state, `6s` timeout watchdog (8 s for live questions) + auto-retry, the on-phone OTP and ID warnings, the screen on a first how/where question, cross-app handoff survival, the two-try cap on automatic steps, and `1.08f` guidance speech rate). Runs both as a floating overlay host and in-app when Accessibility is disabled, including voice-only banking help from the bank-pause card (`startBankVoiceHelp`) |
| `core/AutoStall` | Counts automatic tries on one page: two at most, then Saathi waits for her words, a new app or a clearly new page |
| `core/TurnPolicy` | When a screenshot is worth sending, when a first question carries the screen, the reply timeouts and the settle waits. Plain rules with unit tests |
| `core/LocalCommandParser` | Tier 1 on-device deterministic parser for basic `list_read`, `list_clear`, `alarm_set`, and `reminder_set` commands (`<50 ms`, offline) |
| `core/SessionState` | The state sent with each request, and the rules for merging the model's patch |
| `core/AiReply` | The reply contract and its validation |
| `EvalLog` | The optional on-device log of turns for building test cases |
| `ScreenReader` | Screen to compact numbered list (`MAX_ELEMENTS = 48`), with masking. Asks for a screenshot only when the list is too thin (see the privacy table). Skips invisible views, tiny/zero-bound dividers (`<= 4px`), decorative images/glyphs, and unlabeled wrapper containers that enclose child buttons |
| `core/PrivacyFilter` | Rules for secrets, payments, scams and number masking, and `isSharingSensitiveData` for her reading out an ID or credential |
| `core/PiiMasker` | Masks IDs (`[id]`), dictated names (`[name]`) and addresses (`[address]`) in her words. `scrub_pii` in `backend/main.py` runs the same rules again; change both together |
| `core/Allowlist` | `STRICT_BANK_PACKAGES` (auto `disableSelf()`, including Google Pay, PhonePe, Paytm, YONO SBI, HDFC, ICICI, Axis, Kotak, bob World, Central Bank, PNB One), `FINANCIAL_AND_SECURE_PACKAGES` & `NEVER` blocklist, allow-by-default for all ordinary consumer apps (including Gemini `com.google.android.apps.bard` / `com.google.android.googlequicksearchbox`) with dynamic app names resolved from Android's `PackageManager`, and `ASK_FIRST` consent for WhatsApp |
| `core/ScreenAccess` | Consent for WhatsApp: asked, allowed or refused, and when access ends |
| `core/ControlsOnly` | WhatsApp's safe button labels, what each looks like, and the reader that hides everything else |
| `core/Guides` | Prepared WhatsApp guides as data, the router that picks one, and the runner that moves through the steps on the phone |
| `LabelProbe`, `core/ProbeRules` | Test phones only: writes WhatsApp's real button labels to a file for checking |
| `core/ListenPolicy` | When the mic reopens by itself (the watchdog), and when her words hold back the guide moving on |
| `core/Language` | The languages: Hindi, English and Kannada ready, others "coming soon"; `Lang.current` and `Lang.pick` |
| `core/Lines` | Fixed lines, buttons and setup text in each ready language, which work with no AI or internet |
| `GeminiClient` | Calls the Cloud Run `/v1/ask` (`model="auto"`, 6 s read timeout, 8 s for a live question with no screen) and `/v1/report` endpoints and parses `trace_id`. Connections are not disconnected after a reply, so the warm TLS socket is reused; only a failed or cancelled request disconnects. The system instruction and reply schema live only in `backend/main.py`, which the eval reads too |
| `backend/main.py` | FastAPI Cloud Run proxy: `scrub_pii` on her words and on reports, risk-based Tier 2 (`gemini-3.5-flash-lite`) → Tier 3 (`gemini-4-hydrogen`) router, Google Search for live questions, `FORCE_HYDROGEN` kill-switch, eval secret, previous-secret rotation, per-IP burst limit, Firestore 200/day per-device quota and 2,000/day global cap in one read, structured audit logging without prompt text (`saathi_audit_trace`), and `/v1/report` (`flagged_outputs/{trace_id}`). Tests in `backend/test_main.py` |
| `VoiceIO` | Pause-tolerant on-device speech recognition in her language (`3500ms` complete silence, partial buffer accumulation, `300ms` post-TTS delay before the mic reopens, a guard for speech engines that never report the end of a line), `1.08f` guidance speech rate, `stopAllSpeaking()`, speech-pack check and download for her language, and text-to-speech (`speakAndShutdown`) |
| `OverlayController`, `CharacterViews` | The face, vertically draggable panel/strip, 1-tap Re-Enable Accessibility card, ring and arrow |
| `PhoneHelp`, `SaathiMediaListener` | Which app is playing sound, and the navigation mode |
| `ListsStore`, `ReminderScheduler` | Lists and reminders, stored only on the phone |
| `AccessibilitySetupGuide` | The spoken Accessibility setup line for the phone's Settings language and maker, and the Settings intent that asks the phone to highlight Saathi's row |
| `MainActivity`, `ListsActivity` | Language choice first, then the voice-guided 3-step visual onboarding stepper (speaks the `AccessibilitySetupGuide` line with `QUEUE_FLUSH` on tap), collapsible sideload restricted-settings guide, in-app voice assistant host when Accessibility is disabled, the bank-pause card (safety note, voice-only question, turn back on), and the lists and reminders screen |

## Privacy and safety, enforced in code

| Rule | Where it is enforced |
| --- | --- |
| Never acts on other apps | `canPerformGestures` is absent, and the `auditReadOnly` Gradle task **fails every build** if action APIs appear in the source |
| Automatic Bank Mode (`disableSelf()`) | When any strict banking app in `Allowlist.STRICT_BANK_PACKAGES` (`com.sbi.lotusintouch`, `com.sbi.SBIFreedomPlus`, `com.snapwork.hdfc`, `com.csam.icici.bank.imobile`, `com.axis.mobile`, `com.msf.kbank.mobile`, `com.bankofbaroda.mconnect`, `com.infrasofttech.CentralBank`, `com.fss.pnbpsp`) enters the foreground, `SaathiAccessibilityService` removes all overlays and stops reading, saves `pausedForBanking = true`, and posts a silent "Bank suraksha ke liye Saathi ruka hai" notification. It then speaks, in her language, *"Bank ki suraksha ke liye main abhi band ho rahi hoon. Kuch poochhna ho ya kaam ke baad mujhe wapas chalu karna ho, toh Saathi app ya upar notification kholein."* `disableSelf()` runs only after the speech finishes or fails, or after 4.5 s at most. `isDisablingForBank` stops repeat events from speaking twice. The notification opens the bank-pause card in `MainActivity`: a note never to search Google or YouTube for a customer care number, **"Bina Screen Dekhe Poochhein"** (a voice-only banking question, with Accessibility still off, sent as `bank_text_help`), and **"Bank Kaam Ho Gaya — Saathi Wapas Chalu Karein"** (opens Accessibility settings). Turning Saathi back on clears the flag and the notification |
| Financial & UPI Guardrail | On other financial/UPI/credential apps (`com.phonepe.app`, `net.one97.paytm`, `com.cred.club`, `in.org.npci.upiapp`, etc.), `SaathiAccessibilityService` immediately pauses and hides the AI overlay and never inspects screen contents. Google Pay (`com.google.android.apps.nbu.paisa.user`) is allowed for guidance and hides overlays only when a `FLAG_SECURE` / PIN screen is active |
| Respects `FLAG_SECURE` windows | `SaathiAccessibilityService.capture()` checks for `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` (`FLAG_SECURE`): in Google Pay or financial apps it immediately calls `assistant.onFlagSecureDetected()` to pause the overlay, while in ordinary shopping/content apps (e.g. Flipkart) it skips the screenshot and falls back cleanly to text DOM so the floating helper stays visible |
| Asks before reading WhatsApp | `ScreenAccess`: nothing in WhatsApp is read before she taps or says Haan. Access ends when she leaves WhatsApp, the goal ends, she says stop or closes the panel, or after 2 minutes idle. The idle limit is part of the read check itself |
| WhatsApp sends buttons only | `ControlsOnly`: a label leaves the phone only if it exactly matches a fixed list of WhatsApp button names. Names, messages, previews, times and photo descriptions become `[hidden]`, text boxes say only whether they have text, and there is never a title or a screenshot |
| Hides on secrets | `PrivacyFilter.hasSecret`: password fields, labelled fields, prompts such as "Enter UPI PIN", and custom number pads. In WhatsApp, a code inside a chat or message row (such as a bank's "123456 is your verification code") is someone's message, not a prompt, so it doesn't hide Saathi. WhatsApp's own PIN screens still do |
| Hides on system prompts | `Assistant` hides whenever a system window has focus, and recognises credential screens by class name, which covers Android's PIN and fingerprint prompt |
| Stops on payment screens | `PrivacyFilter.isPaymentScreen`: no reading, conversation only |
| Masks private values | Amounts, phones, account numbers, emails and UPI IDs, before anything is sent |
| Filters her speech | Runs of 4 or more digits become `[number]`, with spaces, hyphens or dots between them ("5 6 0 0 3 4"), and in Devanagari or Kannada digits. So do 3 or more number words in a row, in English, Hindi or Kannada ("nine eight four", "ek do teen", "ಒಂದು ಎರಡು ಮೂರು"). PAN, voter ID, passport and other letter-and-digit IDs become `[id]` |
| Masks what she dictates into a form | `PiiMasker`: a name after "mera naam … hai", "my name is", "naam … likho" or "c/o" becomes `[name]`, and an address with a house number ("flat 204 3rd cross Indiranagar", "house no 12 MG road", "mera address …") becomes `[address]`. How-to questions stay as said ("aadhar mei address kaise change krte h", "naam galat hai kaise theek karein", "pin code kahan daalna hai"). The prompt tells Saathi never to ask her to repeat a masked detail, but to say which box to type it into |
| Masks her words again on the server | `scrub_pii` in `backend/main.py`, the same rules as the phone, runs on HER WORDS and the history lines before routing, Gemini or logs, and on `/v1/report` before Firestore. CONTEXT, STATE and SCREEN are left alone, because they hold times and counts |
| Warns when she reads out an ID or credential | `PrivacyFilter.isSharingSensitiveData`, on the phone in under 50 ms, with nothing sent: Aadhaar, PAN, account or card numbers, CVV, password, PIN or OTP, spoken with a number, or as hers and being dictated ("mera password hai…", "mera account number likho"). How-to questions ("Aadhar mei address kaise change krte h", "password reset kaise karein", "mera pin bhool gayi") go on normally. She hears "Ruko! Aadhaar, PIN ya password jaisi baatein mujhe ya kisi ko mat batana." |
| Sends a screenshot only when needed | `TurnPolicy.needsScreenshot`: only when a screen has fewer than 4 labelled buttons, more than 35% unnamed buttons, or a WebView. DOM-only apps (Settings, Play Store, WhatsApp, Contacts, dialer, launchers) never send one routinely. Her repeat question or a low-confidence reply still adds a picture. Never in WhatsApp |
| Answers questions within limits | The prompt's QUESTIONS SHE ASKS rules: live facts only from a search, no doses or diagnoses, no money advice (see [Questions and live information](#questions-and-live-information)) |
| Warns about scams | Remote-access apps, collect requests and requests to share a code |
| Ignores instructions on screen | Screen text is marked as data for the model, and a live test ignored a fake instruction to ask for her UPI PIN. The model has no way to act anyway |
| Her own words still go to Gemini | Everything above is about the screen. What she says or types is sent masked, so if she says "Rahul ko message karna hai", that name is sent. Only a name she introduces ("mera naam …") or an address is masked. Steps answered on the phone send nothing |
| Limits what is kept | Each request asks Google not to store it (`store: false`). Screen data is never logged or saved to disk. Her words are saved only if the testing log is switched on in setup, and then only masked |
| Keeps the state free of screen text | The state is built after masking. Its one screen field, the masked title, is sent only with turns that send the screen list anyway, and is left out of the testing log |

### Which apps Saathi can read

| Status | Apps |
| --- | --- |
| Allowed by default | All ordinary consumer apps (Flipkart, Amazon, Meesho, JioMart, Swiggy, Zomato, Zepto, Blinkit, Settings, Home and recent apps, YouTube, Play Store, Clock, Calculator, Google Maps, Chrome, Snapchat, Instagram, ChatGPT, Gemini `com.google.android.apps.bard` / `com.google.android.googlequicksearchbox`, etc.). No hardcoded consumer-app list is maintained; display names are resolved from Android's `PackageManager` |
| Found on each phone | That phone's own preinstalled Clock, Calculator and home screen apps, because makers such as Vivo name them differently (`isLauncher` and `hasOrdinaryNumberPad` use these). Only system apps count, and assistant, messaging and banking apps are never added. `adb logcat -d \| grep SaathiAllowlist` shows what was found |
| Ask first | WhatsApp (`com.whatsapp`). Read only during a help session, after she says Haan, and only its buttons. See [WhatsApp](#whatsapp-ask-first) |
| Strict Bank Mode (`Allowlist.STRICT_BANK_PACKAGES`, auto `disableSelf()`) | Google Pay (`com.google.android.apps.nbu.paisa.user`), PhonePe (`com.phonepe.app`), Paytm (`net.one97.paytm`), YONO SBI (`com.sbi.lotusintouch`), YONO Lite (`com.sbi.SBIFreedomPlus`), HDFC Bank (`com.snapwork.hdfc`), iMobile Pay (`com.csam.icici.bank.imobile`), Axis Mobile (`com.axis.mobile`), Kotak811 (`com.msf.kbank.mobile`), bob World (`com.bankofbaroda.mconnect`), Central Bank (`com.infrasofttech.CentralBank`), PNB One (`com.fss.pnbpsp`), plus proactive step-out on any merchant checkout / payment-options screen (`PrivacyFilter.isCheckoutPaymentOptionsScreen`) |
| Never allowed (`Allowlist.FINANCIAL_AND_SECURE_PACKAGES` & `Allowlist.NEVER`) | All `STRICT_BANK_PACKAGES`, CRED (`com.cred.club`), BHIM (`in.org.npci.upiapp`), Mobikwik, Freecharge, WhatsApp Business (`com.whatsapp.w4b`), Messages/SMS (`messaging`, `mms`, `truecaller`), Gmail (`android.gm`), password managers/authenticators (`bitwarden`, `1password`, `lastpass`, `keepass`, `authenticator`), system UI / notification shade (`systemui`), and WhatsApp's own payment screens |

In apps she cannot read, the face shrinks to a small dot, and Saathi helps by conversation only. The same happens on payment screens.

### WhatsApp (ask first)

1. When her request needs the WhatsApp screen, Saathi asks: "WhatsApp ki screen dekhun? Sirf buttons dekhungi, messages nahi." She answers with the Haan or Nahi button, or by voice. Nahi means help by conversation only for the rest of that task.
2. After Haan, `ScreenAccess` grants access for that session only, and the panel shows "WhatsApp: sirf buttons dekh rahi hoon".
3. The privacy checks run first, as in every app: secret fields, PIN prompts and payment screens hide Saathi. WhatsApp's payment screens are also recognised by their screen class name.
4. Only labels on the fixed list leave the phone. A button is described by what it looks like, such as "hara gol button, jisme chat ka nishaan hai", and Saathi says that instead of the English label. Saathi never points at a hidden element. It says in words where to tap.
5. The labels in `ControlsOnly` are a first guess, because WhatsApp's real labels vary. Check them on a test phone with the label probe (see [the test checklist](docs/TESTING.md)).

#### Prepared guides

Three WhatsApp tasks run on the phone from data in `core/Guides.kt`: **saving a contact**, **messaging a number that isn't saved** and **editing a sent message**.

- **Picking a guide.** Her words are matched on the phone, in Roman and Devanagari, when the task is clearly about WhatsApp. Otherwise Gemini can name the guide's id, and the phone runs it from there.
- **What each step holds:** a Roman line, a Devanagari line, the safe label to point at, the screen it is done on and the screen that should come next. Screens are recognised only by safe labels and by whether a text box has text.
- **When Gemini is called.** If the screen fits the current step, or a later one she has already reached, Saathi points and speaks with no model call. Gemini is asked only when the screen fits no expected step, and the guide takes over again once a screen fits. The testing log records each turn as `local` or `gemini`.
- **After Nahi**, the same steps are spoken one at a time, and "ho gaya" moves to the next one.
- **Phone numbers:** if she says a number during a guide, it is masked as always, and Saathi asks her to type it herself.
- **Editing:** if Edit is missing from a sent message's menu, Saathi says WhatsApp allows editing only for a short time.

The screens in the guides are built on the guessed labels, so check them along with the labels.

A chat or contact whose whole text is exactly one of the button names (for example a contact saved as "Info") would be sent as that word. A safe label borrowed from inside a row is used only when nothing else is written in that row, so a message bubble that also shows a time stays hidden.

## Where this differs from the PRD

The [PRD](../Saathi%20—%20PRD%20On-screen%20Hindi%20assistant%20for%20parents.md) has been updated to match these decisions.

- **Sound finder needs Notification access.** Android does not tell ordinary apps which app is playing audio. The only public route is the media session list, which requires a notification listener, so `SaathiMediaListener` exists and reads no notifications. It is an optional setup step, and it asks Android to start new installs on Android 12+ with only ongoing notifications on, so conversations (SMS and chats) start off. Someone can turn more on in Android's settings at their own risk, and Saathi gains nothing from it. Sound with no media session, such as games and ads, cannot be named.
- **A small on-device check runs while she is not in a session.** To hide on a PIN screen even with no session active, Saathi checks allowed apps for secret fields on the phone. It keeps nothing and sends nothing. Screen content is sent only during a session she started.
- **Blocklist instead of XML `packageNames` filter.** Setting `android:packageNames` in `accessibility_service_config.xml` prevented Android from notifying Saathi when the user switched from the home screen into apps like WhatsApp, Zepto, Blinkit or Snapchat, leaving the home-screen pointer stuck. Events are now delivered across apps so foreground changes are always detected, while `Allowlist.isAllowed` and `allowedRoot()` strictly block `Allowlist.NEVER` apps (banking, UPI, SMS, email, password managers, system UI) and require session consent for WhatsApp.
- **Default model is `gemini-3.6-flash`, not `gemini-flash-latest`.** In testing on 2026-11-06 the alias and `gemini-3.8-flash` timed out or returned 503 on every call, while `gemini-3.6-flash` answered correctly in under 2 seconds with minimal thinking.
- **The mic can reopen once after an answer.** Tapping before every sentence made conversation tiring. After an answer, it reopens for one follow-up with the green ring showing, and closes if she stays silent. It never reopens after a guide step, on a secret screen or after typing, and setup can turn this off. There is still no wake word and no always-on listening.
- **A small state replaces the chat history.** Each request sends the state and only the last 4 turns. A goal ends after 10 minutes idle, when she says "rehne do" or taps ×, when it is done, or after 2 minutes in an unrelated app. The last saved item and the chat are kept for 30 minutes, in memory only, so "aur Maggi bhi" still works later.
- **Optional testing log.** Off by default. When switched on in setup, each turn is saved on the phone: her masked words, the state, the reply, validation and timing, with no screen text. A "Galat tha" button marks a wrong reply, and the marked turns are pulled over USB.
- **Ordinary apps (including Clock, Calculator, Google Maps, Zepto, Blinkit and Snapchat) are allowed.** In Clock and Calculator a number pad alone does not hide Saathi, while every other secret check still applies.
- **Alarms are taught, not set.** Saathi's reminders are its own and are separate from the Clock app. If she asks for an alarm, Saathi guides her through Clock. A code check stops an alarm request turning into a reminder.
- **Two versions of every Hindi line.** Hindi text-to-speech mispronounces Roman Hinglish, so Gemini returns Roman text for the bubble and Devanagari for the voice. In English and Kannada the bubble shows exactly the spoken line.
- **Three languages, not Hindi only.** Setup asks her language first: Hindi, English or Kannada. See [Languages](#languages).
- **WhatsApp is "ask first", not "never allowed".** The PRD kept WhatsApp blocked. It is now readable during one help session after she agrees, and only its buttons, because the most common questions are about WhatsApp and its unlabelled icons confused her. Access is enforced by the read check in `ScreenAccess` and `Allowlist.isAllowed`. WhatsApp Business stays blocked.
- **The PIN line is spoken once as she hides.** The PRD asks for this line and also says Saathi never speaks while a PIN field is showing. It says the one line in that moment, then stays silent. On Android's own PIN prompt it says nothing.

## Known limits

- On-device recognition needs Android 12+ and a speech pack for her language (about 66 MB for Hindi on the test phone). On Android 13+ Saathi starts the download by itself the first time she taps Bolo and the pack is missing, and setup can download it in advance. Android may wait for Wi-Fi. If the phone has no offline pack for her language, the only way is the opt-in for online speech.
- Bank apps that refuse to run with an accessibility service enabled cannot be helped by any overlay assistant.
- On a few phones, recent apps belongs to the system UI rather than the launcher. It is never on the allowlist, so Saathi cannot see that screen there.
- A shared APK may be blocked by Play Protect on some phones, and always needs the restricted-settings step (see [Install on a phone](#install-on-a-phone)). For judges or other testers, use the Play Store internal testing link, or a live phone or a video for the demo.
- The Accessibility setup line is spoken in Saathi's language, but it knows the Settings labels of Samsung, the Chinese brands and Pixel only when Settings are in English or Hindi. Other brands get the closest guess, and other Settings languages get a general "find the Saathi icon" line. The Saathi row is highlighted on only some phones.
- Saathi hides briefly whenever a system window takes focus, which may include the notification shade.
- **Kannada is a draft** that needs a native speaker's check, and on-phone commands (lists, reminders, alarms) are understood without the AI only in Hindi and English. Kannada requests for these go to the model.
- **The app waits only 6 seconds for an answer** (8 for a live question). Hydrogen at MEDIUM thinking took 9–10 s, so it now runs at LOW everywhere. A slow answer is still dropped and retried once, and then she hears "Ek second, main phir se dekh rahi hoon". A turn that escalates (Flash-Lite, then Hydrogen) can still come close to the limit, above all with a screenshot uploaded twice. A low-confidence resend with a screenshot starts on Flash-Lite again, so one hard step can take three calls; a marker for those resends is planned.
- **Masking what she says has gaps.** Names are masked only after an introducing phrase, and not in Kannada, because "my name is X" and "my name is wrong" couldn't be told apart safely there. An address without a house number ("Gandhi nagar") is sent as said, so reminders keep their places. Three number words in a row are always masked, so a quantity such as "ek sau bees" becomes `[number]`.
- **Automatic steps can stop too early on a busy page.** After two failed tries Saathi waits for her, and a page that changes only a little doesn't count as new. She then has to speak or tap the face to go on.
- **The web page and banking rules are not in the eval.** They are prompt rules checked only on the phone (tests 61–67).
- **The bank step-out is silent without the assistant.** If the service has no assistant running when a bank app opens, it turns off at once without speaking.
- **Search use is not counted on the AI Studio key.** `search_query_count` reads 0 there even when the answer clearly came from a search, so the logs can't yet show how often search runs.
- **The first screen on a how/where question is sent before a goal exists.** It goes only after the same payment, secret and scam checks and never in WhatsApp, but it is a change from "the screen goes only with a guided goal".
- **The server sleeps when unused.** The first request after a quiet spell waits for Cloud Run to start (about 6 s). `--min-instances=1` would avoid this at a small monthly cost.
- **A 401 says "Bete se API key daalne ko kahiye".** That line dates from when the app had its own key. It now means the app's secret doesn't match the backend, usually an old build after a secret rotation. Separate messages for auth, rate limits and offline are planned.
- **Open findings from the security review (2026-11-23):** the per-IP limit can be bypassed (see above), `/v1/ask` accepts any model name (now at most 64 characters) and `level` from the caller (prompt and picture sizes have been capped since 2026-11-27), `/v1/report` is not counted against the caps, and `/docs` and `/openapi.json` are public. The daily cap limits the cost of a leaked secret, but these should be fixed before a wider release. Play Integrity is planned before public launch.

