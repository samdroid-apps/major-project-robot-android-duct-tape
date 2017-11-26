package robot.eng.sam.today.robot

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_server_selector.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocketListener

class ServerSelectorActivity : AppCompatActivity() {

    private fun handleButtonClick(forceLocal: Boolean = false) {
        if (ipEntry.text.trim().isEmpty() || forceLocal) {
            val intent = Intent(this, ArduinoController::class.java)
            startActivity(intent)
        } else {
            val intent = Intent(this, RemoteActivity::class.java)
            val bundle = Bundle()
            bundle.putString("addr", "ws://${ipEntry.text}:5337/ws")
            intent.putExtras(bundle)
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_selector)

        ipEntry.setText("10.42.0.224")
        continueButton.setOnClickListener { handleButtonClick() }
        continueLocalButton.setOnClickListener { handleButtonClick(true) }
    }
}
