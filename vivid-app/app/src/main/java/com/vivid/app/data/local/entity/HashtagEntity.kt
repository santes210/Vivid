package com.vivid.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Catálogo local de hashtags descubiertos en posts públicos.
 *
 * Alimenta los chips de Explorar OFFLINE: la tabla se llena escaneando los
 * posts públicos recientes (ver `HashtagRepository.refresh`) y cada fila
 * guarda cuántas veces se vio el tag y cuándo fue la última.
 */
@Immutable
@Entity(tableName = "hashtags")
data class HashtagEntity(
    @PrimaryKey val tag: String,
    val count: Int,
    val lastSeenAt: Long
)
