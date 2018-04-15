package dk.strova.mama

import android.arch.lifecycle.Observer
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.HandlerThread
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.CheckBox
import dk.strova.mama.localdb.ShoppingListItem
import kotlinx.android.synthetic.main.activity_shopping_list.*
import kotlinx.android.synthetic.main.content_shopping_list.*

val TAG = "ShoppingList"

class ShoppingList : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var viewManager: LinearLayoutManager

    private lateinit var viewAdapter: MyAdapter

    private lateinit var localDataThread: HandlerThread
    private lateinit var localDataHandler: LocalDataHandler
    private lateinit var remoteDataThread: HandlerThread
    private var remoteDataHandler: RemoteDataHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_list)
        setSupportActionBar(toolbar)

        localDataThread = HandlerThread("LocalDataHandler")
        localDataThread.start()
        localDataHandler = LocalDataHandler(this, localDataThread.looper)

        viewAdapter = MyAdapter(localDataHandler::updateSelected)
        viewManager = LinearLayoutManager(this)

        localDataHandler.liveAll {
            it.observe(this, Observer {
                runOnUiThread {
                    viewAdapter.modelItems = it
                }
            })
        }

        recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        remoteDataThread = HandlerThread("RemoteDataHandler")
        remoteDataThread.start()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        initRemoteHandler(sharedPreferences)

        val simpleItemTouchCallback = ItemSwipeCallback {
            it.modelItem?.let {
                Log.d(TAG, "Deleting " + it)
                localDataHandler.delete(it)
                remoteDataHandler?.let { handler ->
                    swipeContainer.isRefreshing = true
                    handler.delete(it, this::setItemsFromRemote)
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        editText.setOnEditorActionListener { _, _, _ ->
            addItemFromEditText()
        }
        fab.setOnClickListener {
            addItemFromEditText()
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        initRemoteHandler(sharedPreferences)
    }

    override fun onPause() {
        super.onPause()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun initRemoteHandler(sharedPreferences: SharedPreferences) {
        val endpoint = sharedPreferences.getString(SettingsActivity.KEY_ENDPOINT, "").trim()
        if (endpoint.isEmpty()) {
            remoteDataHandler = null
            swipeContainer.isEnabled = false
        } else {
            remoteDataHandler = RemoteDataHandler(this, remoteDataThread.looper, endpoint)
            swipeContainer.isEnabled = true
            swipeContainer.setOnRefreshListener {
                Log.i(TAG, "Registered refresh swipe")
                remoteDataHandler!!.getAll(this::setItemsFromRemote)
            }
            remoteDataHandler!!.getAll(this::setItemsFromRemote)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == SettingsActivity.KEY_ENDPOINT) initRemoteHandler(sharedPreferences!!)
    }

    private fun setItemsFromRemote(items: List<String>) {
        localDataHandler.setItemsFromRemote(items)
        swipeContainer.isRefreshing = false
    }

    fun remoteError(text: String) {
        Snackbar.make(fab, text, Snackbar.LENGTH_SHORT).show()
        swipeContainer.isRefreshing = false
    }

    private fun addItemFromEditText(): Boolean {
        val text = editText.text.toString().trim()
        return if (text.isNotEmpty()) {
            editText.text.clear()
            val item = ShoppingListItem(text = text)
            localDataHandler.insert(item)
            remoteDataHandler?.let { handler ->
                swipeContainer.isRefreshing = true
                handler.insert(item, this::setItemsFromRemote)
            }
            true
        } else {
            false
        }
    }
    private class ItemSwipeCallback(
            val onItemSwipe: (MyAdapter.ViewHolder) -> Unit
    ) : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {

        override fun onMove(
                recyclerView: RecyclerView?,
                viewHolder: RecyclerView.ViewHolder?,
                target: RecyclerView.ViewHolder?)
                : Boolean {
            throw NotImplementedError()
        }
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val adapterPosition = viewHolder.adapterPosition
            if (adapterPosition == RecyclerView.NO_POSITION) {
                return
            }
            onItemSwipe(viewHolder as MyAdapter.ViewHolder)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_shopping_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_delete_all -> deleteAllItems()
            R.id.action_delete_selected -> deleteSelectedItems()
            R.id.action_settings -> openSettings()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteAllItems(): Boolean {
        localDataHandler.deleteAndReturnAll() {
            remoteDataHandler?.let { handler ->
                swipeContainer.isRefreshing = true
                handler.deleteList(it, this::setItemsFromRemote)
            }
        }
        return true
    }

    private fun deleteSelectedItems(): Boolean {
        localDataHandler.deleteAndReturnSelected() {
            remoteDataHandler?.let { handler ->
                runOnUiThread { swipeContainer.isRefreshing = true }
                handler.deleteList(it, this::setItemsFromRemote)
            }
        }
        return true
    }

    private fun openSettings(): Boolean {
        startActivity(Intent(this, SettingsActivity::class.java))
        return true
    }

    private class MyAdapter(val save: (ShoppingListItem) -> Unit) : RecyclerView.Adapter<MyAdapter.ViewHolder>() {
        var modelItems: List<ShoppingListItem>? = null
        set(value) {
            val new = value!!
            if (field == null) {
                field = new
                notifyDataSetChanged()
                return
            }
            val old = field!!
            var i = 0
            while (i < old.size && i < new.size && old[i] == new[i]) {
                i++
            }
            var j = 0
            while (old.size - j > i && new.size - j > i && old[old.size - j - 1] == new[new.size - j - 1]) {
                j++
            }
            field = new
            val removed = old.size - i - j
            when (removed) {
                0 -> {}
                1 -> notifyItemRemoved(i)
                else -> notifyItemRangeRemoved(i, removed)
            }
            val inserted = new.size - i - j
            when (inserted) {
                0 -> {}
                1 -> notifyItemInserted(i)
                else -> notifyItemRangeInserted(i, inserted)
            }
        }

        class ViewHolder(
                val root: CheckBox,
                val save: (ShoppingListItem) -> Unit,
                var modelItem: ShoppingListItem? = null
        ) : RecyclerView.ViewHolder(root) {
            init {
                root.setOnClickListener {
                    modelItem?.let {
                        it.selected = root.isChecked
                        save(it)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val checkBox = LayoutInflater.from(parent.context)
                    .inflate(R.layout.my_text_view, parent, false) as CheckBox
            return ViewHolder(checkBox, save)
        }

        override fun getItemCount() = modelItems?.size ?: 0

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.modelItem = modelItems?.get(position)
            holder.root.isChecked = holder.modelItem?.selected == true
            holder.root.text = holder.modelItem?.text ?: "Unknown"
        }

    }
}
