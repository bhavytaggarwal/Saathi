# Saathi

An on-screen Hindi and Hinglish helper for parents who are new to smartphones. A small character sits on top of any app, reads the screen, points at the exact button and speaks one step at a time. **It never taps, types or swipes for her.**

Built with the Gemini API. Native Kotlin, Android 8.0+, no third-party runtime libraries, and a release APK of about 100 KB.

## Install on a phone

1. On the phone, turn on Developer options and **USB debugging**, then connect it to the Mac and accept the prompt.
2. Install:
   ```sh
   adb install -r dist/saathi-v0.1.apk
   ```
3. Open **Saathi** and work through the setup screen. Each step shows a tick when finished.

To build again:

```sh
./gradlew testDebugUnitTest assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. It carries no API key and asks for one in setup.

For a personal build with a built-in key, copy `secrets.properties.template` to `secrets.properties`, fill it in, and add `-Psaathi.embedKey=true`. **Never share or upload that APK, because anyone holding it can extract the key.**

## How it works

1. She taps the face and asks out loud.
2. Her words go to Gemini **without the screen**. Lists, reminders and chat never send the screen at all.
3. If she needs help on the phone, Saathi reads the allowed app into a numbered list of elements and masks private values.
4. Gemini replies with one JSON step: which element number to point at and what to say.
5. The character moves beside the target, a ring and arrow highlight it, and the line is spoken.
6. When the screen changes, it reads again and gives the next step.

| File | Job |
| --- | --- |
| `SaathiAccessibilityService` | Read-only access and overlay host. `allowedRoot()` is the only way to reach screen content |
| `Assistant` | The guide loop and session state. Privacy checks run first |
| `ScreenReader` | Screen to numbered list, with masking |
| `core/PrivacyFilter` | Rules for secrets, payments, scams and number masking |
| `core/Allowlist` | The apps Saathi may read |
| `GeminiClient` | The REST call, reply schema and system prompt |
| `VoiceIO` | On-device Hindi speech recognition and text-to-speech |
| `OverlayController`, `CharacterViews` | The face, panel, ring and arrow |
| `PhoneHelp` | Which app is playing sound, and the navigation mode |
| `ListsStore`, `ReminderScheduler` | Lists and reminders, stored only on the phone |

## Privacy and safety, enforced in code

| Rule | Where it is enforced |
| --- | --- |
| Never acts on other apps | `canPerformGestures` is absent, and the `auditReadOnly` Gradle task **fails every build** if action APIs appear in the source |
| Reads allowed apps only | `packageNames` filters events, and `allowedRoot()` also checks the active window, because Android's setting does not protect it |
| Hides on secrets | `PrivacyFilter.hasSecret`: password fields, labelled fields, prompts such as "Enter UPI PIN", and custom number pads |
| Stops on payment screens | `PrivacyFilter.isPaymentScreen`: no reading, conversation only |
| Masks private values | Amounts, phones, account numbers, emails and UPI IDs, before anything is sent |
| Filters her speech | Runs of 4 or more digits become `[number]`, including spoken words such as "ek do teen char" |
| Warns about scams | Remote-access apps, collect requests and requests to share a code |
| Limits what is kept | Each request asks Google not to store it (`store: false`). Screen data is never logged or saved |

## Where this differs from the PRD

- **Sound finder needs Notification access.** Android does not tell ordinary apps which app is playing audio. The only public route is the media session list, which requires a notification listener, so `SaathiMediaListener` exists and reads no notifications. It is an optional setup step. Sound with no media session, such as games and ads, cannot be named.
- **A small on-device check runs while she is not in a session.** To hide on a PIN screen even when no session is active, Saathi checks allowed apps for secret fields on the phone. It keeps nothing and sends nothing. Screen content is sent only during a session she started.
- **The allowlist alone is not enough.** The PRD says Android delivers nothing from other apps. That is true for events, but not for reading the active window, so the code checks the allowlist as well.
- **Default model is `gemini-3.6-flash`, not `gemini-flash-latest`.** In testing on 2026-11-06 the alias and `gemini-3.8-flash` timed out or returned 503 on every call, while `gemini-3.6-flash` answered correctly in under 2 seconds. Saathi falls back to `gemini-3.5-flash-lite` automatically, and the model can still be changed in setup.
- **Two versions of every line.** Hindi text-to-speech mispronounces Roman Hinglish, so Gemini returns Roman text for the bubble and Devanagari for the voice.
- **The PIN line is spoken once as she hides.** The PRD asks for this line and also says Saathi never speaks while a PIN field is showing. It says the one line in that moment, then stays silent.

## Known limits

- Hindi on-device recognition needs Android 12+ and a speech pack. Setup offers a download, and an opt-in for online speech.
- Bank apps that refuse to run with an accessibility service enabled cannot be helped by any overlay assistant.
- On a few phones, recent apps belongs to the system UI rather than the launcher. It is never on the allowlist, so Saathi cannot see that screen there.
