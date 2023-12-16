package dk.strova.mama.localdb

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ShoppingListItem(
        @PrimaryKey(autoGenerate = true)
        var id: Long? = null,
        var text: String,
        var selected: Boolean = false
    ) {

}