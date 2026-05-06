package com.accontroller

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), SerialInputOutputManager.Listener {

    private val ACTION_USB_PERMISSION = "com.accontroller.USB_PERMISSION"
    private var usbIoManager: SerialInputOutputManager? = null
    private var port: com.hoho.android.usbserial.driver.UsbSerialPort? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvTemp: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvConnect: TextView
    private lateinit var btnToggle: Button

    private var acOn = false
    private var buffer = StringBuilder()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    connectSerial()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTemp = findViewById(R.id.tvTemp)
        tvStatus = findViewById(R.id.tvStatus)
        tvConnect = findViewById(R.id.tvConnect)
        btnToggle = findViewById(R.id.btnToggle)

        registerReceiver(usbReceiver, IntentFilter(ACTION_USB_PERMISSION))

        btnToggle.setOnClickListener { toggleAC() }

        connectSerial()
    }

    private fun connectSerial() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            tvConnect.text = "● ARDUINO NOT FOUND"
            return
        }
        val driver = drivers[0]
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
            usbManager.requestPermission(driver.device, pi)
            return
        }
        port = driver.ports[0]
        port?.open(connection)
        port?.setParameters(9600, 8, 1, com.hoho.android.usbserial.driver.UsbSerialPort.PARITY_NONE)
        usbIoManager = SerialInputOutputManager(port, this)
        Executors.newSingleThreadExecutor().submit(usbIoManager)
        tvConnect.text = "● ARDUINO CONNECTED"
    }

    private fun toggleAC() {
        acOn = !acOn
        val cmd = if (acOn) "ON\n" else "OFF\n"
        try { port?.write(cmd.toByteArray(), 1000) } catch (e: Exception) { }
        updateUI()
    }

    private fun updateUI() {
        if (acOn) {
            btnToggle.text = "⏻ TURN OFF"
            btnToggle.setBackgroundColor(0xFF742A2A.toInt())
            tvStatus.text = "COOLING"
            tvStatus.setTextColor(0xFF48BB78.toInt())
        } else {
            btnToggle.text = "⏻ TURN ON"
            btnToggle.setBackgroundColor(0xFF276749.toInt())
            tvStatus.text = "STANDBY"
            tvStatus.setTextColor(0xFF4A5568.toInt())
        }
    }

    override fun onNewData(data: ByteArray) {
        buffer.append(String(data))
        val lines = buffer.toString().split("\n")
        for (i in 0 until lines.size - 1) {
            val line = lines[i].trim()
            if (line.startsWith("T:")) {
                val temp = line.substring(2)
                handler.post { tvTemp.text = temp }
            }
        }
        buffer = StringBuilder(lines.last())
    }

    override fun onRunError(e: Exception) {
        handler.post { tvConnect.text = "● DISCONNECTED" }
    }

    override fun onDestroy() {
        super.onDestroy()
        usbIoManager?.stop()
        port?.close()
        unregisterReceiver(usbReceiver)
    }
}
