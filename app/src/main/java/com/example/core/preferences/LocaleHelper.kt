package com.example.core.preferences

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

  fun resolveLocale(language: AppLanguage, systemConfiguration: Configuration): Locale {
    return when (language) {
      AppLanguage.EN -> Locale.ENGLISH
      AppLanguage.KA -> Locale.forLanguageTag("ka")
      AppLanguage.SYSTEM -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          systemConfiguration.locales.get(0) ?: Locale.getDefault()
        } else {
          @Suppress("DEPRECATION")
          systemConfiguration.locale ?: Locale.getDefault()
        }
      }
    }
  }

  fun createLocalizedContext(baseContext: Context, language: AppLanguage): Context {
    if (language == AppLanguage.SYSTEM) {
      return baseContext
    }
    val targetLocale = resolveLocale(language, baseContext.resources.configuration)
    Locale.setDefault(targetLocale)
    val config = Configuration(baseContext.resources.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      config.setLocales(LocaleList(targetLocale))
    } else {
      @Suppress("DEPRECATION")
      config.setLocale(targetLocale)
    }
    return baseContext.createConfigurationContext(config)
  }
}
