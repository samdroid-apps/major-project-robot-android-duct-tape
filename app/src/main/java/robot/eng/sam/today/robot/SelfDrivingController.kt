package robot.eng.sam.today.robot

import java.util.*

private enum class State {
    FORWARD,
    TURNING,
    BACKTRACKING
}


// class that encapsulates the logic for the self driving system
class SelfDrivingController(setMotorSpeedsCallback: (Float, Float) -> Unit) {
    private val setMotorSpeeds = setMotorSpeedsCallback
    // using a state machine with these states would be nice, but was not fully implemented
    private var state = State.FORWARD
    private var mTimer: Timer? = null

    var aimOrientation = 0f
    private var currentOrientation = 0f

    // called ever 10Hz
    fun tick() {
        if (state == State.FORWARD) {
            doTickForward()
        }
        // TODO: implement remaining states
    }

    fun updateRot(rot: Float) {
        currentOrientation = rot
    }

    private fun doTickForward() {
        val drift = currentOrientation - aimOrientation
        // drift is in degrees
        // if it is drifting by 20deg, then one motor should be put into reverse
        // so that the robot turns sharply
        val ldrift = if (drift < 0) -drift else 0f
        val rdrift = if (drift > 0) drift else 0f
        setMotorSpeeds(
                1.0f - 2.0f*(ldrift / 20),
                1.0f - 2.0f*(rdrift / 20)
        )
    }

    // start a timer to tick at 10Hz
    fun start() {
        // clear timer in case one is running
        stop()

        mTimer = Timer()
        mTimer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                tick()
            }
        }, 0, 100)
    }

    // stop the timer
    fun stop() {
        if (mTimer != null) {
            mTimer?.cancel()
            mTimer = null
        }
    }

}