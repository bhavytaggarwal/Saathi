package com.saathi.app.core

/**
 * Fixed lines that must work with no AI and no internet, in each ready [Language].
 *
 * Hindi: text is Roman Hinglish for the bubble, speech is Devanagari for the voice.
 * English: the same text for both.
 * Kannada: Kannada script for both. Drafted, to be checked by a native speaker, above all the
 * OTP, PIN and scam warnings.
 */
object Lines {
    private fun t(hi: Line, en: String, kn: String): Line = Lang.pick(hi, Line(en), Line(kn))
    private fun s(hi: String, en: String, kn: String): String = Lang.pick(hi, en, kn)

    /** Shown the moment she taps, before the recognizer is ready. */
    val LISTENING_NOW get() = t(Line("Sun rahi hoon...", "सुन रही हूँ..."), "Listening...", "ಕೇಳುತ್ತಿದ್ದೇನೆ...")
    val LISTENING get() = s("Boliye, sun rahi hoon.", "Go ahead, I'm listening.", "ಹೇಳಿ, ಕೇಳುತ್ತಿದ್ದೇನೆ.")
    val LISTENING_FOLLOW_UP get() = s("Aur kuch? Boliye, sun rahi hoon.", "Anything else? I'm listening.", "ಇನ್ನೇನಾದರೂ? ಹೇಳಿ, ಕೇಳುತ್ತಿದ್ದೇನೆ.")
    val TALK_NOW get() = s("Boliye, main sun rahi hoon.", "Go ahead, I'm listening.", "ಹೇಳಿ, ನಾನು ಕೇಳುತ್ತಿದ್ದೇನೆ.")
    val LOOKING get() = s("Dekh rahi hoon...", "Looking...", "ನೋಡುತ್ತಿದ್ದೇನೆ...")
    val TAP_IF_MORE get() = s("Kuch aur ho to mujhe dabaiye.", "Press me if you need anything else.", "ಇನ್ನೇನಾದರೂ ಬೇಕಿದ್ದರೆ ನನ್ನನ್ನು ಒತ್ತಿ.")
    val WAITING get() = t(Line("Main ruki hoon, aaram se karo.", "मैं रुकी हूँ, आराम से करो।"), "I'm waiting. Take your time.", "ನಾನು ಕಾಯುತ್ತಿದ್ದೇನೆ, ನಿಧಾನವಾಗಿ ಮಾಡಿ.")
    val PRIVACY get() = t(
        Line("Ab apna PIN daalo, main thodi der ke liye chhup jaati hoon.", "अब अपना PIN डालो, मैं थोड़ी देर के लिए छुप जाती हूँ।"),
        "Enter your PIN now. I'll hide for a little while.",
        "ಈಗ ನಿಮ್ಮ PIN ಹಾಕಿ, ನಾನು ಸ್ವಲ್ಪ ಹೊತ್ತು ಮರೆಯಾಗುತ್ತೇನೆ.",
    )

    /** Spoken when a strict banking or UPI app launches directly and Saathi calls disableSelf(). */
    val BANK_STEP_OUT_SPEECH get() = s(
        "Bank ki suraksha ke liye main abhi band ho rahi hoon. Kuch poochhna ho ya kaam ke baad mujhe wapas chalu karna ho, toh Saathi app ya upar notification kholein.",
        "For bank safety I'm off now. To ask or restart, open Saathi or its notification.",
        "ಬ್ಯಾಂಕ್ ಸುರಕ್ಷತೆಗಾಗಿ ನಾನು ಆಫ್ ಆಗುತ್ತಿದ್ದೇನೆ. ಕೇಳಲು ಅಥವಾ ಮತ್ತೆ ಆನ್ ಮಾಡಲು ಸಾಥಿ ಆ್ಯಪ್ ಅಥವಾ ನೋಟಿಫಿಕೇಶನ್ ತೆರೆಯಿರಿ.",
    )
    val BANK_STEP_OUT get() = Line(BANK_STEP_OUT_SPEECH)

    /** Spoken when a merchant checkout / payment options screen is detected before GPay/UPI launches. */
    val CHECKOUT_PAYMENT_STEP_OUT_SPEECH get() = s(
        "Ab payment screen aa gayi hai. Suraksha ke liye main Saathi band kar rahi hoon. Ab aap Google Pay par dabakar payment kar dijiye.",
        "This is the payment screen. For your safety I'm turning Saathi off. Press Google Pay to pay.",
        "ಇದು ಪಾವತಿ ಪರದೆ. ಸುರಕ್ಷತೆಗಾಗಿ ಸಾಥಿ ಆಫ್ ಮಾಡುತ್ತಿದ್ದೇನೆ. Google Pay ಒತ್ತಿ ಪಾವತಿಸಿ.",
    )
    val CHECKOUT_PAYMENT_STEP_OUT get() = Line(CHECKOUT_PAYMENT_STEP_OUT_SPEECH)

    /** 1-tap re-enable card copy and spoken prompt when a turn needs live screen guidance while Accessibility is off. */
    val ENABLE_ACCESSIBILITY_TITLE get() = s("Screen dekhne ke liye Saathi chalu karein", "Turn on Saathi so it can see the screen", "ಪರದೆ ನೋಡಲು ಸಾಥಿ ಆನ್ ಮಾಡಿ")
    val ENABLE_ACCESSIBILITY_BUTTON get() = s("Saathi chalu karein", "Turn on Saathi", "ಸಾಥಿ ಆನ್ ಮಾಡಿ")
    val ENABLE_ACCESSIBILITY_SPEECH get() = s(
        "Screen dekhne ke liye neeche wala button dabaiye, aur Saathi ko on kar dijiye.",
        "Press the button below and turn on Saathi, so I can see the screen.",
        "ಪರದೆ ನೋಡಲು ಕೆಳಗಿನ ಬಟನ್ ಒತ್ತಿ, ಸಾಥಿ ಆನ್ ಮಾಡಿ.",
    )
    val ENABLE_ACCESSIBILITY_PROMPT get() = Line(ENABLE_ACCESSIBILITY_SPEECH)

