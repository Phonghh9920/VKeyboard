package com.vladgba.keyb

import android.util.Log
import java.io.DataOutputStream

open class KeyAction(val ctx: KeybController) {
    fun utf2char(tx: String) {
        split_for(tx, "\\u") { it.toInt(16).toChar().toString() }
    }

    fun char2utfEscape(tx: String) {
        split_for(tx, "") { "\\u" + it[0].code.toString(16).uppercase().padStart(4, '0') }
    }

    fun hex2char(tx: String) {
        split_for(tx, "0x") { it.trim().toInt(16).toChar().toString() }
    }

    fun char2hex(tx: String) {
        split_for(tx, "") { " 0x" + it[0].code.toString(16).uppercase().padStart(4, '0') }
    }

    fun dec2char(tx: String) {
        split_for(tx, " ") { it.toInt().toChar().toString() }
    }

    fun char2dec(tx: String) {
        split_for(tx, "") { " " + it[0].code.toString(10) }
    }

    fun oct2char(tx: String) {
        split_for(tx, " 0") { it.trim().toInt(8).toChar().toString() }
    }

    fun char2oct(tx: String) {
        split_for(tx, "") { " " + it[0].code.toString(8) }
    }

    fun bin2char(tx: String) {
        split_for(tx, "0b") { it.trim().toInt(2).toChar().toString() }
    }

    fun char2bin(tx: String) {
        split_for(tx, "") { " " + it[0].code.toString(2) }
    }

    fun split_for(tx: String, d: String, fn: (String) -> String) {
        val arr = tx.split(d)
        for (i in 0 until arr.size) {
            if (arr[i].trim() == "") continue
            ctx.onText(fn(arr[i]))
        }
    }

    fun suExec(cmd: String) {
        val p = Runtime.getRuntime().exec("su")
        val dos = DataOutputStream(p!!.getOutputStream())
        dos.writeBytes(cmd)
        dos.writeBytes("\nexit\n")
        dos.flush()
        dos.close()
        p.waitFor()
    }
}