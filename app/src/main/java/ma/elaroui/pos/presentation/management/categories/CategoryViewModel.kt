package ma.elaroui.pos.presentation.management.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.Category
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.ActivateCategoryUseCase
import ma.elaroui.pos.domain.usecase.CreateCategoryUseCase
import ma.elaroui.pos.domain.usecase.DeactivateCategoryUseCase
import ma.elaroui.pos.domain.usecase.GetCategoriesUseCase
import ma.elaroui.pos.domain.usecase.UpdateCategoryUseCase
import javax.inject.Inject

data class CategoryManagementUiState(
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val isAddEditDialogOpen: Boolean = false,
    val editingCategory: Category? = null,
    val categoryNameInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val activateCategoryUseCase: ActivateCategoryUseCase,
    private val deactivateCategoryUseCase: DeactivateCategoryUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManagementUiState())
    val uiState: StateFlow<CategoryManagementUiState> = _uiState.asStateFlow()
    private var categoriesJob: Job? = null

    init {
        loadCategories()
    }

    fun loadCategories() {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getCategoriesUseCase(forOwner = true).collect { list ->
                _uiState.update { it.copy(categories = list, isLoading = false) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                isAddEditDialogOpen = true,
                editingCategory = null,
                categoryNameInput = "",
                errorMessage = null
            )
        }
    }

    fun openEditDialog(category: Category) {
        _uiState.update {
            it.copy(
                isAddEditDialogOpen = true,
                editingCategory = category,
                categoryNameInput = category.name,
                errorMessage = null
            )
        }
    }

    fun closeDialog() {
        _uiState.update { it.copy(isAddEditDialogOpen = false) }
    }

    fun updateCategoryNameInput(input: String) {
        _uiState.update { it.copy(categoryNameInput = input, errorMessage = null) }
    }

    fun submitSaveCategory() {
        val state = _uiState.value
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        if (state.categoryNameInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Le nom de la catégorie est obligatoire.") }
            return
        }
        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur non trouvé.")

                val saved = if (state.editingCategory == null) {
                    createCategoryUseCase(state.categoryNameInput, currentUser)
                } else {
                    updateCategoryUseCase(state.editingCategory.id, state.categoryNameInput, currentUser)
                }

                _uiState.update {
                    it.copy(
                        categories = it.categories
                            .filterNot { category -> category.id == saved.id }
                            .plus(saved)
                            .sortedWith(compareBy<Category> { category -> category.displayOrder }.thenBy { category -> category.name }),
                        isLoading = false,
                        isAddEditDialogOpen = false,
                        successMessage = "Catégorie enregistrée avec succès."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur d'enregistrement."
                    )
                }
            }
        }
    }

    fun toggleCategoryActiveState(category: Category) {
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur non trouvé.")

                if (category.active) {
                    deactivateCategoryUseCase(category.id, currentUser)
                } else {
                    activateCategoryUseCase(category.id, currentUser)
                }
                _uiState.update {
                    it.copy(
                        categories = it.categories.map { item ->
                            if (item.id == category.id) item.copy(active = !category.active) else item
                        },
                        isLoading = false,
                        successMessage = if (category.active) "Catégorie supprimée." else "Catégorie réactivée."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }
}
