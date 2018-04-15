package dk.strova.mama.localdb

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase

@Database(entities = arrayOf(ShoppingListItem::class), version = 2)
abstract class Database: RoomDatabase() {
    abstract fun shoppingListItemDao(): ShoppingListItemDao
}