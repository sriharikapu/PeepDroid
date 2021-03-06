package org.ligi.peepdroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.android.ext.android.inject
import org.ligi.kaxt.startActivityFromClass
import org.ligi.peepdroid.model.Peep
import org.ligi.peepdroid.model.PeepAPI

class PeepAdapter(private val list: List<Peep>) : RecyclerView.Adapter<PeepViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeepViewHolder {
        val li = LayoutInflater.from(parent.context)
        return PeepViewHolder(li.inflate(R.layout.peep, parent, false))
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: PeepViewHolder, position: Int) {
        holder.bind(list[position])
    }

}

class MainActivity : AppCompatActivity() {

    private val peepAPI: PeepAPI by inject()
    private val okHttpClient: OkHttpClient by inject()

    private var currentSecret: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        peep_recycler.layoutManager = LinearLayoutManager(this)

        launch {

            peepAPI.getPeeps()?.let {
                async(UI) {
                    peep_recycler.adapter = PeepAdapter(parsePeeps(it))
                }
            }
        }

        fab.setOnClickListener {
            startActivityFromClass(PeepActivity::class.java)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_sign_in -> {
                val init = peepAPI.init()
                val tokenLine = init?.lines()?.first { it.contains("csrf-token") }?.split("content=")?.last()?.split("\"")?.get(1)

                val setIsUserResult = peepAPI.setIsUser(tokenLine!!)
                currentSecret = peepAPI.getNewSecret()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("ethereum:signtext-$currentSecret"))
                startActivityForResult(intent, 123)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val signature = data?.getStringExtra("SIGNATURE")
        val address = data?.getStringExtra("ADDRESS")?.toLowerCase()

        val url = "https://peepeth.com/verify_signed_secret.js?signed_token=0x" + signature + "&original_token=" + currentSecret?.replace(" ", "+") + "&address=0x$address&provider=metamask"

        val result = okHttpClient.newCall(Request.Builder()
                .header("X-Requested-With", "XMLHttpRequest")
                .url(url).build()).execute().body()?.string()

        AlertDialog.Builder(this).setMessage(result).show()
    }
}
