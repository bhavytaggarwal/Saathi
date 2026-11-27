package com.saathi.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataTest {
    @Test
    fun readingOutHerOwnIdsOrCredentialsIsCaught() {
        for (words in listOf(
            "mera aadhar number hai",
            "mera aadhar 1234 5678 9012",
            "mera pan number hai",
            "mera account number likho",
            "mera cvv 123 hai",
            "mera password hai sunita",
            "meri pin ek do teen char hai",
            "card number 4111 1111 1111 1111",
            "मेरा आधार नंबर है",
            "my password is sunita",
        )) {
            assertTrue(words, PrivacyFilter.isSharingSensitiveData(words))
        }
    }

    @Test
    fun howToQuestionsAboutThemAreNot() {
        for (words in listOf(
            "Aadhar mei address kaise change krte h",
            "PAN card kaise banate hain",
            "password reset kaise karein",
            "mera pin bhool gayi, kya karun",
            "mera pan card kho gaya hai",
            "mera otp aaya hai, kya karun",
            "aadhar card kahan download hota hai",
            "आधार में पता कैसे बदलें",
            "doodh list mein daal do",
            "kal 9 baje dawai yaad dilana",
        )) {
            assertFalse(words, PrivacyFilter.isSharingSensitiveData(words))
        }
    }
}

class PinEdgeCasesTest {
    /** Every on-phone check that can stop her words before the network, in handleUtterance order. */
    private fun blockedOnPhone(words: String): Boolean {
        val filtered = PrivacyFilter.filterSpeech(words)
        return filtered.warn || PrivacyFilter.isSharingSensitiveData(words) || PrivacyFilter.looksLikeScam(filtered.text)
    }

    @Test
    fun forgettingOrResettingAPinReachesTheModel() {
        for (words in listOf("mera pin bhool gayi", "pin reset kaise karein", "UPI pin kaise badlun", "password bhool gaya")) {
            assertFalse(words, blockedOnPhone(words))
        }
    }

    @Test
    fun readingOutAPinIsStoppedOnThePhone() {
        // A spoken number next to "pin" gets the OTP warning; dictation without one gets the ID warning.
        assertTrue(PrivacyFilter.filterSpeech("mera pin 1234 hai").warn)
        assertTrue(PrivacyFilter.isSharingSensitiveData("mera pin likho"))
        for (words in listOf("mera pin 1234 hai", "mera pin likho", "mera pin ek do teen char hai")) {
            assertTrue(words, blockedOnPhone(words))
        }
    }
}
