/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.dialogue.api;

// TODO(rfink): Docs.
public final class Status {

    public enum Code {
        UNKNOWN,
        PERMISSION_DENIED,
        INVALID_ARGUMENT,
        FAILED_PRECONDITION,
        CUSTOM,
    }

    public static final Status UNKNOWN = new Status(Code.UNKNOWN);
    public static final Status PERMISSION_DENIED = new Status(Code.PERMISSION_DENIED);
    public static final Status INVALID_ARGUMENT = new Status(Code.INVALID_ARGUMENT);
    public static final Status FAILED_PRECONDITION = new Status(Code.FAILED_PRECONDITION);

    public static Status custom(String name) {
        return new Status(Code.CUSTOM, name);
    }

    private final Code code;
    private final String name;

    private Status(Code code) {
        this.code = code;
        this.name = code.name();
    }

    private Status(Code code, String name) {
        this.code = code;
        this.name = name;
    }

    public Code code() {
        return code;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        Status status = (Status) other;

        if (code != status.code) {
            return false;
        }
        return name != null ? name.equals(status.name) : status.name == null;
    }

    @Override
    public int hashCode() {
        int result = code != null ? code.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
