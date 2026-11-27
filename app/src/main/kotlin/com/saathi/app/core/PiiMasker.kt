package com.saathi.app.core

/**
 * Masks personal details she says aloud, often while filling a form, before her words leave the
 * phone: ID numbers (PAN, voter ID, passport and other letter-and-digit IDs) become [id], her
 * name after "mera naam ... hai" and similar becomes [name], and a street address becomes
 * [address]. Numbers are masked separately by [PrivacyFilter.filterSpeech].
 *
 * How-to questions stay untouched: "aadhar mei address kaise change krte h", "naam galat hai
 * kaise theek karein", "pin code kahan daalna hai". A name or address is masked only after a
 * phrase that introduces one, or, for an address, when address words come with a house number.
 *
 * The backend runs the same rules again (scrub_pii in backend/main.py). Change both together.
 */
object PiiMasker {
    const val ID = "[id]"
    const val NAME = "[name]"
    const val ADDRESS = "[address]"

    // PAN: five letters, four digits, a letter. Voter ID: three capitals and seven digits.
    // Passport: a capital and seven digits. Any other word with letters and 4+ digits.
    private val PAN = Regex("(?i)\\b[a-z]{5}\\s?\\p{Nd}{4}\\s?[a-z]\\b")
    private val VOTER_OR_PASSPORT = Regex("\\b(?:[A-Z]{3}|[A-Z])\\s?\\p{Nd}{7}\\b")
    private val ALNUM_ID = Regex("\\b[\\p{L}\\p{Nd}]{6,}\\b")

    fun maskIds(input: String): String {
        var text = PAN.replace(input, ID)
        text = VOTER_OR_PASSPORT.replace(text, ID)
        return ALNUM_ID.replace(text) { m ->
            val v = m.value
            if (v.count(Char::isDigit) >= 4 && v.any(Char::isLetter)) ID else v
        }
    }

    /** Words that are never part of a name or the start of an address: question, problem and filler words. */
    private val STOP = setOf(
        "hai", "he", "h", "hain", "is", "tha", "thi", "ka", "ki", "ke", "ko", "kya", "kaise", "kese", "kaisey",
        "kahan", "kaha", "kidhar", "kyun", "kyu", "galat", "galt", "sahi", "theek", "thik", "nahi", "nhi", "na",
        "mein", "me", "mei", "mai", "main", "par", "pe", "se", "aur", "and", "or", "the", "a", "an", "wrong",
        "right", "not", "correct", "incorrect", "spelling", "change", "badal", "badalna", "badalni", "badla",
        "badli", "likh", "likho", "likha", "likhi", "likhna", "likhiye", "likhen", "bhar", "bharo", "bhara",
        "bharna", "bhariye", "daal", "daalo", "daala", "daalna", "dalna", "daaliye", "type", "wala", "wali",
        "wale", "box", "form", "card", "aadhaar", "aadhar", "adhar", "pan", "update", "karna", "karni", "karu",
        "karun", "karein", "kare", "kar", "ho", "hua", "hui", "gaya", "gayi", "dikh", "dikha", "dikhta",
        "dikhega", "please", "hoon", "hu", "hun", "bhi", "to", "toh", "ji", "kuch", "yeh", "ye", "woh", "wo",
        "jo", "iska", "uska", "isme", "usme", "mera", "meri", "mere", "tera", "aapka", "your", "my", "what",
        "how", "where", "in", "on", "of", "for", "showing", "show", "missing", "hindi", "english", "pata",
        "address", "number", "no", "chahiye", "chal", "chala", "lagao", "batao", "bataiye", "kaun", "kab",
        "है", "गलत", "सही", "क्या", "कैसे", "कहाँ", "कहां", "नहीं", "में", "का", "की", "के", "को", "और", "बदल", "बदलना",
    )

    private val WORD = Regex("^\\s*([\\p{L}\\p{M}.']+)")
    private val NAME_END = Regex("(?i)^\\s*(?:hai|he|h|hain|है)(?=[\\s,.।!?]|$)|^\\s*[,.।!?]|^\\s*$")
    private val NAME_WRITE = Regex("(?i)^\\s*(?:likho|likh|likhiye|likhna|bharo|bhar|bhariye|daalo|daal|daaliye|type|लिखो|लिखिए|भरो|डालो)(?=[\\s,.।!?]|$)")

