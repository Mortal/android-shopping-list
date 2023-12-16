package dk.strova.mama

import android.os.Bundle
import android.preference.PreferenceFragment
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity: AppCompatActivity() {
    companion object {
        const val KEY_ENDPOINT = "dk.strova.mama.pref_endpoint"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
    }
}

class SettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
    }
}