    /** Spoken as soon as SaathiAccessibilityService.onServiceConnected() fires. */
    val ACCESSIBILITY_CONNECTED get() = t(Line("Saathi chalu ho gaya hai", "साथी चालू हो गया है।"), "Saathi is on.", "ಸಾಥಿ ಆನ್ ಆಗಿದೆ.")
    val DONE get() = t(Line("Bahut badhiya! Ho gaya.", "बहुत बढ़िया! हो गया।"), "Well done! That's it.", "ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ! ಆಯಿತು.")
    val UNSURE get() = t(
        Line("Mujhe ye screen samajh nahi aa rahi. Ek baar peeche jaake phir try karein?", "मुझे ये screen समझ नहीं आ रही। एक बार पीछे जाके फिर try करें?"),
        "I don't understand this screen. Shall we go back and try again?",
        "ಈ ಪರದೆ ನನಗೆ ಅರ್ಥವಾಗುತ್ತಿಲ್ಲ. ಒಮ್ಮೆ ಹಿಂದೆ ಹೋಗಿ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸೋಣವೇ?",
    )
    val CANNOT_SEE get() = t(
        Line("Main ye screen dekh nahi sakti, aap mujhe batao kya likha hai.", "मैं ये screen देख नहीं सकती, आप मुझे बताओ क्या लिखा है।"),
        "I can't see this screen. Please tell me what it says.",
        "ಈ ಪರದೆ ನನಗೆ ಕಾಣುವುದಿಲ್ಲ. ಏನು ಬರೆದಿದೆ ಎಂದು ಹೇಳಿ.",
    )
    val PAYMENT get() = t(
        Line("Ye payment ka screen hai, main ise nahi dekhti. Aap batao kya likha hai.", "ये payment का screen है, मैं इसे नहीं देखती। आप बताओ क्या लिखा है।"),
        "This is a payment screen, so I won't look. Tell me what it says.",
        "ಇದು ಪಾವತಿ ಪರದೆ, ನಾನು ಇದನ್ನು ನೋಡುವುದಿಲ್ಲ. ಏನು ಬರೆದಿದೆ ಹೇಳಿ.",
    )
    val OTP_WARNING get() = t(
        Line("OTP kabhi kisi ko mat batana, mujhe bhi nahi.", "OTP कभी किसी को मत बताना, मुझे भी नहीं।"),
        "Never tell anyone your OTP, not even me.",
        "OTP ಯಾರಿಗೂ ಹೇಳಬೇಡಿ, ನನಗೂ ಹೇಳಬೇಡಿ.",
    )
    /** She started reading out an ID or credential. Said on the phone; nothing is sent. */
    val SENSITIVE_DATA_WARNING get() = t(
        Line(
            "Ruko! Aadhaar, PIN ya password jaisi baatein mujhe ya kisi ko mat batana.",
            "रुको! आधार, पिन या पासवर्ड जैसी बातें मुझे या किसी को मत बताना।",
        ),
        "Stop! Never tell me or anyone your Aadhaar, PIN or password.",
        "ನಿಲ್ಲಿ! ಆಧಾರ್, ಪಿನ್ ಅಥವಾ ಪಾಸ್‌ವರ್ಡ್ ನನಗೂ ಯಾರಿಗೂ ಹೇಳಬೇಡಿ.",
    )
    val LISTEN_FAILED get() = t(
        Line("Mujhe sunai nahi diya. Bolo button dabakar phir se boliye.", "मुझे सुनाई नहीं दिया। बोलो button दबाकर फिर से बोलिए।"),
        "I didn't hear you. Press Speak and say it again.",
        "ನನಗೆ ಕೇಳಿಸಲಿಲ್ಲ. 'ಹೇಳಿ' ಒತ್ತಿ ಮತ್ತೆ ಹೇಳಿ.",
    )
    val VOICE_UNAVAILABLE get() = t(
        Line("Abhi main sun nahi paa rahi. Likho button dabakar type kar dijiye.", "अभी मैं सुन नहीं पा रही। लिखो button दबाकर type कर दीजिए।"),
        "I can't listen right now. Press Write and type it.",
        "ಈಗ ನನಗೆ ಕೇಳಲು ಆಗುತ್ತಿಲ್ಲ. 'ಬರೆಯಿರಿ' ಒತ್ತಿ ಟೈಪ್ ಮಾಡಿ.",
    )

