package dk.strova.mama

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.LiveData
import androidx.room.Room
import dk.strova.mama.localdb.Database
import dk.strova.mama.localdb.ShoppingListItem
import dk.strova.mama.localdb.ShoppingListItemDao

private val DATABASE_NAME = "mama"

class LocalDataHandler(activity: ComponentActivity, looper: Looper): Handler(looper) {
    private lateinit var dao: ShoppingListItemDao

    init {
        post {
            val database = Room.databaseBuilder(activity.applicationContext, Database::class.java, DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
            dao = database.shoppingListItemDao()
        }
    }

    fun liveAll(cb: (LiveData<List<ShoppingListItem>>) -> Unit) {
        post { cb(dao.liveAll()) }
    }

    fun insert(item: ShoppingListItem) {
        post { dao.insert(item) }
    }

    fun deleteAndReturnSelected(cb: (List<String>) -> Unit) {
        post {
            val items = dao.getAll()
            val result = ArrayList<String>()
            items.forEach { if (it.selected) { result.add(it.text) } }
            dao.deleteSelected()
            cb(result)
        }
    }

    fun updateSelected(item: ShoppingListItem) {
        item.id?.let { id -> post { dao.updateSelected(id, item.selected) } }
    }

    fun delete(item: ShoppingListItem) {
        post { dao.delete(item) }
    }

    fun deleteAndReturnAll(cb: (List<String>) -> Unit) {
        post {
            val items = dao.getAll()
            val result = ArrayList<String>(items.size)
            items.forEach { result.add(it.text) }
            dao.deleteAll()
            cb(result)
        }
    }

    fun setItemsFromRemote(items: List<String>) {
        post {
            val current = dao.getAll()
            var i = 0
            while (i < items.size && i < current.size && items[i] == current[i].text) {
                i++
            }
            for (j in i until current.size) {
                dao.delete(current[j])
            }
            for (j in i until items.size) {
                dao.insert(ShoppingListItem(text = items[j]))
            }
        }
    }
}