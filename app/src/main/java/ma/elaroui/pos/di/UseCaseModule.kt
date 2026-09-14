package ma.elaroui.pos.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ma.elaroui.pos.domain.usecase.DefaultUnpaidOrdersChecker
import ma.elaroui.pos.domain.usecase.UnpaidOrdersChecker

@Module
@InstallIn(SingletonComponent::class)
abstract class UseCaseModule {

    @Binds
    abstract fun bindUnpaidOrdersChecker(
        defaultUnpaidOrdersChecker: DefaultUnpaidOrdersChecker
    ): UnpaidOrdersChecker
}
