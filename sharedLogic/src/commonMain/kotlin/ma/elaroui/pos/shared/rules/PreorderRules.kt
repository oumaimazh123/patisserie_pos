package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.PreparationStatus

enum class PreorderValidationError {
    CUSTOMER_NAME_REQUIRED,
    INVALID_PICKUP_DATE,
    NEGATIVE_DEPOSIT,
    DEPOSIT_EXCEEDS_TOTAL,
    EMPTY_ORDER
}

object PreorderRules {

    /**
     * Valide une précommande de pâtisserie (gâteau sur mesure, commande pour événement).
     */
    fun validatePreorder(
        customerName: String?,
        pickupDateEpochMs: Long?,
        orderCreatedAtMs: Long,
        itemCount: Int,
        totalCentimes: Long,
        depositCentimes: Long
    ): PreorderValidationError? {
        if (customerName.isNull_or_blank()) {
            return PreorderValidationError.CUSTOMER_NAME_REQUIRED
        }
        if (itemCount <= 0 || totalCentimes <= 0L) {
            return PreorderValidationError.EMPTY_ORDER
        }
        if (pickupDateEpochMs != null && pickupDateEpochMs < orderCreatedAtMs - 60_000L) {
            // Tolérance d'1 minute pour les légers décalages d'horloge
            return PreorderValidationError.INVALID_PICKUP_DATE
        }
        if (depositCentimes < 0L) {
            return PreorderValidationError.NEGATIVE_DEPOSIT
        }
        if (depositCentimes > totalCentimes) {
            return PreorderValidationError.DEPOSIT_EXCEEDS_TOTAL
        }
        return null
    }

    /**
     * Calcule le solde restant à payer au moment du retrait en magasin.
     */
    fun calculateBalanceDue(totalCentimes: Long, depositCentimes: Long): Long {
        require(totalCentimes >= 0L) { "Le total de la commande ne peut pas être négatif." }
        require(depositCentimes >= 0L) { "L'acompte ne peut pas être négatif." }
        return MathRules.subtractExact(totalCentimes, depositCentimes.coerceAtMost(totalCentimes))
    }

    /**
     * Calcule l'acompte minimum recommandé (ex: 30% du montant total).
     */
    fun recommendedDepositCentimes(totalCentimes: Long, percentageBasisPoints: Int = 3000): Long {
        require(percentageBasisPoints in 0..10_000) { "Le pourcentage doit être compris entre 0% et 100%." }
        return MathRules.divideHalfUp(
            MathRules.multiplyExact(totalCentimes, percentageBasisPoints.toLong()),
            10_000L
        )
    }

    /**
     * Vérifie si la transition d'état de préparation est autorisée dans le workflow pâtisserie/laboratoire.
     */
    fun canTransition(from: PreparationStatus, to: PreparationStatus): Boolean = when (from) {
        PreparationStatus.PENDING -> to == PreparationStatus.IN_PREPARATION || to == PreparationStatus.READY
        PreparationStatus.IN_PREPARATION -> to == PreparationStatus.READY
        PreparationStatus.READY -> to == PreparationStatus.DELIVERED
        PreparationStatus.DELIVERED -> false
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
