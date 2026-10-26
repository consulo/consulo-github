// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import consulo.diff.util.LineRange;
import consulo.diff.util.Range;
import consulo.diff.util.Side;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.editor.CodeReviewEditorGutterControlsState;
import consulo.review.editor.CodeReviewInlayAdjustmentDisabledReason;
import consulo.review.editor.CodeReviewLineRange;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.Hideable;
import consulo.ui.MessageBoxes;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.review.comment.CodeReviewSuggestionSyntax;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import consulo.versionControlSystem.review.diff.UnifiedCodeReviewItemPosition;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jetbrains.plugins.github.pullrequest.ui.diff.util.LineRangeUtil;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPREditorMappedComponentModel;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewEditorGutterControlsState;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;

class DiffEditorModel implements GHPRReviewDiffEditorModel {
    private final Disposable myCs;
    private final Project myProject;
    private final GHPRDiffViewModel myReviewVm;
    private final GHPRDiffReviewViewModel myDiffVm;
    private final Function<DiffLineLocation, @Nullable Integer> myLocationToLine;
    private final IntFunction<@Nullable DiffLineLocation> myLineToLocation;
    private final IntFunction<UnifiedCodeReviewItemPosition> myLineToUnified;

    private final ObservableValue<List<MappedThread>> myThreads;
    private final ObservableValue<List<MappedNewComment>> myNewComments;

    private final MutableObservableValue<Collection<GHPREditorMappedComponentModel>> myInlays = MutableObservableValue.of(List.of());

    private final MutableObservableValue<Set<Integer>> myLinesWithNewCommentsFlow = MutableObservableValue.of(Set.of());

    private final MutableObservableValue<@Nullable CodeReviewEditorGutterControlsState> myGutterControlsState = MutableObservableValue.of(null);

    private @Nullable Disposable myNewCommentsSubscription;

    DiffEditorModel(Disposable cs,
                    Project project,
                    GHPRDiffViewModel reviewVm,
                    GHPRDiffReviewViewModel diffVm,
                    Function<DiffLineLocation, @Nullable Integer> locationToLine,
                    IntFunction<@Nullable DiffLineLocation> lineToLocation,
                    @RequiredUIAccess IntFunction<UnifiedCodeReviewItemPosition> lineToUnified) {
        myCs = cs;
        myProject = project;
        myReviewVm = reviewVm;
        myDiffVm = diffVm;
        myLocationToLine = locationToLine;
        myLineToLocation = lineToLocation;
        myLineToUnified = lineToUnified;

        myThreads = MappingScopedItemsContainer.mapStatefulToStateful(cs, diffVm.getThreads(), MappedThread::new);
        myNewComments = MappingScopedItemsContainer.mapStatefulToStateful(cs, diffVm.getNewComments(), MappedNewComment::new);

        Runnable updateInlays = () -> {
            List<GHPREditorMappedComponentModel> inlays = new ArrayList<>(myThreads.get());
            inlays.addAll(myNewComments.get());
            myInlays.set(List.copyOf(inlays));
        };
        Disposer.register(cs, myThreads.addListener(it -> updateInlays.run())::run);
        Disposer.register(cs, myNewComments.addListener(it -> updateInlays.run())::run);
        updateInlays.run();

        Disposer.register(cs, diffVm.getNewComments().addListener(it -> resubscribeNewCommentLocations())::run);
        resubscribeNewCommentLocations();

        Runnable updateGutterControls = () -> myGutterControlsState.set(calcGutterControlsState());
        Disposer.register(cs, diffVm.getLocationsWithDiscussions().addListener(it -> updateGutterControls.run())::run);
        Disposer.register(cs, myLinesWithNewCommentsFlow.addListener(it -> updateGutterControls.run())::run);
        updateGutterControls.run();
    }

    private void resubscribeNewCommentLocations() {
        Disposable subscription = Disposable.newDisposable("DiffEditorModel.newCommentLocations");
        synchronized (this) {
            Disposable previous = myNewCommentsSubscription;
            myNewCommentsSubscription = null;
            if (previous != null) {
                Disposer.dispose(previous);
            }
            if (!Disposer.tryRegister(myCs, subscription)) {
                return;
            }
            myNewCommentsSubscription = subscription;
        }
        for (GHPRNewCommentDiffViewModel vm : myDiffVm.getNewComments().get()) {
            Disposer.register(subscription, vm.getLocation().addListener(loc -> updateLinesWithNewComments())::run);
        }
        updateLinesWithNewComments();
    }

