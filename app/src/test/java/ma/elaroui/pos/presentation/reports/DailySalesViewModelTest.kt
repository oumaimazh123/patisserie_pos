package ma.elaroui.pos.presentation.reports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ma.elaroui.pos.domain.usecase.ExportSalesCsvUseCase
import ma.elaroui.pos.domain.usecase.FakeOrderRepository
import ma.elaroui.pos.domain.usecase.FakeRegisterRepository
import ma.elaroui.pos.domain.usecase.FakeUserRepository
import ma.elaroui.pos.domain.usecase.GetDailySalesReportUseCase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class DailySalesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exportCsv_keepsStreamOpenUntilWriteCompletesThenClosesIt() = runTest {
        val orderRepository = FakeOrderRepository()
        val userRepository = FakeUserRepository()
        val registerRepository = FakeRegisterRepository()
        val viewModel = DailySalesViewModel(
            getDailySalesReportUseCase = GetDailySalesReportUseCase(
                orderRepository,
                userRepository
            ),
            exportSalesCsvUseCase = ExportSalesCsvUseCase(
                orderRepository,
                userRepository,
                registerRepository
            )
        )
        advanceUntilIdle()

        val output = TrackingOutputStream()
        viewModel.exportCsv(output)
        advanceUntilIdle()

        assertTrue(output.closed)
        assertTrue(output.size() > 0)
        assertFalse(viewModel.uiState.value.isExporting)
        assertTrue(
            viewModel.uiState.value.exportSuccessMessage
                ?.contains("succès", ignoreCase = true) == true
        )
    }
}

private class TrackingOutputStream : ByteArrayOutputStream() {
    var closed: Boolean = false
        private set

    override fun close() {
        closed = true
        super.close()
    }
}
