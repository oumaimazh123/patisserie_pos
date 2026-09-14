package ma.elaroui.pos.presentation.management.tables

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ma.elaroui.pos.domain.model.DiningArea
import ma.elaroui.pos.domain.model.RestaurantTable
import ma.elaroui.pos.core.util.ImageUtils
import ma.elaroui.pos.domain.repository.UserRepository
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.domain.usecase.ActivateDiningAreaUseCase
import ma.elaroui.pos.domain.usecase.ActivateTableUseCase
import ma.elaroui.pos.domain.usecase.BulkCreateTablesUseCase
import ma.elaroui.pos.domain.usecase.CreateDiningAreaUseCase
import ma.elaroui.pos.domain.usecase.CreateTableUseCase
import ma.elaroui.pos.domain.usecase.DeactivateDiningAreaUseCase
import ma.elaroui.pos.domain.usecase.DeactivateTableUseCase
import ma.elaroui.pos.domain.usecase.GetDiningAreasUseCase
import ma.elaroui.pos.domain.usecase.GetTablesUseCase
import ma.elaroui.pos.domain.usecase.ObserveSelectableTablesUseCase
import ma.elaroui.pos.domain.usecase.UpdateDiningAreaUseCase
import ma.elaroui.pos.domain.usecase.UpdateTableUseCase
import javax.inject.Inject