    /** The setup button that fetches the speech pack for her language. */
    val PACK_BUTTON get() = s("Hindi pack download karein (about 66 MB)", "Download the English speech pack", "ಕನ್ನಡ pack download ಮಾಡಿ")
    /** The phone could not fetch the speech pack by itself, so setup has to. */
    val PACK_MISSING get() = t(
        Line("Hindi pack chahiye. Saathi app kholkar 'Hindi pack download' dabaiye.", "Hindi pack चाहिए। साथी app खोलकर Hindi pack download दबाइए।"),
        "I need the English speech pack. Open the Saathi app and press Download.",
        "ಕನ್ನಡ pack ಬೇಕು. ಸಾಥಿ ಆ್ಯಪ್ ತೆರೆದು 'pack download' ಒತ್ತಿ.",
    )
    /** Said the moment Saathi starts fetching the pack by itself. */
    val PACK_DOWNLOADING get() = t(
        Line("Hindi samajhne ka pack download ho raha hai. Thodi der mein phir boliye.", "Hindi समझने का pack download हो रहा है। थोड़ी देर में फिर बोलिए।"),
        "The English speech pack is downloading. Please try again in a little while.",
        "ಕನ್ನಡ ಅರ್ಥಮಾಡಿಕೊಳ್ಳುವ pack download ಆಗುತ್ತಿದೆ. ಸ್ವಲ್ಪ ಹೊತ್ತಿನ ನಂತರ ಹೇಳಿ.",
    )
    /** Android will fetch it later, usually once the phone is on Wi-Fi. */
    val PACK_SCHEDULED get() = t(
        Line("Hindi pack Wi-Fi milte hi aa jayega. Tab tak Likho dabakar likhiye.", "Hindi pack Wi-Fi मिलते ही आ जाएगा। तब तक लिखो दबाकर लिखिए।"),
        "The speech pack will download on Wi-Fi. Until then, press Write.",
        "Wi-Fi ಸಿಕ್ಕ ತಕ್ಷಣ pack ಬರುತ್ತದೆ. ಅಲ್ಲಿಯವರೆಗೆ 'ಬರೆಯಿರಿ' ಒತ್ತಿ.",
    )
    val PACK_READY get() = t(
        Line("Hindi pack aa gaya. Ab mujhe dabakar boliye.", "Hindi pack आ गया। अब मुझे दबाकर बोलिए।"),
        "The speech pack is ready. Press me and speak.",
        "ಕನ್ನಡ pack ಬಂದಿದೆ. ಈಗ ನನ್ನನ್ನು ಒತ್ತಿ ಹೇಳಿ.",
    )
    val NO_MIC get() = t(
        Line("Mic ki permission chahiye. Saathi app kholkar setup poora kijiye.", "Mic की permission चाहिए। साथी app खोलकर setup पूरा कीजिए।"),
        "I need the mic. Open the Saathi app and finish setup.",
        "ಮೈಕ್ ಅನುಮತಿ ಬೇಕು. ಸಾಥಿ ಆ್ಯಪ್ ತೆರೆದು setup ಮುಗಿಸಿ.",
    )
    val NO_INTERNET get() = t(
        Line("Internet nahi chal raha. Thodi der baad phir try karte hain.", "Internet नहीं चल रहा। थोड़ी देर बाद फिर try करते हैं।"),
        "The internet isn't working. Let's try again in a little while.",
        "ಇಂಟರ್ನೆಟ್ ಕೆಲಸ ಮಾಡುತ್ತಿಲ್ಲ. ಸ್ವಲ್ಪ ಹೊತ್ತಿನ ನಂತರ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸೋಣ.",
    )
    val NO_KEY get() = t(
        Line("Mera setup abhi poora nahi hua. Bete se API key daalne ko kahiye.", "मेरा setup अभी पूरा नहीं हुआ। बेटे से API key डालने को कहिए।"),
        "Saathi needs an update. Please ask your family to check it.",
        "ಸಾಥಿಗೆ ಅಪ್‌ಡೇಟ್ ಬೇಕು. ಮನೆಯವರಿಗೆ ನೋಡಲು ಹೇಳಿ.",
    )
    /** The Gemini project has no credit left, so trying again will not help. */
    val NO_CREDIT get() = t(
        Line("Abhi main jawab nahi de paa rahi. Bete se kahiye ki Saathi ka setup check karein.", "अभी मैं जवाब नहीं दे पा रही। बेटे से कहिए कि साथी का setup check करें।"),
        "I can't answer right now. Please ask your family to check Saathi.",
        "ಈಗ ಉತ್ತರ ಕೊಡಲು ಆಗುತ್ತಿಲ್ಲ. ಮನೆಯವರಿಗೆ ಸಾಥಿ ನೋಡಲು ಹೇಳಿ.",
    )
    val AI_ERROR get() = t(
        Line("Koi baat nahi, phir se karte hain. Ek baar aur boliye.", "कोई बात नहीं, फिर से करते हैं। एक बार और बोलिए।"),
        "No problem, let's try again. Please say it once more.",
        "ಪರವಾಗಿಲ್ಲ, ಮತ್ತೆ ಮಾಡೋಣ. ಇನ್ನೊಮ್ಮೆ ಹೇಳಿ.",
    )
    /** Said when the model's reply was invalid or timed out twice in a row. */
    val RETRY_FAILED get() = t(Line("Ek second, main phir se dekh rahi hoon.", "एक सेकंड, मैं फिर से देख रही हूँ।"), "One second, I'm looking again.", "ಒಂದು ಕ್ಷಣ, ಮತ್ತೆ ನೋಡುತ್ತಿದ್ದೇನೆ.")
    /** Said once when an automatic step failed twice on the same page. Saathi then waits for her. */
    val PAGE_UNCLEAR get() = t(
        Line("Agla button saaf nahi dikh raha. Khud aage badhiye, ya madad ke liye mujhe dabaiye.", "अगला button साफ़ नहीं दिख रहा। खुद आगे बढ़िए, या मदद के लिए मुझे दबाइए।"),
        "I can't see the next button clearly. Carry on yourself, or press me for help.",
        "ಮುಂದಿನ ಬಟನ್ ಸ್ಪಷ್ಟವಾಗಿ ಕಾಣುತ್ತಿಲ್ಲ. ನೀವೇ ಮುಂದುವರಿಯಿರಿ, ಅಥವಾ ಸಹಾಯಕ್ಕೆ ನನ್ನನ್ನು ಒತ್ತಿ.",
    )
    /** Asked before an ask-first app such as WhatsApp is read, with Haan and Nahi buttons. */
    val CONSENT get() = t(
        Line("WhatsApp ki screen dekhun? Sirf buttons dekhungi, messages nahi.", "WhatsApp की screen देखूँ? सिर्फ़ buttons देखूँगी, messages नहीं।"),
        "May I look at the WhatsApp screen? Only the buttons, not your messages.",
        "WhatsApp ಪರದೆ ನೋಡಲೇ? ಬಟನ್‌ಗಳನ್ನು ಮಾತ್ರ ನೋಡುತ್ತೇನೆ, ಮೆಸೇಜ್ ಅಲ್ಲ.",
    )
    /** The same question, when she already said Haan once during this goal. */
    val CONSENT_AGAIN get() = t(Line("Phir se WhatsApp ki screen dekhun?", "फिर से WhatsApp की screen देखूँ?"), "May I look at WhatsApp again?", "ಮತ್ತೆ WhatsApp ಪರದೆ ನೋಡಲೇ?")
    val CONSENT_DECLINED get() = t(
        Line("Theek hai, screen nahi dekhungi. Main bataati hoon, aap karte jaao.", "ठीक है, screen नहीं देखूँगी। मैं बताती हूँ, आप करते जाओ।"),
        "All right, I won't look. I'll tell you the steps.",
        "ಸರಿ, ಪರದೆ ನೋಡುವುದಿಲ್ಲ. ನಾನು ಹೇಳುತ್ತೇನೆ, ನೀವು ಮಾಡುತ್ತಾ ಹೋಗಿ.",
    )
    val ACCESS_ENDED get() = t(Line("Ab main WhatsApp nahi dekh rahi.", "अब मैं WhatsApp नहीं देख रही।"), "I've stopped looking at WhatsApp.", "ಈಗ ನಾನು WhatsApp ನೋಡುತ್ತಿಲ್ಲ.")
    /** Shown in the panel for as long as she has allowed Saathi to read WhatsApp. */
    val ACCESS_NOTE get() = s("WhatsApp: sirf buttons dekh rahi hoon", "WhatsApp: looking at buttons only", "WhatsApp: ಬಟನ್‌ಗಳನ್ನು ಮಾತ್ರ ನೋಡುತ್ತಿದ್ದೇನೆ")

