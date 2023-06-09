package flwr.android_client

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.provider.Settings
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
import androidx.room.Room
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.eu.fedcampus.train.Train
import org.tensorflow.lite.examples.transfer.api.TransferLearningModel
import java.util.*

class MainActivity : AppCompatActivity() {
    private val scope = MainScope()
    lateinit var train: Train
    private lateinit var ip: EditText
    private lateinit var port: EditText
    private lateinit var loadDataButton: Button
    private lateinit var connectButton: Button
    private lateinit var trainButton: Button
    private lateinit var resultText: TextView
    private lateinit var device_id: EditText
    lateinit var db: Db

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(this, Db::class.java, "model-db").build()
        setContentView(R.layout.activity_main)
        resultText = findViewById(R.id.grpc_response_text)
        resultText.movementMethod = ScrollingMovementMethod()
        device_id = findViewById(R.id.device_id_edit_text)
        ip = findViewById(R.id.serverIP)
        port = findViewById(R.id.serverPort)
        loadDataButton = findViewById(R.id.load_data)
        connectButton = findViewById(R.id.connect)
        trainButton = findViewById(R.id.trainFederated)
        scope.launch { restoreInput() }
    }

    suspend fun restoreInput() {
        val input = db.inputDao().get() ?: return
        runOnUiThread {
            device_id.text.append(input.device_id)
            ip.text.append(input.ip)
            port.text.append(input.port)
        }
    }

    fun setResultText(text: String) {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
        val time = dateFormat.format(Date())
        resultText.append("\n$time   $text")
    }

    suspend fun runWithStacktrace(call: suspend () -> Unit) {
        try {
            call()
        } catch (err: Error) {
            Log.e(TAG, Log.getStackTraceString(err))
        }
    }

    suspend fun <T> runWithStacktraceOr(or: T, call: suspend () -> T): T {
        return try {
            call()
        } catch (err: Error) {
            Log.e(TAG, Log.getStackTraceString(err))
            or
        }
    }

    fun loadData(@Suppress("UNUSED_PARAMETER") view: View) {
        if (device_id.text.isEmpty() || !(1..10).contains(device_id.text.toString().toInt())) {
            Toast.makeText(
                this,
                "Please enter a client partition ID between 1 and 10 (inclusive)",
                Toast.LENGTH_LONG
            ).show()
        } else {
            hideKeyboard()
            setResultText("Loading the local training dataset in memory. It will take several seconds.")
            device_id.isEnabled = false
            loadDataButton.isEnabled = false
            scope.launch {
                loadDataInBackground()
            }
            scope.launch {
                db.inputDao().upsertAll(inputFromEditText(device_id, ip, port))
            }
        }
    }

    suspend fun loadDataInBackground() {
        val result = runWithStacktraceOr("Failed to load training dataset.") {
            loadData(this, train.flowerClient.tlModel, device_id.text.toString().toInt())
            "Training dataset is loaded in memory. Ready to train!"
        }
        runOnUiThread {
            setResultText(result)
            trainButton.isEnabled = true
        }
    }

    fun connect(@Suppress("UNUSED_PARAMETER") view: View) {
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
                runWithStacktrace {
                    connectInBackground(host, port)
                }
            }
            hideKeyboard()
            ip.isEnabled = false
            this.port.isEnabled = false
            connectButton.isEnabled = false
            setResultText("Creating channel object.")
        }
    }

    fun stringToLong(string: String): Long {
        val hashCode = string.hashCode().toLong()
        val secondHashCode = string.reversed().hashCode().toLong()
        return (hashCode shl 32) or secondHashCode
    }

    @SuppressLint("HardwareIds")
    @Throws
    suspend fun connectInBackground(host: String, port: Int) {
        val backendUrl = "http://$host:$port"
        Log.i(TAG, "Backend URL: $backendUrl")
        train = Train(this, backendUrl, db.modelDao())
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        train.enableTelemetry(stringToLong(deviceId))
        val modelLoader = train.prepareModelLoader()
        val classes = listOf(
            "cat",
            "dog",
            "truck",
            "bird",
            "airplane",
            "ship",
            "frog",
            "horse",
            "deer",
            "automobile"
        )
        val model = TransferLearningModel(modelLoader, classes)
        val serverData = train.getServerInfo()
        if (serverData.port == null) {
            throw Error("Flower server port not available, status ${serverData.status}")
        }
        train.prepare(model, "dns:///$host:${serverData.port}", false)
        runOnUiThread {
            loadDataButton.isEnabled = true
            setResultText("Prepared for training.")
        }
    }

    fun runGrpc(@Suppress("UNUSED_PARAMETER") view: View) {
        scope.launch {
            runGrpcInBackground()
        }
    }

    suspend fun runGrpcInBackground() {
        val result = runWithStacktraceOr("Failed to connect to the FL server \n") {
            train.start {
                runOnUiThread {
                    setResultText(it)
                }
            }
            "Connection to the FL server successful \n"
        }
        runOnUiThread {
            setResultText(result)
            trainButton.isEnabled = false
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

private const val TAG = "MainActivity"
