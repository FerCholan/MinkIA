package com.moviles.minkia.data.model

/**
 * Usuario autenticado. Modelo de dominio mínimo del flujo de acceso; cuando
 * exista el backend real se completará con más campos (token, foto, rol, etc.).
 */
data class Usuario(
    val id: String,
    val nombre: String,
    val email: String,
    val esAdmin: Boolean = false
)
