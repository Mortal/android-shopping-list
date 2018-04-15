package dk.strova.mama.localdb

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class ShoppingListItem(
        @PrimaryKey(autoGenerate = true)
        var id: Long? = null,
        var text: String,
        var selected: Boolean = false
    ) {

}