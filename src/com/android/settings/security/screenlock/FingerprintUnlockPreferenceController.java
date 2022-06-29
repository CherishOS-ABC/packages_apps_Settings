/*
 * Copyright (C) 2022 The LineageOS Project
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

package com.android.settings.security.screenlock;

import android.content.Context;
import android.os.UserHandle;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

import lineageos.providers.LineageSettings;

public class FingerprintUnlockPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    static final String KEY_FINGERPRINT_WAKE_UNLOCK = "fingerprint_wake_unlock";

    public FingerprintUnlockPreferenceController(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        // Enable it for just powerbutton fps devices.
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_is_powerbutton_fps);
    }

    @Override
    public String getPreferenceKey() {
        return KEY_FINGERPRINT_WAKE_UNLOCK;
    }

    @Override
    public void updateState(Preference preference) {
        // Use default value in overlay if theres none set.
        final boolean def = mContext.getResources().getBoolean(
                com.android.systemui.R.bool.config_fingerprintWakeAndUnlock);
        ((TwoStatePreference) preference).setChecked(
                LineageSettings.System.getIntForUser(mContext.getContentResolver(),
                LineageSettings.System.FINGERPRINT_WAKE_UNLOCK, def ? 1 : 0,
                UserHandle.USER_CURRENT) == 1;);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        LineageSettings.System.putIntForUser(mContext.getContentResolver(),
                LineageSettings.System.FINGERPRINT_WAKE_UNLOCK,
                (Boolean) newValue ? 1 : 0, UserHandle.USER_CURRENT);
        return true;
    }
}
