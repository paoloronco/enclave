package com.paoloronco.codevault.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Entry type — controls which fields are shown in AddEdit/Detail. */
object EntryType {
    const val ACCOUNT = "account"   // username + password
    const val PIN     = "pin"       // pin code only (credit card, door code, etc.)
    const val NOTE    = "note"      // free-text secure note
    const val WIFI    = "wifi"      // SSID + password

    data class Meta(
        val key:      String,
        val label:    String,
        val emoji:    String,
        val subtitle: String
    )

    val ALL = listOf(
        Meta(ACCOUNT, "Account",       "🔑", "Username e password"),
        Meta(PIN,     "PIN / Carta",   "💳", "Codice PIN, carta di credito"),
        Meta(NOTE,    "Nota sicura",   "📝", "Testo riservato"),
        Meta(WIFI,    "Wi-Fi",         "📶", "SSID e password di rete"),
    )
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type:       String  = EntryType.ACCOUNT,  // NEW in v3
    val title:      String,
    val username:   String  = "",
    val password:   String  = "",
    val notes:      String  = "",
    val category:   String  = "General",
    val isFavorite: Boolean = false,
    val updatedAt:  Long    = System.currentTimeMillis()
)

object Categories {
    const val GENERAL  = "General"
    const val SOCIAL   = "Social"
    const val BANKING  = "Banking"
    const val EMAIL    = "Email"
    const val WORK     = "Work"
    const val SHOPPING = "Shopping"
    const val OTHER    = "Other"

    val ALL = listOf(GENERAL, SOCIAL, BANKING, EMAIL, WORK, SHOPPING, OTHER)
}
