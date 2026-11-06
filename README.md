# Saathi

An on-screen Hindi and Hinglish helper for parents who are new to smartphones. A small character sits on top of apps, reads the screen of allowed apps, points at the exact button and speaks one step at a time. **It never taps, types or swipes for her.**

Built with the Gemini API. Native Kotlin, Android 8.0+, no third-party runtime libraries, and a release APK of about 110 KB.

**Status (2026-11-06):** v0.1 is running on a real phone, but its testing isn't finished. See [the test checklist](docs/TESTING.md) for what has passed and what is left.

## Install on a phone

Install over USB. **Sending the APK through WhatsApp, a browser or a file manager gets blocked** by Google Play Protect in India, because the app uses accessibility and notification access. The USB install went through on the test phone.

1. Turn on Developer options on the phone. Go to **Settings → About phone** and tap **Build number** or **Software version** 7–10 times. On Vivo, it may be under **About phone → Version**.
2. In **Developer options**, turn on **USB debugging**. TalkBack has its own screen called "Developer settings", which is not this one. If you see an **Install via USB** option, turn that on too.
3. Connect the phone with a data cable and tap **Allow** on the phone.
4. Install:
   ```sh
   adb install -r dist/saathi-v0.1.apk
   ```
5. Open **Saathi** and work through the setup screen. Most steps show a tick when finished.

## Build

