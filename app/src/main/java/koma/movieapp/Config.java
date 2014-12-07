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

package koma.movieapp;

import java.util.TimeZone;

public class Config {

    // General configuration

    // Is this an internal dogfood build?
    public static final boolean IS_DOGFOOD_BUILD = false;
    // Warning messages for dogfood build
    public static final String DOGFOOD_BUILD_WARNING_TITLE = "Test build";
    public static final String DOGFOOD_BUILD_WARNING_TEXT = "This is a test build.";

    public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone("Europe/Stockholm");

    // shorthand for some units of time
    public static final long SECOND_MILLIS = 1000;
    public static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
    public static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;
    public static final long DAY_MILLIS = 24 * HOUR_MILLIS;


    // How long we snooze the stale data notification for after the user has acted on it
    // (to keep from showing it repeatedly and being annoying)
    public static final long STALE_DATA_WARNING_SNOOZE = 10 * MINUTE_MILLIS;

    public static final long STALE_DATA_THRESHOLD = 7 * DAY_MILLIS;

    public static final String TMDB_IMAGE_BASE_URL = "http://image.tmdb.org/t/p/";

    public static final String TMDB_IMAGE_SIZE = "w780";

    public static final String TMDB_API_KEY = "6933170df8ee99aaea39ffe9521bedc5";

    // OAuth 2.0 related config
    public static final String APP_NAME = "GoogleIO-Android";



    private static String piece(String s, char start, char end) {
        int startIndex = s.indexOf(start), endIndex = s.indexOf(end);
        return s.substring(startIndex + 1, endIndex);
    }

    private static String piece(String s, char start) {
        int startIndex = s.indexOf(start);
        return s.substring(startIndex + 1);
    }

    private static String rep(String s, String orig, String replacement) {
        return s.replaceAll(orig, replacement);
    }
}
