// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.diff.util;

import consulo.diff.util.LineRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LineRangeUtil {
    private LineRangeUtil() {
    }

    public static List<LineRange> extract(List<LineRange> leftRange,
                                          List<LineRange> rightRange,
                                          List<LineRange> leftChangedRanges,
                                          List<LineRange> rightChangedRanges) {
        List<LineRange> newLeftRanges = subtractRanges(leftRange, rightChangedRanges);
        List<LineRange> newRightRanges = subtractRanges(rightRange, leftChangedRanges);
        List<LineRange> all = new ArrayList<>(newLeftRanges);
        all.addAll(newRightRanges);
        return sumRanges(all);
    }

    private static List<LineRange> subtractRanges(List<LineRange> source, List<LineRange> subtract) {
        List<LineRange> results = source;
        for (LineRange subtractRange : subtract) {
            List<LineRange> next = new ArrayList<>();
            for (LineRange currentRange : results) {
                int overlapStart = Math.max(currentRange.start(), subtractRange.start());
                int overlapEnd = Math.min(currentRange.end(), subtractRange.end());

                if (overlapStart >= overlapEnd) {
                    next.add(currentRange);
                }
                else {
                    if (currentRange.start() < overlapStart) {
                        next.add(new LineRange(currentRange.start(), overlapStart));
                    }
                    if (currentRange.end() > overlapEnd) {
                        next.add(new LineRange(overlapEnd, currentRange.end()));
                    }
                }
            }
            results = next;
        }
        return results;
    }

    private static List<LineRange> sumRanges(List<LineRange> ranges) {
        List<LineRange> results = new ArrayList<>();
        List<LineRange> sortedRanges = new ArrayList<>(ranges);
        sortedRanges.sort(Comparator.comparingInt(LineRange::start));
        results.add(sortedRanges.getFirst());
        for (int i = 1; i < sortedRanges.size(); i++) {
            LineRange current = results.getLast();
            LineRange next = sortedRanges.get(i);

            LineRange nextResult;
            if (next.start() <= current.end()) {
                results.removeLast();
                nextResult = new LineRange(current.start(), Math.max(current.end(), next.end()));
            }
            else {
                nextResult = next;
            }

            results.add(nextResult);
        }
        return results;
    }
}