    /** Said when the Edit option is missing from a sent message's menu. */
    val EDIT_TOO_LATE get() = t(
        Line("Edit nahi dikh raha? WhatsApp thodi der tak hi message badalne deta hai.", "Edit नहीं दिख रहा? WhatsApp थोड़ी देर तक ही message बदलने देता है।"),
        "No Edit option? WhatsApp lets you change a message only for a short time.",
        "Edit ಕಾಣುತ್ತಿಲ್ಲವೇ? WhatsApp ಸ್ವಲ್ಪ ಸಮಯ ಮಾತ್ರ ಮೆಸೇಜ್ ಬದಲಿಸಲು ಬಿಡುತ್ತದೆ.",
    )
    /** Said instead of using a number she spoke during a guide. The number was masked and not sent. */
    val TYPE_NUMBER get() = t(
        Line("Number aap khud type kijiye. Main number nahi sunti.", "Number आप खुद type कीजिए। मैं number नहीं सुनती।"),
        "Please type the number yourself. I don't listen to numbers.",
        "ನಂಬರ್ ನೀವೇ ಟೈಪ್ ಮಾಡಿ. ನಾನು ನಂಬರ್ ಕೇಳುವುದಿಲ್ಲ.",
    )

    val REMINDER_NOTIFICATION_TITLE get() = s("Saathi: yaad dila rahi hoon", "Saathi reminder", "ಸಾಥಿ: ನೆನಪಿಸುತ್ತಿದ್ದೇನೆ")
    val REENABLE_NOTIFICATION_TITLE get() = s(
        "Bank suraksha ke liye Saathi ruka hai",
        "Saathi paused for bank safety",
        "ಬ್ಯಾಂಕ್ ಸುರಕ್ಷತೆಗಾಗಿ ಸಾಥಿ ನಿಂತಿದೆ",
    )
    val REENABLE_NOTIFICATION_TEXT get() = s(
        "Bank ka sawaal poochhna ho ya Saathi wapas chalu karna ho, toh yahan dabaiye.",
        "Press here to ask a bank question, or to turn Saathi back on.",
        "ಬ್ಯಾಂಕ್ ಪ್ರಶ್ನೆಯನ್ನು ಸುರಕ್ಷಿತವಾಗಿ ಕೇಳಲು, ಅಥವಾ ಬ್ಯಾಂಕ್ ಕೆಲಸದ ನಂತರ ಸಾಥಿಯನ್ನು ಮತ್ತೆ ಆನ್ ಮಾಡಲು ಇಲ್ಲಿ ಒತ್ತಿ.",
    )

    // Buttons on the panel.
    val BUTTON_SPEAK get() = s("Bolo", "Speak", "ಹೇಳಿ")
    val BUTTON_WRITE get() = s("Likho", "Write", "ಬರೆಯಿರಿ")
    val BUTTON_REPEAT get() = s("Phir se", "Again", "ಮತ್ತೆ")
    val BUTTON_SEND get() = s("Bhejo", "Send", "ಕಳುಹಿಸಿ")
    val BUTTON_CLOSE get() = s("Band karo", "Close", "ಮುಚ್ಚಿ")
    val BUTTON_DONE get() = s("Ho gaya", "Done", "ಆಯಿತು")
    val BUTTON_SNOOZE get() = s("10 minute baad", "In 10 minutes", "10 ನಿಮಿಷದ ನಂತರ")
    val BUTTON_YES get() = s("Haan", "Yes", "ಹೌದು")
    val BUTTON_NO get() = s("Nahi", "No", "ಬೇಡ")
    val BUTTON_WRONG get() = s("Galat hai / Report", "Wrong / Report", "ತಪ್ಪು / Report")
    val PARK_HERE get() = s("Yahan chhodo, chhup jaungi", "Drop here to hide me", "ಇಲ್ಲಿ ಬಿಡಿ, ಮರೆಯಾಗುತ್ತೇನೆ")

    private val ORDINALS_HI = listOf(
        "pehli" to "पहली", "doosri" to "दूसरी", "teesri" to "तीसरी", "chauthi" to "चौथी",
        "paanchvi" to "पाँचवीं", "chhathi" to "छठी", "saatvi" to "सातवीं", "aathvi" to "आठवीं",
        "nauvi" to "नौवीं", "dasvi" to "दसवीं",
    )
    private val ORDINALS_EN = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")
    private val ORDINALS_KN = listOf("ಮೊದಲ", "ಎರಡನೇ", "ಮೂರನೇ", "ನಾಲ್ಕನೇ", "ಐದನೇ", "ಆರನೇ", "ಏಳನೇ", "ಎಂಟನೇ", "ಒಂಬತ್ತನೇ", "ಹತ್ತನೇ")

