package com.example.picencrypt.data

import android.content.Context
import android.content.SharedPreferences
import com.example.picencrypt.model.AppThemeMode
import com.example.picencrypt.model.ImageExportFormat
import com.example.picencrypt.utils.ExportFilenameGenerator

/**
 * Repository interface for managing application-wide persistent preferences.
 * Decouples ViewModel and business logic from direct Android Context / SharedPreferences access.
 */
interface PreferencesRepository {
    fun isHapticFeedbackEnabled(): Boolean
    fun setHapticFeedbackEnabled(enabled: Boolean)

    fun getAppThemeMode(): AppThemeMode
    fun setAppThemeMode(mode: AppThemeMode)

    fun isStripExifOnExport(): Boolean
    fun setStripExifOnExport(enabled: Boolean)

    fun getExportFormat(): ImageExportFormat
    fun setExportFormat(format: ImageExportFormat)

    fun getExportQuality(): Int
    fun setExportQuality(quality: Int)

    fun getExportFilenamePattern(): String
    fun setExportFilenamePattern(pattern: String)

    fun getExportCustomPrefix(): String
    fun setExportCustomPrefix(prefix: String)

    companion object {
        const val PREFS_NAME = "repix_prefs"
        const val KEY_HAPTIC_FEEDBACK = "key_haptic_feedback"
        const val KEY_THEME_MODE = "key_theme_mode"
        const val KEY_STRIP_EXIF_ON_EXPORT = "key_strip_exif_on_export"
        const val KEY_EXPORT_FORMAT = "key_export_format"
        const val KEY_EXPORT_QUALITY = "key_export_quality"
        const val KEY_EXPORT_FILENAME_PATTERN = "key_export_filename_pattern"
        const val KEY_EXPORT_CUSTOM_PREFIX = "key_export_custom_prefix"

        @Volatile
        private var instance: PreferencesRepository? = null

        /**
         * Initialize the shared repository with Android application context.
         */
        fun init(context: Context): PreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: SharedPreferencesPreferencesRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * Returns the active repository instance, falling back to an in-memory repository
         * for isolated unit test environments without Android Context.
         */
        fun getInstance(): PreferencesRepository {
            return instance ?: InMemoryPreferencesRepository()
        }

        /**
         * For unit tests to inject custom/mock repository instances.
         */
        fun setInstanceForTesting(repo: PreferencesRepository?) {
            instance = repo
        }
    }
}

/**
 * Standard Android SharedPreferences implementation.
 */
