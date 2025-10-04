package com.example.relaycontrol

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnRelay1: Button
    private lateinit var btnRelay2: Button
    private val client = OkHttpClient()
    private val baseUrl = "http://192.168.4.1"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var pendingRelayToToggle: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus)
        btnRelay1 = findViewById(R.id.btnRelay1)
        btnRelay2 = findViewById(R.id.btnRelay2)

        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this@MainActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // proceed to toggle the pending relay
                    if (pendingRelayToToggle != 0) {
                        sendToggle(pendingRelayToToggle, requirePin = false)
                        pendingRelayToToggle = 0
                    }
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تأیید هویت")
            .setSubtitle("برای کنترل رله اثرانگشت را لمس کنید")
            .setNegativeButtonText("استفاده از PIN")
            .build()

        btnRelay1.setOnClickListener {
            requestBiometricThenToggle(1)
        }
        btnRelay2.setOnClickListener {
            requestBiometricThenToggle(2)
        }

        startPolling()
    }

    private fun requestBiometricThenToggle(n: Int) {
        val bm = BiometricManager.from(this)
        val can = bm.canAuthenticate()
        if (can == BiometricManager.BIOMETRIC_SUCCESS) {
            pendingRelayToToggle = n
            biometricPrompt.authenticate(promptInfo)
        } else {
            // fallback to PIN dialog
            showPinPromptAndToggle(n)
        }
    }

    private fun showPinPromptAndToggle(n: Int) {
        // simple Android dialog for PIN entry
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("PIN")
        val input = android.widget.EditText(this)
        input.hint = "Enter PIN"
        builder.setView(input)
        builder.setPositiveButton("OK") { d, _ ->
            val pin = input.text.toString()
            sendToggle(n, requirePin = true, pin = pin)
            d.dismiss()
        }
        builder.setNegativeButton("Cancel") { d, _ -> d.dismiss() }
        builder.show()
    }

    private fun sendToggle(n: Int, requirePin: Boolean, pin: String = "") {
        val url = "$baseUrl/api/toggle"
        val json = JSONObject()
        json.put("relay", n)
        if (requirePin) json.put("pin", pin)
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString())
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvStatus.text = "خطا در ارسال فرمان: ${e.localizedMessage}"
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                runOnUiThread {
                    if (ok) {
                        tvStatus.text = "فرمان ارسال شد. به‌روزرسانی وضعیت..."
                        // refresh immediately
                        fetchStatus()
                    } else {
                        tvStatus.text = "سرور پاسخ ناموفق داد"
                    }
                }
            }
        })
    }

    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                fetchStatus()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun fetchStatus() {
        val url = "$baseUrl/api/status"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvStatus.text = "ارتباط برقرار نشد: ${e.localizedMessage}"
                }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        runOnUiThread { tvStatus.text = "پاسخ ناموفق از سرور" }
                        return
                    }
                    val body = it.body()?.string() ?: ""
                    try {
                        val j = JSONObject(body)
                        val r1 = j.optBoolean("relay1", false)
                        val r2 = j.optBoolean("relay2", false)
                        runOnUiThread {
                            tvStatus.text = "وضعیت — رله1: ${if (r1) "روشن" else "خاموش"} | رله2: ${if (r2) "روشن" else "خاموش"}"
                            btnRelay1.text = if (r1) "خاموش کن" else "روشن کن"
                            btnRelay2.text = if (r2) "خاموش کن" else "روشن کن"
                        }
                    } catch (e: Exception) {
                        runOnUiThread { tvStatus.text = "خطا در پردازش پاسخ" }
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
