package com.devtools.displayhelper

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.applicationContext.getSharedPreferences("p", Context.MODE_PRIVATE) }
    fun f(k: String, d: Float) = sp.getFloat(k, d)
    fun setF(k: String, v: Float) = sp.edit().putFloat(k, v).apply()
    fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    fun setB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun remove(k: String) = sp.edit().remove(k).apply()
}
