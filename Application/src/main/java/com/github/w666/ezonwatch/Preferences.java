/**
 * Created by vmartynov on 11/11/16.
 */
package com.github.w666.ezonwatch;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
    private static String PREFS_NAME = "EzonAppPrefs";
    public static String DEVICE_NAME = "ezon_device_name";
    public static String DEVICE_ADDR = "ezon_device_addess";
    public static String EZON_STEPS_TARGET = "ezon_steps_target";

    public static String read (Context context, String name) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        return settings.getString(name, null);
    }

    public static int readInt (Context context, String name) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        return settings.getInt(name, 0);
    }

    public static void write (Context context, String name, String value) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(name, value);
        editor.apply();
    }

    public static void write (Context context, String name, int value) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(name, value);
        editor.apply();
    }

    public static String readDeviceName (Context context) {
        return read(context, DEVICE_NAME);
    }

    public static String readDeviceAddress (Context context) {
        return read(context, DEVICE_ADDR);
    }

    public static int readStepsTarget (Context context) {
        return readInt(context, EZON_STEPS_TARGET);
    }

    public static void writeDeviceName (Context context, String value) {
        write(context, DEVICE_NAME, value);
    }

    public static void writeDeviceAddress (Context context, String value) {
        write(context, DEVICE_ADDR, value);
    }

    public static void writeStepsTarget (Context context, int value) {
        write(context, EZON_STEPS_TARGET, value);
    }
}