data class TableManagementUiState(
    val areas: List<DiningArea> = emptyList(),
    val tables: List<RestaurantTable> = emptyList(),
    val searchQuery: String = "",
    val selectedAreaId: Long? = null,
    val isAreaDialogOpen: Boolean = false,
    val editingArea: DiningArea? = null,
    val areaNameInput: String = "",
    val areaImagePathInput: String? = null,
    val isTableDialogOpen: Boolean = false,
    val editingTable: RestaurantTable? = null,
    val tableNameInput: String = "",
    val tableAreaIdInput: Long? = null,
    val isBulkDialogOpen: Boolean = false,
    val bulkAreaIdInput: Long? = null,
    val bulkPrefixInput: String = "Table",
    val bulkStartNumberInput: String = "1",
    val bulkCountInput: String = "10",
    val bulkPaddingInput: String = "0",
    val bulkGeneratedNamesPreview: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class TableManagementViewModel @Inject constructor(
    private val getDiningAreasUseCase: GetDiningAreasUseCase,
    private val getTablesUseCase: GetTablesUseCase,
    private val createDiningAreaUseCase: CreateDiningAreaUseCase,
    private val updateDiningAreaUseCase: UpdateDiningAreaUseCase,
    private val activateDiningAreaUseCase: ActivateDiningAreaUseCase,
    private val deactivateDiningAreaUseCase: DeactivateDiningAreaUseCase,
    private val createTableUseCase: CreateTableUseCase,
    private val updateTableUseCase: UpdateTableUseCase,
    private val activateTableUseCase: ActivateTableUseCase,
    private val deactivateTableUseCase: DeactivateTableUseCase,
    private val bulkCreateTablesUseCase: BulkCreateTablesUseCase,
    val observeSelectableTablesUseCase: ObserveSelectableTablesUseCase,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TableManagementUiState())
    val uiState: StateFlow<TableManagementUiState> = _uiState.asStateFlow()
    private var areasJob: Job? = null
    private var tablesJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        areasJob?.cancel()
        tablesJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        areasJob = viewModelScope.launch {
            getDiningAreasUseCase(forOwner = true).collect { areaList ->
                _uiState.update { state ->
                    val validFilter = state.selectedAreaId?.takeIf { id -> areaList.any { it.id == id } }
                    state.copy(areas = areaList, selectedAreaId = validFilter)
                }
            }
        }
        tablesJob = viewModelScope.launch {
            getTablesUseCase(forOwner = true).collect { tableList ->
                _uiState.update { it.copy(tables = tableList, isLoading = false) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectAreaFilter(areaId: Long?) {
        _uiState.update { it.copy(selectedAreaId = areaId) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    // AREA DIALOGS
    fun openAreaDialog(area: DiningArea? = null) {
        _uiState.update {
            it.copy(
                isAreaDialogOpen = true,
                editingArea = area,
                areaNameInput = area?.name ?: "",
                areaImagePathInput = area?.imagePath,
                errorMessage = null
            )
        }
    }

    fun closeAreaDialog() {
        val state = _uiState.value
        if (state.areaImagePathInput != state.editingArea?.imagePath) {
            ImageUtils.deleteAreaImage(state.areaImagePathInput)
        }
        _uiState.update { it.copy(isAreaDialogOpen = false) }
    }

    fun importAreaImage(context: Context, uri: Uri) {
        runCatching { ImageUtils.saveAreaImage(context, uri) }
            .onSuccess { path ->
                val state = _uiState.value
                ImageUtils.deleteAreaImage(state.areaImagePathInput?.takeIf { it != state.editingArea?.imagePath })
                _uiState.update { it.copy(areaImagePathInput = path, errorMessage = null) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Image non valide.") }
            }
    }

    fun removeAreaImage() {
        val state = _uiState.value
        ImageUtils.deleteAreaImage(state.areaImagePathInput?.takeIf { it != state.editingArea?.imagePath })
        _uiState.update { it.copy(areaImagePathInput = null, errorMessage = null) }
    }

    fun updateAreaNameInput(input: String) {
        _uiState.update { it.copy(areaNameInput = input, errorMessage = null) }
    }

    fun submitSaveArea() {
        val state = _uiState.value
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        if (state.areaNameInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Le nom de la zone est obligatoire.") }
            return
        }
        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                val saved = if (state.editingArea == null) {
                    createDiningAreaUseCase(state.areaNameInput, currentUser, state.areaImagePathInput)
                } else {
                    updateDiningAreaUseCase(
                        state.editingArea.id,
                        state.areaNameInput,
                        currentUser,
                        state.areaImagePathInput,
                        replaceImage = state.areaImagePathInput != state.editingArea.imagePath
                    )
                }

                if (state.editingArea?.imagePath != null && state.editingArea.imagePath != saved.imagePath) {
                    ImageUtils.deleteAreaImage(state.editingArea.imagePath)
                }

                _uiState.update {
                    it.copy(
                        areas = it.areas
                            .filterNot { area -> area.id == saved.id }
                            .plus(saved)
                            .sortedWith(compareBy<DiningArea> { area -> area.displayOrder }.thenBy { area -> area.name }),
                        isLoading = false,
                        isAreaDialogOpen = false,
                        successMessage = "Zone enregistrée avec succès."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur d'enregistrement de zone."
                    )
                }
            }
        }
    }

    fun toggleAreaActiveState(area: DiningArea) {
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                if (area.active) {
                    deactivateDiningAreaUseCase(area.id, currentUser)
                } else {
                    activateDiningAreaUseCase(area.id, currentUser)
                }
                _uiState.update {
                    it.copy(
                        areas = it.areas.map { item ->
                            if (item.id == area.id) item.copy(active = !area.active) else item
                        },
                        selectedAreaId = it.selectedAreaId.takeUnless { selected -> selected == area.id && area.active },
                        isLoading = false,
                        successMessage = if (area.active) {
                            "Zone désactivée."
                        } else {
                            "Zone réactivée."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    // TABLE DIALOGS
    fun openTableDialog(table: RestaurantTable? = null) {
        val firstArea = _uiState.value.areas.firstOrNull { it.active }?.id
        _uiState.update {
            it.copy(
                isTableDialogOpen = true,
                editingTable = table,
                tableNameInput = table?.name ?: "",
                tableAreaIdInput = table?.areaId ?: firstArea,
                errorMessage = null
            )
        }
    }

    fun closeTableDialog() {
        _uiState.update { it.copy(isTableDialogOpen = false) }
    }

    fun updateTableForm(name: String, areaId: Long?) {
        _uiState.update {
            it.copy(
                tableNameInput = name,
                tableAreaIdInput = areaId,
                errorMessage = null
            )
        }
    }

    fun submitSaveTable() {
        val state = _uiState.value
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        val areaId = state.tableAreaIdInput ?: run {
            _uiState.update { it.copy(errorMessage = "Veuillez sélectionner une zone.") }
            return
        }
        if (state.isLoading) return
        if (state.tableNameInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Le nom de la table est obligatoire.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                val saved = if (state.editingTable == null) {
                    createTableUseCase(areaId, state.tableNameInput, currentUser)
                } else {
                    updateTableUseCase(state.editingTable.id, areaId, state.tableNameInput, currentUser)
                }

                _uiState.update {
                    it.copy(
                        tables = it.tables
                            .filterNot { table -> table.id == saved.id }
                            .plus(saved)
                            .sortedWith(compareBy<RestaurantTable> { table -> table.displayOrder }.thenBy { table -> table.name }),
                        isLoading = false,
                        isTableDialogOpen = false,
                        successMessage = "Table enregistrée avec succès."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur d'enregistrement de table."
                    )
                }
            }
        }
    }

    fun toggleTableActiveState(table: RestaurantTable) {
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                if (table.active) {
                    deactivateTableUseCase(table.id, currentUser)
                } else {
                    activateTableUseCase(table.id, currentUser)
                }
                _uiState.update {
                    it.copy(
                        tables = it.tables.map { item ->
                            if (item.id == table.id) item.copy(active = !table.active) else item
                        },
                        isLoading = false,
                        successMessage = if (table.active) {
                            "Table désactivée."
                        } else {
                            "Table réactivée."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    // BULK TABLE CREATION DIALOG
    fun openBulkDialog() {
        val firstArea = _uiState.value.areas.firstOrNull { it.active }?.id
        _uiState.update {
            it.copy(
                isBulkDialogOpen = true,
                bulkAreaIdInput = firstArea,
                bulkPrefixInput = "Table",
                bulkStartNumberInput = "1",
                bulkCountInput = "10",
                bulkPaddingInput = "0",
                errorMessage = null
            )
        }
        updateBulkPreview()
    }

    fun closeBulkDialog() {
        _uiState.update { it.copy(isBulkDialogOpen = false) }
    }

    fun updateBulkForm(areaId: Long?, prefix: String, start: String, count: String, padding: String) {
        _uiState.update {
            it.copy(
                bulkAreaIdInput = areaId,
                bulkPrefixInput = prefix,
                bulkStartNumberInput = start,
                bulkCountInput = count,
                bulkPaddingInput = padding,
                errorMessage = null
            )
        }
        updateBulkPreview()
    }

    private fun updateBulkPreview() {
        val state = _uiState.value
        val start = state.bulkStartNumberInput.toIntOrNull()
        val count = state.bulkCountInput.toIntOrNull()
        val padding = state.bulkPaddingInput.toIntOrNull()
        val generated = if (
            start != null && start >= 0 &&
            count != null && count in 1..100 &&
            padding != null && padding in 0..6
        ) {
            bulkCreateTablesUseCase.generateNames(state.bulkPrefixInput, start, count, padding)
        } else {
            emptyList()
        }
        _uiState.update { it.copy(bulkGeneratedNamesPreview = generated) }
    }

    fun submitBulkCreateTables() {
        val state = _uiState.value
        val currentUserId = sessionManager.currentUser?.userId ?: run {
            _uiState.update { it.copy(errorMessage = "Session utilisateur expirée.") }
            return
        }
        val areaId = state.bulkAreaIdInput ?: run {
            _uiState.update { it.copy(errorMessage = "Veuillez sélectionner une zone.") }
            return
        }
        val start = state.bulkStartNumberInput.toIntOrNull() ?: run {
            _uiState.update { it.copy(errorMessage = "Le numéro de départ est invalide.") }
            return
        }
        val count = state.bulkCountInput.toIntOrNull() ?: run {
            _uiState.update { it.copy(errorMessage = "Le nombre de tables est invalide.") }
            return
        }
        val padding = state.bulkPaddingInput.toIntOrNull() ?: run {
            _uiState.update { it.copy(errorMessage = "Le remplissage zéro est invalide.") }
            return
        }
        if (padding !in 0..6) {
            _uiState.update {
                it.copy(errorMessage = "Le remplissage zéro doit être compris entre 0 et 6.")
            }
            return
        }

        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentUser = userRepository.getUserById(currentUserId)
                    ?: throw Exception("Utilisateur introuvable.")

                val created = bulkCreateTablesUseCase(
                    areaId = areaId,
                    prefix = state.bulkPrefixInput,
                    startNumber = start,
                    count = count,
                    paddingLength = padding,
                    currentUser = currentUser
                )

                _uiState.update {
                    it.copy(
                        tables = (it.tables + created)
                            .distinctBy { table -> table.id.takeIf { id -> id != 0L } ?: "${table.areaId}:${table.name.lowercase()}" }
                            .sortedWith(compareBy<RestaurantTable> { table -> table.displayOrder }.thenBy { table -> table.name }),
                        isLoading = false,
                        isBulkDialogOpen = false,
                        successMessage = "${created.size} tables créées en lot avec succès."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Erreur de création en lot."
                    )
                }
            }
        }
    }
}
