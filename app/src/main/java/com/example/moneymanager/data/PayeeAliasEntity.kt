package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payee_aliases",
    foreignKeys = [
        ForeignKey(
            entity = PayeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["payeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("payeeId"), Index(value = ["aliasKey"], unique = true)]
)
data class PayeeAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payeeId: Long,
    /** VPA (merchant@oksbi) or cleaned merchant string, already normalized. */
    val aliasKey: String
)
