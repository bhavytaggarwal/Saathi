package com.saathi.app.core

/** Fixed lines that must work with no AI and no internet. */
object Lines {
    /** Shown the moment she taps, before the recognizer is ready. */
    val LISTENING_NOW = Line("Sun rahi hoon...", "सुन रही हूँ...")
    val WAITING = Line("Main ruki hoon, aaram se karo.", "मैं रुकी हूँ, आराम से करो।")
    val PRIVACY = Line(
        "Ab apna PIN daalo, main thodi der ke liye chhup jaati hoon.",
        "अब अपना PIN डालो, मैं थोड़ी देर के लिए छुप जाती हूँ।",
    )
    /** Spoken when a strict banking or UPI app launches directly and Saathi calls disableSelf(). */
    const val BANK_STEP_OUT_SPEECH =
        "Suraksha ke liye Saathi band kar diya hai. Ab back jaakar ek baar phir se app kholein."
    val BANK_STEP_OUT = Line(BANK_STEP_OUT_SPEECH, BANK_STEP_OUT_SPEECH)

    /** Spoken when a merchant checkout / payment options screen is detected before GPay/UPI launches. */
    const val CHECKOUT_PAYMENT_STEP_OUT_SPEECH =
        "Ab payment screen aa gayi hai. Suraksha ke liye main Saathi band kar rahi hoon. Ab aap Google Pay par dabakar payment kar dijiye."
    val CHECKOUT_PAYMENT_STEP_OUT = Line(CHECKOUT_PAYMENT_STEP_OUT_SPEECH, CHECKOUT_PAYMENT_STEP_OUT_SPEECH)

    /** 1-tap re-enable card copy and spoken prompt when a turn needs live screen guidance while Accessibility is off. */
    const val ENABLE_ACCESSIBILITY_TITLE = "Screen dekhne ke liye Saathi chalu karein"
    const val ENABLE_ACCESSIBILITY_BUTTON = "Saathi chalu karein"
    const val ENABLE_ACCESSIBILITY_SPEECH =
        "Screen dekhne ke liye neeche wala button dabaiye, aur Saathi ko on kar dijiye."
    val ENABLE_ACCESSIBILITY_PROMPT = Line(ENABLE_ACCESSIBILITY_SPEECH, ENABLE_ACCESSIBILITY_SPEECH)

