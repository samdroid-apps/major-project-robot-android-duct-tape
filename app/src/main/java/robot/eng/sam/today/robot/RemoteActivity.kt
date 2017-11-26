package robot.eng.sam.today.robot

import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.provider.Settings
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.SeekBar
import com.koushikdutta.async.http.AsyncHttpClient
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import kotlinx.android.synthetic.main.activity_remote.*
import kotlinx.android.synthetic.main.content_remote.*
import java.util.*

// This connects to the robot's server phone over WiFi
class RemoteActivity : AppCompatActivity() {
    var mSocket: com.koushikdutta.async.http.WebSocket? = null

    // basic function to log a message on screen, but limit the length to 1000 chars for perf
    fun output(text: String) {
        runOnUiThread {
            var current = logTextView.text.toString()
            if (current.length > 1000) {
                current = current.substring(0..1000)
            }
            logTextView.text = text + "\n" + current
        }
    }

    // sends a message (that is the Kotlin class) via the websocket, and logs it
    fun send(msg: Message) {
        val text = MessageAdapter.toJson(msg)
        mSocket!!.send(text)
        output("Send: " + text)
    }

    // tuple of power and steering inputs
    data class PowerSteer(val power: Float, val steer: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)
        setSupportActionBar(toolbar)

        // get the address that is passed from the launcher screen
        val addr = intent.extras.getString("addr")
        if (addr == null) {
            // crash if not launched from the launcher with an address
            finish()
            return
        }

        // Setup the websocket client
        AsyncHttpClient.getDefaultInstance().websocket(
                addr, null
        ) { ex, webSocket ->
            // when the websocket is connected, save the socket as a member variable
            mSocket = webSocket
            output("Connection Opened")

            /*webSocket.setStringCallback { msg ->
               output("Recv: $msg")
           }*/

            webSocket.setClosedCallback { error ->
                output("Closed $error")
            }
        }

        resetButton.setOnClickListener {
            send(Message(requestReset = true))
        }

        // Crete an observable that merges the two slider's data streams; power and steering
        // the subscriber will get a PowerSteer value with the latest power and steering input
        Observable.create<PowerSteer> { emitter ->
            var power = 0f
            var steer = 0f
            seekBarObservableFromView(powerSeekBar, resetTo = 0f, min = -1f, max = 1f)
                    .subscribe { v ->
                        power = v;
                        emitter.onNext(PowerSteer(power, steer));
                    }
            seekBarObservableFromView(steerSeekBar, resetTo = 0f, min = -1f, max = 1f)
                    .subscribe { v ->
                        steer = v;
                        emitter.onNext(PowerSteer(power, steer));
                    }
        }.subscribe { v ->
            // v is a PowerSteer tuple
            val aCoeff = if (v.steer < 0) 1f else 1f - 2 * Math.abs(v.steer)
            val bCoeff = if (v.steer > 0) 1f else 1f - 2 * Math.abs(v.steer)
            send(Message(
                    setMotorAPower = v.power * aCoeff,
                    setMotorBPower = v.power * bCoeff
            ))
        }

        recordButton.setOnClickListener {
            send(Message(startRecording = true))
        }
        stopRecordingButton.setOnClickListener {
            send(Message(stopRecording = true))
        }
        playRecordingButton.setOnClickListener {
            send(Message(playRecording = true))
        }

        sdapSwitch.setOnCheckedChangeListener { _, isChecked ->
            send(Message(setSDCEnable = isChecked))
        }
        seekBarObservableFromView(aimOrientationSeekBar, min = -180f, max = 180f)
                .subscribe { v ->
                    send(Message(setAimOrientation = v))
                }
    }

}
