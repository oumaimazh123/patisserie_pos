package ma.elaroui.pos.data.mapper

import ma.elaroui.pos.data.local.entity.*
import ma.elaroui.pos.domain.model.*

fun UserEntity.toDomain() = User(
    id = id,
    name = name,
    role = UserRole.valueOf(role),
    pinHash = pinHash,
    active = active,
    createdAt = createdAt
)

fun User.toEntity() = UserEntity(
    id = id,
    name = name,
    role = role.name,
    pinHash = pinHash,
    active = active,
    createdAt = createdAt
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    displayOrder = displayOrder,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    displayOrder = displayOrder,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ProductEntity.toDomain() = Product(
    id = id,
    categoryId = categoryId,
    name = name,
    priceCentimes = priceCentimes,
    tvaRate = tvaRate,
    imagePath = imagePath,
    available = available,
    active = active,
    displayOrder = displayOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sku = sku,
    barcode = barcode
)

fun Product.toEntity() = ProductEntity(
    id = id,
    categoryId = categoryId,
    name = name,
    priceCentimes = priceCentimes,
    tvaRate = tvaRate,
    imagePath = imagePath,
    available = available,
    active = active,
    displayOrder = displayOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sku = sku?.trim()?.ifBlank { null },
    barcode = barcode?.trim()?.ifBlank { null }
)

fun DiningAreaEntity.toDomain() = DiningArea(
    id = id,
    name = name,
    displayOrder = displayOrder,
    active = active,
    imagePath = imagePath,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DiningArea.toEntity() = DiningAreaEntity(
    id = id,
    name = name,
    displayOrder = displayOrder,
    active = active,
    imagePath = imagePath,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TableEntity.toDomain() = RestaurantTable(
    id = id,
    areaId = areaId,
    name = name,
    status = TableStatus.valueOf(status),
    active = active,
    displayOrder = displayOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RestaurantTable.toEntity() = TableEntity(
    id = id,
    areaId = areaId,
    name = name,
    status = status.name,
    active = active,
    displayOrder = displayOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RegisterEntity.toDomain() = Register(
    id = id,
    name = name,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Register.toEntity() = RegisterEntity(
    id = id,
    name = name,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RegisterSessionEntity.toDomain() = RegisterSession(
    id = id,
    registerId = registerId,
    cashierId = cashierId,
    openedByUserId = openedByUserId,
    openedAt = openedAt,
    openingCashCentimes = openingCashCentimes,
    closedByUserId = closedByUserId,
    closedAt = closedAt,
    expectedCashCentimes = expectedCashCentimes,
    countedCashCentimes = countedCashCentimes,
    differenceCentimes = differenceCentimes,
    closingNote = closingNote,
    ownerApprovedDifference = ownerApprovedDifference,
    approvedByOwnerId = approvedByOwnerId,
    status = RegisterSessionStatus.valueOf(status)
)

fun RegisterSession.toEntity() = RegisterSessionEntity(
    id = id,
    registerId = registerId,
    cashierId = cashierId,
    openedByUserId = openedByUserId,
    openedAt = openedAt,
    openingCashCentimes = openingCashCentimes,
    closedByUserId = closedByUserId,
    closedAt = closedAt,
    expectedCashCentimes = expectedCashCentimes,
    countedCashCentimes = countedCashCentimes,
    differenceCentimes = differenceCentimes,
    closingNote = closingNote,
    ownerApprovedDifference = ownerApprovedDifference,
    approvedByOwnerId = approvedByOwnerId,
    status = status.name
)

fun CashMovementEntity.toDomain() = CashMovement(
    id = id,
    registerSessionId = registerSessionId,
    type = CashMovementType.valueOf(type),
    amountCentimes = amountCentimes,
    reason = reason,
    createdByUserId = createdByUserId,
    createdAt = createdAt
)

fun CashMovement.toEntity() = CashMovementEntity(
    id = id,
    registerSessionId = registerSessionId,
    type = type.name,
    amountCentimes = amountCentimes,
    reason = reason,
    createdByUserId = createdByUserId,
    createdAt = createdAt
)

fun OrderItemEntity.toDomain() = OrderItem(
    id = id,
    orderId = orderId,
    productId = productId,
    productNameSnapshot = productNameSnapshot,
    categoryIdSnapshot = categoryIdSnapshot,
    categoryNameSnapshot = categoryNameSnapshot,
    unitPriceSnapshotCentimes = unitPriceSnapshotCentimes,
    tvaRateSnapshot = tvaRateSnapshot,
    quantity = quantity,
    note = note,
    lineTotalCentimes = lineTotalCentimes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderItem.toEntity() = OrderItemEntity(
    id = id,
    orderId = orderId,
    productId = productId,
    productNameSnapshot = productNameSnapshot,
    categoryIdSnapshot = categoryIdSnapshot,
    categoryNameSnapshot = categoryNameSnapshot,
    unitPriceSnapshotCentimes = unitPriceSnapshotCentimes,
    tvaRateSnapshot = tvaRateSnapshot,
    quantity = quantity,
    note = note,
    lineTotalCentimes = lineTotalCentimes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PaymentEntity.toDomain() = Payment(
    id = id,
    orderId = orderId,
    registerSessionId = registerSessionId,
    method = PaymentMethod.valueOf(method),
    amountCentimes = amountCentimes,
    receivedAmountCentimes = receivedAmountCentimes,
    changeAmountCentimes = changeAmountCentimes,
    createdByUserId = createdByUserId,
    status = PaymentStatus.valueOf(status),
    externalReference = externalReference,
    submissionToken = submissionToken,
    note = note,
    createdAt = createdAt
)

fun Payment.toEntity() = PaymentEntity(
    id = id,
    orderId = orderId,
    registerSessionId = registerSessionId,
    method = method.name,
    amountCentimes = amountCentimes,
    receivedAmountCentimes = receivedAmountCentimes,
    changeAmountCentimes = changeAmountCentimes,
    createdByUserId = createdByUserId,
    status = status.name,
    externalReference = externalReference,
    submissionToken = submissionToken,
    note = note,
    createdAt = createdAt
)

fun OrderEntity.toDomain(items: List<OrderItem> = emptyList(), payments: List<Payment> = emptyList()) = Order(
    id = id,
    orderNumber = orderNumber,
    type = OrderType.valueOf(type),
    tableId = tableId,
    cashierId = cashierId,
    registerSessionId = registerSessionId,
    status = OrderStatus.valueOf(status),
    subtotalCentimes = subtotalCentimes,
    discountCentimes = discountCentimes,
    tvaTotalCentimes = tvaTotalCentimes,
    totalCentimes = totalCentimes,
    buyerCompanyName = buyerCompanyName,
    buyerAddress = buyerAddress,
    buyerIce = buyerIce,
    items = items,
    payments = payments,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    completedByUserId = completedByUserId,
    cancelledAt = cancelledAt,
    cancelledByUserId = cancelledByUserId,
    cancellationReason = cancellationReason,
    approvedByOwnerId = approvedByOwnerId,
    version = version
)

fun Order.toEntity() = OrderEntity(
    id = id,
    orderNumber = orderNumber,
    type = type.name,
    tableId = tableId,
    cashierId = cashierId,
    registerSessionId = registerSessionId,
    status = status.name,
    subtotalCentimes = subtotalCentimes,
    discountCentimes = discountCentimes,
    tvaTotalCentimes = tvaTotalCentimes,
    totalCentimes = totalCentimes,
    buyerCompanyName = buyerCompanyName,
    buyerAddress = buyerAddress,
    buyerIce = buyerIce,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    completedByUserId = completedByUserId,
    cancelledAt = cancelledAt,
    cancelledByUserId = cancelledByUserId,
    cancellationReason = cancellationReason,
    approvedByOwnerId = approvedByOwnerId,
    version = version
)

fun AuditLogEntity.toDomain() = AuditLog(
    id = id,
    actionType = AuditAction.valueOf(actionType),
    actingUserId = actingUserId,
    approvedByOwnerId = approvedByOwnerId,
    entityType = entityType,
    entityId = entityId,
    timestamp = timestamp,
    details = details,
    result = result
)

fun AuditLog.toEntity() = AuditLogEntity(
    id = id,
    actionType = actionType.name,
    actingUserId = actingUserId,
    approvedByOwnerId = approvedByOwnerId,
    entityType = entityType,
    entityId = entityId,
    timestamp = timestamp,
    details = details,
    result = result
)

fun PrinterSettingsEntity.toDomain() = PrinterSettings(
    enabled = enabled,
    printerType = PrinterType.valueOf(printerType),
    printerName = printerName,
    printerAddress = printerAddress,
    paperWidth = paperWidth,
    copies = copies,
    autoPrint = autoPrint,
    printLogo = printLogo,
    kitchenEnabled = kitchenEnabled,
    kitchenPrinterType = runCatching { PrinterType.valueOf(kitchenPrinterType) }
        .getOrDefault(PrinterType.ETHERNET_ESCPOS),
    kitchenPrinterName = kitchenPrinterName,
    kitchenPrinterAddress = kitchenPrinterAddress,
    kitchenPaperWidth = kitchenPaperWidth,
    kitchenAutoPrint = kitchenAutoPrint
)

fun PrinterSettings.toEntity() = PrinterSettingsEntity(
    id = 1L,
    enabled = enabled,
    printerType = printerType.name,
    printerName = printerName,
    printerAddress = printerAddress,
    paperWidth = paperWidth,
    copies = copies,
    autoPrint = autoPrint,
    printLogo = printLogo,
    kitchenEnabled = kitchenEnabled,
    kitchenPrinterType = kitchenPrinterType.name,
    kitchenPrinterName = kitchenPrinterName,
    kitchenPrinterAddress = kitchenPrinterAddress,
    kitchenPaperWidth = kitchenPaperWidth,
    kitchenAutoPrint = kitchenAutoPrint
)
