package com.example.nhumonglenh.data.local;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {WatchlistItem.class, NewsEntity.class, AiAnalysisEntity.class},
    version = 5,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract WatchlistDao watchlistDao();
    public abstract NewsDao newsDao();

    private static volatile AppDatabase INSTANCE;

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE watchlist_table ADD COLUMN userEmail TEXT NOT NULL DEFAULT ''");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 1. Thêm các cột metadata cho news_table khớp chính xác schema Room (nullable theo Java String)
            database.execSQL("ALTER TABLE news_table ADD COLUMN source TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN author TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN publishedAtRaw TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN imageUrl TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN summary TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN sentiment TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN confidence INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE news_table ADD COLUMN bulletPoints TEXT");

            // 2. Tái cấu trúc ai_analysis_table: newsId làm PrimaryKey (khử trùng lặp xác định bằng MAX(analysisId))
            database.execSQL("CREATE TABLE IF NOT EXISTS ai_analysis_table_new (" +
                    "newsId TEXT PRIMARY KEY NOT NULL, " +
                    "summary TEXT, " +
                    "sentiment TEXT, " +
                    "confidenceScore INTEGER NOT NULL DEFAULT 0, " +
                    "reason TEXT, " +
                    "FOREIGN KEY(newsId) REFERENCES news_table(newsId) ON UPDATE NO ACTION ON DELETE CASCADE)");

            database.execSQL("INSERT OR REPLACE INTO ai_analysis_table_new (newsId, summary, sentiment, confidenceScore, reason) " +
                    "SELECT a.newsId, a.summary, a.sentiment, a.confidenceScore, a.reason FROM ai_analysis_table a " +
                    "WHERE a.analysisId IN (SELECT MAX(analysisId) FROM ai_analysis_table GROUP BY newsId)");

            database.execSQL("DROP TABLE ai_analysis_table");
            database.execSQL("ALTER TABLE ai_analysis_table_new RENAME TO ai_analysis_table");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ai_analysis_table_newsId ON ai_analysis_table(newsId)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Chuyển đổi watchlist_table: price và change24h sang nullable (REAL)
            // Tuyệt đối KHÔNG drop hoặc tác động lên news_table để bảo toàn 100% dữ liệu ai_analysis_table (tránh ON DELETE CASCADE)
            database.execSQL("CREATE TABLE IF NOT EXISTS watchlist_table_new (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "symbol TEXT, " +
                    "price REAL, " +
                    "change24h REAL, " +
                    "userEmail TEXT)");

            database.execSQL("INSERT INTO watchlist_table_new (id, symbol, price, change24h, userEmail) " +
                    "SELECT id, symbol, price, change24h, userEmail FROM watchlist_table");

            database.execSQL("DROP TABLE watchlist_table");
            database.execSQL("ALTER TABLE watchlist_table_new RENAME TO watchlist_table");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE news_table ADD COLUMN originalTitle TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN originalSummary TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN displayTitleVi TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN displaySummaryVi TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN bulletPointsVi TEXT");
            database.execSQL("ALTER TABLE news_table ADD COLUMN publisher TEXT");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "fnmf_unified_mobile_db"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
