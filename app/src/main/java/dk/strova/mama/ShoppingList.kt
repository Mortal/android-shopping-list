package dk.strova.mama

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.HandlerThread
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dk.strova.mama.databinding.ActivityShoppingListBinding
import dk.strova.mama.databinding.ContentShoppingListBinding
import dk.strova.mama.localdb.ShoppingListItem

val TAG = "ShoppingList"

class ShoppingList : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var viewManager: LinearLayoutManager
    private lateinit var binding: ActivityShoppingListBinding
    private lateinit var content: ContentShoppingListBinding

    private lateinit var viewAdapter: MyAdapter

    private lateinit var localDataThread: HandlerThread
    private lateinit var localDataHandler: LocalDataHandler
    private lateinit var remoteDataThread: HandlerThread
    private var remoteDataHandler: RemoteDataHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityShoppingListBinding.inflate(layoutInflater)
        content = ContentShoppingListBinding.inflate(layoutInflater)

        setContentView(R.layout.activity_shopping_list)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setOnMenuItemClickListener {
            onOptionsItemSelected(it)
        }
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        localDataThread = HandlerThread("LocalDataHandler")
        localDataThread.start()
        localDataHandler = LocalDataHandler(this, localDataThread.looper)

        viewAdapter = MyAdapter(localDataHandler::updateSelected)
        viewManager = LinearLayoutManager(this)

        localDataHandler.liveAll {
            runOnUiThread {
                it.observe(this, Observer {
                    viewAdapter.modelItems = it
                })
            }
        }

        content.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        remoteDataThread = HandlerThread("RemoteDataHandler")
        remoteDataThread.start()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        initRemoteHandler(sharedPreferences)

        val simpleItemTouchCallback = ItemSwipeCallback {
            it.position?.let { viewAdapter.modelItems?.get(it)?.let {
                Log.d(TAG, "Deleting " + it)
                localDataHandler.delete(it)
                remoteDataHandler?.let { handler ->
                    binding.swipeContainer.isRefreshing = true
                    handler.delete(it, this::setItemsFromRemote)
                }
            } }
        }

        val itemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        itemTouchHelper.attachToRecyclerView(content.recyclerView)

        content.editText.setOnEditorActionListener { _, _, _ ->
            addItemFromEditText()
        }
        content.fab.setOnClickListener {
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
        val endpoint = sharedPreferences.getString(SettingsActivity.KEY_ENDPOINT, "")!!.trim()
        if (endpoint.isEmpty()) {
            remoteDataHandler = null
            binding.swipeContainer.isEnabled = false
        } else {
            remoteDataHandler = RemoteDataHandler(this, remoteDataThread.looper, endpoint)
            binding.swipeContainer.isEnabled = true
            binding.swipeContainer.setOnRefreshListener {
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
        binding.swipeContainer.isRefreshing = false
    }

    fun remoteError(text: String) {
        Snackbar.make(content.fab, text, Snackbar.LENGTH_SHORT).show()
        binding.swipeContainer.isRefreshing = false
    }

    private fun addItemFromEditText(): Boolean {
        val text = content.editText.text.toString().trim()
        return if (text.isNotEmpty()) {
            content.editText.text.clear()
            val item = ShoppingListItem(text = text)
            localDataHandler.insert(item)
            remoteDataHandler?.let { handler ->
                binding.swipeContainer.isRefreshing = true
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
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            TODO("Not yet implemented")
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
                binding.swipeContainer.isRefreshing = true
                handler.deleteList(it, this::setItemsFromRemote)
            }
        }
        return true
    }

    private fun deleteSelectedItems(): Boolean {
        localDataHandler.deleteAndReturnSelected() {
            remoteDataHandler?.let { handler ->
                runOnUiThread { binding.swipeContainer.isRefreshing = true }
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
                notifyItemRangeInserted(0, new.size)
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
            notifyItemRangeRemoved(i, removed)
            val inserted = new.size - i - j
            notifyItemRangeInserted(i, inserted)
        }

        class ViewHolder(
                val root: CheckBox,
                val onClick: (Int, Boolean) -> Unit,
                var position: Int? = null
        ) : RecyclerView.ViewHolder(root) {
            init {
                root.setOnClickListener {
                    position?.let {
                        onClick(it, root.isChecked)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val checkBox = LayoutInflater.from(parent.context)
                    .inflate(R.layout.my_text_view, parent, false) as CheckBox
            return ViewHolder(checkBox, this::onClick)
        }

        override fun getItemCount() = modelItems?.size ?: 0

        fun onClick(position: Int, selected: Boolean) {
            modelItems?.get(position)?.let {
                it.selected = selected
                save(it)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val modelItem = modelItems?.get(position)
            holder.position = position
            holder.root.isChecked = modelItem?.selected == true
            holder.root.text = modelItem?.text ?: "Unknown"
        }

    }
}
