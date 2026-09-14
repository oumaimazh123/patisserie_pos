package ma.elaroui.pos.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ma.elaroui.pos.core.print.ConfiguredReceiptPrinter
import ma.elaroui.pos.core.print.ReceiptPrinter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrinterModule {

    @Provides
    @Singleton
    fun provideReceiptPrinter(@ApplicationContext context: Context): ReceiptPrinter {
        return ConfiguredReceiptPrinter(context)
    }
}
