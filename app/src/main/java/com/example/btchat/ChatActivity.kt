package com.example.btchat

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast

class ChatActivity : Activity() {
    private val msgs = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Chat mit " + BtConnection.remoteName

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, msgs)
        list.adapter = adapter
        list.transcriptMode = ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val input = EditText(this).apply { hint = "Nachricht schreiben" }
        val send = Button(this).apply { text = "Senden" }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(send)
        root.addView(row)
        setContentView(root)

        BtConnection.onMessage = { msgs.add("Er/Sie: $it"); adapter.notifyDataSetChanged() }
        BtConnection.onClosed = {
            Toast.makeText(this, "Verbindung getrennt", Toast.LENGTH_LONG).show()
            finish()
        }

        send.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            if (BtConnection.send(t)) {
                msgs.add("Ich: $t"); adapter.notifyDataSetChanged(); input.setText("")
            } else Toast.makeText(this, "Senden fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BtConnection.onMessage = null
        BtConnection.onClosed = null
        if (isFinishing) BtConnection.close()
    }
}
