package ma.elaroui.pos.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ma.elaroui.pos.data.local.dao.*
import ma.elaroui.pos.data.local.database.POSDatabase
import ma.elaroui.pos.data.local.database.MIGRATION_4_5
import ma.elaroui.pos.data.local.database.MIGRATION_5_6
import ma.elaroui.pos.data.local.database.MIGRATION_6_7
import ma.elaroui.pos.data.local.database.MIGRATION_7_8
import ma.elaroui.pos.data.local.database.MIGRATION_8_9
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePOSDatabase(@ApplicationContext context: Context): POSDatabase {
        return Room.databaseBuilder(
            context,
            POSDatabase::class.java,
            "pos_restaurant_db"
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideUserDao(db: POSDatabase): UserDao = db.userDao()

    @Provides
    fun provideCategoryDao(db: POSDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideProductDao(db: POSDatabase): ProductDao = db.productDao()

    @Provides
    fun provideTableDao(db: POSDatabase): TableDao = db.tableDao()

    @Provides
    fun provideRegisterDao(db: POSDatabase): RegisterDao = db.registerDao()

    @Provides
    fun provideCashMovementDao(db: POSDatabase): CashMovementDao = db.cashMovementDao()

    @Provides
    fun provideOrderDao(db: POSDatabase): OrderDao = db.orderDao()

    @Provides
    fun providePaymentDao(db: POSDatabase): PaymentDao = db.paymentDao()

    @Provides
    fun provideAuditLogDao(db: POSDatabase): AuditLogDao = db.auditLogDao()

    @Provides
    fun providePrinterSettingsDao(db: POSDatabase): PrinterSettingsDao = db.printerSettingsDao()
}
