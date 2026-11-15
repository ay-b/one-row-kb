package com.example.onerow

/** When does a typed punctuation mark take back the space before it? */
object SpacePolicy {
    /**
     * [twoBefore] is the text right before the caret (up to two characters), [wasAuto] says the space there was inserted
     * automatically (after a swiped word or a picked guess) rather than typed by the user.
     * "word ." becomes "word." whichever way the space got there, but ":" and ";" only take back an automatic space:
     * a space the user typed before them is probably the start of a smiley (" :) ", " ;-)").
     */
    fun cutsSpaceBefore(ch: Char, twoBefore: String, wasAuto: Boolean): Boolean {
        if (twoBefore.length != 2 || twoBefore[1] != ' ' || twoBefore[0].isWhitespace()) return false
        return wasAuto || ch !in ":;"
    }
}
