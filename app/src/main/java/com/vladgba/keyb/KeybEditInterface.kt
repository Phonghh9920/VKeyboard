package com.vladgba.keyb

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.*
import kotlin.math.max
import kotlin.math.min

class KeybEditInterface(private val c: KeybCtl) {
    private var ePressX: Int = -1
    private var ePressY: Int = -1
    private var editingKey: Key? = null

    fun onPress(currentKey: Key, x: Int, y: Int) {
        editingKey = currentKey
        ePressX = x
        ePressY = y
    }

    fun onRelease(x: Int, y: Int) {
        if (editingKey == null) return

        val xMove = x - ePressX
        val yMove = y - ePressY
        val sense = Settings.num(SENSE_ADDITIONAL_CHARS)
        if (xMove > sense) moveRight()
        else if (xMove < -sense) moveLeft()

        if (yMove > sense) moveBottom()
        else if (yMove < -sense) moveTop()

        if ((xMove in -sense..sense) and (yMove in -sense..sense)) {

            val dialog = Dialog(c.ctx)
            dialog.setContentView(R.layout.key_edit)
            hideAll(dialog)

            dialog.findViewById<RadioButton>(
                when (editingKey!!.str(KEY_MODE)) {
                    KEY_MODE_JOY -> R.id.radio_joy
                    KEY_MODE_META -> R.id.radio_meta
                    KEY_MODE_RANDOM -> R.id.radio_random
                    else -> R.id.radio_popup
                }
            ).isChecked = true

            val groupOne = dialog.findViewById<RadioGroup>(R.id.rad_group)
            val groupTwo = dialog.findViewById<RadioGroup>(R.id.rad_group2)

            dialog.findViewById<Button>(R.id.button_hard).setOnClickListener {
                textEditor(KEY_HARD_PRESS, title = c.ctx.getString(R.string.enter_keycode))
            }

            dialog.findViewById<Button>(R.id.button_hold).setOnClickListener {
                textEditor(KEY_HOLD, title = c.ctx.getString(R.string.enter_keycode))
            }

            dialog.findViewById<Button>(R.id.key_meta_label).setOnClickListener {
                textEditor(KEY_KEY, title = c.ctx.getString(R.string.enter_text))
            }

            dialog.findViewById<Button>(R.id.meta_keycode).setOnClickListener {
                textEditor(KEY_CODE, title = c.ctx.getString(R.string.enter_keycode))
            }

            dialog.findViewById<Button>(R.id.meta_mask).setOnClickListener {
                textEditor(KEY_MOD_META, title = c.ctx.getString(R.string.enter_meta_code))
            }

            dialog.findViewById<Button>(R.id.button_layout).setOnClickListener {
                textEditor(KEY_LAYOUT, title = c.ctx.getString(R.string.enter_layout_name))
            }

            dialog.findViewById<Button>(R.id.button_sound).setOnClickListener {

            }

            dialog.findViewById<ImageButton>(R.id.button_delete).setOnClickListener {
                var dialogDel: AlertDialog? = null
                val builder = AlertDialog.Builder(c.ctx)
                builder.setTitle(R.string.confirm_title)
                builder.setMessage(R.string.confirm_delete_key)
                builder.setPositiveButton(R.string.yes) { _, _ ->
                    val row = editingKey!!.row
                    row.keys.remove(editingKey)
                    row.calcWidth()
                    c.view.reload()
                    dialog.cancel()
                }

                builder.setNeutralButton(R.string.delete_row) { _, _ ->
                    val row = editingKey!!.row
                    val layout = row.layout
                    layout.rows.remove(row)

                    layout.calcY()
                    c.view.repaintKeyb()

                    c.view.reload()
                    dialog.cancel()
                }
                builder.setNegativeButton(R.string.no) { _, _ -> dialogDel?.cancel() }
                dialogDel = builder.create()
                dialogDel.show()
                dialogDel.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
                dialogDel.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.RED)
            }

            dialog.findViewById<Button>(R.id.button_edit_row).setOnClickListener {
                val builder = AlertDialog.Builder(c.ctx)
                builder.setTitle(R.string.row_preferences)
                builder.setView(R.layout.row_edit)
                builder.setOnDismissListener { c.view.reload() }
                val dialogRow = builder.create()
                dialogRow.show()
                dialogRow.findViewById<ImageButton>(R.id.button_delete).setOnClickListener {
                    val builderDel = AlertDialog.Builder(c.ctx)
                    builderDel.setTitle(R.string.confirm_title)
                    builderDel.setMessage(R.string.confirm_delete_row)
                    builderDel.setPositiveButton(R.string.yes) { _, _ ->
                        val layout = editingKey!!.row.layout
                        layout.rows.remove(editingKey!!.row)
                        layout.calcY()
                        c.view.reload()
                        dialog.cancel() // close all
                    }

                    builderDel.setNegativeButton(R.string.no) { d, _ -> d.cancel() }
                    val dialogDel = builderDel.create()
                    dialogDel.show()
                    dialogDel.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
                }

                colorSettings(dialogRow, editingKey!!.row)
                bindTextarea(dialogRow, R.id.row_height, ROW_HEIGHT, editingKey!!.row)
                visibilityCheckbox(dialogRow, editingKey!!.row)
            }

            dialog.findViewById<Button>(R.id.button_style).setOnClickListener {
                val builder = AlertDialog.Builder(c.ctx)
                builder.setView(R.layout.key_style_edit)

                val dialogStyle = builder.create()
                dialogStyle.show()
                dialogStyle.findViewById<EditText>(R.id.text_width).text = edit(editingKey!!.str(KEY_WIDTH))
                dialogStyle.findViewById<EditText>(R.id.text_height).text = edit(editingKey!!.row.str(ROW_HEIGHT))
                dialogStyle.setOnDismissListener {
                    bindTextarea(dialogStyle, R.id.text_width, KEY_WIDTH, editingKey!!)
                    bindTextarea(dialogStyle, R.id.text_height, ROW_HEIGHT, editingKey!!.row)
                    editingKey!!.row.calcHeight()
                    c.keybLayout!!.calcY()
                    c.view.reload()
                }
                borderCheckbox(dialogStyle, R.id.checkbox_border_top, "t")
                borderCheckbox(dialogStyle, R.id.checkbox_border_left, "l")
                borderCheckbox(dialogStyle, R.id.checkbox_border_right, "r")
                borderCheckbox(dialogStyle, R.id.checkbox_border_bottom, "b")

                visibilityCheckbox(dialogStyle, editingKey!!)

                colorSettings(dialogStyle, editingKey!!)
            }

            // TODO: Key style presets (normal, modifier, additional etc.)

            // TODO: Sounds menu
            // TODO: Sounds manage menu?

            keyModeSetup(groupOne, groupTwo)

            showChecked(dialog, R.id.radio_joy, R.id.edit_joy, KEY_MODE_JOY)
            showChecked(dialog, R.id.radio_meta, R.id.edit_meta, KEY_MODE_META)
            showChecked(dialog, R.id.radio_popup, R.id.edit_popup, KEY_MODE_POPUP)
            showChecked(dialog, R.id.radio_random, R.id.edit_random, KEY_MODE_RANDOM)

            midKeyEditing(dialog.findViewById(R.id.button_midpopup))
            midKeyEditing(dialog.findViewById(R.id.button_midjoy))

            joyActionEdit(dialog, R.id.button22, KEY_TOP_ACTION, c.ctx.getString(R.string.enter_keycode_swipe_top))
            joyActionEdit(dialog, R.id.button42, KEY_LEFT_ACTION, c.ctx.getString(R.string.enter_keycode_swipe_left))
            joyActionEdit(dialog, R.id.button52, KEY_RIGHT_ACTION, c.ctx.getString(R.string.enter_keycode_swipe_right))
            joyActionEdit(
                dialog,
                R.id.button72,
                KEY_BOTTOM_ACTION,
                c.ctx.getString(R.string.enter_keycode_swipe_bottom)
            )

            for ((i, extLink) in arrayOf(
                R.id.button1,
                R.id.button2,
                R.id.button3,
                R.id.button4,
                R.id.button5,
                R.id.button6,
                R.id.button7,
                R.id.button8,
            ).withIndex()) {
                val ext = dialog.findViewById<Button>(extLink)
                ext.text = editingKey!!.str(i)
                ext.setOnClickListener {
                    // TODO: textEditor(...)
                    val builder = AlertDialog.Builder(c.ctx)
                    builder.setTitle("Enter Text")
                    val input = EditText(c.ctx)
                    input.text = edit(editingKey!!.str(i))
                    input.inputType = InputType.TYPE_CLASS_TEXT
                    builder.setView(input)

                    builder.setPositiveButton(android.R.string.ok) { _, _ ->
                        editingKey!![i] = input.text.toString()
                        c.view.repaintKeyb()
                        c.view.invalidate()
                        ext.text = input.text.toString()
                    }
                    builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
                    builder.show()
                }
            }
            dialog.show()
        }

