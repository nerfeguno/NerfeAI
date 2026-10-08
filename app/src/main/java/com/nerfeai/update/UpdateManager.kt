package com.nerfeai.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Context

class UpdateManager(
    private val activity: Activity
) {

    companion object {

        private const val PREFS =
            "nerfeai_update"

        private const val LAST_SHOWN_VERSION =
            "last_shown_version"
    }

    fun showUpdateIfNeeded() {

        val release =
            ReleaseInfo.current()

        val preferences =
            activity.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val lastVersion =
            preferences.getString(
                LAST_SHOWN_VERSION,
                ""
            )

        if (
            lastVersion ==
            release.versionName
        ) {
            return
        }

        preferences.edit()
            .putString(
                LAST_SHOWN_VERSION,
                release.versionName
            )
            .apply()

        AlertDialog.Builder(activity)
            .setTitle(
                "NerfeAI ${release.versionName}"
            )
            .setMessage(
                buildString {

                    appendLine(
                        "What's New"
                    )

                    appendLine()

                    append(
                        release.notes
                    )
                }
            )
            .setPositiveButton(
                "Continue",
                null
            )
            .show()
    }
}