    /** Spoken immediately with QUEUE_FLUSH when the user taps "Saathi chalu karein" to open Accessibility Settings. */
    /** Spoken as soon as SaathiAccessibilityService.onServiceConnected() fires. */
    const val ACCESSIBILITY_CONNECTED_SPEECH = "Saathi chalu ho gaya hai"
    val ACCESSIBILITY_CONNECTED = Line(ACCESSIBILITY_CONNECTED_SPEECH, "साथी चालू हो गया है।")
    val DONE = Line("Bahut badhiya! Ho gaya.", "बहुत बढ़िया! हो गया।")
    val UNSURE = Line(
        "Mujhe ye screen samajh nahi aa rahi. Ek baar peeche jaake phir try karein?",
        "मुझे ये screen समझ नहीं आ रही। एक बार पीछे जाके फिर try करें?",
    )
    val CANNOT_SEE = Line(
        "Main ye screen dekh nahi sakti, aap mujhe batao kya likha hai.",
        "मैं ये screen देख नहीं सकती, आप मुझे बताओ क्या लिखा है।",
    )
    val PAYMENT = Line(
        "Ye payment ka screen hai, main ise nahi dekhti. Aap batao kya likha hai.",
        "ये payment का screen है, मैं इसे नहीं देखती। आप बताओ क्या लिखा है।",
    )
    val OTP_WARNING = Line(
        "OTP kabhi kisi ko mat batana, mujhe bhi nahi.",
        "OTP कभी किसी को मत बताना, मुझे भी नहीं।",
    )
    val LISTEN_FAILED = Line(
        "Mujhe sunai nahi diya. Bolo button dabakar phir se boliye.",
        "मुझे सुनाई नहीं दिया। बोलो button दबाकर फिर से बोलिए।",
    )
    val VOICE_UNAVAILABLE = Line(
        "Abhi main sun nahi paa rahi. Likho button dabakar type kar dijiye.",
        "अभी मैं सुन नहीं पा रही। लिखो button दबाकर type कर दीजिए।",
    )
    val PACK_MISSING = Line(
        "Hindi samajhne ka pack abhi phone mein nahi hai. Tab tak Likho button dabakar type kar dijiye.",
        "Hindi समझने का pack अभी phone में नहीं है। तब तक लिखो button दबाकर type कर दीजिए।",
    )
    val NO_MIC = Line(
        "Mic ki permission chahiye. Saathi app kholkar setup poora kijiye.",
        "Mic की permission चाहिए। साथी app खोलकर setup पूरा कीजिए।",
    )
    val NO_INTERNET = Line(
        "Internet nahi chal raha. Thodi der baad phir try karte hain.",
        "Internet नहीं चल रहा। थोड़ी देर बाद फिर try करते हैं।",
    )
    val NO_KEY = Line(
        "Mera setup abhi poora nahi hua. Bete se API key daalne ko kahiye.",
        "मेरा setup अभी पूरा नहीं हुआ। बेटे से API key डालने को कहिए।",
    )
    /** The Gemini project has no credit left, so trying again will not help. */
    val NO_CREDIT = Line(
        "Abhi main jawab nahi de paa rahi. Bete se kahiye ki Saathi ka setup check karein.",
        "अभी मैं जवाब नहीं दे पा रही। बेटे से कहिए कि साथी का setup check करें।",
    )
    val AI_ERROR = Line(
        "Koi baat nahi, phir se karte hain. Ek baar aur boliye.",
        "कोई बात नहीं, फिर से करते हैं। एक बार और बोलिए।",
    )
    /** Said when the model's reply was invalid or timed out twice in a row. */
    val RETRY_FAILED = Line("Ek second, main phir se dekh rahi hoon.", "एक सेकंड, मैं फिर से देख रही हूँ।")
    /** Asked before an ask-first app such as WhatsApp is read, with Haan and Nahi buttons. */
    val CONSENT = Line(
        "WhatsApp ki screen dekhun? Sirf buttons dekhungi, messages nahi.",
        "WhatsApp की screen देखूँ? सिर्फ़ buttons देखूँगी, messages नहीं।",
    )
    /** The same question, when she already said Haan once during this goal. */
    val CONSENT_AGAIN = Line("Phir se WhatsApp ki screen dekhun?", "फिर से WhatsApp की screen देखूँ?")
    val CONSENT_DECLINED = Line(
        "Theek hai, screen nahi dekhungi. Main bataati hoon, aap karte jaao.",
        "ठीक है, screen नहीं देखूँगी। मैं बताती हूँ, आप करते जाओ।",
    )
    val ACCESS_ENDED = Line("Ab main WhatsApp nahi dekh rahi.", "अब मैं WhatsApp नहीं देख रही।")
    /** Shown in the panel for as long as she has allowed Saathi to read WhatsApp. */
    const val ACCESS_NOTE = "WhatsApp: sirf buttons dekh rahi hoon"

    /** Said when the Edit option is missing from a sent message's menu. */
    val EDIT_TOO_LATE = Line(
        "Edit nahi dikh raha? WhatsApp thodi der tak hi message badalne deta hai.",
        "Edit नहीं दिख रहा? WhatsApp थोड़ी देर तक ही message बदलने देता है।",
    )
    /** Said instead of using a number she spoke during a guide. The number was masked and not sent. */
    val TYPE_NUMBER = Line(
        "Number aap khud type kijiye. Main number nahi sunti.",
        "Number आप खुद type कीजिए। मैं number नहीं सुनती।",
    )

    private val ORDINALS = listOf(
        "pehli" to "पहली", "doosri" to "दूसरी", "teesri" to "तीसरी", "chauthi" to "चौथी",
        "paanchvi" to "पाँचवीं", "chhathi" to "छठी", "saatvi" to "सातवीं", "aathvi" to "आठवीं",
        "nauvi" to "नौवीं", "dasvi" to "दसवीं",
    )

    /**
     * Points her at a button found on the phone. A list row is named by counting from the top,
     * because she may not be able to read the English label. Otherwise she follows the orange ring.
     */
    fun tapHere(row: Int?): Line {
        val ordinal = row?.let { ORDINALS.getOrNull(it - 1) }
        return if (ordinal != null) {
            Line(
                "Upar se ${ordinal.first} line par dabaiye, jahan ghera bana hai.",
                "ऊपर से ${ordinal.second} line पर दबाइए, जहाँ घेरा बना है।",
            )
        } else {
            Line("Jahan ghera bana hai, wahan dabaiye.", "जहाँ घेरा बना है, वहाँ दबाइए।")
        }
    }

