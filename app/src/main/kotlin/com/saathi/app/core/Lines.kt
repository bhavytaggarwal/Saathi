package com.saathi.app.core

/** Fixed lines that must work with no AI and no internet. */
object Lines {
    val WAITING = Line("Main ruki hoon, aaram se karo.", "मैं रुकी हूँ, आराम से करो।")
    val PRIVACY = Line(
        "Ab apna PIN daalo, main thodi der ke liye chhup jaati hoon.",
        "अब अपना PIN डालो, मैं थोड़ी देर के लिए छुप जाती हूँ।",
    )
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
    val AI_ERROR = Line(
        "Koi baat nahi, phir se karte hain. Ek baar aur boliye.",
        "कोई बात नहीं, फिर से करते हैं। एक बार और बोलिए।",
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

    private fun joinRoman(items: List<ListItem>) =
        items.joinToString(", ") { listOf(it.qty, it.name).filter(String::isNotBlank).joinToString(" ") }

    private fun joinHindi(items: List<ListItem>) =
        items.joinToString(", ") { listOf(it.qty, it.nameHi).filter(String::isNotBlank).joinToString(" ") }
}
