package com.paoloronco.codevault.ui

sealed class Screen(val route: String) {
    object Setup    : Screen("setup")
    object Lock     : Screen("lock")
    object Home     : Screen("home")
    object AddEdit  : Screen("add_edit?id={id}&type={type}") {
        fun forNew(type: String) = "add_edit?id=-1&type=$type"
        fun forEdit(id: Long)    = "add_edit?id=$id&type=edit"
    }
    object Detail   : Screen("detail/{id}") {
        fun withId(id: Long) = "detail/$id"
    }
    object Settings : Screen("settings")
}