    private void updateLinesWithNewComments() {
        Set<Integer> lines = new LinkedHashSet<>();
        for (GHPRNewCommentDiffViewModel vm : myDiffVm.getNewComments().get()) {
            Integer line = myLocationToLine.apply(vm.getLocation().get().getLineLocation());
            if (line != null) {
                lines.add(line);
            }
        }
        myLinesWithNewCommentsFlow.set(Set.copyOf(lines));
    }

    private CodeReviewEditorGutterControlsState calcGutterControlsState() {
        Set<Integer> linesWithComments = new LinkedHashSet<>();
        for (DiffLineLocation location : myDiffVm.getLocationsWithDiscussions().get()) {
            Integer line = myLocationToLine.apply(location);
            if (line != null) {
                linesWithComments.add(line);
            }
        }
        return new GHPRReviewEditorGutterControlsState(
            Set.copyOf(linesWithComments), myLinesWithNewCommentsFlow.get(),
            myDiffVm.canComment() ? transferRanges(myDiffVm.getCommentableRanges(), myDiffVm.getChangedRanges()) : List.of()
        );
    }

    @Override
    public ObservableValue<Collection<GHPREditorMappedComponentModel>> getInlays() {
        return myInlays;
    }

    @Override
    public ObservableValue<@Nullable CodeReviewEditorGutterControlsState> getGutterControlsState() {
        return myGutterControlsState;
    }

    private List<LineRange> transferRanges(List<Range> ranges, List<Range> changedRanges) {
        List<LineRange> leftRange = new ArrayList<>();
        List<LineRange> rightRange = new ArrayList<>();
        for (Range range : ranges) {
            addIfNotNull(leftRange, getSideRange(range, Side.LEFT));
            addIfNotNull(rightRange, getSideRange(range, Side.RIGHT));
        }

        if (leftRange.isEmpty() || rightRange.isEmpty()) {
            return !leftRange.isEmpty() ? leftRange : rightRange;
        }

        List<LineRange> leftChangedRanges = new ArrayList<>();
        List<LineRange> rightChangedRanges = new ArrayList<>();
        for (Range range : changedRanges) {
            addIfNotNull(leftChangedRanges, getSideRange(range, Side.LEFT));
            addIfNotNull(rightChangedRanges, getSideRange(range, Side.RIGHT));
        }

        return LineRangeUtil.extract(leftRange, rightRange, leftChangedRanges, rightChangedRanges);
    }

    private static void addIfNotNull(List<LineRange> list, @Nullable LineRange range) {
        if (range != null) {
            list.add(range);
        }
    }

    private @Nullable LineRange getSideRange(Range range, Side side) {
        int startLineIdx = side == Side.LEFT ? range.start1() : range.start2();
        Integer start = myLocationToLine.apply(new DiffLineLocation(side, startLineIdx));
        if (start == null) {
            return null;
        }
        int endLineIdx = side == Side.LEFT ? range.end1() : range.end2();
        int end;
        if (startLineIdx == endLineIdx) {
            end = start;
        }
        else {
            Integer endLine = myLocationToLine.apply(new DiffLineLocation(side, endLineIdx - 1));
            if (endLine == null) {
                return null;
            }
            end = endLine + 1;
        }

        if (start > end) {
            throw new IllegalStateException("Start line of range " + range + " is greater than end line: " + start + " > " + end);
        }
        return new LineRange(start, end);
    }

    @RequiredUIAccess
    @Override
    public boolean canCreateComment(CodeReviewLineRange lineRange) {
        DiffLineLocation loc1 = myLineToLocation.apply(lineRange.start());
        if (loc1 == null) {
            return false;
        }
        DiffLineLocation loc2 = myLineToLocation.apply(lineRange.end());
        if (loc2 == null) {
            return false;
        }
        CodeReviewEditorGutterControlsState gutterControls = myGutterControlsState.get();
        if (gutterControls == null) {
            return false;
        }
        return loc1.side() == loc2.side()
            && gutterControls.isLineCommentable(lineRange.start())
            && gutterControls.isLineCommentable(lineRange.end());
    }

    @RequiredUIAccess
    @Override
    public void requestNewComment(CodeReviewLineRange lineRange) {
        DiffLineLocation loc1 = myLineToLocation.apply(lineRange.start());
        if (loc1 == null) {
            return;
        }
        DiffLineLocation loc2 = myLineToLocation.apply(lineRange.end());
        if (loc2 == null) {
            return;
        }
        GHPRReviewCommentLocation loc = new GHPRReviewCommentLocation.MultiLine(loc1.side(), loc1.line(), loc2.side(), loc2.line());
        myDiffVm.requestNewComment(loc, true);
    }

