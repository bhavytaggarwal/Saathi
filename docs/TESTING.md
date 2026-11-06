# Saathi test checklist

Test phone: Vivo V2250, Android 15, Funtouch OS 15. Last updated 2026-11-06.

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

The current build still has the old free-tier key, which is limited to 20 requests per day per model. `gemini-3.6-flash` had reached that limit on 2026-11-06.

Keep the phone connected over USB, so the logs can be read while you test.

## Test first: PIN hiding

**Don't enter a PIN on any of these screens.**

| # | Test | Expected | Status |
| --- | --- | --- | --- |
| 1 | Close and reopen GPay, and wait for the phone's lock prompt | Face disappears within about half a second | ⬜ Fix installed, not retested |
| 2 | GPay → bank account → **Check balance**, and stop at **ENTER UPI PIN** | Face and panel disappear | ⬜ Not reached yet |
| 3 | Press back from that screen | Face returns within a second or two | ⬜ |
| 4 | Tap the face and ask something, then go to the UPI PIN screen | Says the PIN line once, then stays silent and hidden | ⬜ |

**If the face stays visible, stop and fix that before anything else.** To check that it hid, run:

```sh
adb logcat -d | grep SaathiPrivacy
```

This should show `hidden=true`, then `hidden=false`.

## Everything else

| # | Test | Expected | Status |
| --- | --- | --- | --- |
| 5 | Add a bank account in GPay, the main goal | Each step points at the right button, and Saathi hides before the PIN | ⬜ |
| 6 | Clock: "Subah 8 baje ka alarm kaise lagaun?" | Guides her through Clock and never says it is already set | ⬜ Fix installed, not retested |
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
| 17 | Log step times with `adb logcat -d \| grep SaathiGemini` | Replies arrive within 4 seconds on the paid key | ⬜ |

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
