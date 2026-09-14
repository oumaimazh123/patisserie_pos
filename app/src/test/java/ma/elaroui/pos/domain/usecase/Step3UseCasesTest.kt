package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.core.exception.DomainException
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.data.local.preferences.SetupPreferences
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import ma.elaroui.pos.domain.session.SessionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeSetupPreferences : SetupPreferences(context = null) {
    private var setupState = false
    private var lang = "fr"
    private var restName = ""
    private var phone = ""
    private var addr = ""
    private var curr = "MAD"
    private var regId = 1L
    private var cashOutPinHash = ""

    override var isSetupComplete: Boolean
        get() = setupState
        set(value) { setupState = value }

    override var selectedLanguage: String
        get() = lang
        set(value) { lang = value }

    override var restaurantName: String
        get() = restName
        set(value) { restName = value }

    override var restaurantPhone: String
        get() = phone
        set(value) { phone = value }

    override var restaurantAddress: String
        get() = addr
        set(value) { addr = value }

    override var currency: String
        get() = curr
        set(value) { curr = value }

    override var defaultRegisterId: Long
        get() = regId
        set(value) { regId = value }

    override var cashOutApprovalPinHash: String
        get() = cashOutPinHash
        set(value) { cashOutPinHash = value }

    override fun resetPreferences() {
        setupState = false
        lang = "fr"
        restName = ""
        phone = ""
        addr = ""
        curr = "MAD"
        regId = 1L
        cashOutPinHash = ""
    }
}

class Step3UseCasesTest {

    private lateinit var userRepository: FakeUserRepository
    private lateinit var registerRepository: FakeRegisterRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var productRepository: FakeProductRepository
    private lateinit var tableRepository: FakeTableRepository
    private lateinit var setupPreferences: FakeSetupPreferences
    private lateinit var sessionManager: SessionManager

    private lateinit var completeFirstRunSetupUseCase: CompleteFirstRunSetupUseCase
    private lateinit var authenticateUserUseCase: AuthenticateUserUseCase
    private lateinit var manageCashierUseCase: ManageCashierUseCase

    @Before
    fun setUp() {
        userRepository = FakeUserRepository()
        registerRepository = FakeRegisterRepository()
        categoryRepository = FakeCategoryRepository()
        productRepository = FakeProductRepository()
        tableRepository = FakeTableRepository()
        setupPreferences = FakeSetupPreferences()
        sessionManager = SessionManager()

        completeFirstRunSetupUseCase = CompleteFirstRunSetupUseCase(
            userRepository = userRepository,
            registerRepository = registerRepository,
            ensureStarterDataUseCase = EnsureStarterDataUseCase(
                categoryRepository,
                productRepository,
                tableRepository
            ),
            setupPreferences = setupPreferences
        )

        authenticateUserUseCase = AuthenticateUserUseCase(
            userRepository = userRepository,
            registerRepository = registerRepository,
            sessionManager = sessionManager,
            setupPreferences = setupPreferences
        )

        manageCashierUseCase = ManageCashierUseCase(userRepository = userRepository)
    }

    @Test
    fun firstRunSetup_createsOwner_andSetsSetupComplete() = runTest {
        assertFalse(setupPreferences.isSetupComplete)

        val owner = completeFirstRunSetupUseCase(
            language = "fr",
            restaurantName = "Café Palmeraie",
            phone = "0600000000",
            address = "Marrakech",
            ownerName = "Zakaria Owner",
            ownerPin = "123456",
            registerName = "Caisse 1"
        )

        assertTrue(setupPreferences.isSetupComplete)
        assertEquals("Café Palmeraie", setupPreferences.restaurantName)
        assertEquals("Zakaria Owner", owner.name)
        assertEquals(UserRole.OWNER, owner.role)
        assertTrue(SecurityUtils.verifyPin("123456", owner.pinHash))
        assertEquals(1, categoryRepository.categories.size)
        assertFalse(categoryRepository.categories.values.any { it.name == "General" })
        assertFalse(tableRepository.areas.values.any { it.name == "Salle" })
        assertEquals(2, tableRepository.tables.size)
    }

    @Test(expected = DomainException::class)
    fun firstRunSetup_alreadyCompleted_throwsException() = runTest {
        completeFirstRunSetupUseCase("fr", "Resto", "", "", "Owner", "1234", "Reg")
        completeFirstRunSetupUseCase("fr", "Resto 2", "", "", "Owner 2", "1234", "Reg 2")
    }

