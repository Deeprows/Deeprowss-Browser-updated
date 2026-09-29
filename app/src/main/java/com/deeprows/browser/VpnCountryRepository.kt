package com.deeprows.browser

import java.util.Locale

object VpnCountryRepository {
    /** Full ISO 3166-1 alpha-2 list exposed by the Android/Java runtime.
     * Availability is supplied by the Deeprows backend, not hard-coded in the APK.
     */
    fun allCountries(availableCodes: Set<String> = emptySet()): List<VpnCountry> =
        Locale.getISOCountries()
            .map { code -> code.uppercase() }
            .distinct()
            .map { code ->
                VpnCountry(
                    code = code,
                    name = Locale.Builder().setRegion(code).build().displayCountry,
                    available = availableCodes.contains(code)
                )
            }
            .filter { it.name.isNotBlank() }
            .sortedBy { it.name }
}
