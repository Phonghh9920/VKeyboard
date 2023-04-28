package com.vladgba.keyb

import java.io.DataOutputStream

const val CMD = "sendevent"

const val DOWN = 1
const val UP = 0

const val NO_TRACKING_ID = 0xffffffff

// Event types
const val EV_SYN = 0x00
const val EV_KEY = 0x01
const val EV_REL = 0x02
const val EV_ABS = 0x03
const val EV_MSC = 0x04
const val EV_SW = 0x05
const val EV_LED = 0x11
const val EV_SND = 0x12
const val EV_REP = 0x14
const val EV_FF = 0x15
const val EV_PWR = 0x16
const val EV_FF_STATUS = 0x17

// Synchronization events
const val SYN_REPORT = 0
const val SYN_CONFIG = 1
const val SYN_MT_REPORT = 2
const val SYN_DROPPED = 3

const val BTN_TOUCH = 0x14a

const val ABS_MT_SLOT = 0x2f    // MT slot being modified
const val ABS_MT_TOUCH_MAJOR = 0x30    // Major axis of touching ellipse
const val ABS_MT_TOUCH_MINOR = 0x31    // Minor axis (omit if circular)
const val ABS_MT_WIDTH_MAJOR = 0x32    // Major axis of approaching ellipse
const val ABS_MT_WIDTH_MINOR = 0x33    // Minor axis (omit if circular)
const val ABS_MT_ORIENTATION = 0x34    // Ellipse orientation
const val ABS_MT_POSITION_X = 0x35    // Center X touch position
const val ABS_MT_POSITION_Y = 0x36    // Center Y touch position
const val ABS_MT_TOOL_TYPE = 0x37    // Type of touching device
const val ABS_MT_BLOB_ID = 0x38    // Group a set of packets as a blob
const val ABS_MT_TRACKING_ID = 0x39    // Unique ID of initiated contact
const val ABS_MT_PRESSURE = 0x3a    // Pressure on contact area
const val ABS_MT_DISTANCE = 0x3b    // Contact hover distance
const val ABS_MT_TOOL_X = 0x3c    // Center X tool position
const val ABS_MT_TOOL_Y = 0x3d    // Center Y tool position

object InjectedEvent {
    var DEV = "/dev/input/event4"
    fun press(x: Int, y: Int, tid: Int, btnTouch: Int = -1, slot: Int = -1) {
        val cmd = CmdList()
        if (slot >= 0) cmd += "$CMD $DEV $EV_ABS $ABS_MT_SLOT $slot"
        cmd += "$CMD $DEV $EV_ABS $ABS_MT_TRACKING_ID $tid"
        cmd += "$CMD $DEV $EV_ABS $ABS_MT_PRESSURE 20"
        cmd += "$CMD $DEV $EV_ABS $ABS_MT_TOUCH_MAJOR 2"
        cmd += "$CMD $DEV $EV_ABS $ABS_MT_POSITION_X $x"
        cmd += "$CMD $DEV $EV_ABS $ABS_MT_POSITION_Y $y"
        if (btnTouch >= 0) cmd += "$CMD $DEV $EV_KEY $BTN_TOUCH $btnTouch"
        cmd += "$CMD $DEV $EV_SYN $SYN_REPORT 0"
        cmd(cmd.toString())
    }

    private fun cmd(s: String) {
        val p = Runtime.getRuntime().exec("su")
        val dos = DataOutputStream((p ?: return).outputStream)
        dos.writeBytes("$s\nexit\n")
        dos.flush()
        dos.close()
        p.waitFor()
    }

    fun move(x: Int = -1, y: Int = -1, slot: Int = -1) {
        val cmd = CmdList()
        if (slot >= 0) cmd += "$CMD $DEV $EV_ABS $ABS_MT_SLOT $slot"
        if (x >= 0) cmd += "$CMD $DEV $EV_ABS $ABS_MT_POSITION_X $x"
        if (y >= 0) cmd += "$CMD $DEV $EV_ABS $ABS_MT_POSITION_Y $y"
        cmd += "$CMD $DEV $EV_SYN $SYN_REPORT 0"
        cmd(cmd.toString())
    }

    fun release(btnTouch: Int = -1, slot: Int = -1) {
        val cmd = CmdList()
        if (slot >= 0) cmd += "$CMD $DEV $EV_ABS $ABS_MT_SLOT $slot"
        cmd += "$CMD $DEV $EV_ABS $ABS_MT_TRACKING_ID $NO_TRACKING_ID"
        if (btnTouch >= 0) cmd += "$CMD $DEV $EV_KEY $BTN_TOUCH $btnTouch"
        cmd += "$CMD $DEV $EV_SYN $SYN_REPORT 0"
        cmd(cmd.toString())
    }

    class CmdList {
        private var commands: String = ""

        operator fun plusAssign(s: String) {
            commands += "$s;"
        }

        override fun toString() = commands
    }

}