```sh
./gradlew testDebugUnitTest assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. `dist/` holds local copies and is git-ignored.

| APK | Gemini key | Use it for |
| --- | --- | --- |
| Default build | None, asks in setup | Anyone else |
| Personal build | Built in | Your own test phone only |

For a personal build, copy `secrets.properties.template` to `secrets.properties`, add your key, and add `-Psaathi.embedKey=true` to the build command.

**Never share or upload a personal APK, including through chat apps, because anyone holding it can extract the key.**

## Gemini key

Use a key from Google AI Studio in **its own project with billing on**, and set a monthly spend cap. Turn off Generate Content logging in the project too.

- The free tier allowed only **20 requests per day per model** in testing, and a single guided question uses at least 2. Google also uses free-tier content to improve its products.
- On the paid tier, one guided step without a screenshot costs about **$0.002** on `gemini-3.6-flash`, based on about 1,650 tokens per step. Google's listed price for this model doubles on 2027-01-01.
- Default model: `gemini-3.6-flash`, with an automatic switch to `gemini-3.5-flash-lite`. You can change the main model in setup.

## How it works

1. She taps the face and asks out loud, or types.
2. Her words go to Gemini **without the screen**. Lists, reminders and chat never send the screen.
3. If she needs help on the phone, Saathi reads the allowed app into a numbered list of elements and masks private values.
4. Gemini replies with one JSON step: which element number to point at and what to say.
5. The character moves beside the target, a ring and arrow highlight it, and the line is spoken. The panel shrinks to a slim strip so the app stays visible.
6. She does the action herself. When the screen changes, Saathi reads it again and gives the next step.

| File | Job |
| --- | --- |
| `SaathiAccessibilityService` | Read-only access and overlay host. `allowedRoot()` is the only way to reach screen content |
| `Assistant` | The guide loop, session state and chat memory. Privacy checks run first |
| `ScreenReader` | Screen to numbered list, with masking |
| `core/PrivacyFilter` | Rules for secrets, payments, scams and number masking |
| `core/Allowlist` | The apps Saathi may read |
| `core/Lines` | Fixed Hinglish lines that work with no AI or internet |
| `GeminiClient` | The REST call, reply schema, system prompt and model fallback |
| `VoiceIO` | On-device Hindi speech recognition, Hindi pack check and text-to-speech |
| `OverlayController`, `CharacterViews` | The face, panel, ring and arrow |
| `PhoneHelp`, `SaathiMediaListener` | Which app is playing sound, and the navigation mode |
| `ListsStore`, `ReminderScheduler` | Lists and reminders, stored only on the phone |
| `MainActivity`, `ListsActivity` | Setup, and the lists and reminders screen |

## Privacy and safety, enforced in code

| Rule | Where it is enforced |
| --- | --- |
| Never acts on other apps | `canPerformGestures` is absent, and the `auditReadOnly` Gradle task **fails every build** if action APIs appear in the source |
| Reads allowed apps only | `packageNames` filters events, and `allowedRoot()` also checks the active window, because Android's setting does not protect it |
| Hides on secrets | `PrivacyFilter.hasSecret`: password fields, labelled fields, prompts such as "Enter UPI PIN", and custom number pads |
| Hides on system prompts | `Assistant` hides whenever a system window has focus, and recognises credential screens by class name, which covers Android's PIN and fingerprint prompt |
| Stops on payment screens | `PrivacyFilter.isPaymentScreen`: no reading, conversation only |
| Masks private values | Amounts, phones, account numbers, emails and UPI IDs, before anything is sent |
| Filters her speech | Runs of 4 or more digits become `[number]`, including spoken words such as "ek do teen char" |
| Warns about scams | Remote-access apps, collect requests and requests to share a code |
| Ignores instructions on screen | Screen text is marked as data for the model, and a live test ignored a fake instruction to ask for her UPI PIN. The model has no way to act anyway |
| Limits what is kept | Each request asks Google not to store it (`store: false`). Screen data and her words are never logged or saved to disk |

### Which apps Saathi can read

| Status | Apps |
| --- | --- |
| Allowed | Google Pay, Settings, home and recent apps, YouTube, Play Store, ChatGPT, Gemini, Clock, Calculator, Google Maps |
| Never allowed | WhatsApp, Messages/SMS, Truecaller, email, bank apps, notification shade |
| Blocked by default | Everything else, including Chrome, Phone, Contacts, Gallery and Camera |

In apps she cannot read, the face shrinks to a small dot, and Saathi helps by conversation only. The same happens on payment screens.

## Where this differs from the PRD

The [PRD](../Saathi%20—%20PRD%20On-screen%20Hindi%20assistant%20for%20parents.md) has been updated to match these decisions.

- **Sound finder needs Notification access.** Android does not tell ordinary apps which app is playing audio. The only public route is the media session list, which requires a notification listener, so `SaathiMediaListener` exists and reads no notifications. It is an optional setup step, and it asks Android to start new installs on Android 12+ with only ongoing notifications on, so conversations (SMS and chats) start off. Someone can turn more on in Android's settings at their own risk, and Saathi gains nothing from it. Sound with no media session, such as games and ads, cannot be named.
- **A small on-device check runs while she is not in a session.** To hide on a PIN screen even with no session active, Saathi checks allowed apps for secret fields on the phone. It keeps nothing and sends nothing. Screen content is sent only during a session she started.
- **The allowlist alone is not enough.** The PRD originally said Android delivers nothing from other apps. That is true for events, but not for reading the active window, so the code checks the allowlist as well.
- **Default model is `gemini-3.6-flash`, not `gemini-flash-latest`.** In testing on 2026-11-06 the alias and `gemini-3.8-flash` timed out or returned 503 on every call, while `gemini-3.6-flash` answered correctly in under 2 seconds with minimal thinking.
- **The mic can reopen once after an answer.** Tapping before every sentence made conversation tiring. After an answer, it reopens for one follow-up with the green ring showing, and closes if she stays silent. It never reopens after a guide step, on a secret screen or after typing, and setup can turn this off. There is still no wake word and no always-on listening.
- **Chat is remembered for 30 minutes.** Her masked words and Saathi's replies stay in memory on the phone, with no screen data, so a closed panel or dark screen does not lose her place.
- **Clock, Calculator and Google Maps are also allowed.** In Clock and Calculator a number pad alone does not hide Saathi, while every other secret check still applies. During a help session, alarm names and places shown in Maps can go to Gemini.
- **Alarms are taught, not set.** Saathi's reminders are its own and are separate from the Clock app. If she asks for an alarm, Saathi guides her through Clock. A code check stops an alarm request turning into a reminder.
- **Two versions of every line.** Hindi text-to-speech mispronounces Roman Hinglish, so Gemini returns Roman text for the bubble and Devanagari for the voice.
- **The PIN line is spoken once as she hides.** The PRD asks for this line and also says Saathi never speaks while a PIN field is showing. It says the one line in that moment, then stays silent. On Android's own PIN prompt it says nothing.

## Known limits

- Hindi on-device recognition needs Android 12+ and a speech pack of about 66 MB on the test phone. Setup shows whether it is ready on Android 13+ and can download it in advance, and there is an opt-in for online speech.
- Bank apps that refuse to run with an accessibility service enabled cannot be helped by any overlay assistant.
- On a few phones, recent apps belongs to the system UI rather than the launcher. It is never on the allowlist, so Saathi cannot see that screen there.
- Because of Play Protect, judges or other testers probably cannot install from a shared link. Plan on a live phone or a video for the demo.
- Saathi hides briefly whenever a system window takes focus, which may include the notification shade.
