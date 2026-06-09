package com.zyz4.gamepademu.input

import com.zyz4.gamepademu.model.ControllerMode
import com.zyz4.gamepademu.model.GamepadState
import com.zyz4.gamepademu.proto.GamepadInput

fun GamepadState.toProto(controllerMode: ControllerMode): GamepadInput {
    val protoMode = com.zyz4.gamepademu.proto.ControllerMode.forNumber(controllerMode.ordinal)
        ?: com.zyz4.gamepademu.proto.ControllerMode.XBOX_360
    val builder = GamepadInput.newBuilder()
        .setButtons(buttons.toInt())
        .setLeftStickX(leftStickX.toInt())
        .setLeftStickY(leftStickY.toInt())
        .setRightStickX(rightStickX.toInt())
        .setRightStickY(rightStickY.toInt())
        .setLeftTrigger(leftTrigger)
        .setRightTrigger(rightTrigger)
        .setDpad(dpad)
        .setBatteryLevel(batteryLevel)

    if (protoMode == com.zyz4.gamepademu.proto.ControllerMode.DS4) {
        builder
            .setGyroX(gyroX)
            .setGyroY(gyroY)
            .setGyroZ(gyroZ)
            .setAccelX(accelX)
            .setAccelY(accelY)
            .setAccelZ(accelZ)
            .setTouchpadX(touchpadX)
            .setTouchpadY(touchpadY)
            .setTouchpadTouch(touchpadTouch)
            .setTouchpadClick(touchpadClick)
    }
    return builder.build()
}

object GamepadInputProcessor {

}