    /**
     * Points her at a button found on the phone. A list row is named by counting from the top,
     * because she may not be able to read the English label. Otherwise she follows the orange ring.
     */
    fun tapHere(row: Int?): Line {
        val i = row?.minus(1)
        val hi = ORDINALS_HI.getOrNull(i ?: -1)
        val en = ORDINALS_EN.getOrNull(i ?: -1)
        val kn = ORDINALS_KN.getOrNull(i ?: -1)
        return if (hi != null && en != null && kn != null) {
            t(
                Line("Upar se ${hi.first} line par dabaiye, jahan ghera bana hai.", "ऊपर से ${hi.second} line पर दबाइए, जहाँ घेरा बना है।"),
                "Press the $en line from the top, where the circle is.",
                "ಮೇಲಿನಿಂದ $kn ಸಾಲನ್ನು ಒತ್ತಿ, ವೃತ್ತ ಇರುವಲ್ಲಿ.",
            )
        } else {
            t(Line("Jahan ghera bana hai, wahan dabaiye.", "जहाँ घेरा बना है, वहाँ दबाइए।"), "Press where the circle is.", "ವೃತ್ತ ಇರುವಲ್ಲಿ ಒತ್ತಿ.")
        }
    }

    val REMINDER_SET get() = t(Line("Theek hai, yaad dila dungi.", "ठीक है, याद दिला दूँगी।"), "All right, I'll remind you.", "ಸರಿ, ನೆನಪಿಸುತ್ತೇನೆ.")
    val REMINDERS_NONE get() = t(Line("Abhi koi reminder nahi laga hai.", "अभी कोई reminder नहीं लगा है।"), "There are no reminders yet.", "ಈಗ ಯಾವ ರಿಮೈಂಡರ್ ಕೂಡ ಇಲ್ಲ.")
    val NOTED get() = t(Line("Theek hai, maine note kar liya.", "ठीक है, मैंने note कर लिया।"), "All right, I've noted it.", "ಸರಿ, ಬರೆದುಕೊಂಡೆ.")

    /** Asked instead of saving when the model was not sure what she said. native is her script. */
    fun confirmHeard(roman: String, native: String) = t(
        Line("Maine suna: $roman. Sahi hai?", "मैंने सुना: $native। सही है?"),
        "I heard: $roman. Is that right?",
        "ನಾನು ಕೇಳಿದ್ದು: $native. ಸರಿಯೇ?",
    )

    val SLOWER get() = t(Line("Theek hai, ab dheere bolti hoon.", "ठीक है, अब धीरे बोलती हूँ।"), "All right, I'll speak more slowly.", "ಸರಿ, ಈಗ ನಿಧಾನವಾಗಿ ಮಾತನಾಡುತ್ತೇನೆ.")
    val FASTER get() = t(Line("Theek hai, thoda tez bolti hoon.", "ठीक है, थोड़ा तेज़ बोलती हूँ।"), "All right, I'll speak a little faster.", "ಸರಿ, ಸ್ವಲ್ಪ ವೇಗವಾಗಿ ಮಾತನಾಡುತ್ತೇನೆ.")
    val BYE get() = t(Line("Theek hai, main yahin hoon.", "ठीक है, मैं यहीं हूँ।"), "All right, I'm right here.", "ಸರಿ, ನಾನು ಇಲ್ಲೇ ಇದ್ದೇನೆ.")
    val NOTHING_TO_REPEAT get() = t(Line("Abhi tak kuch nahi bola. Aap boliye.", "अभी तक कुछ नहीं बोला। आप बोलिए।"), "I haven't said anything yet. Go ahead.", "ಇನ್ನೂ ಏನೂ ಹೇಳಿಲ್ಲ. ನೀವು ಹೇಳಿ.")
    val TIME_PASSED get() = t(
        Line("Ye samay to nikal gaya. Koi aur samay boliye.", "ये समय तो निकल गया। कोई और समय बोलिए।"),
        "That time has passed. Please say another time.",
        "ಈ ಸಮಯ ಆಗಲೇ ಕಳೆದಿದೆ. ಬೇರೆ ಸಮಯ ಹೇಳಿ.",
    )
    val TIME_UNCLEAR get() = t(Line("Kis din aur kitne baje yaad dilaun?", "किस दिन और कितने बजे याद दिलाऊँ?"), "Which day and what time should I remind you?", "ಯಾವ ದಿನ, ಎಷ್ಟು ಗಂಟೆಗೆ ನೆನಪಿಸಲಿ?")
    val SOUND_NONE get() = t(
        Line("Abhi mujhe koi app awaaz chalata nahi dikh raha.", "अभी मुझे कोई app आवाज़ चलाता नहीं दिख रहा।"),
        "I don't see any app playing sound right now.",
        "ಈಗ ಯಾವ ಆ್ಯಪ್ ಕೂಡ ಶಬ್ದ ಮಾಡುತ್ತಿಲ್ಲ.",
    )
    val SOUND_UNKNOWN get() = t(
        Line("Awaaz aa rahi hai, par app ka naam nahi pata chala. Chalo recent apps dekhte hain.", "आवाज़ आ रही है, पर app का नाम नहीं पता चला। चलो recent apps देखते हैं।"),
        "Something is playing, but I can't tell which app. Let's check recent apps.",
        "ಶಬ್ದ ಬರುತ್ತಿದೆ, ಆದರೆ ಆ್ಯಪ್ ಹೆಸರು ತಿಳಿಯಲಿಲ್ಲ. Recent apps ನೋಡೋಣ.",
    )

    fun scamWarning(son: String): Line = t(
        Line(
            "Ruko! Ye dhokha ho sakta hai. Code ya paisa mat dena. Pehle ${son.ifBlank { "bete" }} ko phone kar lo.",
            "रुको! ये धोखा हो सकता है। Code या पैसा मत देना। पहले ${son.ifBlank { "बेटे" }} को phone कर लो।",
        ),
        "Stop! This may be a fraud. Don't give any code or money. Call ${son.ifBlank { "your family" }} first.",
        "ನಿಲ್ಲಿ! ಇದು ಮೋಸ ಇರಬಹುದು. ಕೋಡ್ ಅಥವಾ ಹಣ ಕೊಡಬೇಡಿ. ಮೊದಲು ${if (son.isBlank()) "ಮನೆಯವರಿಗೆ" else "$son ಅವರಿಗೆ"} ಫೋನ್ ಮಾಡಿ.",
    )

    fun soundFrom(app: String) = t(
        Line("Ye awaaz $app se aa rahi hai. Chalo band karte hain.", "ये आवाज़ $app से आ रही है। चलो बंद करते हैं।"),
        "The sound is coming from $app. Let's turn it off.",
        "ಈ ಶಬ್ದ $app ಇಂದ ಬರುತ್ತಿದೆ. ಬನ್ನಿ, ನಿಲ್ಲಿಸೋಣ.",
    )

