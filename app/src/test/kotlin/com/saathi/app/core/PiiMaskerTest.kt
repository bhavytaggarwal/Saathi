package com.saathi.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiMaskerTest {
    private fun said(words: String) = PrivacyFilter.filterSpeech(words).text

    // Digits the recogniser writes with spaces, hyphens or in other scripts.

    @Test fun spacedPinCode() = assertEquals("[address] hai", said("pin code 5 6 0 0 3 4 hai"))

    @Test fun spacedPinCodeIsHidden() = assertFalse(said("5 6 0 0 3 4").any(Char::isDigit))

    @Test fun groupedTwelveDigitAadhaar() = assertEquals("aadhaar [number]", said("aadhaar 1234 5678 9012"))

    @Test fun groupedPhone() = assertEquals("number [number] hai", said("number 98450-12345 hai"))

    @Test fun shortGroups() = assertEquals("[number]", said("56 00 01"))

    @Test fun devanagariDigits() = assertEquals("[number]", said("५६० ०३४"))

    @Test fun kannadaDigits() = assertEquals("[number]", said("೫೬೦೦೩೪"))

    @Test fun smallNumbersStay() {
        assertEquals("step 2 ke baad box 1 dabaun?", said("step 2 ke baad box 1 dabaun?"))
        assertEquals("kal subah 10 baje yaad dilana", said("kal subah 10 baje yaad dilana"))
    }

    // Number words.

    @Test fun englishNumberWords() = assertEquals("[number]", said("nine eight four five zero"))

    @Test fun hindiNumberWords() = assertEquals("[number]", said("ek do teen char paanch"))

    @Test fun threeNumberWordsAreEnough() = assertEquals("mera code [number] hai", said("mera code saat aath nau hai"))

    @Test fun devanagariNumberWords() = assertEquals("[number]", said("नौ आठ चार पाँच"))

    @Test fun kannadaNumberWords() = assertEquals("[number]", said("ಒಂಬತ್ತು ಎಂಟು ನಾಲ್ಕು"))

    @Test fun twoNumberWordsStay() {
        assertEquals("do teen packet maggi daal do", said("do teen packet maggi daal do"))
        assertEquals("ek saath do kaam karo", said("ek saath do kaam karo"))
    }

    // IDs.

    @Test fun pan() = assertEquals("mera pan [id] hai", said("mera pan ABCDE1234F hai"))

    @Test fun panSpacedLowercase() = assertEquals("pan [id]", said("pan abcde 1234 f"))

    @Test fun voterId() = assertEquals("voter id [id]", said("voter id ABC1234567"))

    @Test fun passport() = assertEquals("passport [id]", said("passport J8369854"))

    // Names.

    @Test fun meraNaam() = assertEquals("mera naam [name] hai", said("mera naam Sunita Sharma hai"))

    @Test fun myNameIs() = assertEquals("my name is [name] and I need help", said("my name is Sunita Sharma and I need help"))

    @Test fun naamLikho() = assertEquals("naam [name] likho", said("naam Ramesh Kumar likho"))

    @Test fun careOf() {
        assertEquals("c/o [name]", said("c/o Ramesh Kumar"))
        assertEquals("care of [name], gali", said("care of Ramesh, gali"))
        assertEquals("w/o [name] hai", said("w/o Suresh hai"))
    }

    @Test fun devanagariName() = assertEquals("मेरा नाम [name] है", said("मेरा नाम सुनीता शर्मा है"))

    // Addresses.

    @Test fun bangaloreAddress() = assertEquals("[address]", said("flat 204 3rd cross Indiranagar"))

    @Test fun houseNumberAndRoad() = assertEquals("[address] hai", said("house no 12 MG road hai"))

    @Test fun meraAddress() = assertEquals("mera address [address]", said("mera address Ramesh ji ke ghar ke paas, Gandhi chowk"))

    @Test fun sectorNumber() = assertEquals("[address] mein rehti hoon", said("sector 15 mein rehti hoon"))

    @Test fun pinCodeDigits() = assertTrue(said("pin code 560034").contains("[address]"))

    // How-to questions stay exactly as she said them.

    @Test fun benignQuestionsUntouched() {
        for (q in listOf(
            "aadhar mei address kaise change krte h",
            "naam galat hai kaise theek karein",
            "pin code kahan daalna hai",
            "pin code box kahan hai",
            "mera naam galat likha hai",
            "mera address kaise badlun",
            "mujhe pata nahi",
            "mera pata nahi chal raha",
            "naam kahan likhun",
            "my name is wrong on aadhaar",
            "main kal Gandhi nagar jaungi",
            "kal 5 baje Gandhi nagar jaana hai",
            "main 5 baje uthti hoon",
            "is number ko block karo",
            "pin bhool gayi",
            "flat kaise book karein",
            "doodh list mein daal do",
        )) assertEquals(q, said(q))
    }

    @Test fun pinWordStaysWhenNumberIsMasked() = assertEquals("pin [number] hai", said("pin ek do teen char hai"))
}
