package org.eu.fedcampus.benchmark

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.eu.fedcampus.benchmark.databinding.ActivityBenchmarkBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BenchmarkActivity : AppCompatActivity() {
    lateinit var binding: ActivityBenchmarkBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appendLog("Initialized.")
    }

    private fun appendLog(log: String) {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = dateFormat.format(Date())
        val text = "$time: $log\n"
        Log.i(TAG, "appendLog: $text")
        runOnUiThread {
            binding.tvHello.append(text)
        }
    }

    companion object {
        const val TAG = "BenchmarkActivity"
    }
}
