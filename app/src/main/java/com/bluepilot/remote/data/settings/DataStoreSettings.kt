package com.bluepilot.remote.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bluepilot.remote.domain.SettingsStore
import com.bluepilot.remote.model.AppSettings
import com.bluepilot.remote.model.GamepadMappingMode
import com.bluepilot.remote.model.HapticIntensity
import com.bluepilot.remote.model.GamepadSettings
import com.bluepilot.remote.model.KeyboardSettings
import com.bluepilot.remote.model.MouseSettings
import com.bluepilot.remote.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bluepilot_settings"
)

/**
 * DataStore-backed implementation of [SettingsStore].
 *
 * Defensive rules:
 *  - Read errors (corrupt file) fall back to defaults instead of crashing.
 *  - Enum values are parsed with safe fallback.
 *  - Every write sanitizes numeric ranges first.
 */
@Singleton
class DataStoreSettings @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsStore {

    private object Keys {
        // App
        val THEME = stringPreferencesKey("theme")
        val THEME_ID = stringPreferencesKey("theme_id")
        val FULLSCREEN = booleanPreferencesKey("fullscreen")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VIBRATIONS = booleanPreferencesKey("vibrations")
        val HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val QUALITY_3D = stringPreferencesKey("quality_3d")
        // Mouse
        val M_SENSITIVITY = intPreferencesKey("m_sensitivity")
        val M_SCROLL = intPreferencesKey("m_scroll")
        val M_SMOOTHING = intPreferencesKey("m_smoothing")
        val M_INVERT = booleanPreferencesKey("m_invert")
        val M_TAP_CLICK = booleanPreferencesKey("m_tap_click")
        val M_PEN = booleanPreferencesKey("m_pen")
        // Keyboard
        val K_TEXT_BAR = booleanPreferencesKey("k_text_bar")
        // Gamepad
        val G_MODE = stringPreferencesKey("g_mode")
        val G_SENSITIVITY = intPreferencesKey("g_sensitivity")
        val G_DEADZONE = intPreferencesKey("g_deadzone")
        val G_HAPTIC = booleanPreferencesKey("g_haptic")
    }

    /** Preferences flow that never throws: IO errors → defaults. */
    private val safePrefs: Flow<Preferences> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                Timber.e(error, "settings read failed; using defaults")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    override val appSettings: Flow<AppSettings> = safePrefs.map { p ->
        AppSettings(
            theme = p[Keys.THEME].toEnum(ThemeMode.SYSTEM),
            themeId = p[Keys.THEME_ID] ?: "pilot_dark",
            fullscreenMode = p[Keys.FULLSCREEN] ?: false,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            touchVibrations = p[Keys.VIBRATIONS] ?: true,
            hapticIntensity = p[Keys.HAPTIC_INTENSITY].toEnum(HapticIntensity.MEDIUM),
            secureScreen = p[Keys.SECURE_SCREEN] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            reduceMotion = p[Keys.REDUCE_MOTION] ?: false,
            iconPack = p[Keys.ICON_PACK] ?: "ROUNDED",
            quality3D = p[Keys.QUALITY_3D] ?: "FULL"
        )
    }

    override val mouseSettings: Flow<MouseSettings> = safePrefs.map { p ->
        MouseSettings(
            sensitivity = p[Keys.M_SENSITIVITY] ?: 65,
            scrollSpeed = p[Keys.M_SCROLL] ?: 50,
            movementSmoothing = p[Keys.M_SMOOTHING] ?: 20,
            invertScroll = p[Keys.M_INVERT] ?: false,
            tapToClick = p[Keys.M_TAP_CLICK] ?: true,
            penMode = p[Keys.M_PEN] ?: false
        ).sanitized()
    }

    override val keyboardSettings: Flow<KeyboardSettings> = safePrefs.map { p ->
        KeyboardSettings(showTextInputBar = p[Keys.K_TEXT_BAR] ?: true)
    }

    override val gamepadSettings: Flow<GamepadSettings> = safePrefs.map { p ->
        GamepadSettings(
            mappingMode = p[Keys.G_MODE].toEnum(GamepadMappingMode.HID_GAMEPAD),
            joystickSensitivity = p[Keys.G_SENSITIVITY] ?: 70,
            deadZone = p[Keys.G_DEADZONE] ?: 10,
            hapticFeedback = p[Keys.G_HAPTIC] ?: true
        ).sanitized()
    }

    // ------------------------------------------------------------------
    // Writes (sanitize first, never store junk)
    // ------------------------------------------------------------------

    override suspend fun updateApp(settings: AppSettings) {
        safeEdit { p ->
            p[Keys.THEME] = settings.theme.name
            p[Keys.THEME_ID] = settings.themeId
            p[Keys.FULLSCREEN] = settings.fullscreenMode
            p[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
            p[Keys.VIBRATIONS] = settings.touchVibrations
            p[Keys.HAPTIC_INTENSITY] = settings.hapticIntensity.name
            p[Keys.SECURE_SCREEN] = settings.secureScreen
            p[Keys.ONBOARDING_DONE] = settings.onboardingDone
            p[Keys.REDUCE_MOTION] = settings.reduceMotion
            p[Keys.ICON_PACK] = settings.iconPack
            p[Keys.QUALITY_3D] = settings.quality3D
        }
    }

    override suspend fun updateMouse(settings: MouseSettings) {
        val s = settings.sanitized()
        safeEdit { p ->
            p[Keys.M_SENSITIVITY] = s.sensitivity
            p[Keys.M_SCROLL] = s.scrollSpeed
            p[Keys.M_SMOOTHING] = s.movementSmoothing
            p[Keys.M_INVERT] = s.invertScroll
            p[Keys.M_TAP_CLICK] = s.tapToClick
            p[Keys.M_PEN] = s.penMode
        }
    }

    override suspend fun updateKeyboard(settings: KeyboardSettings) {
        safeEdit { p -> p[Keys.K_TEXT_BAR] = settings.showTextInputBar }
    }

    override suspend fun updateGamepad(settings: GamepadSettings) {
        val s = settings.sanitized()
        safeEdit { p ->
            p[Keys.G_MODE] = s.mappingMode.name
            p[Keys.G_SENSITIVITY] = s.joystickSensitivity
            p[Keys.G_DEADZONE] = s.deadZone
            p[Keys.G_HAPTIC] = s.hapticFeedback
        }
    }

    private suspend fun safeEdit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { context.settingsDataStore.edit(block) }
            .onFailure { Timber.e(it, "settings write failed") }
    }

    /** Enum-from-string with fallback — storage corruption can't crash us. */
    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
