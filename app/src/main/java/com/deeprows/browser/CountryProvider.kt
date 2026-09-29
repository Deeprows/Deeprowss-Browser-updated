package com.deeprows.browser

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

object CountryProvider {

    fun getCountryCode(
        context: Context
    ): String {

        try {

            val telephonyManager =
                context.getSystemService(
                    Context.TELEPHONY_SERVICE
                ) as? TelephonyManager

            val networkCountry =
                telephonyManager
                    ?.networkCountryIso
                    ?.uppercase()

            if (
                !networkCountry.isNullOrBlank() &&
                networkCountry.length == 2
            ) {

                return networkCountry
            }

        } catch (
            e: Exception
        ) {

            e.printStackTrace()
        }

        /*
         * Fallback to the device's locale.
         */

        val localeCountry =
            Locale.getDefault()
                .country
                .uppercase()

        if (
            localeCountry.length == 2
        ) {

            return localeCountry
        }

        /*
         * Worldwide fallback.
         */

        return ""
    }
}
