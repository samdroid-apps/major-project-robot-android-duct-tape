package robot.eng.sam.today.robot

import android.content.Context
import android.hardware.*
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.design.widget.Snackbar
import android.text.format.Formatter
import kotlinx.android.synthetic.main.activity_arduino_controller.*
import com.koushikdutta.async.http.server.AsyncHttpServer
import android.hardware.SensorManager.SENSOR_DELAY_UI
import android.util.Log
import java.util.*
import java.util.concurrent.TimeUnit


data class RecordedMessage(val time: Long, val msg: Message)


private val SMART_CONTROL_THREASHOLD = 10f


// This activity runs on the phone that is on the robot
// It connects to the Arduino over USB Serial
class ArduinoController : AppCompatActivity() {

    val mSerial = SerialConnectionWrapper(
            fun(x) { log(x) },
            fun() { setupDone() })
    val mServer = AsyncHttpServer()
    var mSensorManager: SensorManager? = null
    var mSelfDrivingController = SelfDrivingController { a, b ->
        runOnUiThread {
            setProgressInRange(motor1SeekBar, a, -1f, 1f)
            setProgressInRange(motor2SeekBar, b, -1f, 1f)
        }
    }

    var mIsAboutToCollide = false
    var mRecordingStartTime: Long = 0
    var mRecording = mutableListOf<RecordedMessage>()
    var mIsRecording = false


    // A basic method to append text to the log, but limit the log to 1000 characters (for perf)
    private fun log(text: String) {
        runOnUiThread {
            var current = statusTextView.text.toString()
            if (current.length > 1000) {
                current = current.substring(0..1000)
            }
            statusTextView.text = text + "\n" + current
        }
    }

    // enable and disable the UI from manual control
    private fun setEnabled(en: Boolean) {
        runOnUiThread {
            motor1SeekBar.isEnabled = en
            motor2SeekBar.isEnabled = en
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arduino_controller)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mSerial.init(this)

        // setup the routes on the server, which is started in a later phase
        mServer.get("/") { request, response -> response.send("Please connect over websockets") }
        mServer.websocket("/ws") { webSocket, request ->
            webSocket.setClosedCallback { error ->
                // log disconnects on the screen
                log("Closed websocket ($error) from ${request.headers["User-Agent"]}")
            }

            webSocket.setStringCallback { rawMsg ->
                // MessageAdapter decodes the messages from JSON into the Kotlin class
                val msg = MessageAdapter.fromJson(rawMsg.trim())
                log("Message: $msg")
                if (msg != null) {
                    if (mIsRecording && msg.stopRecording == null && msg.startRecording == null && msg.playRecording == null) {
                        // add messages to the list if we are recording; but do not add message
                        // relating to starting and stopping recordings (as that would cause
                        // unwanted recursion during playback)
                        val timeOffset = System.currentTimeMillis() - mRecordingStartTime
                        mRecording.add(RecordedMessage(timeOffset, msg))
                    }
                    processMessage(msg)
                }
            }
        }
    }

    private fun processMessage(msg: Message) {
        if (msg.requestReset == true) {
            runOnUiThread {
                reset()
            }
        }
        if (msg.setLEDState != null) {
            runOnUiThread {
                ledSwitch.isChecked = msg.setLEDState
            }
        }
        if (msg.setMotorAPower != null) {
            runOnUiThread {
                setProgressInRange(motor1SeekBar, msg.setMotorAPower, -1f, 1f)
            }
        }
        if (msg.setMotorBPower != null) {
            runOnUiThread {
                setProgressInRange(motor2SeekBar, msg.setMotorBPower, -1f, 1f)
            }
        }
        if (msg.startRecording == true) {
            mRecordingStartTime = System.currentTimeMillis()
            mRecording = mutableListOf()
            mIsRecording = true
            log("Starting to record...")
            setEnabled(false)
        }
        if (msg.stopRecording == true) {
            mIsRecording = false
            log("Stopped recording; recording len = ${mRecording.size}")
            setEnabled(true)
        }
        if (msg.playRecording == true) {
            log("Playing back a recording")
            val rp = RecordingPlayer(mRecording, { m -> processMessage(m) })
            rp.start()
        }
        if (msg.setSDCEnable != null) {
            runOnUiThread {
                sdcSwitch.isChecked = msg.setSDCEnable
            }
        }
        if (msg.setAimOrientation != null) {
            mSelfDrivingController.aimOrientation = msg.setAimOrientation
        }
    }

