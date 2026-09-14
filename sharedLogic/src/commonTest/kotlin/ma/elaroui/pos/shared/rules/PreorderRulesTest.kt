package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.PreparationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreorderRulesTest {

    @Test
    fun validPreorderReturnsNull() {
        val now = 1_700_000_000_000L
        val pickup = now + 86_400_000L // lendemain
        val error = PreorderRules.validatePreorder(
            customerName = "Mme Fatima",
            pickupDateEpochMs = pickup,
            orderCreatedAtMs = now,
            itemCount = 2,
            totalCentimes = 25_000L, // 250.00 DH
            depositCentimes = 10_000L // 100.00 DH acompte
        )
        assertNull(error)
    }

    @Test
    fun missingCustomerNameFails() {
        val now = 1_700_000_000_000L
        val error = PreorderRules.validatePreorder(
            customerName = "   ",
            pickupDateEpochMs = now + 10_000L,
            orderCreatedAtMs = now,
            itemCount = 1,
            totalCentimes = 5_000L,
            depositCentimes = 0L
        )
        assertEquals(PreorderValidationError.CUSTOMER_NAME_REQUIRED, error)
    }

    @Test
    fun pickupDateInPastFails() {
        val now = 1_700_000_000_000L
        val past = now - 3_600_000L // 1 heure dans le passé
        val error = PreorderRules.validatePreorder(
            customerName = "Karim",
            pickupDateEpochMs = past,
            orderCreatedAtMs = now,
            itemCount = 1,
            totalCentimes = 15_000L,
            depositCentimes = 5_000L
        )
        assertEquals(PreorderValidationError.INVALID_PICKUP_DATE, error)
    }

    @Test
    fun depositExceedingTotalFails() {
        val now = 1_700_000_000_000L
        val error = PreorderRules.validatePreorder(
            customerName = "Sarah",
            pickupDateEpochMs = now + 100_000L,
            orderCreatedAtMs = now,
            itemCount = 1,
            totalCentimes = 10_000L,
            depositCentimes = 12_000L // acompte > total
        )
        assertEquals(PreorderValidationError.DEPOSIT_EXCEEDS_TOTAL, error)
    }

    @Test
    fun negativeDepositFails() {
        val now = 1_700_000_000_000L
        val error = PreorderRules.validatePreorder(
            customerName = "Ali",
            pickupDateEpochMs = now + 100_000L,
            orderCreatedAtMs = now,
            itemCount = 1,
            totalCentimes = 10_000L,
            depositCentimes = -500L
        )
        assertEquals(PreorderValidationError.NEGATIVE_DEPOSIT, error)
    }

    @Test
    fun balanceDueCalculatesCorrectly() {
        assertEquals(15_000L, PreorderRules.calculateBalanceDue(totalCentimes = 25_000L, depositCentimes = 10_000L))
        assertEquals(0L, PreorderRules.calculateBalanceDue(totalCentimes = 20_000L, depositCentimes = 20_000L))
        assertEquals(5_000L, PreorderRules.calculateBalanceDue(totalCentimes = 5_000L, depositCentimes = 0L))
    }

    @Test
    fun recommendedDepositDefaultsTo30Percent() {
        // 30% de 300.00 DH (30 000 centimes) = 90.00 DH (9 000 centimes)
        assertEquals(9_000L, PreorderRules.recommendedDepositCentimes(30_000L))
        // 50% d'acompte sur pièce montée de 1000.00 DH = 500.00 DH
        assertEquals(50_000L, PreorderRules.recommendedDepositCentimes(100_000L, percentageBasisPoints = 5000))
    }

    @Test
    fun preparationStatusTransitions() {
        assertTrue(PreorderRules.canTransition(PreparationStatus.PENDING, PreparationStatus.IN_PREPARATION))
        assertTrue(PreorderRules.canTransition(PreparationStatus.PENDING, PreparationStatus.READY))
        assertTrue(PreorderRules.canTransition(PreparationStatus.IN_PREPARATION, PreparationStatus.READY))
        assertTrue(PreorderRules.canTransition(PreparationStatus.READY, PreparationStatus.DELIVERED))

        assertFalse(PreorderRules.canTransition(PreparationStatus.DELIVERED, PreparationStatus.PENDING))
        assertFalse(PreorderRules.canTransition(PreparationStatus.READY, PreparationStatus.IN_PREPARATION))
    }
}
