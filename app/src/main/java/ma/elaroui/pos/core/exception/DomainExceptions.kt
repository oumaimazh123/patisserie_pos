package ma.elaroui.pos.core.exception

open class DomainException(message: String) : Exception(message)

class SessionAlreadyOpenException(registerId: Long) :
    DomainException("An active register session already exists for register ID $registerId")

class NoActiveSessionException(registerId: Long) :
    DomainException("No active register session found for register ID $registerId. Orders cannot be created.")

class TableOccupiedException(tableName: String) :
    DomainException("Table '$tableName' is currently occupied and cannot take another open order.")

class OrderAlreadyClosedException(orderId: Long, status: String = "COMPLETED") :
    DomainException("Order #$orderId is $status and cannot be edited or modified.")

class UnpaidOrdersExistException(session: Long, count: Int) :
    DomainException("Cannot close register session #$session: $count unpaid open order(s) exist.")

class InvalidOwnerPinException :
    DomainException("Invalid Owner PIN provided. Authorization denied.")

class CancellationReasonRequiredException :
    DomainException("A valid non-empty cancellation reason is required.")

class ProductNotAvailableException(productName: String) :
    DomainException("Product '$productName' is currently unavailable.")

class InsufficientPaymentException(total: String, paid: String) :
    DomainException("Total paid ($paid) is less than order total ($total).")

class OrderAlreadyCompletedException(orderId: Long) :
    DomainException("Commande #$orderId déjà réglée et clôturée.")

class OrderCancelledException(orderId: Long) :
    DomainException("Commande #$orderId a été annulée et ne peut plus être payée.")

class EmptyOrderException :
    DomainException("Impossible de régler une commande sans aucun article.")

class CashReceivedTooLowException(total: String, received: String) :
    DomainException("Montant reçu ($received) inférieur au total de la commande ($total).")

class InvalidPaymentAmountException :
    DomainException("Le montant du paiement doit être supérieur à zéro.")

class DuplicatePaymentSubmissionException :
    DomainException("Paiement déjà soumis et enregistré.")

class BackupFailedException(reason: String) :
    DomainException("Échec de la création de la sauvegarde : $reason")

class RestoreFailedException(reason: String) :
    DomainException("Échec de la restauration : $reason")
