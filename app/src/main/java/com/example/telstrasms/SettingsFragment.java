package com.example.telstrasms;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        /*
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        ListPreference pref = findPreference("api_key");

        String keyPairs = sharedPref.getString("api_keys", "");
        if (keyPairs == null || keyPairs.equals("")) {
            pref.setEntries(new CharSequence[]{"-"});
            pref.setEntryValues(new CharSequence[]{""});
        } else {
            CharSequence[] entryValues = keyPairs.split(",");
            pref.setEntries(entryValues);
            pref.setEntryValues(entryValues);
        }
        */
    }
}