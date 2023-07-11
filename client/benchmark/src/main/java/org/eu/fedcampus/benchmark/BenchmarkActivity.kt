package org.eu.fedcampus.benchmark

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.eu.fedcampus.benchmark.databinding.ActivityBenchmarkBinding

class BenchmarkActivity : AppCompatActivity() {
    lateinit var binding: ActivityBenchmarkBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
