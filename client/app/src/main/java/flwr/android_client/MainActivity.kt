package flwr.android_client

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import flwr.android_client.train.FlowerServiceRunnable
import flwr.android_client.train.Train
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

class MainActivity : AppCompatActivity() {
    private val scope = MainScope()
    lateinit var train: Train
    lateinit var fc: FlowerClient
    private lateinit var ip: EditText
    private lateinit var port: EditText
    private lateinit var loadDataButton: Button
    private lateinit var connectButton: Button
    private lateinit var trainButton: Button
    private lateinit var resultText: TextView
    private lateinit var device_id: EditText
    private lateinit var channel: ManagedChannel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        resultText = findViewById(R.id.grpc_response_text)
        resultText.movementMethod = ScrollingMovementMethod()
        device_id = findViewById(R.id.device_id_edit_text)
        ip = findViewById(R.id.serverIP)
        port = findViewById(R.id.serverPort)
        loadDataButton = findViewById(R.id.load_data)
        connectButton = findViewById(R.id.connect)
        trainButton = findViewById(R.id.trainFederated)
    }

    fun setResultText(text: String) {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
        val time = dateFormat.format(Date())
        resultText.append("\n$time   $text")
    }

    fun loadData(view: View) {
        if (TextUtils.isEmpty(device_id.text.toString())) {
            Toast.makeText(
                this,
                "Please enter a client partition ID between 1 and 10 (inclusive)",
                Toast.LENGTH_LONG
            ).show()
        } else if (device_id.text.toString().toInt() > 10 || device_id.text.toString()
                .toInt() < 1
        ) {
            Toast.makeText(
                this,
                "Please enter a client partition ID between 1 and 10 (inclusive)",
                Toast.LENGTH_LONG
            ).show()
        } else {
            hideKeyboard()
            setResultText("Loading the local training dataset in memory. It will take several seconds.")
            loadDataButton.isEnabled = false
            scope.launch {
                loadDataInBackground()
            }
        }
    }

    suspend fun loadDataInBackground() {
        withContext(Dispatchers.IO) {
            val result = try {
                fc.loadData(device_id.text.toString().toInt())
                "Training dataset is loaded in memory. Ready to train!"
            } catch (e: Exception) {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                e.printStackTrace(pw)
                pw.flush()
                "Training dataset is loaded in memory."
            }
            runOnUiThread {
                setResultText(result)
                trainButton.isEnabled = true
            }
        }
    }

    fun connect(view: View) {
        val host = ip.text.toString()
        val portStr = port.text.toString()
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(portStr) || !Patterns.IP_ADDRESS.matcher(
                host
            ).matches()
        ) {
            Toast.makeText(
                this,
                "Please enter the correct IP and port of the FL server",
                Toast.LENGTH_LONG
            ).show()
        } else {
            val port = if (TextUtils.isEmpty(portStr)) 0 else portStr.toInt()
            scope.launch {
                try {
                    connectInBackground(host, port)
                } catch (err: Exception) {
                    Log.e(TAG, "connectInBackground", err)
                }
            }
            hideKeyboard()
            connectButton.isEnabled = false
            setResultText("Creating channel object.")
        }
    }

    suspend fun connectInBackground(host: String, port: Int) {
        val activity = this
        withContext(Dispatchers.IO) {
            train = Train(host, port)
            val model = train.getAdvertisedModel()
            val modelDir = model.getModelDir(activity)
            train.downloadModelFiles(modelDir)
            val server = train.getServerInfo()
            if (server.port != null) {
                connectGrpc(modelDir, host, server.port)
            } else {
                Log.w("Flower server not available", server.status)
            }
        }
    }

    fun connectGrpc(modelDir: File, host: String, port: Int) {
        fc = FlowerClient(this, modelDir)
        channel =
            ManagedChannelBuilder.forAddress(host, port).maxInboundMessageSize(10 * 1024 * 1024)
                .usePlaintext().build()
        runOnUiThread {
            loadDataButton.isEnabled = true
            setResultText("Channel object created.")
        }
    }

    fun runGrpc(view: View) {
        scope.launch {
            runGrpcInBackground()
        }
    }

    suspend fun runGrpcInBackground() {
        val activity = this
        withContext(Dispatchers.Default) {
            val result = try {
                FlowerServiceRunnable().run(FlowerServiceGrpc.newStub(channel), activity)
                "Connection to the FL server successful \n"
            } catch (e: Exception) {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                e.printStackTrace(pw)
                pw.flush()
                "Failed to connect to the FL server \n$sw"
            }
            runOnUiThread {
                setResultText(result)
                trainButton.isEnabled = false
            }
        }
    }

    fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        var view = currentFocus
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

private const val TAG = "Flower"
