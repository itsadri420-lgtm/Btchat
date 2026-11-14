package com.example.btchat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

@SuppressLint("MissingPermission")
class MainActivity : Activity() {
    private var adapter: BluetoothAdapter? = null
    private val devices = ArrayList<BluetoothDevice>()
    private val labels = ArrayList<String>()
    private lateinit var listAdapter: ArrayAdapter<String>
    private lateinit var status: TextView
    private var pending: (() -> Unit)? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null) addDevice(d)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                    status.text = "Suche beendet. Gerät antippen zum Verbinden."
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        val title = TextView(this).apply { text = "BT Chat"; textSize = 26f }
        val connect = Button(this).apply { text = "Connect (Geräte suchen)" }
        val wait = Button(this).apply { text = "Warten auf Verbindung (sichtbar machen)" }
        status = TextView(this).apply { text = "Bereit."; setPadding(0, 16, 0, 16) }
        val list = ListView(this)
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.adapter = listAdapter

        root.addView(title)
        root.addView(connect)
        root.addView(wait)
        root.addView(status)
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        connect.setOnClickListener { withPermissions { startScan() } }
        wait.setOnClickListener { withPermissions { startWaiting() } }
        list.setOnItemClickListener { _, _, pos, _ ->
            val a = adapter ?: return@setOnItemClickListener
            status.text = "Verbinde mit ${labels[pos]} …"
            BtConnection.connect(a, devices[pos],
                { openChat() },
                { status.text = "Fehler: $it\n(Läuft die App auf dem anderen Gerät mit „Warten auf Verbindung“?)" })
        }
    }

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun withPermissions(action: () -> Unit) {
        val missing = neededPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) action()
        else { pending = action; requestPermissions(missing.toTypedArray(), 1) }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res.all { it == PackageManager.PERMISSION_GRANTED }) pending?.invoke()
        else status.text = "Ohne Berechtigung geht es leider nicht."
        pending = null
    }

    private fun ensureEnabled(): Boolean {
        val a = adapter
        if (a == null) { status.text = "Dieses Gerät hat kein Bluetooth."; return false }
        if (!a.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            status.text = "Bitte Bluetooth einschalten und erneut tippen."
            return false
        }
        return true
    }

    private fun addDevice(d: BluetoothDevice) {
        if (devices.any { it.address == d.address }) return
        devices.add(d)
        labels.add((d.name ?: "Unbekannt") + "\n" + d.address)
        listAdapter.notifyDataSetChanged()
    }

    private fun startScan() {
        if (!ensureEnabled()) return
        val a = adapter!!
        devices.clear(); labels.clear()
        a.bondedDevices?.forEach { addDevice(it) }
        listAdapter.notifyDataSetChanged()
        if (!receiverRegistered) {
            val f = IntentFilter(BluetoothDevice.ACTION_FOUND)
            f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            registerReceiver(receiver, f)
            receiverRegistered = true
        }
        a.cancelDiscovery()
        a.startDiscovery()
        status.text = "Suche läuft …"
    }

    private fun startWaiting() {
        if (!ensureEnabled()) return
        val d = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        d.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        startActivity(d)
        status.text = "Warte auf Verbindung …"
        BtConnection.listen(adapter!!, { openChat() }, { status.text = "Fehler: $it" })
    }

    private fun openChat() {
        status.text = "Verbunden."
        startActivity(Intent(this, ChatActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false }
        BtConnection.stopListening()
    }
}
