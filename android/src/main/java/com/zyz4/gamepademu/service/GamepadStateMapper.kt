package com.zyz4.gamepademu.service

import com.zyz4.gamepademu.model.GamepadState
import com.zyz4.gamepademu.model.TargetPlatform
import com.zyz4.gamepademu.proto.GamepadInput

object GamepadStateMapper {

    fun map(input: GamepadInput, target: TargetPlatform = TargetPlatform.WINDOWS): ByteArray {
        return when (target) {
            TargetPlatform.WINDOWS -> mapWindows(input)
            TargetPlatform.ANDROID -> mapClassicAndroid(input)
        }
    }

    private fun mapWindows(input: GamepadInput): ByteArray {
        var buttons = input.buttons.toInt() and 0x1FFFF
        buttons = buttons or mapDpadBits(input.dpad)
        val report = ByteArray(11)
        report[0] = (buttons and 0xFF).toByte()
        report[1] = ((buttons shr 8) and 0xFF).toByte()
        report[2] = ((buttons shr 16) and 0xFF).toByte()

        val lx = input.leftStickX; report[3] = (lx and 0xFF).toByte(); report[4] = ((lx shr 8) and 0xFF).toByte()
        val ly = input.leftStickY; report[5] = (ly and 0xFF).toByte(); report[6] = ((ly shr 8) and 0xFF).toByte()
        val rx = input.rightStickX; report[7] = (rx and 0xFF).toByte(); report[8] = ((rx shr 8) and 0xFF).toByte()
        val ry = input.rightStickY; report[9] = (ry and 0xFF).toByte(); report[10] = ((ry shr 8) and 0xFF).toByte()
        return report
    }

    private fun mapClassicAndroid(input: GamepadInput): ByteArray {
        val report = ByteArray(7)
        val bits = input.buttons.toInt() and 0xFFF  // 12 buttons

        report[0] = (bits and 0xFF).toByte()
        report[1] = ((bits shr 8) and 0x0F).toByte()
        report[2] = (mapDpadHatAndroid(input.dpad) and 0x0F).toByte()

        report[3] = scaleAxis(input.leftStickX)
        report[4] = scaleAxis(input.leftStickY)
        report[5] = scaleAxis(input.rightStickX)
        report[6] = scaleAxis(input.rightStickY)
        return report
    }

    private fun scaleAxis(value: Int): Byte {
        return (value * 127 / 32767).coerceIn(-127, 127).toByte()
    }

    /** 0=center, 1=N, 2=NE, 3=E, 4=SE, 5=S, 6=SW, 7=W, 8=NW */
    private fun mapDpadHatAndroid(dpad: Int): Int {
        return when (dpad) {
            GamepadState.DPAD_UP -> 1
            GamepadState.DPAD_UP_RIGHT -> 2
            GamepadState.DPAD_RIGHT -> 3
            GamepadState.DPAD_DOWN_RIGHT -> 4
            GamepadState.DPAD_DOWN -> 5
            GamepadState.DPAD_DOWN_LEFT -> 6
            GamepadState.DPAD_LEFT -> 7
            GamepadState.DPAD_UP_LEFT -> 8
            else -> 0
        }
    }

    private fun mapDpadBits(dpad: Int): Int {
        return when (dpad) {
            GamepadState.DPAD_UP -> GamepadState.DPAD_BIT_UP
            GamepadState.DPAD_UP_RIGHT -> GamepadState.DPAD_BIT_UP or GamepadState.DPAD_BIT_RIGHT
            GamepadState.DPAD_RIGHT -> GamepadState.DPAD_BIT_RIGHT
            GamepadState.DPAD_DOWN_RIGHT -> GamepadState.DPAD_BIT_DOWN or GamepadState.DPAD_BIT_RIGHT
            GamepadState.DPAD_DOWN -> GamepadState.DPAD_BIT_DOWN
            GamepadState.DPAD_DOWN_LEFT -> GamepadState.DPAD_BIT_DOWN or GamepadState.DPAD_BIT_LEFT
            GamepadState.DPAD_LEFT -> GamepadState.DPAD_BIT_LEFT
            GamepadState.DPAD_UP_LEFT -> GamepadState.DPAD_BIT_UP or GamepadState.DPAD_BIT_LEFT
            else -> 0
        }
    }

}