    val REMINDER_SET = Line("Theek hai, yaad dila dungi.", "ठीक है, याद दिला दूँगी।")
    val NOTED = Line("Theek hai, maine note kar liya.", "ठीक है, मैंने note कर लिया।")

    /** Asked instead of saving when the model was not sure what she said. */
    fun confirmHeard(roman: String, hindi: String) = Line(
        "Maine suna: $roman. Sahi hai?",
        "मैंने सुना: $hindi। सही है?",
    )

    val SLOWER = Line("Theek hai, ab dheere bolti hoon.", "ठीक है, अब धीरे बोलती हूँ।")
    val FASTER = Line("Theek hai, thoda tez bolti hoon.", "ठीक है, थोड़ा तेज़ बोलती हूँ।")
    val BYE = Line("Theek hai, main yahin hoon.", "ठीक है, मैं यहीं हूँ।")
    val NOTHING_TO_REPEAT = Line("Abhi tak kuch nahi bola. Aap boliye.", "अभी तक कुछ नहीं बोला। आप बोलिए।")
    val TIME_PASSED = Line(
        "Ye samay to nikal gaya. Koi aur samay boliye.",
        "ये समय तो निकल गया। कोई और समय बोलिए।",
    )
    val TIME_UNCLEAR = Line(
        "Kis din aur kitne baje yaad dilaun?",
        "किस दिन और कितने बजे याद दिलाऊँ?",
    )
    val SOUND_NONE = Line(
        "Abhi mujhe koi app awaaz chalata nahi dikh raha.",
        "अभी मुझे कोई app आवाज़ चलाता नहीं दिख रहा।",
    )
    val SOUND_UNKNOWN = Line(
        "Awaaz aa rahi hai, par app ka naam nahi pata chala. Chalo recent apps dekhte hain.",
        "आवाज़ आ रही है, पर app का नाम नहीं पता चला। चलो recent apps देखते हैं।",
    )

    fun scamWarning(son: String): Line {
        val name = son.ifBlank { "bete" }
        val nameHi = son.ifBlank { "बेटे" }
        return Line(
            "Ruko! Ye dhokha ho sakta hai. Code ya paisa mat dena. Pehle $name ko phone kar lo.",
            "रुको! ये धोखा हो सकता है। Code या पैसा मत देना। पहले $nameHi को phone कर लो।",
        )
    }

    fun soundFrom(app: String) = Line(
        "Ye awaaz $app se aa rahi hai. Chalo band karte hain.",
        "ये आवाज़ $app से आ रही है। चलो बंद करते हैं।",
    )

    fun listAdded(items: List<ListItem>): Line {
        val verbRoman = if (items.size > 1) "add kar diye" else "list mein daal diya"
        val verbHindi = if (items.size > 1) "add कर दिए" else "list में डाल दिया"
        return Line(
            "${joinRoman(items)} $verbRoman.",
            "${joinHindi(items)} $verbHindi।",
        )
    }

    fun listRemoved(items: List<ListItem>) = Line(
        "${joinRoman(items)} list se hata diya.",
        "${joinHindi(items)} list से हटा दिया।",
    )

    fun listEmpty() = Line("List abhi khaali hai.", "List अभी खाली है।")

    fun listCleared() = Line("List saaf kar di.", "List साफ़ कर दी।")

    fun listRead(items: List<ListItem>) = Line(
        "List mein hai: ${joinRoman(items)}.",
        "List में है: ${joinHindi(items)}।",
    )

    fun listHas(item: ListItem) = Line(
        "Haan, ${item.name} already list mein hai.",
        "हाँ, ${item.nameHi} already list में है।",
    )

    fun listMissing(item: ListItem) = Line(
        "Nahi, ${item.name} list mein nahi hai.",
        "नहीं, ${item.nameHi} list में नहीं है।",
    )

    fun reminderAlert(title: String, titleHi: String) = Line(
        "Yaad dila rahi hoon: $title",
        "याद दिला रही हूँ: $titleHi",
    )

    fun joinRoman(items: List<ListItem>) =
        items.joinToString(", ") { listOf(it.qty, it.name).filter(String::isNotBlank).joinToString(" ") }

    fun joinHindi(items: List<ListItem>) =
        items.joinToString(", ") { listOf(it.qty, it.nameHi).filter(String::isNotBlank).joinToString(" ") }
}
