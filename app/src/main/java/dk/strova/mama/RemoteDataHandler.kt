package dk.strova.mama

import android.os.Handler
import android.os.Looper
import dk.strova.mama.localdb.ShoppingListItem

class RemoteDataHandler(val activity: ShoppingList, looper: Looper, endpoint: String): Handler(looper) {
    val remoteData = RemoteData(activity.applicationContext, endpoint)

    fun getAll(cb: (List<String>) -> Unit) {
        post { remoteData.getAll(wrapCallback(cb)) }
    }

    fun insert(item: ShoppingListItem, cb: (List<String>) -> Unit) {
        post { remoteData.insert(item.text, wrapCallback(cb)) }
    }

    fun delete(item: ShoppingListItem, cb: (List<String>) -> Unit) {
        post { remoteData.delete(item.text, wrapCallback(cb)) }
    }

    private fun wrapCallback(cb: (List<String>) -> Unit): (RemoteData.Result) -> Unit {
        return {
            when (it) {
                is RemoteData.Result.Success -> cb(it.items)
                is RemoteData.Result.Error -> {
                    activity.remoteError("Error retrieving shopping list from server!")
                }
            }
        }
    }

    fun deleteList(names: List<String>, cb: (List<String>) -> Unit) {
        if (names.isEmpty()) {
            getAll(cb)
        } else {
            post {
                remoteData.delete(names[0]) {
                    when(it) {
                        is RemoteData.Result.Success -> {
                            val rec = names.filter { name -> it.items.contains(name) }
                            if (rec.isEmpty()) {
                                cb(it.items)
                            } else {
                                deleteList(rec, cb)
                            }
                        }
                        is RemoteData.Result.Error -> {
                            activity.remoteError("Error deleting shopping list items on server!")
                        }
                    }
                }
            }
        }
    }
}