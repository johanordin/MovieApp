/*
 * Copyright 2014 Google Inc. All rights reserved.
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

package koma.movieapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.TimeZone;

import koma.movieapp.Config;

/**
 * Utilities and constants related to app preferences.
 */
public class PrefUtils  {

    /**
     * Boolean preference that when checked, indicates that the user would like to see times
     * in their local timezone throughout the app.
     */
    public static final String PREF_LOCAL_TIMES = "pref_local_times";

    /**
     * Boolean indicating whether we performed the (one-time) welcome flow.
     */
    public static final String PREF_WELCOME_DONE = "pref_welcome_done";

    /**
     * Boolean preference that when checked, indicates that the user will be attending the
     * conference.
     */
    public static final String PREF_ATTENDEE_AT_VENUE = "pref_attendee_at_venue";


    /** Long indicating when a sync was last ATTEMPTED (not necessarily succeeded) */
    public static final String PREF_LAST_SYNC_ATTEMPTED = "pref_last_sync_attempted";

    /** Long indicating when a sync last SUCCEEDED */
    public static final String PREF_LAST_SYNC_SUCCEEDED = "pref_last_sync_succeeded";



    public static TimeZone getDisplayTimeZone(Context context) {
        TimeZone defaultTz = TimeZone.getDefault();
        return (isUsingLocalTime(context) && defaultTz != null)
                ? defaultTz : Config.DEFAULT_TIMEZONE;
    }

    public static boolean isUsingLocalTime(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(PREF_LOCAL_TIMES, false);
    }

    public static void setUsingLocalTime(final Context context, final boolean usingLocalTime) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putBoolean(PREF_LOCAL_TIMES, usingLocalTime).commit();
    }


    public static boolean isWelcomeDone(final Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(PREF_WELCOME_DONE, false);
    }


    public static void markWelcomeDone(final Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putBoolean(PREF_WELCOME_DONE, true).commit();
    }


    public static void init(final Context context) {

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

    }

    public static long getLastSyncSucceededTime(final Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getLong(PREF_LAST_SYNC_SUCCEEDED, 0L);
    }

    public static void markSyncSucceededNow(final Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putLong(PREF_LAST_SYNC_SUCCEEDED, UIUtils.getCurrentTime(context)).commit();
    }


    public static void registerOnSharedPreferenceChangeListener(final Context context,
                                                                SharedPreferences.OnSharedPreferenceChangeListener listener) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.registerOnSharedPreferenceChangeListener(listener);
    }
    public static void unregisterOnSharedPreferenceChangeListener(final Context context,
                                                                  SharedPreferences.OnSharedPreferenceChangeListener listener) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.unregisterOnSharedPreferenceChangeListener(listener);
    }

}
