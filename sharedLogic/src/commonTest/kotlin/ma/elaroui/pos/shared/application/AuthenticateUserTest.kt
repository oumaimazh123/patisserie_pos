package ma.elaroui.pos.shared.application

import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.shared.domain.AuthenticationRepository
import ma.elaroui.pos.shared.domain.User
import ma.elaroui.pos.shared.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthenticateUserTest {
    private val user = User(1, "Owner", UserRole.OWNER, true)
    private val repository = object : AuthenticationRepository {
        override suspend fun authenticate(credential: String): User? =
            user.takeIf { credential in setOf("1234", "12345", "123456") }
    }

    @Test fun acceptsFourFiveAndSixDigits() = runTest {
        listOf("1234", "12345", "123456").forEach { pin ->
            assertIs<UseCaseResult.Success<User>>(AuthenticateUser(repository).execute(pin))
        }
    }

    @Test fun rejectsShortLongAndNonNumericBeforeRepositoryAuthentication() = runTest {
        listOf("123", "1234567", "12a4").forEach { pin ->
            val result = assertIs<UseCaseResult.Failure>(AuthenticateUser(repository).execute(pin))
            assertEquals("PIN must contain 4 to 6 digits", result.reason)
        }
    }
}
