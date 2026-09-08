package com.example.nhumonglenh

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nhumonglenh.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun testMigrate2To3_deduplicatesAiAnalysisAndAddsNewsMetadata() {
        var db = helper.createDatabase(testDb, 2).apply {
            execSQL("INSERT INTO news_table (newsId, title, url, publishedAt) VALUES ('n1', 'Title 1', 'http://news1', 1700000000)")
            execSQL("INSERT INTO ai_analysis_table (analysisId, newsId, summary, sentiment, confidenceScore, reason) VALUES (1, 'n1', 'Old Summary', 'neutral', 50, 'Reason 1')")
            execSQL("INSERT INTO ai_analysis_table (analysisId, newsId, summary, sentiment, confidenceScore, reason) VALUES (2, 'n1', 'New Summary', 'bullish', 85, 'Reason 2')")
            close()
        }

        db = helper.runMigrationsAndValidate(testDb, 3, true, AppDatabase.MIGRATION_2_3)

        val fkCursor: Cursor = db.query("PRAGMA foreign_key_check;")
        assertEquals("Không được có bất kỳ vi phạm foreign key nào sau migration 2->3", 0, fkCursor.count)
        fkCursor.close()

        val aiCursor: Cursor = db.query("SELECT newsId, summary, sentiment, confidenceScore, reason FROM ai_analysis_table WHERE newsId = 'n1'")
        assertTrue(aiCursor.moveToFirst())
        assertEquals("Phải khử trùng lặp và chỉ giữ 1 bản ghi có MAX(analysisId)", 1, aiCursor.count)
        assertEquals("New Summary", aiCursor.getString(aiCursor.getColumnIndexOrThrow("summary")))
        assertEquals("bullish", aiCursor.getString(aiCursor.getColumnIndexOrThrow("sentiment")))
        assertEquals(85, aiCursor.getInt(aiCursor.getColumnIndexOrThrow("confidenceScore")))
        aiCursor.close()

        val newsCursor: Cursor = db.query("SELECT newsId, title, source, author, confidence FROM news_table WHERE newsId = 'n1'")
        assertTrue(newsCursor.moveToFirst())
        assertEquals("Title 1", newsCursor.getString(newsCursor.getColumnIndexOrThrow("title")))
        assertEquals(0, newsCursor.getInt(newsCursor.getColumnIndexOrThrow("confidence")))
        newsCursor.close()
    }

    @Test
    fun testMigrate3To4_preservesAllNewsAndAiAnalysisWithoutCascadeAndEnablesNullableWatchlist() {
        // 1. Chèn sẵn dữ liệu hoàn chỉnh ở phiên bản 3: News + AI Analysis (có FK) + Watchlist
        var db = helper.createDatabase(testDb, 3).apply {
            execSQL("INSERT INTO news_table (newsId, title, url, publishedAt, source, author, publishedAtRaw, imageUrl, summary, sentiment, confidence, bulletPoints) " +
                    "VALUES ('news-v3', 'Fed Holds Rates', 'https://fnmf.com/news/1', 1710000000, 'Bloomberg', 'Jane Doe', '2026-03-31', 'https://fnmf.com/img.jpg', 'Summary 1', 'bullish', 90, 'Bullet 1')")
            execSQL("INSERT INTO ai_analysis_table (newsId, summary, sentiment, confidenceScore, reason) " +
                    "VALUES ('news-v3', 'AI Summary Bullish', 'bullish', 90, 'Macro fundamentals solid')")
            execSQL("INSERT INTO watchlist_table (id, symbol, price, change24h, userEmail) " +
                    "VALUES (1, 'BTCUSDT', 50000.0, 2.5, 'user@fnmf.com')")
            close()
        }

        // 2. Chạy Migration 3 -> 4 và đối chiếu schema tự động với 4.json
        db = helper.runMigrationsAndValidate(testDb, 4, true, AppDatabase.MIGRATION_3_4)

        // 3. Xác minh PRAGMA foreign_key_check hoàn toàn không có lỗi vi phạm
        val fkCursor: Cursor = db.query("PRAGMA foreign_key_check;")
        assertEquals("Không được có bất kỳ vi phạm foreign key nào sau migration", 0, fkCursor.count)
        fkCursor.close()

        // 4. Xác minh ai_analysis_table BẢO TOÀN NGUYÊN VẸN (không bị xóa bởi ON DELETE CASCADE)
        val aiCursor: Cursor = db.query("SELECT newsId, summary, sentiment, confidenceScore, reason FROM ai_analysis_table WHERE newsId = 'news-v3'")
        assertTrue("Bản ghi AI analysis phải được bảo toàn nguyên vẹn qua migration 3->4", aiCursor.moveToFirst())
        assertEquals("news-v3", aiCursor.getString(aiCursor.getColumnIndexOrThrow("newsId")))
        assertEquals("AI Summary Bullish", aiCursor.getString(aiCursor.getColumnIndexOrThrow("summary")))
        assertEquals("bullish", aiCursor.getString(aiCursor.getColumnIndexOrThrow("sentiment")))
        assertEquals(90, aiCursor.getInt(aiCursor.getColumnIndexOrThrow("confidenceScore")))
        aiCursor.close()

        // 5. Xác minh news_table bảo toàn nguyên vẹn
        val newsCursor: Cursor = db.query("SELECT newsId, title, source, confidence FROM news_table WHERE newsId = 'news-v3'")
        assertTrue("Bản ghi News phải được bảo toàn nguyên vẹn", newsCursor.moveToFirst())
        assertEquals("Fed Holds Rates", newsCursor.getString(newsCursor.getColumnIndexOrThrow("title")))
        assertEquals("Bloomberg", newsCursor.getString(newsCursor.getColumnIndexOrThrow("source")))
        assertEquals(90, newsCursor.getInt(newsCursor.getColumnIndexOrThrow("confidence")))
        newsCursor.close()

        // 6. Xác minh watchlist_table bảo toàn dữ liệu cũ và hỗ trợ giá NULL mới
        val wlOldCursor: Cursor = db.query("SELECT id, symbol, price, change24h, userEmail FROM watchlist_table WHERE id = 1")
        assertTrue("Bản ghi Watchlist cũ phải còn nguyên", wlOldCursor.moveToFirst())
        assertEquals("BTCUSDT", wlOldCursor.getString(wlOldCursor.getColumnIndexOrThrow("symbol")))
        assertEquals(50000.0, wlOldCursor.getDouble(wlOldCursor.getColumnIndexOrThrow("price")), 0.001)
        wlOldCursor.close()

        db.execSQL("INSERT INTO watchlist_table (symbol, price, change24h, userEmail) VALUES ('ETHUSDT', NULL, NULL, 'user@fnmf.com')")
        val wlNewCursor: Cursor = db.query("SELECT symbol, price, change24h FROM watchlist_table WHERE symbol = 'ETHUSDT'")
        assertTrue(wlNewCursor.moveToFirst())
        assertTrue("Price phải hỗ trợ giá trị NULL", wlNewCursor.isNull(wlNewCursor.getColumnIndexOrThrow("price")))
        assertTrue("Change24h phải hỗ trợ giá trị NULL", wlNewCursor.isNull(wlNewCursor.getColumnIndexOrThrow("change24h")))
        wlNewCursor.close()
    }

    @Test
    fun testMigrate2To4_directMigrationPreservesAllDataAndMatchesSchema() {
        // 1. Tạo CSDL ở version 2 và chèn dữ liệu phiên bản 2 (chèn 2 bản ghi AI để kiểm tra khử trùng lặp theo MAX(analysisId))
        var db = helper.createDatabase(testDb, 2).apply {
            execSQL("INSERT INTO news_table (newsId, title, url, publishedAt) " +
                    "VALUES ('n-direct', 'Breaking News v2', 'https://fnmf.com/2', 1705000000)")
            execSQL("INSERT INTO ai_analysis_table (analysisId, newsId, summary, sentiment, confidenceScore, reason) " +
                    "VALUES (10, 'n-direct', 'Old AI Summary', 'neutral', 50, 'Outdated')")
            execSQL("INSERT INTO ai_analysis_table (analysisId, newsId, summary, sentiment, confidenceScore, reason) " +
                    "VALUES (20, 'n-direct', 'Direct AI Summary', 'bullish', 88, 'Strong earnings')")
            execSQL("INSERT INTO watchlist_table (id, symbol, price, change24h, userEmail) " +
                    "VALUES (100, 'BTCUSDT', 42000.0, 1.8, 'direct@fnmf.com')")
            close()
        }

        // 2. Chạy chuỗi di trú liên tục 2 -> 3 -> 4 trực tiếp lên version 4
        db = helper.runMigrationsAndValidate(testDb, 4, true, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)

        // 3. Kiểm tra tính toàn vẹn khóa ngoại (Foreign Key Check)
        val fkCursor: Cursor = db.query("PRAGMA foreign_key_check;")
        assertEquals("Không được có bất kỳ vi phạm foreign key nào sau migration trực tiếp 2->4", 0, fkCursor.count)
        fkCursor.close()

        // 4. Xác minh dữ liệu news_table được bảo toàn và có các cột metadata mới
        val newsCursor: Cursor = db.query("SELECT newsId, title, publishedAt, source, confidence FROM news_table WHERE newsId = 'n-direct'")
        assertTrue("News record phải tồn tại sau migration 2->4", newsCursor.moveToFirst())
        assertEquals("Breaking News v2", newsCursor.getString(newsCursor.getColumnIndexOrThrow("title")))
        assertEquals(1705000000L, newsCursor.getLong(newsCursor.getColumnIndexOrThrow("publishedAt")))
        assertEquals(0, newsCursor.getInt(newsCursor.getColumnIndexOrThrow("confidence")))
        newsCursor.close()

        // 5. Xác minh ai_analysis_table được chuyển sang newsId làm PrimaryKey và giữ bản ghi MAX(analysisId)
        val aiCursor: Cursor = db.query("SELECT newsId, summary, sentiment, confidenceScore, reason FROM ai_analysis_table WHERE newsId = 'n-direct'")
        assertTrue("AI analysis record phải tồn tại và được giữ nguyên sau migration 2->4", aiCursor.moveToFirst())
        assertEquals("Chỉ được giữ 1 bản ghi AI duy nhất có MAX(analysisId)", 1, aiCursor.count)
        assertEquals("Direct AI Summary", aiCursor.getString(aiCursor.getColumnIndexOrThrow("summary")))
        assertEquals("bullish", aiCursor.getString(aiCursor.getColumnIndexOrThrow("sentiment")))
        assertEquals(88, aiCursor.getInt(aiCursor.getColumnIndexOrThrow("confidenceScore")))
        aiCursor.close()

        // 6. Xác minh watchlist_table bảo toàn dữ liệu và hỗ trợ chèn giá NULL
        val wlCursor: Cursor = db.query("SELECT id, symbol, price, change24h, userEmail FROM watchlist_table WHERE id = 100")
        assertTrue("Watchlist cũ phải tồn tại", wlCursor.moveToFirst())
        assertEquals(42000.0, wlCursor.getDouble(wlCursor.getColumnIndexOrThrow("price")), 0.001)
        wlCursor.close()

        db.execSQL("INSERT INTO watchlist_table (symbol, price, change24h, userEmail) VALUES ('XAUUSD', NULL, NULL, 'direct@fnmf.com')")
        val wlNewCursor: Cursor = db.query("SELECT symbol, price, change24h FROM watchlist_table WHERE symbol = 'XAUUSD'")
        assertTrue(wlNewCursor.moveToFirst())
        assertTrue("Price phải hỗ trợ NULL", wlNewCursor.isNull(wlNewCursor.getColumnIndexOrThrow("price")))
        wlNewCursor.close()
    }
}