    fun listAdded(items: List<ListItem>): Line {
        val many = items.size > 1
        return t(
            Line(
                "${joinRoman(items)} ${if (many) "add kar diye" else "list mein daal diya"}.",
                "${joinHindi(items)} ${if (many) "add कर दिए" else "list में डाल दिया"}।",
            ),
            "Added ${joinRoman(items)} to the list.",
            "${joinHindi(items)} ಪಟ್ಟಿಗೆ ಸೇರಿಸಿದೆ.",
        )
    }

    fun listRemoved(items: List<ListItem>) = t(
        Line("${joinRoman(items)} list se hata diya.", "${joinHindi(items)} list से हटा दिया।"),
        "Removed ${joinRoman(items)} from the list.",
        "${joinHindi(items)} ಪಟ್ಟಿಯಿಂದ ತೆಗೆದೆ.",
    )

    fun listEmpty() = t(Line("List abhi khaali hai.", "List अभी खाली है।"), "The list is empty.", "ಪಟ್ಟಿ ಈಗ ಖಾಲಿ ಇದೆ.")

    fun listCleared() = t(Line("List saaf kar di.", "List साफ़ कर दी।"), "I've cleared the list.", "ಪಟ್ಟಿ ಖಾಲಿ ಮಾಡಿದೆ.")

    fun listRead(items: List<ListItem>) = t(
        Line("List mein hai: ${joinRoman(items)}.", "List में है: ${joinHindi(items)}।"),
        "On the list: ${joinRoman(items)}.",
        "ಪಟ್ಟಿಯಲ್ಲಿ ಇದೆ: ${joinHindi(items)}.",
    )

    fun listHas(item: ListItem) = t(
        Line("Haan, ${item.name} already list mein hai.", "हाँ, ${item.nameHi} already list में है।"),
        "Yes, ${item.name} is already on the list.",
        "ಹೌದು, ${item.nameHi} ಆಗಲೇ ಪಟ್ಟಿಯಲ್ಲಿ ಇದೆ.",
    )

    fun listMissing(item: ListItem) = t(
        Line("Nahi, ${item.name} list mein nahi hai.", "नहीं, ${item.nameHi} list में नहीं है।"),
        "No, ${item.name} isn't on the list.",
        "ಇಲ್ಲ, ${item.nameHi} ಪಟ್ಟಿಯಲ್ಲಿ ಇಲ್ಲ.",
    )

    fun reminderAlert(title: String, titleNative: String) = t(
        Line("Yaad dila rahi hoon: $title", "याद दिला रही हूँ: $titleNative"),
        "Reminder: $title",
        "ನೆನಪಿಸುತ್ತಿದ್ದೇನೆ: $titleNative",
    )

    fun joinRoman(items: List<ListItem>) =
        items.joinToString(", ") { listOf(it.qty, it.name).filter(String::isNotBlank).joinToString(" ") }

    /** Item names in her script: Devanagari for Hindi, Kannada script for Kannada (the name_hi field). */
    fun joinHindi(items: List<ListItem>) =
        items.joinToString(", ") { listOf(it.qty, it.nameHi).filter(String::isNotBlank).joinToString(" ") }

    /** The setup steps she sees first, in her language. */
    object Setup {
        val CHOOSE_TITLE = "भाषा चुनें · Choose language · ಭಾಷೆ ಆಯ್ಕೆಮಾಡಿ"
        val CHOOSE_SPEECH get() = s("अपनी भाषा चुनिए।", "Please choose your language.", "ನಿಮ್ಮ ಭಾಷೆ ಆಯ್ಕೆಮಾಡಿ.")
        val COMING_SOON get() = s("jald aa rahi hai", "coming soon", "ಶೀಘ್ರದಲ್ಲೇ")
        fun step(n: Int) = s("Step $n of 3", "Step $n of 3", "ಹಂತ $n / 3")
        val REPLAY get() = s("🔊 Dobara sunein", "🔊 Hear again", "🔊 ಮತ್ತೆ ಕೇಳಿ")
        val MIC_HEADLINE get() = s("Bas boliye, main sunungi", "Just speak, I'll listen", "ಸುಮ್ಮನೆ ಹೇಳಿ, ನಾನು ಕೇಳುತ್ತೇನೆ")
        val MIC_SPEECH get() = s(
            "बस बोलिए, मैं सुनूँगी। नीचे माइक अलाउ करें बटन दबाइए।",
            "Just speak and I'll listen. Press the Allow mic button below.",
            "ಸುಮ್ಮನೆ ಹೇಳಿ, ನಾನು ಕೇಳುತ್ತೇನೆ. ಕೆಳಗಿನ 'ಮೈಕ್ ಅನುಮತಿಸಿ' ಒತ್ತಿ.",
        )
        val MIC_BUTTON get() = s("Mic allow karein", "Allow mic", "ಮೈಕ್ ಅನುಮತಿಸಿ")
        val SCREEN_HEADLINE get() = s("Main aapko button dikhaungi", "I'll show you the buttons", "ನಾನು ನಿಮಗೆ ಬಟನ್ ತೋರಿಸುತ್ತೇನೆ")
        val SCREEN_SPEECH get() = s(
            "मैं आपको बटन दिखाऊँगी। नीचे साथी चालू करें बटन दबाइए।",
            "I'll show you the buttons. Press Turn on Saathi below.",
            "ನಾನು ನಿಮಗೆ ಬಟನ್ ತೋರಿಸುತ್ತೇನೆ. ಕೆಳಗಿನ 'ಸಾಥಿ ಆನ್ ಮಾಡಿ' ಒತ್ತಿ.",
        )
        val START_HEADLINE get() = s("Shuru karein! Poochiye: 'Shopping list padho'", "Let's start! Ask: 'Read my shopping list'", "ಶುರು ಮಾಡೋಣ! ಕೇಳಿ: 'ಶಾಪಿಂಗ್ ಪಟ್ಟಿ ಓದು'")
        val START_SPEECH get() = s("शुरू करें! पूछिए: शॉपिंग लिस्ट पढ़ो।", "Let's start! Ask me to read your shopping list.", "ಶುರು ಮಾಡೋಣ! ಶಾಪಿಂಗ್ ಪಟ್ಟಿ ಓದು ಎಂದು ಕೇಳಿ.")
        val START_BUTTON get() = s("Shuru karein", "Start", "ಶುರು ಮಾಡಿ")
        val CHANGE_LANGUAGE get() = s("🌐 Bhasha badlein", "🌐 Change language", "🌐 ಭಾಷೆ ಬದಲಿಸಿ")
    }