    @Test
    fun starterData_repairsPartialInstallation_withoutOverwritingOrDuplicating() = runTest {
        val starterData = EnsureStarterDataUseCase(
            categoryRepository,
            productRepository,
            tableRepository
        )
        val dessertsId = categoryRepository.getCategoryByName("Desserts")!!.id
        productRepository.insertProduct(
            Product(
                categoryId = dessertsId,
                name = "Croissant",
                priceCentimes = 9_999L,
                available = false
            )
        )
        val salleId = tableRepository.insertArea(DiningArea(name = "Salle"))
        tableRepository.insertTable(
            RestaurantTable(areaId = salleId, name = "Table 1", active = false)
        )

        starterData()
        starterData()

        assertEquals(1, categoryRepository.categories.size)
        assertEquals(
            9_999L,
            productRepository.getProductByNameInCategory(dessertsId, "Croissant")?.priceCentimes
        )
        assertEquals(
            1,
            tableRepository.tables.values.count { it.areaId == salleId }
        )
        assertFalse(
            tableRepository.getTableByNameInArea(salleId, "Table 1")!!.active
        )
    }

    @Test
    fun authenticateUser_correctPin_logsInSuccessfully() = runTest {
        val owner = completeFirstRunSetupUseCase("fr", "Resto", "", "", "Owner", "1234", "Reg")

        val user = authenticateUserUseCase(owner.id, "1234")
        assertNotNull(user)
        assertTrue(sessionManager.isAuthenticated)
        assertEquals(owner.id, sessionManager.currentUser?.userId)
    }

    @Test
    fun authenticateUser_cashierJoinsSessionOpenedByAnotherUser() = runTest {
        val owner = completeFirstRunSetupUseCase("fr", "Resto", "", "", "Owner", "1234", "Reg")
        val cashier = manageCashierUseCase.createCashier("Salma", "4321")
        val sharedSessionId = registerRepository.openSession(
            RegisterSession(
                registerId = setupPreferences.defaultRegisterId,
                cashierId = owner.id,
                openingCashCentimes = 20_000L,
                status = RegisterSessionStatus.OPEN
            )
        )

        authenticateUserUseCase(cashier.id, "4321")

        assertEquals(cashier.id, sessionManager.currentUser?.userId)
        assertEquals(sharedSessionId, sessionManager.currentUser?.currentRegisterSessionId)
    }

    @Test(expected = DomainException::class)
    fun authenticateUser_incorrectPin_throwsException() = runTest {
        val owner = completeFirstRunSetupUseCase("fr", "Resto", "", "", "Owner", "1234", "Reg")
        authenticateUserUseCase(owner.id, "9999")
    }

    @Test(expected = AccountLockedException::class)
    fun authenticateUser_fiveFailedAttempts_locksAccount() = runTest {
        val owner = completeFirstRunSetupUseCase("fr", "Resto", "", "", "Owner", "1234", "Reg")

        // 4 failed attempts
        for (i in 1..4) {
            try { authenticateUserUseCase(owner.id, "9999") } catch (_: DomainException) {}
        }

        // 5th failed attempt -> locks account
        authenticateUserUseCase(owner.id, "9999")
    }

    @Test
    fun manageCashier_createCashier_andResetPinWithOwnerApproval() = runTest {
        val owner = completeFirstRunSetupUseCase("fr", "Resto", "", "", "Owner", "1234", "Reg")
        val cashier = manageCashierUseCase.createCashier("Caissier Ahmed", "4321")

        assertNotNull(cashier)
        assertEquals("Caissier Ahmed", cashier.name)
        assertEquals(UserRole.CASHIER, cashier.role)

        // Owner resets cashier PIN
        manageCashierUseCase.resetCashierPin(
            cashierId = cashier.id,
            newPin = "5555",
            ownerPin = "1234",
            ownerUser = owner
        )

        val updatedCashier = userRepository.getUserById(cashier.id)
        assertNotNull(updatedCashier)
        assertTrue(SecurityUtils.verifyPin("5555", updatedCashier!!.pinHash))
    }

    @Test(expected = DomainException::class)
    fun manageCashier_deactivateSelf_throwsException() = runTest {
        val owner = completeFirstRunSetupUseCase("fr", "Resto", "", "", "Owner", "1234", "Reg")
        manageCashierUseCase.toggleCashierActiveState(cashierId = owner.id, currentAuthenticatedOwnerId = owner.id)
    }

    @Test
    fun sessionManager_lockPreservesSession_logoutClearsState() = runTest {
        val owner = completeFirstRunSetupUseCase("fr", "Resto", "", "", "Owner", "1234", "Reg")
        authenticateUserUseCase(owner.id, "1234")

        sessionManager.updateRegisterSessionId(100L)
        sessionManager.lock()

        assertTrue(sessionManager.currentUser?.isLocked == true)
        assertEquals(100L, sessionManager.currentUser?.currentRegisterSessionId)

        sessionManager.unlock()
        assertFalse(sessionManager.currentUser?.isLocked == true)

        sessionManager.logout()
        assertFalse(sessionManager.isAuthenticated)
        assertNull(sessionManager.currentUser)
    }
}
