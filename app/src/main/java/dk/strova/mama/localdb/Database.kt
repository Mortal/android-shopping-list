package dk.strova.mama.localdb

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ShoppingListItem::class], version = 2)
abstract class Database: RoomDatabase() {
    abstract fun shoppingListItemDao(): ShoppingListItemDao
}