    /**
     * Google Play Prominent Disclosure for Android AccessibilityService API, with explicit
     * Accept and Decline paths in all ready languages.
     */
    object Disclosure {
        val TITLE get() = s(
            "AccessibilityService अनुमति की जानकारी",
            "AccessibilityService Permission Disclosure",
            "AccessibilityService ಅನುಮತಿ ಮಾಹಿತಿ",
        )
        val BODY get() = s(
            "• Saathi स्क्रीन पर मार्गदर्शन के लिए Android AccessibilityService API का उपयोग करता है।\n" +
                "• जब आप स्क्रीन पर मदद माँगते हैं, यह अनुमति प्राप्त ऐप्स में स्क्रीन का टेक्स्ट, बटन के नाम व स्थान, और ज़रूरत पड़ने पर मास्क किए गए स्क्रीनशॉट पढ़ता है।\n" +
                "• सभी नंबर, राशि, ईमेल और UPI ID हटाकर (मास्क करके) एन्क्रिप्टेड HTTPS द्वारा Saathi की Google Cloud AI सेवा को केवल अगला बटन दिखाने और बोलकर समझाने के लिए भेजे जाते हैं—इन्हें कभी सेव, लॉग या बेचा नहीं जाता, और Saathi कभी आपकी ओर से कोई बटन नहीं दबाता।\n" +
                "• पासवर्ड, PIN, OTP, SMS, Gmail और बैंकिंग ऐप्स कभी नहीं पढ़े जाते।",
            "• Saathi uses the Android AccessibilityService API to guide you on screen.\n" +
                "• When you ask for screen help, it reads on-screen text, button labels and positions, and occasional masked screenshots of allowed apps.\n" +
                "• Numbers, amounts, emails, and UPI IDs are masked and sent over encrypted HTTPS to Saathi's Google Cloud AI service solely to point at the next button and speak instructions—never stored, logged, or sold, and Saathi never acts or presses buttons on your behalf.\n" +
                "• Passwords, PINs, OTPs, SMS, Gmail, and banking apps are never read.",
            "• ಪರದೆಯ ಮೇಲೆ ಮಾರ್ಗದರ್ಶನ ನೀಡಲು ಸಾಥಿ Android AccessibilityService API ಬಳಸುತ್ತದೆ.\n" +
                "• ನೀವು ಪರದೆಯ ಸಹಾಯ ಕೇಳಿದಾಗ, ಅನುಮತಿಸಲಾದ ಆ್ಯಪ್‌ಗಳ ಪಠ್ಯ, ಬಟನ್ ಹೆಸರು ಹಾಗೂ ಸ್ಥಾನ, ಮತ್ತು ಅಗತ್ಯವಿದ್ದಾಗ ಮಾಸ್ಕ್ ಮಾಡಿದ ಸ್ಕ್ರೀನ್‌ಶಾಟ್‌ಗಳನ್ನು ಇದು ಓದುತ್ತದೆ.\n" +
                "• ಸಂಖ್ಯೆಗಳು, ಮೊತ್ತ, ಇಮೇಲ್ ಮತ್ತು UPI ID ಗಳನ್ನು ಮರೆಮಾಡಿ (ಮಾಸ್ಕ್ ಮಾಡಿ), ಮುಂದಿನ ಬಟನ್ ತೋರಿಸಲು ಮತ್ತು ಧ್ವನಿಯಲ್ಲಿ ಹೇಳಲು ಮಾತ್ರ ಎನ್‌ಕ್ರಿಪ್ಟೆಡ್ HTTPS ಮೂಲಕ ಸಾಥಿಯ Google Cloud AI ಸೇವೆಗೆ ಕಳುಹಿಸಲಾಗುತ್ತದೆ—ಇದನ್ನು ಎಂದಿಗೂ ಸಂಗ್ರಹಿಸುವುದಿಲ್ಲ ಅಥವಾ ಮಾರಾಟ ಮಾಡುವುದಿಲ್ಲ, ಹಾಗೂ ಸಾಥಿ ನಿಮ್ಮ ಪರವಾಗಿ ಯಾವುದೇ ಬಟನ್ ಒತ್ತುವುದಿಲ್ಲ.\n" +
                "• ಪಾಸ್‌ವರ್ಡ್, PIN, OTP, SMS, Gmail ಮತ್ತು ಬ್ಯಾಂಕಿಂಗ್ ಆ್ಯಪ್‌ಗಳನ್ನು ಎಂದಿಗೂ ಓದುವುದಿಲ್ಲ.",
        )
        val SPEECH get() = s(
            "साथी आपको अगला बटन दिखाने के लिए Android AccessibilityService का उपयोग करता है। पासवर्ड, ओटीपी और बैंकिंग ऐप्स कभी नहीं पढ़े जाते, और साथी खुद कोई बटन नहीं दबाता। स्क्रीन मदद चालू करने के लिए स्वीकार करें दबाइए, या सिर्फ़ आवाज़ से मदद के लिए अभी नहीं दबाइए।",
            "Saathi uses the Android AccessibilityService to point at buttons on your screen. Passwords, OTPs, and banking apps are never read, and Saathi never presses buttons for you. Press Agree to open Settings, or press Decline to use voice only.",
            "ಪರದೆಯ ಮೇಲೆ ಬಟನ್ ತೋರಿಸಲು ಸಾಥಿ Android AccessibilityService ಬಳಸುತ್ತದೆ. ಪಾಸ್‌ವರ್ಡ್, OTP ಮತ್ತು ಬ್ಯಾಂಕಿಂಗ್ ಆ್ಯಪ್‌ಗಳನ್ನು ಎಂದಿಗೂ ಓದುವುದಿಲ್ಲ, ಹಾಗೂ ಸಾಥಿ ತಾನಾಗಿಯೇ ಬಟನ್ ಒತ್ತುವುದಿಲ್ಲ. ಸೆಟ್ಟಿಂಗ್ಸ್ ತೆರೆಯಲು ಒಪ್ಪಿಗೆ ಒತ್ತಿ, ಅಥವಾ ಧ್ವನಿ ಮಾತ್ರ ಬಳಸಲು ಬೇಡ ಒತ್ತಿ.",
        )
        val ACCEPT_BUTTON get() = s(
            "स्वीकार करें और सेटिंग्स खोलें",
            "Agree & Open Settings",
            "ಒಪ್ಪುತ್ತೇನೆ ಮತ್ತು ಸೆಟ್ಟಿಂಗ್ಸ್ ತೆರೆಯಿರಿ",
        )
        val DECLINE_BUTTON get() = s(
            "अभी नहीं (सिर्फ़ आवाज़ से मदद)",
            "Decline (Use voice only)",
            "ಈಗ ಬೇಡ (ಧ್ವನಿ ಸಹಾಯ ಮಾತ್ರ)",
        )
        val REVIEW_CARD_TITLE get() = s(
            "स्क्रीन मदद (AccessibilityService)",
            "Screen Helper (AccessibilityService)",
            "ಪರದೆ ಸಹಾಯ (AccessibilityService)",
        )
        val REVIEW_CARD_WHY get() = s(
            "स्क्रीन पर बटन दिखाने के लिए AccessibilityService जानकारी पढ़ें और चालू करें।",
            "Review the AccessibilityService disclosure and turn on live screen guidance.",
            "ಪರದೆಯ ಮೇಲೆ ಬಟನ್ ತೋರಿಸಲು AccessibilityService ಮಾಹಿತಿ ಓದಿ ಮತ್ತು ಆನ್ ಮಾಡಿ.",
        )
        val REVIEW_CARD_BUTTON get() = s(
            "स्क्रीन मदद चालू करें",
            "Review & Turn on Screen Helper",
            "ಪರದೆ ಸಹಾಯ ಆನ್ ಮಾಡಿ",
        )
    }

