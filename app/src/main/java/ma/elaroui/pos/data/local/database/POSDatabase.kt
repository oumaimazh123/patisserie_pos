package ma.elaroui.pos.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import ma.elaroui.pos.data.local.dao.*
import ma.elaroui.pos.data.local.entity.*
import java.math.BigDecimal

class Converters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }
}

@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        DiningAreaEntity::class,
        TableEntity::class,
        RegisterEntity::class,
        RegisterSessionEntity::class,
        CashMovementEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        PaymentEntity::class,
        AuditLogEntity::class,
        PrinterSettingsEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class POSDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun tableDao(): TableDao
    abstract fun registerDao(): RegisterDao
    abstract fun cashMovementDao(): CashMovementDao
    abstract fun orderDao(): OrderDao
    abstract fun paymentDao(): PaymentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun printerSettingsDao(): PrinterSettingsDao
}
