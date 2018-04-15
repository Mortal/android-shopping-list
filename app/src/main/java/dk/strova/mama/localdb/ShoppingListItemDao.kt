package dk.strova.mama.localdb

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.arch.persistence.room.OnConflictStrategy.REPLACE

@Dao
interface ShoppingListItemDao {
    @Query("SELECT * from shoppinglistitem ORDER BY id")
    fun getAll(): List<ShoppingListItem>

    @Query("SELECT * from shoppinglistitem ORDER BY id")
    fun liveAll(): LiveData<List<ShoppingListItem>>

    @Insert(onConflict = REPLACE)
    fun insert(shoppingListItem: ShoppingListItem)

    @Query("DELETE from shoppinglistitem")
    fun deleteAll()

    @Delete
    fun delete(modelItem: ShoppingListItem)

    @Query("DELETE from shoppinglistitem WHERE selected = 1")
    fun deleteSelected()

    @Query("UPDATE shoppinglistitem SET selected = :selected WHERE id = :id")
    fun updateSelected(id: Long, selected: Boolean)
}