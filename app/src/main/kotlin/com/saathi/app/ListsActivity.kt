package com.saathi.app

import android.app.Activity
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Her lists and reminders. Ticking works here by tap as well as by voice. */
class ListsActivity : Activity() {
    private lateinit var lists: ListsStore
    private lateinit var reminders: ReminderScheduler
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lists = ListsStore(this)
        reminders = ReminderScheduler(this)
        val (scroll, root) = page()
        content = root
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        content.addView(title("Lists and reminders"))
        section("Shopping list", "shopping")
        section("To-do list", "todo")

        content.addView(heading("Reminders"))
        val all = reminders.all().filter { !it.fired }
        if (all.isEmpty()) content.addView(body("Nothing yet. Tell Saathi: \"Roz raat 9 baje dawai yaad dilana\"."))
        all.forEach { reminder ->
            val row = rowCard()
            val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            text.addView(label(reminder.title))
            text.addView(body(reminder.whenText()))
            row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(removeButton("Remove ${reminder.title}") {
                reminders.delete(reminder.id)
                render()
            })
            content.addView(row, wrapWithGap(8))
        }
    }

    private fun section(name: String, list: String) {
        content.addView(heading(name))
        val items = lists.all(list)
        if (items.isEmpty()) {
            content.addView(body(if (list == "shopping") "Empty. Tell Saathi: \"ghar mein aata khatam ho gaya\"." else "Empty."))
            return
        }
        items.forEach { stored ->
            val row = rowCard()
            val itemName = listOf(stored.item.qty, stored.item.name).filter(String::isNotBlank).joinToString(" ")
            val check = CheckBox(this).apply {
                text = itemName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                isChecked = stored.done
                if (stored.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                setOnCheckedChangeListener { _, checked ->
                    lists.setDone(stored.id, checked)
                    render()
                }
            }
            row.addView(check, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(removeButton("Remove $itemName") {
                lists.delete(stored.id)
                render()
            })
            content.addView(row, wrapWithGap(8))
        }
    }

    private fun rowCard() = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(0xFF1F1A14.toInt())
    }

    private fun removeButton(description: String, onClick: () -> Unit) = TextView(this).apply {
        text = "✕"
        contentDescription = description
        gravity = Gravity.CENTER
        minWidth = dp(44)
        minHeight = dp(44)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(0xFF8A7F73.toInt())
        setOnClickListener { onClick() }
    }
}
