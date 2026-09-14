package ma.elaroui.pos.domain.usecase

import ma.elaroui.pos.domain.repository.CategoryRepository
import ma.elaroui.pos.domain.repository.ProductRepository
import ma.elaroui.pos.domain.repository.TableRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnsureStarterDataUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke() {
        // Production starts with an empty catalogue. Categories and products are created
        // by the owner or imported from CSV; no demo restaurant or retail records are seeded.
    }
}