    // reset the board state, and update UI to reflect that
    private fun reset() {
        mSerial.write("R") // Reset board state
        setProgressInRange(motor1SeekBar, 0f, -1f, 1f)
        setProgressInRange(motor2SeekBar, 0f, -1f, 1f)
    }

    // callback once USB serial connection is established
    private fun setupDone() {
        // start the task to read ultrasonic sensor values from the serial
        AsyncTask.execute {
            while (true) {
                val dist = mSerial.read()
                if (dist != null) {
                    mIsAboutToCollide = dist < SMART_CONTROL_THREASHOLD && smartControlSwitch.isChecked
                    if (mIsAboutToCollide) {
                        // reset the motors to 0 if about to collide
                        setProgressInRange(motor1SeekBar, 0f, -1f, 1f)
                        setProgressInRange(motor2SeekBar, 0f, -1f, 1f)
                    }

                    runOnUiThread {
                        // update the displays of the distance
                        mapView.updateLength(dist)
                        lengthLabel.setText("$dist")
                    }
                }
            }
        }

        Snackbar.make(ledSwitch, "Connection established", Snackbar.LENGTH_LONG).show()
        reset()

        ledSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            // when the led is switched, send a message to the Arduino with the new state
            mSerial.write(if (isChecked) "L" else "l")
        }
        sdcSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                mSelfDrivingController.start()
            } else {
                mSelfDrivingController.stop()
            }
        }

        seekBarObservableFromView(motor1SeekBar, resetTo = 0f, min = -1f, max = 1f)
                // Throttle the value changes so that the serial connection is not overloaded
                // and that the UI remains responsive.  This limits changes to 10Hz
                .throttleFirst(100, TimeUnit.MILLISECONDS)
                .subscribe { value ->
                    // reject moving forwards when the robot is about to collide
                    if (!(mIsAboutToCollide && value > 0f)) {
                        mSerial.setMotorState(value, MOTOR_A)
                    }
                }
        seekBarObservableFromView(motor2SeekBar, resetTo = 0f, min = -1f, max = 1f)
                .throttleFirst(100, TimeUnit.MILLISECONDS)
                .subscribe { value ->
                    if (!(mIsAboutToCollide && value > 0f)) {
                        mSerial.setMotorState(value, MOTOR_B)
                    }
                }

        startServer()
    }

    // start the websocket server for the remote control
    private fun startServer() {
        mServer.listen(5337)

        // get the current ip address on the WiFi interface
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        serverStatusTextView.text = "Running server at $ip"
    }

    // clealy shutdown the app
    override fun onDestroy() {
        super.onDestroy()
        mServer.stop()
        mSerial.destroy()
    }

    // callback object used on sensor events
    private val mSensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                var rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                // this is the values for when the phone is placed forwards, face-up in portrait
                // mode; as it is in this robot
                val worldAxisForDeviceAxisX = SensorManager.AXIS_X
                val worldAxisForDeviceAxisY = SensorManager.AXIS_Y

                var adjustedRotationMatrix = FloatArray(9)
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix)

                // transform rotation matrix into azimuth/pitch/roll
                var orientation = FloatArray(3)
                SensorManager.getOrientation(adjustedRotationMatrix, orientation)

                // azimuth value represents the rotation of the robot on the axis
                // perpendicular to the ground plane
                val (az, _, _) = orientation
                mapView.updateRot(az.toDouble())
                mSelfDrivingController.updateRot(az)
            }
        }
    }


    // on resume (when the app comes into focus) setup the sensor
    override fun onResume() {
        super.onResume()

        val rotSensor = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        mSensorManager!!.registerListener(mSensorListener, rotSensor, SENSOR_DELAY_UI)

        if (sdcSwitch.isChecked) {
            mSelfDrivingController.start()
        }
    }

    // onPause is called when the user quits the view; so the self driving controller must be stopped
    override fun onPause() {
        super.onPause()
        mSensorManager!!.unregisterListener(mSensorListener)

        mSelfDrivingController.stop()
    }
}