        c.view.repaintKeyb()
        c.view.invalidate()
        Log.d("move", "$xMove; $yMove")
    }

    private fun colorSettings(d: AlertDialog, node: Flexaml.FxmlNode) {
        val btn = d.findViewById<Button>(R.id.button_edit_colors)
        btn.setOnClickListener {

            val dialogBuilder = AlertDialog.Builder(c.ctx)

            dialogBuilder.setTitle("Select a Color")
            dialogBuilder.setView(R.layout.color_settings)
            val dialog = dialogBuilder.create()

            dialogBuilder.setNegativeButton("Cancel") { _, _ ->
                dialog.dismiss()
            }

            dialog.show()

            for (i in COLORS) {
                val item = View.inflate(c.ctx, R.layout.color_settings_item, null)
                item.findViewById<TextView>(R.id.color_name).text = i
                val currColorValue = node.str(i)
                val hexColor = try {
                    Color.parseColor("#$currColorValue")
                } catch (_: Exception) {
                    0
                }
                val itemPreview = item.findViewById<View>(R.id.color_preview).apply { setBackgroundColor(hexColor) }
                dialog.findViewById<LinearLayout>(R.id.scroll_colors).addView(item)

                bindColorPicker(item, i, hexColor, node, itemPreview)
            }
        }
    }

    private fun bindColorPicker(
        item: View,
        i: String,
        hexColor: Int,
        node: Flexaml.FxmlNode,
        preview: View
    ) {
        item.setOnClickListener {
            c.log("$i: " + hexColor.toUInt().toString(16))
            ColorPicker(c.ctx, hexColor, true, object : ColorPicker.ColorPickerListener {
                override fun onOk(dialog: ColorPicker, color: Int) {
                    val newColor = color.toUInt().toString(16)
                    node[i] = newColor
                    preview.setBackgroundColor(color)
                    bindColorPicker(item, i, color, node, preview)
                    c.view.reload()
                }

                override fun onCancel(dialog: ColorPicker) {}
            }).show()
        }
    }

    private fun bindTextarea(d: AlertDialog, id: Int, key: String, node: Flexaml.FxmlNode) {
        node[key] = d.findViewById<EditText>(id).text.toString()
    }

    private fun keyModeSetup(groupOne: RadioGroup, groupTwo: RadioGroup) {
        val checkedChangeListener = RadioGroup.OnCheckedChangeListener { group, checkedId ->
            val checkedButton = group.findViewById<RadioButton>(checkedId)
            if (checkedButton?.isChecked == true) {
                if (group == groupOne) groupTwo.clearCheck()
                if (group == groupTwo) groupOne.clearCheck()
            }
        }

        groupOne.setOnCheckedChangeListener(checkedChangeListener)
        groupTwo.setOnCheckedChangeListener(checkedChangeListener)
    }

    private fun visibilityCheckbox(dialogStyle: AlertDialog, node: Flexaml.FxmlNode) {
        val cb = dialogStyle.findViewById<CheckBox>(R.id.checkbox_visible)
        if (node.bool(KEY_VISIBLE)) cb.isChecked = true
        cb.setOnClickListener {
            val curr = node.bool(KEY_VISIBLE, true)
            if (!curr && cb.isChecked) node[KEY_VISIBLE] = "1"
            else if (curr && !cb.isChecked) node[KEY_VISIBLE] = "0"
        }
    }

    private fun borderCheckbox(dialogStyle: AlertDialog, id: Int, tag: String) {
        val cb = dialogStyle.findViewById<CheckBox>(id)
        if (editingKey!!.str(KEY_HIDE_BORDERS).indexOf(tag) < 0) cb.isChecked = true
        cb.setOnClickListener {
            val curr = editingKey!!.str(KEY_HIDE_BORDERS)
            if (curr.indexOf(tag) < 0 && !cb.isChecked) editingKey!![KEY_HIDE_BORDERS] = curr + tag
            else if (curr.indexOf(tag) >= 0 && cb.isChecked) editingKey!![KEY_HIDE_BORDERS] = curr.replace(tag, "")
        }
    }

    private fun joyActionEdit(dialog: Dialog, btnId: Int, propName: String, title: String) {
        val top = dialog.findViewById<Button>(btnId)
        top.text = edit(editingKey!!.str(propName))
        top.setOnClickListener { textEditor(propName, top, title) }
    }

    private fun textEditor(node: String, btn: Button? = null, title: String) {
        val builder = AlertDialog.Builder(c.ctx)
        builder.setTitle(title)
        val input = EditText(c.ctx)
        input.text = edit(editingKey!!.str(node))
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            editingKey!![node] = input.text.toString()
            c.view.repaintKeyb()
            c.view.invalidate()
            btn?.text = input.text.toString()
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun midKeyEditing(et: Button) {
        et.text = edit(editingKey!!.str(KEY_KEY))

        et.setOnClickListener {
            val builder = AlertDialog.Builder(c.ctx).apply { setTitle(R.string.title_mid_action) }
            val input = EditText(c.ctx)
            input.text = Editable.Factory.getInstance().newEditable(editingKey!!.str(KEY_KEY))
            input.inputType = InputType.TYPE_CLASS_TEXT
            val v = View.inflate(c.ctx, R.layout.mid_key_edit, null)

            val midLabel = v.findViewById<EditText>(R.id.mid_label).apply {
                text = edit(editingKey!!.str(KEY_KEY))
                setOnClickListener { editingKey!!.params[KEY_KEY] = text.toString() }
            }
            val midcode = v.findViewById<EditText>(R.id.mid_code).apply {
                text = edit(editingKey!!.str(KEY_CODE))
                setOnClickListener {
                    editingKey!!.params[KEY_CODE] = text.toString()
                }
            }
            builder.setView(v)

            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                val label = midLabel.text.toString()
                editingKey!![KEY_KEY] = label
                editingKey!![KEY_CODE] = midcode.text.toString()
                et.text = edit(label)
                c.view.repaintKeyb()
                c.view.invalidate()
            }

            builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            builder.show()
        }
    }

    private fun edit(s: String) = Editable.Factory.getInstance().newEditable(s)

    private fun showChecked(dialog: Dialog, rb: Int, show: Int, mode: String) {
        val r = dialog.findViewById<RadioButton>(rb)
        if (r.isChecked) dialog.findViewById<LinearLayout>(show).visibility = View.VISIBLE
        r.setOnClickListener {
            hideAll(dialog)
            if (r.isChecked) {
                editingKey!![KEY_MODE] = mode
                dialog.findViewById<LinearLayout>(show).visibility = View.VISIBLE
            }
        }
    }

    private fun hideAll(dialog: Dialog) {
        dialog.findViewById<LinearLayout>(R.id.edit_joy).visibility = View.GONE
        dialog.findViewById<LinearLayout>(R.id.edit_meta).visibility = View.GONE
        dialog.findViewById<LinearLayout>(R.id.edit_popup).visibility = View.GONE
        dialog.findViewById<LinearLayout>(R.id.edit_random).visibility = View.GONE
    }

    private fun moveRight() {
        val row = editingKey!!.row
        val keyIndex = row.keys.indexOf(editingKey)
        if (row.keys.size > keyIndex + 1) moveHorizontally(row, keyIndex + 1, keyIndex)
    }

    private fun moveLeft() {
        val row = editingKey!!.row
        val keyIndex = row.keys.indexOf(editingKey)
        if (keyIndex > 0) moveHorizontally(row, keyIndex - 1, keyIndex)
    }

    private fun moveBottom() {
        val row = editingKey!!.row
        val rowIndex = c.keybLayout!!.rows.indexOf(row)
        if (c.keybLayout!!.rows.size > rowIndex + 1) moveVertically(
            row,
            c.keybLayout!!.rows[rowIndex + 1],
            row.keys.indexOf(editingKey)
        )
    }

    private fun moveTop() {
        val row = editingKey!!.row
        val rowIndex = c.keybLayout!!.rows.indexOf(row)
        if (rowIndex > 0) moveVertically(row, c.keybLayout!!.rows[rowIndex - 1], row.keys.indexOf(editingKey))
    }

    private fun moveHorizontally(row: KeybLayout.Row, prev: Int, keyIndex: Int) {
        row.keys[keyIndex] = row.keys[prev]
        row.keys[prev] = editingKey!!

        val tmp = row[keyIndex]
        row[keyIndex] = row[prev]
        row[prev] = tmp
        row.calcWidth()
    }

    private fun moveVertically(row: KeybLayout.Row, otherRow: KeybLayout.Row, keyIndex: Int) {
        editingKey!!.y = otherRow.y

        otherRow.keys.add(min(keyIndex, max(otherRow.keys.lastIndex, 0)), editingKey!!)
        row.keys.removeAt(keyIndex)

        otherRow.childs.add(min(keyIndex, max(otherRow.keys.lastIndex, 0)), row.childs[keyIndex])
        row.childs.removeAt(keyIndex)

        editingKey!!.row = otherRow
        otherRow.calcWidth()
        row.calcWidth()
    }
}