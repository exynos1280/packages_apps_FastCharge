/*
 * Copyright (C) 2020 YAAP
 * Copyright (C) 2023-2024 cyberknight777
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.fastcharge.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.fastcharge.R;
import com.android.fastcharge.utils.FileUtils;

public class FastChargeFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener {

    private SwitchPreferenceCompat mFastChargePreference;
    private FastChargeConfig mConfig;
    private boolean mInternalFastChargeStart = false;

    private final BroadcastReceiver mServiceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(FastChargeConfig.ACTION_FAST_CHARGE_SERVICE_CHANGED)) {
                if (mInternalFastChargeStart) {
                        mInternalFastChargeStart = false;
                        return;
                }

                if (mFastChargePreference == null) return;

                final boolean fastchargeStarted = intent.getBooleanExtra(
                            FastChargeConfig.EXTRA_FAST_CHARGE_STATE, false);

                mFastChargePreference.setChecked(fastchargeStarted);

            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fastcharge_settings, rootKey);
        mConfig = FastChargeConfig.getInstance(getContext());
        mFastChargePreference = (SwitchPreferenceCompat) findPreference(FastChargeConfig.FASTCHARGE_KEY);
        if (FileUtils.fileExists(mConfig.getFastChargePath())) {
            mFastChargePreference.setEnabled(true);
            mFastChargePreference.setOnPreferenceChangeListener(this);

            // Get saved preference state, default to true if not set
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            boolean savedState = sharedPrefs.getBoolean(FastChargeConfig.FASTCHARGE_KEY, true);

            // Set UI and apply saved state to sysfs
            mFastChargePreference.setChecked(savedState);
            boolean sysfsValue = mConfig.isLogicInverted() ? !savedState : savedState;
            // Convert the UI value to the actual sysfs value based on inversion setting
            FileUtils.writeLine(mConfig.getFastChargePath(), sysfsValue ? "1":"0");
        } else {
            mFastChargePreference.setSummary(R.string.fast_charging_summary_not_supported);
            mFastChargePreference.setEnabled(false);
        }

        // Registering observers
        IntentFilter filter = new IntentFilter();
        filter.addAction(mConfig.ACTION_FAST_CHARGE_SERVICE_CHANGED);
        getContext().registerReceiver(mServiceStateReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean savedState = sharedPrefs.getBoolean(FastChargeConfig.FASTCHARGE_KEY, true);
        mFastChargePreference.setChecked(savedState);
    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mConfig.FASTCHARGE_KEY.equals(preference.getKey())) {
            mInternalFastChargeStart = true;
            Context mContext = getContext();

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

            // Save user preference first
            boolean uiValue = (Boolean) newValue;
            sharedPrefs.edit().putBoolean(FastChargeConfig.FASTCHARGE_KEY, uiValue).commit();

            // Then apply to sysfs
            boolean sysfsValue = mConfig.isLogicInverted() ? !uiValue : uiValue;
            // Convert the UI value to the actual sysfs value based on inversion setting
            FileUtils.writeLine(mConfig.getFastChargePath(), sysfsValue ? "1":"0");

            Intent intent = new Intent(FastChargeConfig.ACTION_FAST_CHARGE_SERVICE_CHANGED);
            intent.putExtra(FastChargeConfig.EXTRA_FAST_CHARGE_STATE, uiValue);
            intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(mServiceStateReceiver);
    }

}
