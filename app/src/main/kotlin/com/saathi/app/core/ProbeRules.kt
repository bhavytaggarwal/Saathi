package com.saathi.app.core

import java.util.Locale

/**
 * Which nodes the label probe may write down. The probe exists only in builds made with
 * -Psaathi.labelProbe=true, for collecting WhatsApp's real button labels on a test phone.
 *
 * Chat and message lists hold names, previews and photo descriptions, so nothing inside a list
 * row is ever written. Pop-up menus are lists too, but their rows are labels such as "Edit", so
 * they are kept. Outside lists, only buttons, tappable icons, menu and tab items, and text box
 * hints are written. Plain text, such as a chat's title, never is.
 */
object ProbeRules {
    private val LIST = Regex("(?i)recyclerview|listview|gridview")
    private val MENU = Regex("(?i)menu")
    private val LABEL_CONTAINER = Regex("(?i)menuitemview|tabview|navigationitemview|navigationbaritemview|\\.tab$")
    private val BUTTON = Regex("(?i)button")
    private val ICON = Regex("(?i)imageview")

    /** A list whose rows are skipped with everything inside them. Menus are not skipped. */
    fun skipsRows(className: String?): Boolean =
        className != null && LIST.containsMatchIn(className) && !MENU.containsMatchIn(className)

    /** A menu or tab item. Text inside it is its label, so it may be written. */
    fun isLabelContainer(className: String?): Boolean = className != null && LABEL_CONTAINER.containsMatchIn(className)

    fun keep(className: String?, clickable: Boolean, editable: Boolean, insideLabelContainer: Boolean): Boolean {
        val name = className.orEmpty().lowercase(Locale.ROOT)
        return insideLabelContainer || isLabelContainer(name) || editable ||
            BUTTON.containsMatchIn(name) || (ICON.containsMatchIn(name) && clickable)
    }
}
