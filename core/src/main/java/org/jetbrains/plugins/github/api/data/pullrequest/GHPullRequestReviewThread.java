// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.diff.util.Side;
import consulo.http.graphql.dto.GraphQLFragment;
import consulo.http.graphql.dto.GraphQLNodesDTO;
import consulo.logging.Logger;
import consulo.review.diff.DiscussionsViewOption;
import consulo.review.editor.CodeReviewLineRange;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import git4idea.changes.GitTextFilePatchWithHistory;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@GraphQLFragment("/graphql/fragment/pullRequestReviewThread.graphql")
public class GHPullRequestReviewThread extends GHNode {
    private final boolean isResolved;
    private final boolean isOutdated;
    private final String path;
    private final GHPullRequestReviewThreadSubjectType subjectType;
    @JsonProperty("diffSide")
    private final Side side;
    private final @Nullable Integer line;
    private final @Nullable Integer originalLine;
    @JsonProperty("startDiffSide")
    private final @Nullable Side startSide;
    private final @Nullable Integer startLine;
    private final @Nullable Integer originalStartLine;
    // To be precise: the elements of this list can be null, but should practically never be...
    @JsonProperty("comments")
    private final GraphQLNodesDTO<GHPullRequestReviewComment> commentsNodes;
    private final boolean viewerCanReply;
    private final boolean viewerCanResolve;
    private final boolean viewerCanUnresolve;

    protected GHPullRequestReviewThread() {
        super("");
        this.isResolved = false;
        this.isOutdated = false;
        this.path = "";
        this.subjectType = GHPullRequestReviewThreadSubjectType.LINE;
        this.side = null;
        this.line = null;
        this.originalLine = null;
        this.startSide = null;
        this.startLine = null;
        this.originalStartLine = null;
        this.commentsNodes = new GraphQLNodesDTO<>();
        this.viewerCanReply = false;
        this.viewerCanResolve = false;
        this.viewerCanUnresolve = false;
    }

    public GHPullRequestReviewThread(String id,
                                     boolean isResolved,
                                     boolean isOutdated,
                                     String path,
                                     GHPullRequestReviewThreadSubjectType subjectType,
                                     Side side,
                                     @Nullable Integer line,
                                     @Nullable Integer originalLine,
                                     @Nullable Side startSide,
                                     @Nullable Integer startLine,
                                     @Nullable Integer originalStartLine,
                                     GraphQLNodesDTO<GHPullRequestReviewComment> commentsNodes,
                                     boolean viewerCanReply,
                                     boolean viewerCanResolve,
                                     boolean viewerCanUnresolve) {
        super(id);
        this.isResolved = isResolved;
        this.isOutdated = isOutdated;
        this.path = path;
        this.subjectType = subjectType;
        this.side = side;
        this.line = line;
        this.originalLine = originalLine;
        this.startSide = startSide;
        this.startLine = startLine;
        this.originalStartLine = originalStartLine;
        this.commentsNodes = commentsNodes;
        this.viewerCanReply = viewerCanReply;
        this.viewerCanResolve = viewerCanResolve;
        this.viewerCanUnresolve = viewerCanUnresolve;
    }

    public GHPullRequestReviewThread withComments(List<GHPullRequestReviewComment> comments) {
        return new GHPullRequestReviewThread(getId(), isResolved, isOutdated, path, subjectType, side, line, originalLine, startSide, startLine,
            originalStartLine, new GraphQLNodesDTO<>(comments), viewerCanReply, viewerCanResolve, viewerCanUnresolve);
    }

    public boolean isResolved() {
        return isResolved;
    }

    public boolean isOutdated() {
        return isOutdated;
    }

    public String getPath() {
        return path;
    }

    public GHPullRequestReviewThreadSubjectType getSubjectType() {
        return subjectType;
    }

    public Side getSide() {
        return side;
    }

    public @Nullable Integer getLine() {
        return line;
    }

    public @Nullable Integer getOriginalLine() {
        return originalLine;
    }

    public @Nullable Side getStartSide() {
        return startSide;
    }

    public @Nullable Integer getStartLine() {
        return startLine;
    }

    public @Nullable Integer getOriginalStartLine() {
        return originalStartLine;
    }

    public boolean getViewerCanReply() {
        return viewerCanReply;
    }

    public boolean getViewerCanResolve() {
        return viewerCanResolve;
    }

