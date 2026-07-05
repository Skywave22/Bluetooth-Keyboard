package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.data.keyboard.KeyboardLayoutStore
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.keyboard.KeySpec
import com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Full keyboard driver: renders the persisted board, sends HID on tap
 * (primary) / long-press (secondary), and powers the per-key editor.
 */
@HiltViewModel
class FullKeyboardViewModel @Inject constructor(
    private val store: KeyboardLayoutStore,
    observeConnection: ObserveConnectionUseCase,
    private val sendAction: SendHidActionUseCase
) : ViewModel() {

    val layout: StateFlow<KeyboardLayoutSpec> = store.layout
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardLayoutSpec())

    val fullMode: StateFlow<Boolean> = store.fullMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Key currently open in the editor sheet (null = none). */
    private val _editingKey = MutableStateFlow<KeySpec?>(null)
    val editingKey: StateFlow<KeySpec?> = _editingKey.asStateFlow()

    /** Edit mode: long-press opens the key editor instead of sending secondary. */
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    fun tap(key: KeySpec) {
        sendAction(HidAction.KeyTap(key.keyCode, key.modifiers))
    }

    /** Long-press: secondary function if bound; otherwise nothing. */
    fun longPress(key: KeySpec) {
        if (_editMode.value) {
            _editingKey.value = key
            return
        }
        val secondary = key.secondaryKeyCode ?: return
        sendAction(HidAction.KeyTap(secondary, key.secondaryModifiers))
    }

    // ------------------------------------------------------------------
    // Modes / editor
    // ------------------------------------------------------------------

    fun setFullMode(value: Boolean) = viewModelScope.launch { store.setFullMode(value) }

    fun toggleEditMode() {
        _editMode.value = !_editMode.value
        if (!_editMode.value) _editingKey.value = null
    }

    fun closeEditor() {
        _editingKey.value = null
    }

    /** Apply a transform to the edited key everywhere it appears. */
    fun updateEditingKey(transform: (KeySpec) -> KeySpec) {
        val target = _editingKey.value ?: return
        val updated = transform(target).sanitized()
        _editingKey.value = updated
        viewModelScope.launch {
            val current = layout.value
            store.save(
                current.copy(
                    rows = current.rows.map { row ->
                        row.map { if (it.id == target.id) updated else it }
                    },
                    favorites = current.favorites.map {
                        if (it.id == target.id) updated else it
                    }
                )
            )
        }
    }

    /** Move the edited key within its row (reposition left/right). */
    fun moveEditingKey(offset: Int) {
        val target = _editingKey.value ?: return
        viewModelScope.launch {
            val current = layout.value
            store.save(
                current.copy(
                    rows = current.rows.map { row ->
                        val idx = row.indexOfFirst { it.id == target.id }
                        if (idx < 0) row
                        else {
                            val to = (idx + offset).coerceIn(0, row.size - 1)
                            if (to == idx) row
                            else row.toMutableList().apply {
                                val item = removeAt(idx); add(to, item)
                            }
                        }
                    }
                )
            )
        }
    }

    /** Add the edited key (or any combo) to the Favorites row. */
    fun addToFavorites(key: KeySpec) {
        viewModelScope.launch {
            val current = layout.value
            if (current.favorites.size >= KeyboardLayoutSpec.FAVORITES_MAX) return@launch
            if (current.favorites.any { it.keyCode == key.keyCode && it.modifiers == key.modifiers }) return@launch
            store.save(
                current.copy(
                    favorites = current.favorites +
                        key.copy(id = "fav-" + UUID.randomUUID().toString()).sanitized()
                )
            )
        }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch {
            val current = layout.value
            store.save(current.copy(favorites = current.favorites.filterNot { it.id == id }))
        }
    }

    /** Create a custom combo key straight into Favorites (e.g. Ctrl+C). */
    fun addComboFavorite(label: String, keyCode: Byte, modifiers: Byte) {
        addToFavorites(
            KeySpec(
                id = "fav-" + UUID.randomUUID().toString(),
                label = label,
                keyCode = keyCode,
                modifiers = modifiers
            )
        )
    }

    fun resetBoard() = viewModelScope.launch { store.reset() }
}
