package com.example.nhumonglenh

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.nhumonglenh.ui.news.NewsFeedFragment
import com.example.nhumonglenh.ui.watchlist.WatchlistFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class Activity2 : AppCompatActivity() {

    companion object {
        private const val KEY_SELECTED_NAV_ID = "KEY_SELECTED_NAV_ID"
        private const val TAG_TRADING = "TAG_TRADING"
        private const val TAG_FORECAST = "TAG_FORECAST"
        private const val TAG_NEWS = "TAG_NEWS"
        private const val TAG_WATCHLIST = "TAG_WATCHLIST"
    }

    private lateinit var bottomNav: BottomNavigationView
    private val fragmentMap = mutableMapOf<Int, Fragment>()
    private var activeFragment: Fragment? = null

    private var tradingFragment: TradingFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.layout_activity2)

        bottomNav = findViewById(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            val initialFragment = TradingFragment()
            tradingFragment = initialFragment
            fragmentMap[R.id.nav_trading] = initialFragment
            activeFragment = initialFragment

            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, initialFragment, TAG_TRADING)
                .commit()
        } else {
            // 1. Xây dựng lại fragmentMap từ các Fragment đã được FragmentManager khôi phục
            val navIds = listOf(R.id.nav_trading, R.id.nav_forecast, R.id.nav_news, R.id.nav_watchlist)
            for (navId in navIds) {
                val tag = getTagForNavId(navId)
                val restoredFrag = supportFragmentManager.findFragmentByTag(tag)
                if (restoredFrag != null) {
                    fragmentMap[navId] = restoredFrag
                    if (navId == R.id.nav_trading && restoredFrag is TradingFragment) {
                        tradingFragment = restoredFrag
                    }
                }
            }

            // 2. Xác định tab đang chọn và Fragment đang visible
            val selectedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV_ID, R.id.nav_trading)
            var currentVisible = fragmentMap.values.firstOrNull { it.isAdded && !it.isHidden }
            if (currentVisible == null) {
                currentVisible = fragmentMap[selectedNavId] ?: fragmentMap[R.id.nav_trading]
            }
            activeFragment = currentVisible

            // 3. Đồng bộ trạng thái show/hide của các Fragment theo Fragment đang active
            val tx = supportFragmentManager.beginTransaction()
            for ((_, frag) in fragmentMap) {
                if (frag === activeFragment) {
                    tx.show(frag)
                } else {
                    tx.hide(frag)
                }
            }
            tx.commit()

            bottomNav.selectedItemId = selectedNavId
        }

        bottomNav.setOnItemSelectedListener { item ->
            switchTab(item.itemId)
            true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_NAV_ID, bottomNav.selectedItemId)
    }

    private fun getTagForNavId(itemId: Int): String {
        return when (itemId) {
            R.id.nav_trading -> TAG_TRADING
            R.id.nav_forecast -> TAG_FORECAST
            R.id.nav_news -> TAG_NEWS
            R.id.nav_watchlist -> TAG_WATCHLIST
            else -> "TAG_$itemId"
        }
    }

    private fun switchTab(itemId: Int) {
        val current = activeFragment ?: return

        val tag = getTagForNavId(itemId)
        // Tìm trong map trước, nếu chưa có thì tìm lại trong FragmentManager theo tag
        var target = fragmentMap[itemId] ?: supportFragmentManager.findFragmentByTag(tag)

        val tx = supportFragmentManager.beginTransaction()

        if (target == null) {
            // Chỉ tạo Fragment mới nếu tag đó thực sự chưa tồn tại
            target = when (itemId) {
                R.id.nav_trading -> tradingFragment ?: TradingFragment().also { tradingFragment = it }
                R.id.nav_forecast -> ForecastFragment()
                R.id.nav_news -> NewsFeedFragment()
                R.id.nav_watchlist -> WatchlistFragment()
                else -> return
            }
            fragmentMap[itemId] = target
            tx.hide(current).add(R.id.fragment_container, target, tag).commit()
        } else {
            fragmentMap[itemId] = target
            if (target === current) return
            tx.hide(current).show(target).commit()
        }
        activeFragment = target
    }

    /**
     * Cho phép WatchlistFragment gọi để chuyển về tab Trading và chọn mã tương ứng
     */
    fun switchToTradingSymbol(symbol: String) {
        bottomNav.selectedItemId = R.id.nav_trading
        tradingFragment?.switchMarketSymbol(symbol)
    }
}
