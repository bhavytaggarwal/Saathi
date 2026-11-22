# Saathi

An on-screen Hindi and Hinglish helper for parents who are new to smartphones. A small character sits on top of apps, reads the screen of allowed apps, points at the exact button and speaks one step at a time. **It never taps, types or swipes for her.**

Built with **Vertex AI (`google-genai`) on Google Cloud Run + Firestore** and a native Kotlin Android client (`applicationId = "in.getsaathi.app"`, `namespace = "com.saathi.app"`, Android 8.0+, zero third-party Android runtime libraries, zero client-side Gemini API keys, and a release-signed APK of ~146 KB / AAB of ~299 KB).

**Status (2026-11-21):** Production-ready Cloud Run + Vertex AI + Firestore architecture is deployed (`https://saathi-api-744486955078.asia-south1.run.app`, revision `saathi-api-00010-hbg`) with 3-Tier Edge-to-Cloud routing (`--model auto`), passing all 78 evaluation cases (100% valid JSON, 100% element accuracy, 100% guide-routing accuracy, 0 safety violations) and 87 on-device unit tests. Includes voice-guided Accessibility setup, cross-app handoff survival (e.g. Play Store → Flipkart), 1000ms auto-advance with a 6-second LLM timeout watchdog, and crisp 8–10 word guidance spoken at `1.08f`. See [the test checklist](docs/TESTING.md) and [eval suite](eval/README.md).

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
   adb install -r dist/saathi-v0.1.apk
   ```
5. Open **Saathi** and work through the setup screen. Most steps show a tick when finished.

### Play Store internal testing

Upload `app/build/outputs/bundle/release/app-release.aab` to an **internal testing** track in Play Console and add the testers' Google accounts. They install from the opt-in link like any Play app, so neither check above applies. Play asks for an accessibility declaration: Saathi reads the screen of allowed apps to point at buttons, never acts on them, and sets `isAccessibilityTool`. Review usually takes a few hours for internal testing.

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

The release-signed APK is written to `app/build/outputs/apk/release/app-release.apk` (and `dist/saathi-v0.1.apk`), and the Play Store App Bundle signed with `upload-keystore.jks` is written to `app/build/outputs/bundle/release/app-release.aab` (and `dist/saathi-v0.1.aab`).

`app/build.gradle.kts` reads `BACKEND_URL` and `CLIENT_SECRET` from `secrets.properties` into `BuildConfig.BACKEND_URL` and `BuildConfig.CLIENT_SECRET`. **No Gemini API key is ever embedded in the Android app or entered by the user.** `EVAL_SECRET` is read only by `eval/run_eval.py`, never by the app.

The client secret is built into the APK, so anyone with the file can extract it. It is not real protection, only a first gate. The limits below cap what a leaked secret can cost. Play Integrity is planned before public launch. The secrets were last rotated on 2026-11-22. After a rotation, every installed copy stops working until it is updated.

### 2. Cloud Run + Vertex AI + Firestore Backend (`backend/`)
The backend proxy in `backend/main.py` runs on Google Cloud Run using IAM Application Default Credentials (`genai.Client(vertexai=True, project=..., location="global")`):

```sh
cd backend
gcloud run deploy saathi-api \
  --source . \
  --project=saathi-514208 \
  --region=asia-south1 \
  --max-instances=2
