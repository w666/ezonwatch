/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.github.w666.ezonwatch;

import java.util.HashMap;

public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String EZON_CHARACTERISTIC = "0000ffb2-0000-1000-8000-00805f9b34fb";

    static {
        attributes.put(EZON_CHARACTERISTIC, "Main ezon characteristic");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
