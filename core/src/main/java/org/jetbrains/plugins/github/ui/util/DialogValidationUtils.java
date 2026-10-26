// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.ui.util;

import consulo.localize.LocalizeValue;
import consulo.ui.HasValidator.ValidationInfo;
import consulo.ui.TextBox;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public final class DialogValidationUtils {
    private DialogValidationUtils() {
    }

    /**
     * Returns {@link ValidationInfo} with {@code message} if {@code textField} is blank
     */
    public static @Nullable ValidationInfo notBlank(TextBox textField, LocalizeValue message) {
        String text = textField.getValue();
        return text == null || text.isBlank() ? new ValidationInfo(message.get()) : null;
    }

    /**
     * Chains the {@code validators} so that if one of them returns non-null {@link ValidationInfo} the rest of them are not checked
     */
    public static Validator chain(Validator... validators) {
        return () -> {
            for (Validator validator : validators) {
                ValidationInfo info = validator.invoke();
                if (info != null) {
                    return info;
                }
            }
            return null;
        };
    }

    /**
     * Stateful validator that checks that contents of {@code textField} are unique among {@link #getRecords()}
     */
    public static class RecordUniqueValidator implements Validator {
        private final TextBox myTextField;
        private final LocalizeValue myMessage;
        private Set<String> myRecords = Set.of();

        public RecordUniqueValidator(TextBox textField, LocalizeValue message) {
            myTextField = textField;
            myMessage = message;
        }

        public Set<String> getRecords() {
            return myRecords;
        }

        public void setRecords(Set<String> records) {
            myRecords = records;
        }

        @Override
        public @Nullable ValidationInfo invoke() {
            String text = myTextField.getValue();
            return text != null && myRecords.contains(text) ? new ValidationInfo(myMessage.get()) : null;
        }
    }

    @FunctionalInterface
    public interface Validator {
        @Nullable
        ValidationInfo invoke();
    }
}
