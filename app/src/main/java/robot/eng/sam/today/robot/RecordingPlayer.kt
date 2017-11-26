package robot.eng.sam.today.robot

import java.util.*

/**
 * Plays back recordings (lists of messages and timestamps), by passing the messages to the
 * processMessageCallback with the correct timings
 *
 * Assumes the recording list is ordered; that is messages were pushed in the order that they were
 * received when creating the recording
 */
class RecordingPlayer(recording: MutableList<RecordedMessage>,
                      processMessageCallback: (Message) -> Unit) {
    var mStartTime: Long = System.currentTimeMillis()
    val mRecording = recording
    val mProcessCb = processMessageCallback
    var mCurrentIndex = 0
    private var mTimer: Timer? = null

    // the tick callback attempts to dispatch the messages that have passed every 100Hz
    fun tick() {
        val time = System.currentTimeMillis() - mStartTime

        // only check the top messages, as the list is ordered
        var top = mRecording[mCurrentIndex]
        while (top.time <= time) {
            mProcessCb(top.msg)
            mCurrentIndex++
            if (mCurrentIndex > mRecording.lastIndex) {
                this.stop()
                return
            }
            top = mRecording[mCurrentIndex]
        }
    }

    // start&stop functions are the same as in the SelfDrivingController, but at 10Hz
    fun start() {
        stop()
        mTimer = Timer()
        mTimer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                tick()
            }
        }, 0, 10)
    }

    fun stop() {
        if (mTimer != null) {
            mTimer?.cancel()
            mTimer = null
        }
    }

}