    private class Trigger(val regex: Regex, val maxWords: Int, val end: Regex?)

    private val NAME_TRIGGERS = listOf(
        // "mera naam Sunita Sharma hai", "my name Sunita"
        Trigger(Regex("(?i)\\b(?:mera|meri|mere|my)\\s+(?:naam|name)\\b(?!\\s+is\\b)"), 3, NAME_END),
        // "my name is Sunita Sharma"
        Trigger(Regex("(?i)\\bmy\\s+name\\s+is\\b"), 3, null),
        // "naam Sunita likho"
        Trigger(Regex("(?i)\\b(?:naam|name)\\b"), 3, NAME_WRITE),
        // "c/o Ramesh Kumar", "care of Ramesh"
        Trigger(Regex("(?i)(?<![\\p{L}])(?:[csdw]\\s*/\\s*o|care\\s+of)\\b\\s*[:\\-]?"), 3, null),
        Trigger(Regex("(?:मेरा|मेरी)\\s+नाम"), 3, NAME_END),
        Trigger(Regex("नाम"), 3, NAME_WRITE),
    )

    /** "mera address 12 MG road hai": everything after it, to the end of the sentence. */
    private val ADDRESS_PHRASE = Regex("(?i)\\b(?:mera|meri|my)\\s+(?:address|pata|patta)\\b\\s*(?:hai|is|:)?|(?:मेरा)\\s+पता\\s*(?:है)?")
    private val SENTENCE_END = Regex("[.?!।]")

    fun maskNames(input: String): String {
        var text = input
        for (trigger in NAME_TRIGGERS) {
            var from = 0
            while (true) {
                val m = trigger.regex.find(text, from) ?: break
                var pos = m.range.last + 1
                var taken = 0
                while (taken < trigger.maxWords) {
                    val w = WORD.find(text.substring(pos)) ?: break
                    if (w.groupValues[1].lowercase().trim('.', '\'') in STOP) break
                    pos += w.range.last + 1
                    taken++
                }
                val ends = trigger.end == null || trigger.end.containsMatchIn(text.substring(pos))
                if (taken > 0 && ends) {
                    val nameStart = m.range.last + 1
                    val lead = text.substring(nameStart, pos).takeWhile(Char::isWhitespace)
                    text = text.substring(0, nameStart) + lead + NAME + text.substring(pos)
                    from = nameStart + lead.length + NAME.length
                } else {
                    from = m.range.last + 1
                }
            }
        }
        return text
    }

    fun maskAddressPhrase(input: String): String {
        val m = ADDRESS_PHRASE.find(input) ?: return input
        val rest = input.substring(m.range.last + 1)
        val first = WORD.find(rest)?.groupValues?.get(1)?.lowercase()
        val restStart = rest.indexOfFirst { !it.isWhitespace() }
        if (restStart < 0) return input
        // "mera address kaise badlun", "mera pata nahi": a question, not an address.
        if (first != null && first.trim('.', '\'') in STOP) return input
        val end = SENTENCE_END.find(rest, restStart)?.range?.first ?: rest.length
        if (rest.substring(restStart, end).isBlank()) return input
        return input.substring(0, m.range.last + 1) + rest.substring(0, restStart) + ADDRESS + rest.substring(end)
    }

