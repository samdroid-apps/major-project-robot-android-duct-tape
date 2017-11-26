package robot.eng.sam.today.robot

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.lang.Math.abs


private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

data class MotorInfo(val stop: String, val fw: String, val rev: String)

// class instances with the letters to perform each command on each motor
val MOTOR_A = MotorInfo("a", "A", "z")
val MOTOR_B = MotorInfo("b", "B", "y")



// class that deals with serial connection to the Arduino, and the motors
// based heavily on USB-Serial-for-Android example code, from the page:
//     https://github.com/mik3y/usb-serial-for-android
class SerialConnectionWrapper(
        logCallback: (String) -> Unit,  // callback log a message for the user to see
        doneCallback: () -> Unit  // callback for when the connection is established and usable
) {
    val log = logCallback
    val mDoneCallback = doneCallback

    var mManager: UsbManager? = null
    var mPort: UsbSerialPort? = null

    // Android sends out broadcasts after USB permission requests have been responded to
    // by the user
    val mUsbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.getAction()
            if (ACTION_USB_PERMISSION.equals(action)) {
                log("got usb broadcast")
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            log("permission granted")
                            gotPermission()
                        }
                    } else {
                        log("permission denied for device " + device)
                    }
                }
            }
        }
    }

    var mContext: Context? = null

    fun init(context: Context) {
        mContext = context
        mManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(mUsbReceiver, filter)

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mManager!!)
        if (availableDrivers.isEmpty()) {
            log("empty drivers; did you forget to plug it in?")
            return
        }

        // Open a connection to the first available driver.
        val driver = availableDrivers[0]
        log("using driver $driver")
        // requesting permission lead to a permission popup
        mManager!!.requestPermission(driver.device, permissionIntent)
    }

    // callback to find the driver and open the connection once permission has been granted
    fun gotPermission() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mManager!!)
        if (availableDrivers.isEmpty()) {
            log("empty drivers")
            return
        }

        // assumption; there is only 1 USB device plugged in and that is the Arduino
        // that is always true, because that is how we built the robot
        val driver = availableDrivers[0]

        // Open a connection to the first available driver.
        val connection = mManager!!.openDevice(driver.device)

        if (connection == null) {
            log("null connection")
            return
        }

        // Arduino Uno only has 1 serial port; on port 0
        mPort = driver.ports[0]
        try {
            mPort!!.open(connection)
            mPort!!.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        } catch (e: IOException) {
            log("io execp")
            return
        }
        mDoneCallback()
    }

    // write a letter to the serial connection
    fun write(text: String) {
        if (mPort == null) {
            log("Can not write before got port: $text")
        } else {
            log("Write: $text")
            mPort!!.write(text.toByteArray(), 100)
        }
    }

    // write a value to the serial connection
    //    v is a float, in the range 0f to 1f
    // it is written as a value from 0-255 (1 unsigned byte)
    fun writeByte(v: Float) {
        val byte: Byte = (minOf(maxOf(0f, v), 1f)*255f).toByte()
        if (mPort == null) {
            log("Can not write before got port: $byte")
        } else {
            log("Write: $byte = $v")
            mPort!!.write(byteArrayOf(byte), 100)
        }
    }

    // reads a one byte unsigned integer from the Serial, or returns null if none can be read
    // might block; can not run on ui thread
    fun read(): Int? {
        var buffer = byteArrayOf(1)
        var nRead = mPort!!.read(buffer, 1)
        if (nRead != 0) {
            val reading = buffer[0].toInt() and 0xFF
            // the "and 0xFF" casts signed ints to unsigned ints in kotlin
            return reading
        }
        return null
    }

    fun destroy() {
        // reset the board state when shutting down
        write("R")
        mContext!!.unregisterReceiver(mUsbReceiver)
    }

    fun setMotorState(v: Float, motor: MotorInfo) {
        // v is a float from -1 to 1; where -1 is backwards, and +1 is forwards, and 0 is stopped
        if (v == 0f) {
            write(motor.stop)
            return
        }


        if (v > 0f) {
            // forward
            write(motor.fw)
        } else {
            // reverse
            write(motor.rev)
        }
        writeByte(abs(v))
    }
}