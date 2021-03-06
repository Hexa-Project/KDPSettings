
/*
 * Copyright (C) 2016 The CyanogenMod project
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

package com.android.kdpsettings.fragments;

import android.app.Activity;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.preference.SwitchPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;
import android.widget.Toast;

import com.android.kdpsettings.R;

import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.Config;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config.ButtonConfig;
import com.android.kdpsettings.KangDroidSeekBarPreference;

import java.util.List;

import cyanogenmod.hardware.CMHardwareManager;
import cyanogenmod.providers.CMSettings;

public class KangDroidNavBarSettings extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "SystemSettings";

    private static final String KEY_NAVIGATION_BAR_LEFT = "navigation_bar_left";
    private static final String KEY_NAVIGATION_HOME_LONG_PRESS = "navigation_home_long_press";
    private static final String KEY_NAVIGATION_HOME_DOUBLE_TAP = "navigation_home_double_tap";
    private static final String KEY_NAVIGATION_RECENTS_LONG_PRESS = "navigation_recents_long_press";
    private static final String CATEGORY_NAVBAR = "navigation_bar_category";
	private static final String NAVBAR_VISIBILITY = "navbar_visibility";
    private static final String KEY_NAVBAR_MODE = "navbar_mode";
    private static final String KEY_AOSP_NAVBAR_SETTINGS = "aosp_navbar_settings";
    private static final String KEY_FLING_NAVBAR_SETTINGS = "fling_settings";
    private static final String KEY_SMARTBAR_SETTINGS = "smartbar_settings";
    private static final String KEY_NAVIGATION_BAR_SIZE = "navigation_bar_size";
	
    private static final String KEY_NAVIGATION_HEIGHT_PORT = "navbar_height_portrait";
    private static final String KEY_NAVIGATION_HEIGHT_LAND = "navbar_height_landscape";
    private static final String KEY_NAVIGATION_WIDTH = "navbar_width";
	private static final String NAVBAR_DYNAMIC = "navbar_dynamic";
	
	/**
	 * The fragment argument representing the section number for this
	 * fragment.
	 */
	private static final String ARG_SECTION_NUMBER = "section_number";

    private SwitchPreference mDisableNavigationKeys;
    private SwitchPreference mNavigationBarLeftPref;
    private ListPreference mNavigationHomeLongPressAction;
    private ListPreference mNavigationHomeDoubleTapAction;
    private ListPreference mNavigationRecentsLongPressAction;
	
    private SwitchPreference mNavbarVisibility;
    private ListPreference mNavbarMode;
    private PreferenceScreen mFlingSettings;
    private PreferenceScreen mSmartbarSettings;
	
    private KangDroidSeekBarPreference mBarHeightPort;
    private KangDroidSeekBarPreference mBarHeightLand;
    private KangDroidSeekBarPreference mBarWidth;
	private SwitchPreference mNavbarDynamic;
	
	private Context context;
	
	public static final int KEY_MASK_HOME = 0x01;

    private Handler mHandler;
	
    private enum Action {
        NOTHING,
        MENU,
        APP_SWITCH,
        SEARCH,
        VOICE_SEARCH,
        IN_APP_SEARCH,
        LAUNCH_CAMERA,
        SLEEP,
        LAST_APP,
        SPLIT_SCREEN;

        public static Action fromIntSafe(int id) {
            if (id < NOTHING.ordinal() || id > Action.values().length) {
                return NOTHING;
            }
            return Action.values()[id];
        }

        public static Action fromSettings(ContentResolver cr, String setting, Action def) {
            return fromIntSafe(CMSettings.System.getInt(cr, setting, def.ordinal()));
        }
    }

	public KangDroidNavBarSettings() {
	}

	/**
	 * Returns a new instance of this fragment for the given section
	 * number.
	 */
	public static KangDroidNavBarSettings newInstance(int sectionNumber) {
		KangDroidNavBarSettings fragment = new KangDroidNavBarSettings();
		Bundle args = new Bundle();
		args.putInt(ARG_SECTION_NUMBER, sectionNumber);
		fragment.setArguments(args);
		return fragment;
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.kangdroid_nav_bar_settings);

        final Resources res = getResources();
		final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();
		Activity activity = getActivity();

        mHandler = new Handler();
		
		final int deviceKeys = getResources().getInteger(
			                com.android.internal.R.integer.config_deviceHardwareKeys);
		final boolean hasHomeKey = (deviceKeys & KEY_MASK_HOME) != 0;

        // Navigation bar left
        mNavigationBarLeftPref = (SwitchPreference) findPreference(KEY_NAVIGATION_BAR_LEFT);
		
		mNavbarDynamic = (SwitchPreference) findPreference(NAVBAR_DYNAMIC);

        Action defaultHomeLongPressAction = Action.fromIntSafe(res.getInteger(
                com.android.internal.R.integer.config_longPressOnHomeBehavior));
        Action defaultHomeDoubleTapAction = Action.fromIntSafe(res.getInteger(
                com.android.internal.R.integer.config_doubleTapOnHomeBehavior));
        Action homeLongPressAction = Action.fromSettings(resolver,
                CMSettings.System.KEY_HOME_LONG_PRESS_ACTION,
                defaultHomeLongPressAction);
        Action homeDoubleTapAction = Action.fromSettings(resolver,
                CMSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                defaultHomeDoubleTapAction);

        // Navigation bar home long press
        mNavigationHomeLongPressAction = initActionList(KEY_NAVIGATION_HOME_LONG_PRESS,
                homeLongPressAction);

        // Navigation bar home double tap
        mNavigationHomeDoubleTapAction = initActionList(KEY_NAVIGATION_HOME_DOUBLE_TAP,
                homeDoubleTapAction);

        // Navigation bar recents long press activity needs custom setup
        mNavigationRecentsLongPressAction =
                initRecentsLongPressAction(KEY_NAVIGATION_RECENTS_LONG_PRESS, null);

        final CMHardwareManager hardware = CMHardwareManager.getInstance(getActivity());

		
        mNavbarVisibility = (SwitchPreference) findPreference(NAVBAR_VISIBILITY);
        mNavbarMode = (ListPreference) findPreference(KEY_NAVBAR_MODE);
        mFlingSettings = (PreferenceScreen) findPreference(KEY_FLING_NAVBAR_SETTINGS);
        mSmartbarSettings = (PreferenceScreen) findPreference(KEY_SMARTBAR_SETTINGS);

        boolean showing = Settings.Secure.getInt(resolver,
                Settings.Secure.NAVIGATION_BAR_VISIBLE,
                DUActionUtils.hasNavbarByDefault(getActivity()) ? 1 : 0) != 0;
        updateBarVisibleAndUpdatePrefs(showing);
        mNavbarVisibility.setOnPreferenceChangeListener(this);

        int mode = Settings.Secure.getInt(resolver, Settings.Secure.NAVIGATION_BAR_MODE,
                0);

        // Smartbar moved from 2 to 0, deprecating old navbar
        if (mode == 2) {
            mode = 0;
        }
        updateBarModeSettings(mode);
        mNavbarMode.setOnPreferenceChangeListener(this);
		
        int size = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.NAVIGATION_BAR_HEIGHT, 100, UserHandle.USER_CURRENT);
        mBarHeightPort = (KangDroidSeekBarPreference) findPreference(KEY_NAVIGATION_HEIGHT_PORT);
        mBarHeightPort.setValue(size);
        mBarHeightPort.setOnPreferenceChangeListener(this);

           int sizeone = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_WIDTH, 100, UserHandle.USER_CURRENT);
            mBarWidth = (KangDroidSeekBarPreference) findPreference(KEY_NAVIGATION_WIDTH);
            mBarWidth.setValue(sizeone);
            mBarWidth.setOnPreferenceChangeListener(this);
			
           int sizetwo = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE, 100, UserHandle.USER_CURRENT);
            mBarHeightLand = (KangDroidSeekBarPreference) findPreference(KEY_NAVIGATION_HEIGHT_LAND);
            mBarHeightLand.setValue(sizetwo);
            mBarHeightLand.setOnPreferenceChangeListener(this);

        boolean isDynamic = Settings.System.getInt(resolver,
                Settings.System.NAVBAR_DYNAMIC, 0) == 1;
        mNavbarDynamic.setChecked(isDynamic);
        mNavbarDynamic.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private ListPreference initActionList(String key, Action value) {
        return initActionList(key, value.ordinal());
    }

    private ListPreference initActionList(String key, int value) {
        ListPreference list = (ListPreference) getPreferenceScreen().findPreference(key);
        if (list == null) return null;
        list.setValue(Integer.toString(value));
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        return list;
    }

    private ListPreference initRecentsLongPressAction(String key, Context context) {
		final ContentResolver resolver = getActivity().getContentResolver();
        ListPreference list = (ListPreference) getPreferenceScreen().findPreference(key);
        list.setOnPreferenceChangeListener(this);
        // Read the componentName from Settings.Secure, this is the user's prefered setting
        String componentString = CMSettings.Secure.getString(resolver,
                CMSettings.Secure.RECENTS_LONG_PRESS_ACTIVITY);
        ComponentName targetComponent = null;
        if (componentString == null) {
            list.setSummary(getString(R.string.hardware_keys_action_split_screen));
        } else {
            targetComponent = ComponentName.unflattenFromString(componentString);
        }

        // Dyanamically generate the list array,
        // query PackageManager for all Activites that are registered for ACTION_RECENTS_LONG_PRESS
        Intent intent = new Intent(cyanogenmod.content.Intent.ACTION_RECENTS_LONG_PRESS);
        List<ResolveInfo> recentsActivities = getActivity().getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (recentsActivities.size() == 0) {
            // No entries available, disable
            list.setSummary(getString(R.string.hardware_keys_action_split_screen));
            CMSettings.Secure.putString(resolver,
                    CMSettings.Secure.RECENTS_LONG_PRESS_ACTIVITY, null);
            list.setEnabled(false);
            return list;
        }

        CharSequence[] entries = new CharSequence[recentsActivities.size() + 1];
        CharSequence[] values = new CharSequence[recentsActivities.size() + 1];
        // First entry is always default last app
        entries[0] = getString(R.string.hardware_keys_action_split_screen);
        values[0] = "";
        list.setValue(values[0].toString());
        int i = 1;
        for (ResolveInfo info : recentsActivities) {
            try {
                // Use pm.getApplicationInfo for the label,
                // we cannot rely on ResolveInfo that comes back from queryIntentActivities.
                entries[i] = getActivity().getPackageManager().getApplicationInfo(info.activityInfo.packageName, 0).loadLabel(getActivity().getPackageManager());
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Error package not found: " + info.activityInfo.packageName, e);
                // Fallback to package name
                entries[i] = info.activityInfo.packageName;
            }

            // Set the value to the ComponentName that will handle this intent
            ComponentName entryComponent = new ComponentName(info.activityInfo.packageName,
                    info.activityInfo.name);
            values[i] = entryComponent.flattenToString();
            if (targetComponent != null) {
                if (entryComponent.equals(targetComponent)) {
                    // Update the selected value and the preference summary
                    list.setSummary(entries[i]);
                    list.setValue(values[i].toString());
                }
            }
            i++;
        }
        list.setEntries(entries);
        list.setEntryValues(values);
        return list;
    }

    private void handleActionListChange(ListPreference pref, Object newValue, String setting) {
		ContentResolver resolver = getActivity().getContentResolver();
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
        CMSettings.System.putInt(resolver, setting, Integer.valueOf(value));
    }

    private void handleSystemActionListChange(ListPreference pref, Object newValue, String setting) {
		ContentResolver resolver = getActivity().getContentResolver();
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
        Settings.System.putInt(resolver, setting, Integer.valueOf(value));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
		final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mNavigationRecentsLongPressAction) {
            // RecentsLongPressAction is handled differently because it intentionally uses
            // Settings.System
            String putString = (String) newValue;
            int index = mNavigationRecentsLongPressAction.findIndexOfValue(putString);
            CharSequence summary = mNavigationRecentsLongPressAction.getEntries()[index];
            // Update the summary
            mNavigationRecentsLongPressAction.setSummary(summary);
            if (putString.length() == 0) {
                putString = null;
            }
            CMSettings.Secure.putString(resolver,
                    CMSettings.Secure.RECENTS_LONG_PRESS_ACTIVITY, putString);
            return true;
		} else if (preference == mNavigationHomeLongPressAction) {
            handleActionListChange((ListPreference) preference, newValue,
                    CMSettings.System.KEY_HOME_LONG_PRESS_ACTION);
            return true;
		} else if (preference == mNavigationHomeDoubleTapAction) {
		            handleActionListChange((ListPreference) preference, newValue,
		                    CMSettings.System.KEY_HOME_DOUBLE_TAP_ACTION);
		            return true;
		} else if (preference == mNavbarMode) {
	            int mode = Integer.parseInt(((String) newValue).toString());
	            Settings.Secure.putInt(resolver,
	                    Settings.Secure.NAVIGATION_BAR_MODE, mode);
	            updateBarModeSettings(mode);
	            return true;
        } else if (preference == mNavbarVisibility) {
	            boolean showing = ((Boolean)newValue);
            Settings.Secure.putInt(resolver, Settings.Secure.NAVIGATION_BAR_VISIBLE,
                    showing ? 1 : 0);
            updateBarVisibleAndUpdatePrefs(showing);
	            return true;
        } else if (preference == mBarHeightPort) {
            int val = (Integer) newValue;
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_HEIGHT, val, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mBarHeightLand) {
            int val = (Integer) newValue;
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE, val, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mBarWidth) {
            int val = (Integer) newValue;
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_WIDTH, val, UserHandle.USER_CURRENT);
            return true;
        } else if (preference.equals(mNavbarDynamic)) {
            boolean isDynamic = ((Boolean)newValue);
            Settings.System.putInt(resolver, Settings.System.NAVBAR_DYNAMIC,
                    isDynamic ? 1 : 0);
            Toast.makeText(getActivity(), R.string.restart_app_required,
                    Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }


    @Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
	            final Preference preference) {
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
	
    private void updateBarModeSettings(int mode) {
        mNavbarMode.setValue(String.valueOf(mode));
        mSmartbarSettings.setEnabled(mode == 0);
        mSmartbarSettings.setSelectable(mode == 0);
        mFlingSettings.setEnabled(mode == 1);
        mFlingSettings.setSelectable(mode == 1);
    }

    private void updateBarVisibleAndUpdatePrefs(boolean showing) {
        mNavbarVisibility.setChecked(showing);
		mNavbarDynamic.setEnabled(mNavbarVisibility.isChecked());
    }
}
