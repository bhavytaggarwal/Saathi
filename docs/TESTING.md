# Saathi test checklist

Test phone: Vivo V2250, Android 15, Funtouch OS 15. Last updated 2026-11-09.

## Suggested order for the next session

1. **Set up the paid key:** add it, rebuild, then tap **Save and test**. See [Before testing](#before-testing). That is also test 22, the first check of the new reply format.
2. **Tests 1–4, PIN hiding.** Stop if any of them fails.
3. **Test 5, GPay bank account.** Screen-record it, ideally with your mom doing it. That recording is the buildathon demo video.
4. **Tests 14, 10 and 7:** OTP filter, sound finder and lists. These are quick and show well.
5. **Test 15, restart.** Then let test 16, the 7-day reminder, run in the background.

## Before testing

1. **Create a paid Gemini key** in its own AI Studio project, as described in the [README](../README.md#gemini-key).
2. **Save it** in `secrets.properties`. Don't paste it in a chat.
3. **Rebuild and reinstall** the personal APK:
   ```sh
   ./gradlew testDebugUnitTest assembleRelease -Psaathi.embedKey=true
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
4. In setup, tap **Save and test** under Gemini. You should see a ✅.
5. **Finish setup:** turn off battery optimisation, and untick **Allow online speech**.

The key was replaced with a paid one on 2026-11-09. Its AI Studio project answered HTTP 402 ("prepayment credits are depleted") until credits were added, and they took a few minutes to apply. Setup now says so directly instead of a general error.

Keep the phone connected over USB, so the logs can be read while you test.

## Test first: PIN hiding

**Don't enter a PIN on any of these screens.**

| # | Test | Expected | Status |
| --- | --- | --- | --- |
| 1 | Close and reopen GPay, and wait for the phone's lock prompt | Face disappears within about half a second | ✅ Passed 2026-11-09, and again on the build with the 1 s wait: GPay's PIN prompt is Android's `ConfirmDeviceCredentialActivity`. Hid 25 ms after it opened (16:04:10.7), came back at 16:04:15.2 with no flicker |
| 2 | GPay → bank account → **Check balance**, and stop at **ENTER UPI PIN** | Face and panel disappear | ✅ Passed 2026-11-09. The log also showed the face coming back for 0.2–0.5 s about every 2 s while the PIN screen was up (15:41–15:42). Fixed the same day: it now waits 1 s of a clear screen before returning. **Retest** |
| 3 | Press back from that screen | Face returns within a second or two | ✅ Passed 2026-11-09. Retest with the 1 s wait |
| 4 | Tap the face and ask something, then go to the UPI PIN screen | Says the PIN line once, then stays silent and hidden | ⬜ |

**If the face stays visible, stop and fix that before anything else.** To check that it hid, run:

```sh
adb logcat -d | grep SaathiPrivacy
```

This should show `hidden=true`, then `hidden=false`.

## Everything else

| # | Test | Expected | Status |
| --- | --- | --- | --- |
| 5 | Add a bank account in GPay, the main goal | Each step points at the right button, and Saathi hides before the PIN | ⬜ Steps inside GPay work (2026-11-09). The full flow is not done yet |
| 6 | Clock: "Subah 8 baje ka alarm kaise lagaun?". First check `adb logcat -d \| grep SaathiAllowlist` lists the Vivo Clock app | Guides her through Clock and never says it is already set | ⬜ This phone's Clock is `com.android.deskclock`, already on the list |
| 7 | "Doodh list mein daal do", then "Aur do packet Maggi bhi", then "Shopping list kya hai?" | Follow-ups need no tap, and it reads both items back | ⬜ Not retested |
| 8 | Stay silent after an answer | Mic closes, then the panel closes by itself | ⬜ Not retested |
| 9 | Follow a guided step | Slim strip that does not hide the app, and it moves on after she taps | ⬜ Not retested |
| 10 | Play YouTube, then ask "ye awaaz kahan se aa rahi hai?" | Names YouTube and guides her to stop it | ⬜ |
| 11 | Setup: **Test: what is playing now?** with Notifications and Silent off | Tells you whether the sound finder works with less access | ⬜ |
| 12 | Close a background app | Guides her through recent apps, and she swipes it herself | ⬜ |
| 13 | Open a bank app and ask something | Small dot, and says it cannot see the screen | ⬜ |
| 14 | Say "mera OTP 4 5 6 7 hai" | Warns her, and the number isn't sent | ⬜ |
| 15 | Set a daily reminder, then restart the phone | Fires on time after the restart | ⬜ |
| 16 | Keep a daily reminder running for 7 days | Fires on time each day | ⬜ |
| 17 | Log step times with `adb logcat -d \| grep SaathiGemini` | Replies arrive within 4 seconds on the paid key | ✅ Passed 2026-11-09: 21 replies from `gemini-3.6-flash` in 2.1–2.9 s, none failed and no fallback. The first step of a new guide request takes two calls, about 5 s |
| 18 | Your mom does test 5 while you stay silent. Note where she hesitates | She can follow each step without your help | ⬜ |
| 19 | Swipe Saathi away from recent apps and leave the phone idle for a few hours. Funtouch OS is aggressive about closing apps, even with battery optimisation off | Face and reminders still work | ⬜ |
| 20 | Turn on airplane mode and ask something | The fixed offline lines answer, not an error | ⬜ |
| 21 | Repeat the fake on-screen instruction test through the app on the phone. It has only passed from the Mac so far | Ignores the instruction and never asks for her UPI PIN | ⬜ |
| 22 | First request on the new reply format: "Namaste" and tap **Save and test** in setup | Answers normally. If `adb logcat -d \| grep SaathiGemini` shows "trying compatibility level", Gemini refused the new schema | ✅ Passed 2026-11-09: replies were valid on the phone with no compatibility drop, and GPay steps worked |
| 23 | "Doodh khatam ho gaya", then "list mein" | Asks list or reminder first, then adds doodh without asking again | ⬜ |
| 24 | During the GPay goal, tap Scan QR instead of the pointed button | Notices the wrong screen and guides her back | ⬜ |
| 25 | During the GPay goal, ask for a medicine reminder at 9 pm | Sets the reminder. Saying "GPay wala kaam" later starts that goal again | ⬜ |
| 26 | Have someone send a collect request, then open it in GPay | Warns about a scam on that screen, not just "payment screen" | ⬜ |
| 27 | Turn on the testing log, use Saathi, tap **Galat tha**, then `adb pull` the path shown in setup | `marked.jsonl` has that turn, with masked words and no screen text | ⬜ |

## Seen on 2026-11-09

- **Service restarts:** the accessibility service reconnected 7 times between 15:28 and 15:40, always in the same process and with no crash. Android's log shows the setting itself being switched off and on, and the phone's accessibility button (`accessibility_button_targets`) was set to Saathi, so each tap on that floating icon toggles the service. Turn the Saathi shortcut off in Settings → Accessibility → Saathi, here and on her phone. Otherwise she can switch Saathi off without knowing.
- **Context kept across questions (session state).** Asked again how to add a new bank account while that goal was still active. Saathi asked in Hindi whether to continue the earlier task or start a new one. The goal survived the closed panel as designed. Watch how often it asks this, since it counts toward the clarification rate.
- **Vivo i Manager switches Saathi off when GPay opens.** At 15:50:25 and 15:53:34, the accessibility service was switched off within a second of GPay opening. Android's settings record shows the change came from `com.vivo.imanager`, which is the payment protection in i Manager, not Saathi or the user. Fix on the phone: i Manager → **Payment safe** → turn the **GPay** switch off. This page has no per-app trust option. Confirmed at 16:04: after this, opening GPay left Saathi on. The same will happen on her Vivo phone, so this needs to be a setup step. Check after any change with `adb shell dumpsys settings | grep enabled_accessibility_services` (it names the package that last changed it).
- **The icon left on the PIN screen** was Android's accessibility button, not Saathi. Android draws it on every screen while the Saathi shortcut is on.
- **Hide reasons are logged now:** `adb logcat -d | grep SaathiPrivacy` shows `hidden=true reason=…` (system_window, lock_screen_class, password_field, labelled_field, prompt_text or number_pad), with no screen text.
- **USB:** the phone disconnected once at about 15:35. Saathi kept working, and the logs from the gap were read from the phone's buffer afterwards.

## Already passed

| Test | Result |
| --- | --- |
| USB install on the test phone | Passed, Play Protect did not block it |
| App opens and setup screen appears | Passed, no crashes |
| Accessibility service turns on and face appears over other apps | Passed |
| Face shrinks to a dot in an app that wasn't allowed then (Clock) | Passed |
| On-device Hindi speech recognition | Passed, a full sentence was understood |
| Hindi pack status and advance download in setup | Partly passed: status showed INSTALLED |
| One-time reminder ("remind me after 5 min") | Passed |
| Live Gemini replies from the Mac, not through the app | Passed: step, list, reminder, sound, bank help and fake instruction on screen |

## Clean up

- **8 AM reminder:** a test reminder for 2026-11-07 may still be on the phone. Remove it from **Lists and reminders** if you don't want it.
- **Old key:** if the WhatsApp copy was the personal APK, replace the key in `gearmind-admin` too.
- **Accessibility shortcut:** turn it off on your mom's phone, so she doesn't see the small extra icon on the screen's edge.
