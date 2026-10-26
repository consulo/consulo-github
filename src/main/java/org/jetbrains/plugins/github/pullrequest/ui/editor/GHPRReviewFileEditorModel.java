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
package org.jetbrains.plugins.github.pullrequest.ui.editor;

import consulo.diff.util.LineRange;
import consulo.diff.util.Range;
import consulo.diff.util.Side;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.editor.CodeReviewChangedRange;
import consulo.review.editor.CodeReviewEditorGutterActionableChangesModel;
import consulo.review.editor.CodeReviewEditorGutterControlsState;
import consulo.review.editor.CodeReviewEditorModel;
import consulo.review.editor.CodeReviewInlayAdjustmentDisabledReason;
import consulo.review.editor.CodeReviewLineRange;
import consulo.review.editor.CodeReviewMultilineCommentableEditorModel;
import consulo.review.editor.CodeReviewNavigableEditorViewModel;
import consulo.review.editor.MutableCodeReviewEditorGutterChangesModel;
import consulo.review.editor.ReviewInEditorUtil;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.Hideable;
import consulo.ui.MessageBoxes;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.comment.CodeReviewSuggestionSyntax;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import consulo.versionControlSystem.review.util.ExcludingApproximateChangedRangesShifter;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public class GHPRReviewFileEditorModel implements CodeReviewEditorGutterActionableChangesModel,
    CodeReviewEditorModel<GHPREditorMappedComponentModel>,
    CodeReviewNavigableEditorViewModel,
    CodeReviewMultilineCommentableEditorModel {
    public static final Key<GHPRReviewFileEditorModel> KEY = Key.create("GitHub.PullRequest.Editor.Review.UIModel");

    private final Disposable myCs;
    private final Project myProject;
    private final GithubPullRequestsProjectUISettings mySettings;
    private final GHPRReviewFileEditorViewModel myFileVm;
    private final MutableCodeReviewEditorGutterChangesModel myChangesModel;
    private final BiConsumer<RefComparisonChange, Integer> myShowEditor;

    private final MutableObservableValue<@Nullable List<CodeReviewChangedRange>> myPostReviewRanges = MutableObservableValue.of(null);

    private final MutableObservableValue<Set<Integer>> myLinesWithNewCommentsFlow = MutableObservableValue.of(Set.of());
    private @Nullable Disposable myNewCommentsLocationsCs;

    private final MutableObservableValue<@Nullable CodeReviewEditorGutterControlsState> myGutterControlsState = MutableObservableValue.of(null);

    private final ObservableValue<List<ShiftedThread>> myShiftedThreads;
    private final ObservableValue<List<ShiftedNewComment>> myShiftedNewComments;
    private final MutableObservableValue<Collection<GHPREditorMappedComponentModel>> myInlays = MutableObservableValue.of(List.of());

    public GHPRReviewFileEditorModel(Disposable cs,
                                     Project project,
                                     GithubPullRequestsProjectUISettings settings,
                                     GHPRReviewFileEditorViewModel fileVm,
                                     BiConsumer<RefComparisonChange, Integer> showEditor) {
        this(cs, project, settings, fileVm, new MutableCodeReviewEditorGutterChangesModel(), showEditor);
    }

    public GHPRReviewFileEditorModel(Disposable cs,
                                     Project project,
                                     GithubPullRequestsProjectUISettings settings,
                                     GHPRReviewFileEditorViewModel fileVm,
                                     MutableCodeReviewEditorGutterChangesModel changesModel,
                                     BiConsumer<RefComparisonChange, Integer> showEditor) {
        myCs = cs;
        myProject = project;
        mySettings = settings;
        myFileVm = fileVm;
        myChangesModel = changesModel;
        myShowEditor = showEditor;

        Disposer.register(cs, fileVm.getNewComments().addListener(this::subscribeToNewCommentsLocations)::run);
        subscribeToNewCommentsLocations(fileVm.getNewComments().get());

        Disposer.register(cs, myPostReviewRanges.addListener(it -> updateGutterControlsState())::run);
        Disposer.register(cs, fileVm.getLinesWithComments().addListener(it -> updateGutterControlsState())::run);
        Disposer.register(cs, myLinesWithNewCommentsFlow.addListener(it -> updateGutterControlsState())::run);
        updateGutterControlsState();

        myShiftedThreads = MappingScopedItemsContainer.mapStatefulToStateful(cs, fileVm.getThreads(), ShiftedThread::new);
        myShiftedNewComments = MappingScopedItemsContainer.mapStatefulToStateful(cs, fileVm.getNewComments(), ShiftedNewComment::new);
        Disposer.register(cs, myShiftedThreads.addListener(it -> updateInlays())::run);
        Disposer.register(cs, myShiftedNewComments.addListener(it -> updateInlays())::run);
        updateInlays();
    }

    private synchronized void subscribeToNewCommentsLocations(List<GHPRReviewFileEditorNewCommentViewModel> newComments) {
        Disposable previous = myNewCommentsLocationsCs;
        if (previous != null) {
            Disposer.dispose(previous);
        }
        Disposable locationsCs = Disposable.newDisposable("GHPRReviewFileEditorModel.newCommentsLocations");
        Disposer.register(myCs, locationsCs);
        myNewCommentsLocationsCs = locationsCs;
        Runnable update = () -> {
            Set<Integer> lines = new LinkedHashSet<>();
            for (GHPRReviewFileEditorNewCommentViewModel vm : newComments) {
                lines.add(vm.getLocation().get().lineIdx());
            }
            myLinesWithNewCommentsFlow.set(Set.copyOf(lines));
        };
        for (GHPRReviewFileEditorNewCommentViewModel vm : newComments) {
            Disposer.register(locationsCs, vm.getLocation().addListener(it -> update.run())::run);
        }
        update.run();
    }

    private void updateGutterControlsState() {
        List<CodeReviewChangedRange> postReviewRanges = myPostReviewRanges.get();
        if (postReviewRanges == null) {
            myGutterControlsState.set(null);
            return;
        }
        Set<Integer> shiftedLinesWithComments = new LinkedHashSet<>();
        for (Integer line : myFileVm.getLinesWithComments().get()) {
            shiftedLinesWithComments.add(ReviewInEditorUtil.transferLineToAfter(postReviewRanges, line));
        }
        List<LineRange> shiftedCommentableRanges = new ArrayList<>();
        for (Range range : ExcludingApproximateChangedRangesShifter.shift(myFileVm.getCommentableRanges(), toRanges(postReviewRanges))) {
            shiftedCommentableRanges.add(getAfterLines(range));
        }
        Set<Integer> linesWithNewComments = new LinkedHashSet<>();
        for (Integer line : myLinesWithNewCommentsFlow.get()) {
            linesWithNewComments.add(ReviewInEditorUtil.transferLineToAfter(postReviewRanges, line));
        }
        myGutterControlsState.set(new GHPRReviewEditorGutterControlsState(Set.copyOf(shiftedLinesWithComments),
            Set.copyOf(linesWithNewComments),
            List.copyOf(shiftedCommentableRanges)));
    }

    private void updateInlays() {
        // very explicit ordering: if we order back to front, loading of editor appears smoother (most initial loading happens off-screen)
        List<ShiftedThread> threads = new ArrayList<>(myShiftedThreads.get());
        threads.sort(Comparator.comparingInt((ShiftedThread it) -> {
            Integer line = it.getLine().get();
            return line == null ? -1 : line;
        }).reversed());
        List<GHPREditorMappedComponentModel> inlays = new ArrayList<>(threads);
        inlays.addAll(myShiftedNewComments.get());
        myInlays.set(List.copyOf(inlays));
    }

    @Override
    public ObservableValue<@Nullable List<CodeReviewChangedRange>> getReviewRanges() {
        return myChangesModel.getReviewRanges();
    }

    @RequiredUIAccess
    @Override
    public boolean getShouldHighlightDiffRanges() {
        return mySettings.getHighlightDiffLinesInEditor();
    }

    @RequiredUIAccess
    @Override
    public void setShouldHighlightDiffRanges(boolean value) {
        mySettings.setHighlightDiffLinesInEditor(value);
    }

    @Override
    public boolean canNavigate() {
        return true;
    }

    @Override
    public ObservableValue<@Nullable CodeReviewEditorGutterControlsState> getGutterControlsState() {
        return myGutterControlsState;
    }

    @Override
    public ObservableValue<Collection<GHPREditorMappedComponentModel>> getInlays() {
        return myInlays;
    }

    @RequiredUIAccess
    @Override
    public void requestNewComment(int lineIdx) {
        List<CodeReviewChangedRange> ranges = myPostReviewRanges.get();
        if (ranges == null) {
            return;
        }
        Integer originalLine = ReviewInEditorUtil.transferLineFromAfter(ranges, lineIdx);
        if (originalLine == null || originalLine < 0) {
            return;
        }
        myFileVm.requestNewComment(originalLine, true);
    }

    @RequiredUIAccess
    @Override
    public void cancelNewComment(int lineIdx) {
        List<CodeReviewChangedRange> ranges = myPostReviewRanges.get();
        if (ranges == null) {
            return;
        }
        Integer loc = ReviewInEditorUtil.transferLineFromAfter(ranges, lineIdx);
        if (loc == null || loc < 0) {
            return;
        }
        myFileVm.cancelNewComment(loc);
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

    @RequiredUIAccess
    @Override
    public @Nullable String getBaseContent(int baseLine1, int baseLine2) {
        return myFileVm.getBaseContent(new LineRange(baseLine1, baseLine2));
    }

    @RequiredUIAccess
    @Override
    public void showDiff(@Nullable Integer lineIdx) {
        List<CodeReviewChangedRange> ranges = myPostReviewRanges.get();
        if (ranges == null) {
            return;
        }
        Integer originalLine = lineIdx == null ? null : ReviewInEditorUtil.transferLineFromAfter(ranges, lineIdx, true);
        myFileVm.showDiff(originalLine != null && originalLine >= 0 ? originalLine : null);
    }

    @RequiredUIAccess
    @Override
    public void addDiffHighlightListener(Disposable disposable, Runnable listener) {
        Disposer.register(disposable, mySettings.getHighlightDiffLinesInEditorState().addListener(it -> listener.run())::run);
        listener.run();
    }

    public void setPostReviewChanges(List<CodeReviewChangedRange> changedRanges) {
        myPostReviewRanges.set(List.copyOf(changedRanges));
        List<CodeReviewChangedRange> changes = new ArrayList<>();
        for (Range range : ExcludingApproximateChangedRangesShifter.shift(myFileVm.getChangedRanges(), toRanges(changedRanges))) {
            changes.add(asLst(range));
        }
        myChangesModel.setChanges(List.copyOf(changes));
    }

    @RequiredUIAccess
    @Override
    public boolean canGotoNextComment(String threadId) {
        return myFileVm.lookupNextComment(threadId) != null;
    }

    @RequiredUIAccess
    @Override
    public boolean canGotoNextComment(int line) {
        return myFileVm.lookupNextComment(shiftLineToBefore(line)) != null;
    }

    @RequiredUIAccess
    @Override
    public boolean canGotoPreviousComment(String threadId) {
        return myFileVm.lookupPreviousComment(threadId) != null;
    }

    @RequiredUIAccess
    @Override
    public boolean canGotoPreviousComment(int line) {
        return myFileVm.lookupPreviousComment(shiftLineToBefore(line)) != null;
    }

    @RequiredUIAccess
    @Override
    public void gotoNextComment(String threadId) {
        String commentId = myFileVm.lookupNextComment(threadId);
        if (commentId == null) {
            return;
        }
        gotoComment(commentId);
    }

    @RequiredUIAccess
    @Override
    public void gotoNextComment(int line) {
        String commentId = myFileVm.lookupNextComment(shiftLineToBefore(line));
        if (commentId == null) {
            return;
        }
        gotoComment(commentId);
    }

    @RequiredUIAccess
    @Override
    public void gotoPreviousComment(String threadId) {
        String commentId = myFileVm.lookupPreviousComment(threadId);
        if (commentId == null) {
            return;
        }
        gotoComment(commentId);
    }

    @RequiredUIAccess
    @Override
    public void gotoPreviousComment(int line) {
        String commentId = myFileVm.lookupPreviousComment(shiftLineToBefore(line));
        if (commentId == null) {
            return;
        }
        gotoComment(commentId);
    }

    @RequiredUIAccess
    private void gotoComment(String threadId) {
        Pair<RefComparisonChange, Integer> position = myFileVm.getThreadPosition(threadId);
        if (position == null) {
            return;
        }
        RefComparisonChange change = position.getFirst();
        int unmappedLine = position.getSecond();
        int line;
        if (change.equals(myFileVm.getChange())) {
            // Only shift the line if it comes from this file
            line = shiftLineToAfter(unmappedLine);
            // if the line number is from a different file, we can't currently easily access outside changes to shift with
            // the current line would be a best-guess estimate
        }
        else {
            line = unmappedLine;
        }

        myShowEditor.accept(change, line);
        myFileVm.requestThreadFocus(threadId);
    }

    private int shiftLineToAfter(int line) {
        List<CodeReviewChangedRange> ranges = myPostReviewRanges.get();
        if (ranges == null) {
            return line;
        }
        return ReviewInEditorUtil.transferLineToAfter(ranges, line);
    }

    private int shiftLineToBefore(int line) {
        List<CodeReviewChangedRange> ranges = myPostReviewRanges.get();
        if (ranges == null) {
            return line;
        }
        Integer result = ReviewInEditorUtil.transferLineFromAfter(ranges, line, true);
        return result == null ? 0 : result;
    }

    @RequiredUIAccess
    @Override
    public void requestNewComment(CodeReviewLineRange lineRange) {
        List<CodeReviewChangedRange> ranges = myPostReviewRanges.get();
        if (ranges == null) {
            return;
        }
        Integer originalStartLine = ReviewInEditorUtil.transferLineFromAfter(ranges, lineRange.start());
        if (originalStartLine == null || originalStartLine < 0) {
            return;
        }
        Integer originalEndLine = ReviewInEditorUtil.transferLineFromAfter(ranges, lineRange.end());
        if (originalEndLine == null || originalEndLine < 0) {
            return;
        }
        myFileVm.requestNewComment(new LineRange(originalStartLine, originalEndLine), true);
    }

    @RequiredUIAccess
    @Override
    public boolean canCreateComment(CodeReviewLineRange lineRange) {
        CodeReviewEditorGutterControlsState gutterControls = myGutterControlsState.get();
        if (gutterControls == null) {
            return false;
        }
        return gutterControls.isLineCommentable(lineRange.start()) &&
            gutterControls.isLineCommentable(lineRange.end());
    }

    private static List<Range> toRanges(List<CodeReviewChangedRange> ranges) {
        List<Range> result = new ArrayList<>(ranges.size());
        for (CodeReviewChangedRange range : ranges) {
            result.add(new Range(range.baseLine1(), range.baseLine2(), range.line1(), range.line2()));
        }
        return result;
    }

    private static CodeReviewChangedRange asLst(Range range) {
        return new CodeReviewChangedRange(range.start2(), range.end2(), range.start1(), range.end1());
    }

    private static @Nullable CodeReviewLineRange toLineRange(GHPRReviewCommentLocation location) {
        if (location instanceof GHPRReviewCommentLocation.MultiLine loc) {
            return loc.startSide() == loc.side() ? new CodeReviewLineRange(loc.startLineIdx(), loc.lineIdx()) : null;
        }
        return location.side() == Side.RIGHT ? new CodeReviewLineRange(location.lineIdx(), location.lineIdx()) : null;
    }

    private static @Nullable CodeReviewLineRange transferToAfter(CodeReviewLineRange lineRange, List<CodeReviewChangedRange> ranges) {
        int start = ReviewInEditorUtil.transferLineToAfter(ranges, lineRange.start());
        int end = ReviewInEditorUtil.transferLineToAfter(ranges, lineRange.end());
        return start <= end ? new CodeReviewLineRange(start, end) : null;
    }

    private static LineRange getAfterLines(Range range) {
        return new LineRange(range.start2(), range.end2());
    }

    private class ShiftedThread extends GHPREditorMappedComponentModel.Thread<GHPRReviewFileEditorThreadViewModel> {
        private final MutableObservableValue<Boolean> myIsVisible = MutableObservableValue.of(false);
        private final MutableObservableValue<@Nullable CodeReviewLineRange> myRange = MutableObservableValue.of(null);
        private final MutableObservableValue<@Nullable Integer> myLine = MutableObservableValue.of(null);

        ShiftedThread(Disposable cs, GHPRReviewFileEditorThreadViewModel vm) {
            super(vm);
            Runnable updateVisible = () -> myIsVisible.set(vm.isVisible().get() && !getHiddenState().get());
            Disposer.register(cs, vm.isVisible().addListener(it -> updateVisible.run())::run);
            Disposer.register(cs, getHiddenState().addListener(it -> updateVisible.run())::run);
            updateVisible.run();

            Runnable updateRange = () -> {
                List<CodeReviewChangedRange> ranges = myPostReviewRanges.get();
                CodeReviewLineRange commentRange = vm.getCommentRange().get();
                CodeReviewLineRange range;
                if (ranges == null || commentRange == null) {
                    range = null;
                }
                else {
                    range = transferToAfter(commentRange, ranges);
                }
                myRange.set(range);
                myLine.set(range == null ? null : range.end());
            };
            Disposer.register(cs, myPostReviewRanges.addListener(it -> updateRange.run())::run);
            Disposer.register(cs, vm.getCommentRange().addListener(it -> updateRange.run())::run);
            updateRange.run();
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

    private class ShiftedNewComment extends GHPREditorMappedComponentModel.NewComment<GHPRReviewNewCommentEditorViewModel> {
        private final Object myKey = "NEW_" + UUID.randomUUID();
        private final MutableObservableValue<@Nullable CodeReviewLineRange> myRange = MutableObservableValue.of(null);
        private final MutableObservableValue<@Nullable Integer> myLine = MutableObservableValue.of(null);
        private final ObservableValue<Boolean> myIsVisible = MutableObservableValue.of(true);
        private final MutableObservableValue<@Nullable CodeReviewInlayAdjustmentDisabledReason> myAdjustmentDisabledReason =
            MutableObservableValue.of(null);

        ShiftedNewComment(Disposable cs, GHPRReviewFileEditorNewCommentViewModel vm) {
            super(vm);
            Runnable updateRange = () -> {
                List<CodeReviewChangedRange> postReviewRanges = myPostReviewRanges.get();
                CodeReviewLineRange range;
                if (postReviewRanges == null) {
                    range = null;
                }
                else {
                    CodeReviewLineRange lineRange = toLineRange(vm.getPosition().get().location());
                    range = lineRange == null ? null : transferToAfter(lineRange, postReviewRanges);
                }
                myRange.set(range);
                myLine.set(range == null ? null : range.end());
            };
            Disposer.register(cs, vm.getPosition().addListener(it -> updateRange.run())::run);
            Disposer.register(cs, myPostReviewRanges.addListener(it -> updateRange.run())::run);
            updateRange.run();

            Runnable updateAdjustment = () -> myAdjustmentDisabledReason.set(
                CodeReviewSuggestionSyntax.GITHUB.containsSuggestedChange(vm.getText().get())
                    ? CodeReviewInlayAdjustmentDisabledReason.SUGGESTED_CHANGE
                    : null);
            Disposer.register(cs, vm.getText().addListener(it -> updateAdjustment.run())::run);
            updateAdjustment.run();
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
            if (newStart == null && newEnd == null) {
                return;
            }
            List<CodeReviewChangedRange> postReviewRanges = myPostReviewRanges.get();
            List<CodeReviewChangedRange> ranges = postReviewRanges == null ? List.of() : postReviewRanges;
            DiffLineLocation transferredStart = transferFromAfter(ranges, newStart);
            DiffLineLocation transferredEnd = transferFromAfter(ranges, newEnd);
            getVm().updateLineRange(transferredStart, transferredEnd);
            getVm().requestFocus();
        }

        private static @Nullable DiffLineLocation transferFromAfter(List<CodeReviewChangedRange> ranges, @Nullable Integer line) {
            if (line == null) {
                return null;
            }
            Integer transferred = ReviewInEditorUtil.transferLineFromAfter(ranges, line);
            if (transferred == null || transferred < 0) {
                return null;
            }
            return new DiffLineLocation(Side.RIGHT, transferred);
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
}