    @RequiredUIAccess
    @Override
    public void requestNewComment(int lineIdx) {
        DiffLineLocation loc = myLineToLocation.apply(lineIdx);
        if (loc == null) {
            return;
        }
        myDiffVm.requestNewComment(new GHPRReviewCommentLocation.SingleLine(loc.side(), loc.line()), true);
    }

    @RequiredUIAccess
    @Override
    public void cancelNewComment(int lineIdx) {
        DiffLineLocation loc = myLineToLocation.apply(lineIdx);
        if (loc == null) {
            return;
        }
        myDiffVm.cancelNewComment(loc.side(), loc.line());
    }

    @RequiredUIAccess
    @Override
    public void toggleComments(int lineIdx) {
        List<Hideable> hideables = new ArrayList<>();
        for (GHPREditorMappedComponentModel inlay : myInlays.get()) {
            Integer line = inlay.getLine().get();
            if (line != null && line == lineIdx && inlay instanceof Hideable hideable) {
                hideables.add(hideable);
            }
        }
        Hideable.syncOrToggleAll(hideables);
    }

    @Override
    public boolean canNavigate() {
        return myDiffVm.canNavigate();
    }

    @RequiredUIAccess
    @Override
    public boolean canGotoNextComment(String threadId) {
        return myReviewVm.nextComment(threadId) != null;
    }

    @RequiredUIAccess
    @Override
    public boolean canGotoNextComment(int line) {
        return myReviewVm.nextComment(myLineToUnified.apply(line)) != null;
    }

    @RequiredUIAccess
    @Override
    public boolean canGotoPreviousComment(String threadId) {
        return myReviewVm.previousComment(threadId) != null;
    }

    @RequiredUIAccess
    @Override
    public boolean canGotoPreviousComment(int line) {
        return myReviewVm.previousComment(myLineToUnified.apply(line)) != null;
    }

    @RequiredUIAccess
    @Override
    public void gotoNextComment(String threadId) {
        String commentId = myReviewVm.nextComment(threadId);
        if (commentId == null) {
            return;
        }
        myReviewVm.showDiffAtComment(commentId);
    }

    @RequiredUIAccess
    @Override
    public void gotoNextComment(int line) {
        String commentId = myReviewVm.nextComment(myLineToUnified.apply(line));
        if (commentId == null) {
            return;
        }
        myReviewVm.showDiffAtComment(commentId);
    }

    @RequiredUIAccess
    @Override
    public void gotoPreviousComment(String threadId) {
        String commentId = myReviewVm.previousComment(threadId);
        if (commentId == null) {
            return;
        }
        myReviewVm.showDiffAtComment(commentId);
    }

    @RequiredUIAccess
    @Override
    public void gotoPreviousComment(int line) {
        String commentId = myReviewVm.previousComment(myLineToUnified.apply(line));
        if (commentId == null) {
            return;
        }
        myReviewVm.showDiffAtComment(commentId);
    }

    private static @Nullable CodeReviewLineRange toLineRange(GHPRReviewCommentLocation location,
                                                             Function<DiffLineLocation, @Nullable Integer> locationToLine) {
        if (location instanceof GHPRReviewCommentLocation.MultiLine loc) {
            Integer start = locationToLine.apply(new DiffLineLocation(loc.startSide(), loc.startLineIdx()));
            if (start == null) {
                return null;
            }
            Integer end = locationToLine.apply(new DiffLineLocation(loc.side(), loc.lineIdx()));
            if (end == null || start > end) {
                return null;
            }
            return new CodeReviewLineRange(start, end);
        }
        Integer line = locationToLine.apply(new DiffLineLocation(location.side(), location.lineIdx()));
        return line == null ? null : new CodeReviewLineRange(line, line);
    }

    private final class MappedThread extends GHPREditorMappedComponentModel.Thread<GHPRCompactReviewThreadViewModel> {
        private final MutableObservableValue<Boolean> myIsVisible;
        private final MutableObservableValue<@Nullable CodeReviewLineRange> myRange;
        private final MutableObservableValue<@Nullable Integer> myLine;

        MappedThread(Disposable cs, GHPRReviewThreadDiffViewModel vm) {
            super(vm);
            myIsVisible = MutableObservableValue.of(vm.getMapping().get().isVisible() && !getHiddenState().get());
            myRange = MutableObservableValue.of(rangeOf(vm.getMapping().get()));
            myLine = MutableObservableValue.of(lineOf(myRange.get()));
            Runnable update = () -> {
                GHPRReviewThreadDiffViewModel.MappingData mapping = vm.getMapping().get();
                myIsVisible.set(mapping.isVisible() && !getHiddenState().get());
                CodeReviewLineRange range = rangeOf(mapping);
                myRange.set(range);
                myLine.set(lineOf(range));
            };
            Disposer.register(cs, vm.getMapping().addListener(it -> update.run())::run);
            Disposer.register(cs, getHiddenState().addListener(it -> update.run())::run);
        }

