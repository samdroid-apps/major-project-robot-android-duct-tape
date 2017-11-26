package robot.eng.sam.today.robot

import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi

// class used to represent messages from the remote to the server
// values are null when they are not used
// so setting a value of this object is like sending a message to call a function
data class Message(
        val setMotorAPower: Float? = null,
        val setMotorBPower: Float? = null,
        val setLEDState: Boolean? = null,
        val requestReset: Boolean? = null,
        val startRecording: Boolean? = null,
        val stopRecording: Boolean? = null,
        val playRecording: Boolean? = null,
        val setSDCEnable: Boolean? = null,
        val setAimOrientation: Float? = null
)

private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

// create an "Adapter" that can be used to serialize and deserialize to JSON
val MessageAdapter = moshi.adapter(Message::class.java)