    private val ADDRESS_START = setOf(
        "house", "flat", "plot", "door", "hno", "h.no", "ward", "gali", "lane", "sector", "makaan", "makan",
        "floor", "apartment", "apt", "building", "bldg", "मकान", "फ्लैट", "प्लॉट", "गली", "वार्ड", "सेक्टर", "ಮನೆ",
    )
    private val ADDRESS_END = setOf(
        "road", "rd", "marg", "nagar", "colony", "layout", "extension", "vihar", "puram", "street", "enclave",
        "chowk", "bazaar", "bazar", "society", "pincode", "रोड", "मार्ग", "नगर", "कॉलोनी", "विहार", "पुरम",
        "ರಸ್ತೆ", "ನಗರ", "ಲೇಔಟ್", "ಬಡಾವಣೆ",
    )
    // "3rd cross", "12th main", "2nd stage": address words only right after a number, since
    // "main" is also Hindi for "I".
    private val AFTER_NUMBER = setOf("cross", "main", "stage", "phase", "block", "ಕ್ರಾಸ್", "ಮೇನ್")
    private val LOCALITY_ENDING = listOf("nagar", "puram", "halli", "layout", "vihar", "palya", "colony", "नगर", "ನಗರ")
    private val LINK = setOf("no", "no.", "number", "num", "#", "-", "/", "code", "नंबर")
    // Words that end an address run even before an address word: times and common Hindi glue.
    private val ADDRESS_STOP = STOP + setOf(
        "baje", "bajkar", "subah", "shaam", "sham", "raat", "dopahar", "kal", "aaj", "parso", "tak", "jana",
        "jaana", "jaungi", "jaunga", "chalo", "chaliye", "बजे", "सुबह", "शाम", "रात", "कल", "आज",
    )

    private enum class Kind { START, END, AFTER_NUMBER, LOCALITY, DIGIT, LINK, WORD, STOP }

    private fun bare(token: String) = token.trim(',', '.', ';', ':', '?', '!', '।', '"', '\'').lowercase()

    private fun kind(tokens: List<String>, i: Int): Kind {
        val b = bare(tokens[i])
        if (b.isEmpty()) return Kind.LINK
        // "[number]rd": the number mask can swallow "204 3" of "204 3rd".
        if (PrivacyFilter.MASK in b || ID in b || b.any(Char::isDigit)) return Kind.DIGIT
        if (b == "pin" && i + 1 < tokens.size && bare(tokens[i + 1]) == "code") return Kind.END
        if (b in ADDRESS_START) return Kind.START
        if (b in ADDRESS_END) return Kind.END
        if (b in AFTER_NUMBER) return Kind.AFTER_NUMBER
        if (b in LINK) return Kind.LINK
        if (LOCALITY_ENDING.any { b.length > it.length && b.endsWith(it) }) return Kind.LOCALITY
        if (b in ADDRESS_STOP) return Kind.STOP
        return if (LETTERS.matches(b)) Kind.WORD else Kind.STOP
    }

    private val LETTERS = Regex("[\\p{L}\\p{M}.]+")

    private fun placeAt(kinds: List<Kind>, i: Int) = kinds.getOrNull(i).let { it == Kind.END || it == Kind.LOCALITY }

    /** Address words together with a number: "flat 204 3rd cross Indiranagar", "12 MG road". */
    fun maskAddressRuns(input: String): String {
        val tokens = input.split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return input
        val kinds = tokens.indices.map { kind(tokens, it) }
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            var j = i
            var hasPlace = false
            var hasDigit = false
            while (j < tokens.size) {
                val k = kinds[j]
                val accepted = when (k) {
                    Kind.START, Kind.END, Kind.LOCALITY -> { hasPlace = true; true }
                    Kind.DIGIT -> { hasDigit = true; true }
                    Kind.AFTER_NUMBER -> if (j > i && kinds[j - 1] == Kind.DIGIT) { hasPlace = true; true } else false
                    Kind.LINK -> j > i
                    // A name word counts only when an address word follows within two words: "MG road".
                    Kind.WORD -> placeAt(kinds, j + 1) || (kinds.getOrNull(j + 1) == Kind.WORD && placeAt(kinds, j + 2))
                    Kind.STOP -> false
                }
                if (!accepted) break
                j++
                if (SENTENCE_END.containsMatchIn(tokens[j - 1].takeLast(1))) break
            }
            if (j > i && hasPlace && hasDigit) {
                val last = tokens[j - 1]
                val tail = last.takeLastWhile { it == ',' || it == '.' || it == '?' || it == '!' || it == '।' || it == '"' }
                out += ADDRESS + tail
                i = j
            } else {
                out += tokens[i]
                i++
            }
        }
        return out.joinToString(" ")
    }

    /** All of the above, after numbers are masked. */
    fun maskPersonal(input: String): String = maskAddressRuns(maskAddressPhrase(maskNames(input)))
}