    /**
     * Banner shown at the top of MainActivity when AccessibilityService is off and
     * paused_for_banking == true, giving the user a 1-tap button to turn Saathi back on.
     */
    object BankPaused {
        val TITLE get() = s(
            "🔒 बैंक की सुरक्षा के लिए Saathi बंद हुआ था",
            "🔒 Saathi turned off for bank safety",
            "🔒 ಬ್ಯಾಂಕ್ ಸುರಕ್ಷತೆಗಾಗಿ ಸಾಥಿ ಆಫ್ ಆಗಿತ್ತು",
        )
        val BODY get() = s(
            "आपकी बैंक स्क्रीन प्राइवेट रहे, इसलिए Saathi थोड़ी देर के लिए बाहर आ गया। बैंक का सवाल बिना स्क्रीन देखे पूछ सकती हैं, या काम पूरा होने पर Saathi वापस चालू कर लीजिए।",
            "Saathi stepped out to keep your bank screen private. You can ask a banking question without Saathi seeing the screen, or turn Saathi back on when your bank work is done.",
            "ನಿಮ್ಮ ಬ್ಯಾಂಕ್ ಪರದೆ ಖಾಸಗಿಯಾಗಿರಲು ಸಾಥಿ ಸ್ವಲ್ಪ ಹೊತ್ತು ಹೊರಗೆ ಬಂದಿದೆ. ಪರದೆ ನೋಡದೆ ಬ್ಯಾಂಕ್ ಪ್ರಶ್ನೆ ಕೇಳಬಹುದು, ಅಥವಾ ಕೆಲಸ ಮುಗಿದ ಮೇಲೆ ಸಾಥಿಯನ್ನು ಮತ್ತೆ ಆನ್ ಮಾಡಿ.",
        )
        /** The fake customer-care number scam: search ads and video comments carry fake helplines. */
        val SAFETY_NOTE get() = s(
            "सावधान: Google या YouTube पर customer care नंबर कभी मत ढूँढें — सिर्फ़ अपने ATM card या passbook के पीछे छपे नंबर पर call करें।",
            "Careful: never search Google or YouTube for a customer care number. Call only the number printed on the back of your ATM card or passbook.",
            "ಎಚ್ಚರಿಕೆ: Google ಅಥವಾ YouTube ನಲ್ಲಿ ಕಸ್ಟಮರ್ ಕೇರ್ ನಂಬರ್ ಎಂದೂ ಹುಡುಕಬೇಡಿ. ನಿಮ್ಮ ATM ಕಾರ್ಡ್ ಅಥವಾ ಪಾಸ್‌ಬುಕ್ ಹಿಂದೆ ಮುದ್ರಿಸಿದ ನಂಬರ್‌ಗೆ ಮಾತ್ರ ಕರೆ ಮಾಡಿ.",
        )
        /** Asks a banking question by voice in the app, with screen reading still off. */
        val ASK_BUTTON get() = s(
            "🎙️ Bina Screen Dekhe Poochhein",
            "🎙️ Ask without showing the screen",
            "🎙️ ಪರದೆ ತೋರಿಸದೆ ಕೇಳಿ",
        )
        val REENABLE_BUTTON get() = s(
            "Bank Kaam Ho Gaya — Saathi Wapas Chalu Karein",
            "Bank work done — turn Saathi back on",
            "ಬ್ಯಾಂಕ್ ಕೆಲಸ ಮುಗಿಯಿತು — ಸಾಥಿ ಮತ್ತೆ ಆನ್ ಮಾಡಿ",
        )
        val BUTTON get() = s(
            "Saathi Wapas Chalu Karein",
            "Turn Saathi Back On",
            "ಸಾಥಿ ಮತ್ತೆ ಆನ್ ಮಾಡಿ",
        )
    }
}