    public boolean getViewerCanUnresolve() {
        return viewerCanUnresolve;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPullRequestReviewThread that)) {
            return false;
        }
        return Objects.equals(getId(), that.getId())
            && isResolved == that.isResolved
            && isOutdated == that.isOutdated
            && Objects.equals(path, that.path)
            && Objects.equals(subjectType, that.subjectType)
            && Objects.equals(side, that.side)
            && Objects.equals(line, that.line)
            && Objects.equals(originalLine, that.originalLine)
            && Objects.equals(startSide, that.startSide)
            && Objects.equals(startLine, that.startLine)
            && Objects.equals(originalStartLine, that.originalStartLine)
            && Objects.equals(commentsNodes, that.commentsNodes)
            && viewerCanReply == that.viewerCanReply
            && viewerCanResolve == that.viewerCanResolve
            && viewerCanUnresolve == that.viewerCanUnresolve;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), isResolved, isOutdated, path, subjectType, side, line, originalLine, startSide, startLine, originalStartLine,
            commentsNodes, viewerCanReply, viewerCanResolve, viewerCanUnresolve);
    }

    public List<GHPullRequestReviewComment> getComments() {
        return commentsNodes.getNodes();
    }

    private GHPullRequestReviewComment getRoot() {
        return commentsNodes.getNodes().getFirst();
    }

    public GHPullRequestReviewCommentState getState() {
        return getRoot().getState();
    }

    public @Nullable GHCommitHash getCommit() {
        return getRoot().getCommit();
    }

    public @Nullable GHCommitHash getOriginalCommit() {
        return getRoot().getOriginalCommit();
    }

    public @Nullable GHActor getAuthor() {
        return getRoot().getAuthor();
    }

    public Date getCreatedAt() {
        return getRoot().getCreatedAt();
    }

    public String getDiffHunk() {
        return getRoot().getDiffHunk();
    }

    public @Nullable String getReviewId() {
        return getRoot().getReviewId();
    }

    public static boolean isVisible(GHPullRequestReviewThread thread, DiscussionsViewOption viewOption) {
        return switch (viewOption) {
            case ALL -> true;
            case UNRESOLVED_ONLY -> !thread.isResolved();
            case DONT_SHOW -> false;
        };
    }

    private enum StartOrEnd {
        START,
        END
    }

    private record LineOnCommit(String commitSha, int lineIndex) {
    }

    public static @Nullable Integer mapToLeftSideLine(GHPullRequestReviewThread thread, GitTextFilePatchWithHistory diffData) {
        return mapToSidedLine(thread, diffData, Side.LEFT);
    }

    public static @Nullable Integer mapToRightSideLine(GHPullRequestReviewThread thread, GitTextFilePatchWithHistory diffData) {
        return mapToSidedLine(thread, diffData, Side.RIGHT);
    }

    private static @Nullable Integer mapToSidedLine(GHPullRequestReviewThread thread, GitTextFilePatchWithHistory diffData, Side side) {
        LineOnCommit lineOnCommit = lineOnCommit(thread, diffData, StartOrEnd.END, side);
        if (lineOnCommit == null) {
            return null;
        }
        return diffData.forcefullyMapLine(lineOnCommit.commitSha(), lineOnCommit.lineIndex() - 1, side);
    }

    public static @Nullable Pair<DiffLineLocation, DiffLineLocation> mapToRange(GHPullRequestReviewThread thread,
                                                                                GitTextFilePatchWithHistory diffData) {
        return mapToRange(thread, diffData, Side.LEFT);
    }

    public static @Nullable Pair<DiffLineLocation, DiffLineLocation> mapToRange(GHPullRequestReviewThread thread,
                                                                                GitTextFilePatchWithHistory diffData,
                                                                                Side sideBias) {
        DiffLineLocation initialEnd = mapToLocation(thread, diffData, StartOrEnd.END, sideBias);
        if (initialEnd == null) {
            return null;
        }

        // there is no startLine, we are done mapping
        if (thread.getStartLine() == null && thread.getOriginalStartLine() == null) {
            return Pair.create(initialEnd, initialEnd);
        }

        DiffLineLocation initialStart = mapToLocation(thread, diffData, StartOrEnd.START, initialEnd.side());
        if (initialStart == null) {
            return null;
        }
        if (initialStart.side() == initialEnd.side() && initialStart.line() > initialEnd.line()) {
            Logger.getInstance(GHPullRequestReviewThread.class)
                .warn("Invalid comment range lines: " + thread.getStartLine() + ".." + initialEnd.line());
            return null;
        }

        return Pair.create(initialStart, initialEnd);
    }

    private static @Nullable DiffLineLocation mapToLocation(GHPullRequestReviewThread thread,
                                                            GitTextFilePatchWithHistory diffData,
                                                            StartOrEnd startOrEnd,
                                                            Side sideBias) {
        LineOnCommit lineOnCommit = lineOnCommit(thread, diffData, startOrEnd, sideBias);
        if (lineOnCommit == null) {
            return null;
        }
        Pair<Side, Integer> location = diffData.mapLine(lineOnCommit.commitSha(), lineOnCommit.lineIndex() - 1, sideBias);
        return location == null ? null : new DiffLineLocation(location.getFirst(), location.getSecond());
    }

    public static @Nullable CodeReviewLineRange mapToInEditorRange(GHPullRequestReviewThread thread, GitTextFilePatchWithHistory diffData) {
        // already on latest and there's a mapped line, then use that one
        GHCommitHash commit = thread.getCommit();
        Integer line = thread.getLine();
        if (thread.getStartSide() == Side.RIGHT &&
            thread.getSide() == Side.RIGHT &&
            Objects.equals(diffData.getPatch().getAfterVersionId(), commit != null ? commit.getOid() : null) &&
            line != null) {
            Integer startLine = thread.getStartLine();
            int startLineIndex = (startLine != null ? startLine : line) - 1;
            int endLineIndex = line - 1;

            return startLineIndex <= endLineIndex ? new CodeReviewLineRange(startLineIndex, endLineIndex) : null;
        }

        Pair<DiffLineLocation, DiffLineLocation> range = mapToRange(thread, diffData, Side.RIGHT);
        if (range == null) {
            return null;
        }
        DiffLineLocation start = range.getFirst();
        DiffLineLocation end = range.getSecond();
        if (start.side() == Side.RIGHT && end.side() == Side.RIGHT && start.line() <= end.line()) {
            return new CodeReviewLineRange(start.line(), end.line());
        }
        return null;
    }

    /**
     * @param sideBias Indicates what side we would prefer the lines to be mapped to.
     * We choose the line and commit that are closest to the preferred side.
     */
    private static @Nullable LineOnCommit lineOnCommit(GHPullRequestReviewThread thread,
                                                       GitTextFilePatchWithHistory diffData,
                                                       StartOrEnd startOrEnd,
                                                       Side sideBias) {
        Integer unmappedLine = startOrEnd == StartOrEnd.END ? thread.getLine() : thread.getStartLine();
        Integer unmappedOriginalLine = startOrEnd == StartOrEnd.END ? thread.getOriginalLine() : thread.getOriginalStartLine();
        Side startSide = thread.getStartSide();
        Side threadSide = startOrEnd == StartOrEnd.END || startSide == null ? thread.getSide() : startSide;

        GHCommitHash originalCommit = thread.getOriginalCommit();
        GHCommitHash commit = thread.getCommit();
        String originalCommitSha = originalCommit != null ? originalCommit.getOid() : null;
        String commitSha = commit != null ? commit.getOid() : null;
        Supplier<@Nullable LineOnCommit> mapOriginalLine = () ->
            toLineOnCommit(thread.getPath(), diffData, threadSide, originalCommitSha, unmappedOriginalLine);
        Supplier<@Nullable LineOnCommit> mapLine = () -> toLineOnCommit(thread.getPath(), diffData, threadSide, commitSha, unmappedLine);

        if (sideBias == Side.LEFT) {
            LineOnCommit original = mapOriginalLine.get();
            return original != null ? original : mapLine.get();
        }
        else {
            LineOnCommit mapped = mapLine.get();
            return mapped != null ? mapped : mapOriginalLine.get();
        }
    }

    private static @Nullable LineOnCommit toLineOnCommit(String file,
                                                         GitTextFilePatchWithHistory diffData,
                                                         @Nullable Side side,
                                                         @Nullable String commitSha,
                                                         @Nullable Integer lineIndex) {
        if (side == null || commitSha == null || lineIndex == null) {
            return null;
        }

        if (!diffData.contains(commitSha, file)) {
            return null;
        }

        String sha;
        if (side == Side.RIGHT) {
            sha = commitSha;
        }
        else {
            sha = diffData.isCumulative() ? diffData.getFileHistory().findStartCommit() : diffData.getPatch().getBeforeVersionId();
            if (sha == null) {
                return null;
            }
        }
        return new LineOnCommit(sha, lineIndex);
    }
}
