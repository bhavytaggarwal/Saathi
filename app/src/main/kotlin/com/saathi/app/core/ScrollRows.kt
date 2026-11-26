package com.saathi.app.core

/**
 * Finds the rows of the screen's main scrolling list, such as the list in Settings, so Saathi can
 * say "upar se teesri line" instead of a label she may not be able to read, and knows when the
 * item she needs is further down and she has to scroll.
 */
object ScrollRows {
    /** One visible node. */
    class Node(val box: Box, val scrollable: Boolean, val tappable: Boolean)

    /**
     * rows maps a node's index to its row number, counted from the top of the visible list, and
     * total is the number of visible rows. moreBelow means the list goes on below the screen.
     */
    data class Result(val rows: Map<Int, Int>, val total: Int, val scrolls: Boolean, val moreBelow: Boolean)

    val NONE = Result(emptyMap(), 0, scrolls = false, moreBelow = false)

    /** A row whose edge is this close to the list's bottom edge is cut off, so more follows. */
    private const val EDGE_PX = 4

    fun analyse(nodes: List<Node>, screenHeight: Int): Result {
        // The main list: the largest scrolling container, ignoring small ones such as a row of chips.
        val listIndex = nodes.indices
            .filter { nodes[it].scrollable && nodes[it].box.height * 4 > screenHeight }
            .maxByOrNull { nodes[it].box.width.toLong() * nodes[it].box.height }
            ?: return NONE
        val list = nodes[listIndex].box
        val inside = nodes.indices.filter { it != listIndex && list.contains(nodes[it].box) && !nodes[it].box.isEmpty }

        // A row is tappable and spans most of the list's width. A tappable row inside another
        // row, such as a switch, belongs to that row and is not counted again.
        val wide = inside.filter { nodes[it].tappable && nodes[it].box.width * 10 >= list.width * 6 }
        val rows = wide
            .filter { i -> wide.none { j -> j != i && nodes[j].box.contains(nodes[i].box) && nodes[j].box != nodes[i].box } }
            .sortedWith(compareBy({ nodes[it].box.top }, { it }))
        val numbered = HashMap<Int, Int>()
        var count = 0
        var lastTop = Int.MIN_VALUE
        for (i in rows) {
            val top = nodes[i].box.top
            // Two nodes with the same bounds are one row on the screen.
            if (top != lastTop) count++
            lastTop = top
            numbered[i] = count
        }

        // Android's own row count includes headings, so it would claim more rows than there are.
        // A row cut off by the list's bottom edge is the reliable sign.
        val cutOff = inside.any { nodes[it].box.bottom >= list.bottom - EDGE_PX && nodes[it].box.top < list.bottom }
        return Result(numbered, count, scrolls = true, moreBelow = cutOff)
    }
}