class SharedPreferencesPreferencesRepository(
    context: Context
) : PreferencesRepository {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PreferencesRepository.PREFS_NAME, Context.MODE_PRIVATE)

    override fun isHapticFeedbackEnabled(): Boolean {
        return sp.getBoolean(PreferencesRepository.KEY_HAPTIC_FEEDBACK, true)
    }

    override fun setHapticFeedbackEnabled(enabled: Boolean) {
        sp.edit().putBoolean(PreferencesRepository.KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    override fun getAppThemeMode(): AppThemeMode {
        val themeId = sp.getInt(PreferencesRepository.KEY_THEME_MODE, AppThemeMode.FOLLOW_SYSTEM.id)
        return AppThemeMode.fromId(themeId)
    }

    override fun setAppThemeMode(mode: AppThemeMode) {
        sp.edit().putInt(PreferencesRepository.KEY_THEME_MODE, mode.id).apply()
    }

    override fun isStripExifOnExport(): Boolean {
        return sp.getBoolean(PreferencesRepository.KEY_STRIP_EXIF_ON_EXPORT, true)
    }

    override fun setStripExifOnExport(enabled: Boolean) {
        sp.edit().putBoolean(PreferencesRepository.KEY_STRIP_EXIF_ON_EXPORT, enabled).apply()
    }

    override fun getExportFormat(): ImageExportFormat {
        val formatName = sp.getString(PreferencesRepository.KEY_EXPORT_FORMAT, ImageExportFormat.PNG.name)
            ?: ImageExportFormat.PNG.name
        return ImageExportFormat.entries.find { it.name == formatName } ?: ImageExportFormat.PNG
    }

    override fun setExportFormat(format: ImageExportFormat) {
        sp.edit().putString(PreferencesRepository.KEY_EXPORT_FORMAT, format.name).apply()
    }

    override fun getExportQuality(): Int {
        return sp.getInt(PreferencesRepository.KEY_EXPORT_QUALITY, 90).coerceIn(10, 100)
    }

    override fun setExportQuality(quality: Int) {
        val clamped = quality.coerceIn(10, 100)
        sp.edit().putInt(PreferencesRepository.KEY_EXPORT_QUALITY, clamped).apply()
    }

    override fun getExportFilenamePattern(): String {
        return sp.getString(
            PreferencesRepository.KEY_EXPORT_FILENAME_PATTERN,
            ExportFilenameGenerator.PATTERN_TIMESTAMP
        ) ?: ExportFilenameGenerator.PATTERN_TIMESTAMP
    }

    override fun setExportFilenamePattern(pattern: String) {
        sp.edit().putString(PreferencesRepository.KEY_EXPORT_FILENAME_PATTERN, pattern).apply()
    }

    override fun getExportCustomPrefix(): String {
        return sp.getString(PreferencesRepository.KEY_EXPORT_CUSTOM_PREFIX, "RePix") ?: "RePix"
    }

    override fun setExportCustomPrefix(prefix: String) {
        sp.edit().putString(PreferencesRepository.KEY_EXPORT_CUSTOM_PREFIX, prefix).apply()
    }
}

/**
 * In-memory implementation for isolated tests without Android SharedPreferences.
 */
class InMemoryPreferencesRepository(
    initialHapticFeedbackEnabled: Boolean = true,
    initialAppThemeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    initialStripExifOnExport: Boolean = true,
    initialExportFormat: ImageExportFormat = ImageExportFormat.PNG,
    initialExportQuality: Int = 90,
    initialExportFilenamePattern: String = ExportFilenameGenerator.PATTERN_TIMESTAMP,
    initialExportCustomPrefix: String = "RePix"
) : PreferencesRepository {

    private var hapticFeedbackEnabled: Boolean = initialHapticFeedbackEnabled
    private var appThemeMode: AppThemeMode = initialAppThemeMode
    private var stripExifOnExport: Boolean = initialStripExifOnExport
    private var exportFormat: ImageExportFormat = initialExportFormat
    private var exportQuality: Int = initialExportQuality
    private var exportFilenamePattern: String = initialExportFilenamePattern
    private var exportCustomPrefix: String = initialExportCustomPrefix

    override fun isHapticFeedbackEnabled(): Boolean = hapticFeedbackEnabled

    override fun setHapticFeedbackEnabled(enabled: Boolean) {
        hapticFeedbackEnabled = enabled
    }

    override fun getAppThemeMode(): AppThemeMode = appThemeMode

    override fun setAppThemeMode(mode: AppThemeMode) {
        appThemeMode = mode
    }

    override fun isStripExifOnExport(): Boolean = stripExifOnExport

    override fun setStripExifOnExport(enabled: Boolean) {
        stripExifOnExport = enabled
    }

    override fun getExportFormat(): ImageExportFormat = exportFormat

    override fun setExportFormat(format: ImageExportFormat) {
        exportFormat = format
    }

    override fun getExportQuality(): Int = exportQuality

    override fun setExportQuality(quality: Int) {
        exportQuality = quality.coerceIn(10, 100)
    }

    override fun getExportFilenamePattern(): String = exportFilenamePattern

    override fun setExportFilenamePattern(pattern: String) {
        exportFilenamePattern = pattern
    }

    override fun getExportCustomPrefix(): String = exportCustomPrefix

    override fun setExportCustomPrefix(prefix: String) {
        exportCustomPrefix = prefix
    }
}