        private @Nullable CodeReviewLineRange rangeOf(GHPRReviewThreadDiffViewModel.MappingData mapping) {
            GHPRReviewCommentLocation location = mapping.location();
            return location == null ? null : toLineRange(location, myLocationToLine);
        }

        @Override
        public ObservableValue<Boolean> isVisible() {
            return myIsVisible;
        }

        @Override
        public ObservableValue<@Nullable CodeReviewLineRange> getRange() {
            return myRange;
        }

        @Override
        public ObservableValue<@Nullable Integer> getLine() {
            return myLine;
        }
    }

    private final class MappedNewComment extends GHPREditorMappedComponentModel.NewComment<GHPRReviewNewCommentEditorViewModel> {
        private final Object myKey = "NEW_" + UUID.randomUUID();
        private final MutableObservableValue<@Nullable CodeReviewLineRange> myRange;
        private final MutableObservableValue<@Nullable Integer> myLine;
        private final ObservableValue<Boolean> myIsVisible = ObservableValue.of(true);
        private final MutableObservableValue<@Nullable CodeReviewInlayAdjustmentDisabledReason> myAdjustmentDisabledReason;

        MappedNewComment(Disposable cs, GHPRNewCommentDiffViewModel vm) {
            super(vm);
            myRange = MutableObservableValue.of(toLineRange(vm.getPosition().get().location(), myLocationToLine));
            myLine = MutableObservableValue.of(lineOf(myRange.get()));
            Disposer.register(cs, vm.getPosition().addListener(position -> {
                CodeReviewLineRange range = toLineRange(position.location(), myLocationToLine);
                myRange.set(range);
                myLine.set(lineOf(range));
            })::run);

            myAdjustmentDisabledReason = MutableObservableValue.of(adjustmentDisabledReason(vm.getText().get()));
            Disposer.register(cs, vm.getText().addListener(text -> myAdjustmentDisabledReason.set(adjustmentDisabledReason(text)))::run);
        }

        private static @Nullable CodeReviewInlayAdjustmentDisabledReason adjustmentDisabledReason(String text) {
            if (CodeReviewSuggestionSyntax.GITHUB.containsSuggestedChange(text)) {
                return CodeReviewInlayAdjustmentDisabledReason.SUGGESTED_CHANGE;
            }
            return null;
        }

        @Override
        public Object getKey() {
            return myKey;
        }

        @Override
        public ObservableValue<@Nullable CodeReviewLineRange> getRange() {
            return myRange;
        }

        @Override
        public ObservableValue<@Nullable Integer> getLine() {
            return myLine;
        }

        @Override
        public ObservableValue<Boolean> isVisible() {
            return myIsVisible;
        }

        @Override
        public ObservableValue<@Nullable CodeReviewInlayAdjustmentDisabledReason> getAdjustmentDisabledReason() {
            return myAdjustmentDisabledReason;
        }

        @Override
        public void adjustRange(@Nullable Integer newStart, @Nullable Integer newEnd) {
            if (!(newStart == null && newEnd == null)) {
                CodeReviewLineRange range = myRange.get();
                if (range == null) {
                    return;
                }
                int start = newStart != null ? newStart : range.start();
                int end = newEnd != null ? newEnd : range.end();
                DiffLineLocation startLoc = myLineToLocation.apply(start);
                if (startLoc == null) {
                    return;
                }
                DiffLineLocation endLoc = myLineToLocation.apply(end);
                if (endLoc == null) {
                    return;
                }

                getVm().updateLineRange(startLoc, endLoc);
                getVm().requestFocus();
            }
        }

        @RequiredUIAccess
        @Override
        public void cancel() {
            GHPRReviewNewCommentEditorViewModel vm = getVm();
            if (vm.getText().get().isBlank()) {
                vm.cancel();
                return;
            }
            UIAccess uiAccess = UIAccess.current();
            MessageBoxes.yesNo()
                .title(ReviewLocalize.reviewCommentsDiscardNewConfirmationTitle())
                .text(ReviewLocalize.reviewCommentsDiscardNewConfirmation())
                .showAsync()
                .whenCompleteAsync((yes, error) -> {
                    if (Boolean.TRUE.equals(yes)) {
                        vm.cancel();
                    }
                }, uiAccess);
        }
    }

    private static @Nullable Integer lineOf(@Nullable CodeReviewLineRange range) {
        return range == null ? null : range.end();
    }
}
