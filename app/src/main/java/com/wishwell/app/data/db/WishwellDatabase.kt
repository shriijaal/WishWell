package com.wishwell.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wishwell.app.data.model.Category
import com.wishwell.app.data.model.ItemCategoryTag
import com.wishwell.app.data.model.PricePoint
import com.wishwell.app.data.model.WishItem

@Database(
    entities = [Category::class, WishItem::class, PricePoint::class, ItemCategoryTag::class],
    version = 7,
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

        // No schema changes; keeps databases opened by newer debug builds compatible.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        // Many-to-many item <-> category tags.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `item_category_tags` (
                        `itemId` INTEGER NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        PRIMARY KEY (`itemId`, `categoryId`),
                        FOREIGN KEY (`itemId`) REFERENCES `items` (`id`) ON DELETE CASCADE,
                        FOREIGN KEY (`categoryId`) REFERENCES `categories` (`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_item_category_tags_categoryId` ON `item_category_tags` (`categoryId`)"
                )
                // Backfill existing single categoryId assignments into the junction table.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `item_category_tags` (`itemId`, `categoryId`)
                    SELECT `id`, `categoryId` FROM `items` WHERE `categoryId` IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        fun build(context: Context): WishwellDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WishwellDatabase::class.java,
                "wishwell.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
