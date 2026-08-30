package com.example.nhumonglenh

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class Activity2 : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.layout_activity2)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        // Start with Trading Fragment
        if (savedInstanceState == null) {
            loadFragment(TradingFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_trading -> {
                    loadFragment(TradingFragment())
                    true
                }
                R.id.nav_forecast -> {
                    loadFragment(ForecastFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
