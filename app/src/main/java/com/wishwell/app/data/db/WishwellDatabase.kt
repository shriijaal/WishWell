package com.wishwell.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wishwell.app.data.model.Category
import com.wishwell.app.data.model.PricePoint
import com.wishwell.app.data.model.WishItem

@Database(
    entities = [Category::class, WishItem::class, PricePoint::class],
    version = 5,
    exportSchema = false
)
abstract class WishwellDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    abstract fun categoryDao(): CategoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN imageUrlsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE items ADD COLUMN specsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_isArchived_createdAt` ON `items` (`isArchived`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_points_itemId_recordedAt` ON `price_points` (`itemId`, `recordedAt`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN inStock INTEGER")
            }
        }

        fun build(context: Context): WishwellDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WishwellDatabase::class.java,
                "wishwell.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