```

Env vars on the service: `SAATHI_CLIENT_SECRET` (same as `CLIENT_SECRET`), `EVAL_SECRET`, `GLOBAL_DAILY_CAP` (2000) and `IP_BURST_PER_MINUTE` (30). They persist across deploys. To rotate a secret, change it in `secrets.properties`, run the deploy with `--update-env-vars=SAATHI_CLIENT_SECRET=...`, then rebuild the app.

Keep `--max-instances=2`. The per-IP burst limit is counted in each instance's memory, so the real limit is at most twice the setting.

Backend tests: `cd backend && python -m unittest test_main` (needs the packages in `requirements.txt`).

What the backend enforces on every request:
- **Shared Secret Header Guard:** Verifies `X-Saathi-Client-Secret` against `SAATHI_CLIENT_SECRET` using constant-time `hmac.compare_digest`.
- **Eval runner secret:** a request with a valid `X-Saathi-Eval-Secret` (`EVAL_SECRET`) skips the per-IP and per-device limits but still counts toward the global cap. No `device_id` is exempt; `eval_bot` was removed as a bypass.
- **Per-IP burst limit:** 30 requests a minute per IP (first `X-Forwarded-For` address), rejected with `HTTP 429`. It is per minute, not per day, because Jio and Airtel put many phones behind one IP.
- **Global daily cap:** `global_usage/{YYYY-MM-DD}` in Firestore counts every request from every device. Above 2,000 a day, all requests get `HTTP 429` until midnight UTC.
- **No prompt or reply text in logs:** `saathi_audit_trace` and `saathi_guide_step_telemetry` carry only structured fields (`package_name`, `model_used`/`model_version`, latency, `used_screenshot`, `is_reprompt`, token counts, `prompt_hash`). A flagged output's words are stored only in its `flagged_outputs` document, not in logs.
- **Firestore Daily Quota Guard:** Atomically increments `daily_usage/{device_id}_{YYYY-MM-DD}.count` (where `device_id` is an anonymous SHA-256 hash of `ANDROID_ID`) and rejects requests above **200 turns/day** with `HTTP 429`.
- **Vertex AI Implicit Context Caching & Thinking Optimization:**
  - Keeps the static `SYSTEM_PROMPT` + few-shot rules as a fixed prefix before dynamic turn data so `gemini-4-hydrogen` implicit caching matches cleanly on prompts exceeding 4,096 tokens, and logs `prompt_token_count`, `cached_content_token_count`, and `candidates_token_count` on every `/v1/ask` request.
  - Caps `gemini-4-hydrogen` thinking overhead to `thinking_level="LOW"` on standard turns (`"MEDIUM"` only on wrong-turn recovery), and sets `thinking_level="MINIMAL"` on `gemini-3.5-flash-lite`.
- **3-Tier Edge-to-Cloud Triage Routing (`model="auto"` + `FORCE_HYDROGEN` kill-switch):**
  - **Tier 1 (On-Device Edge, `<50 ms`, ₹0 cost):**
    - `core/LocalCommandParser.kt` deterministically handles unambiguous Romanized Hindi and Devanagari list reads (`list_read`), list clears (`list_clear`), explicit-time alarms (`alarm_set`), and explicit-time reminders (`reminder_set`) offline.
    - `Assistant.tryLocalDomMatch()` and `core/Guides.kt` answer simple UI lookups (`where is`, `find`, `click`, `tap`, `kahan hai`, `dabao`) and prepared WhatsApp flows directly on the phone with zero network calls.
  - **Tier 2 (`gemini-3.5-flash-lite`, `thinking_level="MINIMAL"`):** Handled by `route_model(payload)` in `backend/main.py` for non-UI text turns (lists, reminders, alarms, casual chat, clarifications) with automatic fallback retry on Tier 3 (`gemini-4-hydrogen`) if invalid JSON or `confidence < 0.75`.
  - **Tier 3 (`gemini-4-hydrogen`, `thinking_level="LOW"` / `"MEDIUM"`):** Selected directly by `route_model(payload)` for any turn with a screen DOM, active `guide` goal, WhatsApp privacy modes (`controls_only`, `not_shared`, `ask_first`), banking/UPI apps, `FLAG_SECURE`, Hindi/Hinglish scam & social-engineering triggers, or wrong-turn recovery (or 100% of cloud requests when `FORCE_HYDROGEN=true`).
- **Structured Audit Logging & One-Tap Flagging (`/v1/ask` & `/v1/report`):**
  - Every `/v1/ask` turn generates a UUID4 `trace_id`, returns it to the app, and emits a structured `saathi_audit_trace` JSON event to Cloud Logging for replay.
  - Tapping **"Galat hai / Report"** on Saathi's overlay (or saying *"galat tha"*) sends `trace_id` and masked turn context to `POST /v1/report`, writing a review document to Firestore `flagged_outputs/{trace_id}` (`owner: "bhavyta"`, `status: "needs_review"`) and emitting a `WARNING` `saathi_output_flagged` event to Cloud Logging.

## How it works

1. She taps the face and asks out loud, or types.
2. Her words go to Gemini **without the screen**. Lists, reminders and chat never send the screen.
3. If she needs help on the phone, Saathi reads the allowed app into a numbered list of elements and masks private values. In WhatsApp it asks first, and then sends only known buttons (see [WhatsApp](#whatsapp-ask-first)).
4. Each request carries a small state object (her goal, the last step, the screen's app and whether it changed, a pending question, the last thing saved) and the last 4 turns of masked chat. Gemini replies with one JSON object: an intent, a confidence, the line to say, the element number to point at, and a `state_patch` with only the fields that changed.
5. The app checks the reply. An invalid reply, or an element number that isn't on the screen list, gets one retry with the reason. A list or reminder change with confidence below 0.6 is not saved. Saathi asks "Maine suna: … Sahi hai?" first.
6. The character moves beside the target, a ring and arrow highlight it, and the line is spoken briskly at `1.08f` (1 short sentence, max 8–10 Hindi words). The panel shrinks to a slim strip (and moves to the top whenever the target is in the lower 60% of the screen, or can be dragged up and down by hand) so the app stays visible. Tapping the slim strip expands it and starts listening immediately.
7. She does the action herself. When the screen changes (or hands off across apps, e.g., Play Store → Flipkart), Saathi debounces for `1000ms` so the new screen settles, shows `"Dekh rahi hoon..."` on the banner, reads the screen again, and automatically gives the next step without waiting for her to say *"Ab?"*. A `6-second` timeout watchdog automatically retries once on slow network/LLM calls and speaks *"Ek second, main phir se dekh rahi hoon"* if the retry also fails, preventing silent hangs. Completing a sub-goal (such as tapping "Install" or "Open") transitions the overlay into an active ready/listening state instead of closing it.

| File | Job |
| --- | --- |
| `SaathiAccessibilityService` | Read-only access and overlay host. `allowedRoot()` is the only way to reach screen content. Stops pending setup TTS and speaks *"Saathi chalu ho gaya hai"* on connect; automatically steps out (`disableSelf()`) when a strict banking app in `STRICT_BANK_PACKAGES` opens |
| `Assistant` | The guide loop (`1000ms` screen-settle debounce, `"Dekh rahi hoon..."` in-flight state, `6s` timeout watchdog + auto-retry, cross-app handoff survival, and `1.08f` guidance speech rate). Runs both as a floating overlay host and in-app when Accessibility is disabled |
| `core/LocalCommandParser` | Tier 1 on-device deterministic parser for basic `list_read`, `list_clear`, `alarm_set`, and `reminder_set` commands (`<50 ms`, offline) |
| `core/SessionState` | The state sent with each request, and the rules for merging the model's patch |
| `core/AiReply` | The reply contract and its validation |
| `EvalLog` | The optional on-device log of turns for building test cases |
| `ScreenReader` | Screen to compact numbered list (`MAX_ELEMENTS = 48`), with masking. Skips invisible views, tiny/zero-bound dividers (`<= 4px`), decorative images/glyphs, and unlabeled wrapper containers that enclose child buttons |
| `core/PrivacyFilter` | Rules for secrets, payments, scams and number masking |
| `core/Allowlist` | `STRICT_BANK_PACKAGES` (auto `disableSelf()`), `FINANCIAL_AND_SECURE_PACKAGES` & `NEVER` blocklist, allow-by-default for ordinary apps, system permission dialogs, and Google Pay (`GPAY`), shopping-checkout allowance (`isShoppingApp`), and `ASK_FIRST` consent for WhatsApp |
| `core/ScreenAccess` | Consent for WhatsApp: asked, allowed or refused, and when access ends |
| `core/ControlsOnly` | WhatsApp's safe button labels, what each looks like, and the reader that hides everything else |
| `core/Guides` | Prepared WhatsApp guides as data, the router that picks one, and the runner that moves through the steps on the phone |
| `LabelProbe`, `core/ProbeRules` | Test phones only: writes WhatsApp's real button labels to a file for checking |
| `core/ListenPolicy` | When the mic reopens by itself (the watchdog), and when her words hold back the guide moving on |
| `core/Lines` | Fixed Hinglish lines that work with no AI or internet |
| `GeminiClient` | Calls the Cloud Run `/v1/ask` (`model="auto"`, `6s` read timeout) and `/v1/report` endpoints and parses `trace_id` |
| `backend/main.py` | FastAPI Cloud Run proxy: risk-based Tier 2 (`gemini-3.5-flash-lite`) → Tier 3 (`gemini-4-hydrogen`) router, `FORCE_HYDROGEN` kill-switch, Firestore 200/day quota guard, structured audit logging (`saathi_audit_trace`), and `/v1/report` (`flagged_outputs/{trace_id}`) |
| `VoiceIO` | Pause-tolerant on-device Hindi speech recognition (`3500ms` complete silence, partial buffer accumulation, `300ms` post-TTS delay before the mic reopens, a guard for speech engines that never report the end of a line), `1.08f` guidance speech rate, `stopAllSpeaking()`, Hindi pack check and text-to-speech (`speakAndShutdown`) |
| `OverlayController`, `CharacterViews` | The face, vertically draggable panel/strip, 1-tap Re-Enable Accessibility card, ring and arrow |
| `PhoneHelp`, `SaathiMediaListener` | Which app is playing sound, and the navigation mode |
| `ListsStore`, `ReminderScheduler` | Lists and reminders, stored only on the phone |
| `AccessibilitySetupGuide` | The spoken Accessibility setup line for the phone's Settings language and maker, and the Settings intent that asks the phone to highlight Saathi's row |
| `MainActivity`, `ListsActivity` | Voice-guided 3-step visual onboarding stepper (speaks the `AccessibilitySetupGuide` line with `QUEUE_FLUSH` on tap), collapsible sideload restricted-settings guide, in-app voice assistant host when Accessibility is disabled, and the lists and reminders screen |

## Privacy and safety, enforced in code

| Rule | Where it is enforced |
| --- | --- |
| Never acts on other apps | `canPerformGestures` is absent, and the `auditReadOnly` Gradle task **fails every build** if action APIs appear in the source |
| Automatic Bank Mode (`disableSelf()`) | When any strict banking app in `Allowlist.STRICT_BANK_PACKAGES` (`com.sbi.lotusintouch`, `com.sbi.SBIFreedomPlus`, `com.snapwork.hdfc`, `com.csam.icici.bank.imobile`, `com.axis.mobile`, `com.msf.kbank.mobile`, `com.bankofbaroda.mconnect`, `com.infrasofttech.CentralBank`, `com.fss.pnbpsp`) enters the foreground, `SaathiAccessibilityService` immediately removes all overlays, saves `pausedForBanking = true`, speaks a calm Hindi reassurance, and calls `disableSelf()` so the bank app opens without any Accessibility block. When a turn later needs live screen guidance (`guide_step` or `alarm_teach`), `Assistant` shows a 1-tap **"Screen dekhne ke liye Saathi chalu karein"** card opening `Settings.ACTION_ACCESSIBILITY_SETTINGS` |
| Financial & UPI Guardrail | On other financial/UPI/credential apps (`com.phonepe.app`, `net.one97.paytm`, `com.cred.club`, `in.org.npci.upiapp`, etc.), `SaathiAccessibilityService` immediately pauses and hides the AI overlay and never inspects screen contents. Google Pay (`com.google.android.apps.nbu.paisa.user`) is allowed for guidance and hides overlays only when a `FLAG_SECURE` / PIN screen is active |
| Respects `FLAG_SECURE` windows | `SaathiAccessibilityService.capture()` checks for `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` (`FLAG_SECURE`): in Google Pay or financial apps it immediately calls `assistant.onFlagSecureDetected()` to pause the overlay, while in ordinary shopping/content apps (e.g. Flipkart) it skips the screenshot and falls back cleanly to text DOM so the floating helper stays visible |
| Asks before reading WhatsApp | `ScreenAccess`: nothing in WhatsApp is read before she taps or says Haan. Access ends when she leaves WhatsApp, the goal ends, she says stop or closes the panel, or after 2 minutes idle. The idle limit is part of the read check itself |
| WhatsApp sends buttons only | `ControlsOnly`: a label leaves the phone only if it exactly matches a fixed list of WhatsApp button names. Names, messages, previews, times and photo descriptions become `[hidden]`, text boxes say only whether they have text, and there is never a title or a screenshot |
| Hides on secrets | `PrivacyFilter.hasSecret`: password fields, labelled fields, prompts such as "Enter UPI PIN", and custom number pads. In WhatsApp, a code inside a chat or message row (such as a bank's "123456 is your verification code") is someone's message, not a prompt, so it doesn't hide Saathi. WhatsApp's own PIN screens still do |
| Hides on system prompts | `Assistant` hides whenever a system window has focus, and recognises credential screens by class name, which covers Android's PIN and fingerprint prompt |
| Stops on payment screens | `PrivacyFilter.isPaymentScreen`: no reading, conversation only |
| Masks private values | Amounts, phones, account numbers, emails and UPI IDs, before anything is sent |
| Filters her speech | Runs of 4 or more digits become `[number]`, including spoken words such as "ek do teen char" |
| Warns about scams | Remote-access apps, collect requests and requests to share a code |
| Ignores instructions on screen | Screen text is marked as data for the model, and a live test ignored a fake instruction to ask for her UPI PIN. The model has no way to act anyway |
| Her own words still go to Gemini | Everything above is about the screen. What she says or types is sent masked, as before, so if she says "Rahul ko message karna hai", that name is sent. Steps answered on the phone send nothing |
| Limits what is kept | Each request asks Google not to store it (`store: false`). Screen data is never logged or saved to disk. Her words are saved only if the testing log is switched on in setup, and then only masked |
| Keeps the state free of screen text | The state is built after masking. Its one screen field, the masked title, is sent only with turns that send the screen list anyway, and is left out of the testing log |

### Which apps Saathi can read

| Status | Apps |
| --- | --- |
| Allowed by default | Ordinary apps — including Google Pay (`com.google.android.apps.nbu.paisa.user`, except on PIN/payment/`FLAG_SECURE` screens), Settings, Home and recent apps, YouTube, Play Store, Clock, Calculator, Google Maps, Chrome, Zepto, Blinkit, Snapchat, Swiggy, Zomato, Flipkart, Amazon, Instagram, ChatGPT, Gemini, etc. |
| Found on each phone | That phone's own preinstalled Clock, Calculator and home screen apps, because makers such as Vivo name them differently (`isLauncher` and `hasOrdinaryNumberPad` use these). Only system apps count, and assistant, messaging and banking apps are never added. `adb logcat -d \| grep SaathiAllowlist` shows what was found |
| Ask first | WhatsApp (`com.whatsapp`). Read only during a help session, after she says Haan, and only its buttons. See [WhatsApp](#whatsapp-ask-first) |
| Strict Bank Mode (`Allowlist.STRICT_BANK_PACKAGES`, auto `disableSelf()`) | YONO SBI (`com.sbi.lotusintouch`), YONO Lite (`com.sbi.SBIFreedomPlus`), HDFC Bank (`com.snapwork.hdfc`), iMobile Pay (`com.csam.icici.bank.imobile`), Axis Mobile (`com.axis.mobile`), Kotak811 (`com.msf.kbank.mobile`), bob World (`com.bankofbaroda.mconnect`), Central Bank (`com.infrasofttech.CentralBank`), PNB One (`com.fss.pnbpsp`) |
| Never allowed (`Allowlist.FINANCIAL_AND_SECURE_PACKAGES` & `Allowlist.NEVER`) | PhonePe (`com.phonepe.app`), Paytm (`net.one97.paytm`), CRED (`com.cred.club`), BHIM (`in.org.npci.upiapp`), Mobikwik, Freecharge, all `STRICT_BANK_PACKAGES`, WhatsApp Business (`com.whatsapp.w4b`), Messages/SMS (`messaging`, `mms`, `truecaller`), Gmail (`android.gm`), password managers/authenticators (`bitwarden`, `1password`, `lastpass`, `keepass`, `authenticator`), system UI / notification shade (`systemui`), and WhatsApp's own payment screens |

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
- **Two versions of every line.** Hindi text-to-speech mispronounces Roman Hinglish, so Gemini returns Roman text for the bubble and Devanagari for the voice.
- **WhatsApp is "ask first", not "never allowed".** The PRD kept WhatsApp blocked. It is now readable during one help session after she agrees, and only its buttons, because the most common questions are about WhatsApp and its unlabelled icons confused her. Access is enforced by the read check in `ScreenAccess` and `Allowlist.isAllowed`. WhatsApp Business stays blocked.
- **The PIN line is spoken once as she hides.** The PRD asks for this line and also says Saathi never speaks while a PIN field is showing. It says the one line in that moment, then stays silent. On Android's own PIN prompt it says nothing.

## Known limits

- Hindi on-device recognition needs Android 12+ and a speech pack of about 66 MB on the test phone. Setup shows whether it is ready on Android 13+ and can download it in advance, and there is an opt-in for online speech.
- Bank apps that refuse to run with an accessibility service enabled cannot be helped by any overlay assistant.
- On a few phones, recent apps belongs to the system UI rather than the launcher. It is never on the allowlist, so Saathi cannot see that screen there.
- A shared APK may be blocked by Play Protect on some phones, and always needs the restricted-settings step (see [Install on a phone](#install-on-a-phone)). For judges or other testers, use the Play Store internal testing link, or a live phone or a video for the demo.
- The Accessibility setup line knows the labels of Samsung, the Chinese brands and Pixel in English and Hindi only. Other brands get the closest guess, and other Settings languages get a general "find the Saathi icon" line. The Saathi row is highlighted on only some phones.
- Saathi hides briefly whenever a system window takes focus, which may include the notification shade.
