package ma.elaroui.pos.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionRunnerContractTest {
    @Test
    fun failedPaymentWorkRollsBackAllStagedChanges() = kotlinx.coroutines.test.runTest {
        val store = FakeTransactionalStore()

        assertFailsWith<IllegalStateException> {
            store.inTransaction {
                store.paymentSaved = true
                store.orderStatus = OrderStatus.COMPLETED
                store.cashTotalCentimes = 5_000
                error("simulated persistence failure")
            }
        }

        assertEquals(false, store.paymentSaved)
        assertEquals(OrderStatus.OPEN, store.orderStatus)
        assertEquals(0, store.cashTotalCentimes)
    }

    private class FakeTransactionalStore : TransactionRunner {
        var paymentSaved = false
        var orderStatus = OrderStatus.OPEN
        var cashTotalCentimes = 0L

        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            val snapshot = Triple(paymentSaved, orderStatus, cashTotalCentimes)
            return try {
                block()
            } catch (failure: Throwable) {
                paymentSaved = snapshot.first
                orderStatus = snapshot.second
                cashTotalCentimes = snapshot.third
                throw failure
            }
        }
    }
}
