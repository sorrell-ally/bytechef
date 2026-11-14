/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytechef.automation.configuration.gitsync.git;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.EmptyProgressMonitor;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-project Git operations on a remote branch, implemented with JGit.
 *
 * <p>
 * Every call works in its own throw-away bare repository in a fresh temporary directory, which is always deleted
 * afterwards, so concurrent calls never share state. Nothing is checked out: a push builds the new tree in an in-memory
 * index from the fetched tip and edits only paths inside the project directory, and a pull reads blobs straight from
 * the fetched tree. No path read from or written to the repository is ever resolved against the local file system,
 * which rules out path traversal and symlink tricks by construction.
 *
 * <p>
 * Fetches are shallow ({@code depth=1}), single-branch, without tags, with object checking enabled, bounded by a
 * wall-clock deadline and by a maximum fetched size. Redirects are disabled. The access token is supplied only through
 * a {@link CredentialsProvider} and is redacted from every error message.
 *
 * <p>
 * Callers are responsible for validating the URL ({@link ProjectGitSyncUrlValidator}), the branch and the project
 * directory ({@link ProjectGitSyncPaths}) and for serializing operations of the same project.
 */
public class ProjectGitSyncClient {

    private static final Logger log = LoggerFactory.getLogger(ProjectGitSyncClient.class);

    private static final String REMOTE_NAME = "origin";

    private final ProjectGitSyncLimits limits;

    public ProjectGitSyncClient(ProjectGitSyncLimits limits) {
        this.limits = limits;
    }

    /**
     * Lists the branch names of the remote repository; used to test a connection and credentials.
     */
    public List<String> listBranches(ProjectGitSyncRemote remote) {
        return execute(remote, (repository, monitor) -> {
            List<String> branches = new ArrayList<>();

            for (Ref ref : listRemoteHeads(repository, remote)) {
                String name = ref.getName();

                branches.add(name.substring(Constants.R_HEADS.length()));
            }

            branches.sort(null);

            return branches;
        });
    }

    /**
     * Commits the given files into the project directory of the branch and pushes. Only paths inside
     * {@code projectDirectory} are touched: files in {@code files} are created or replaced, and files whose relative
     * path is accepted by {@code managedFilePredicate} but which are absent from {@code files} are deleted. Every other
     * file in the repository is left unchanged. A missing branch (including an empty repository) is created. When the
     * resulting tree equals the current tip, nothing is committed.
     *
     * @param projectDirectory     the normalized project directory ({@code ""} for the repository root)
     * @param files                UTF-8 contents keyed by path relative to the project directory
     * @param managedFilePredicate decides which existing files (relative paths) this sync owns and may delete
     */
    public ProjectGitSyncPushResult push(
        ProjectGitSyncRemote remote, String projectDirectory, Map<String, String> files,
        Predicate<String> managedFilePredicate, String message, PersonIdent author) {

        int maxPushAttempts = Math.max(1, limits.maxPushAttempts());

        for (int attempt = 1; attempt <= maxPushAttempts; attempt++) {
            PushAttemptResult pushAttemptResult = execute(
                remote, (repository, monitor) -> pushAttempt(
                    repository, monitor, remote, projectDirectory, files, managedFilePredicate, message, author));

            if (!pushAttemptResult.rejectedAsStale()) {
                return Objects.requireNonNull(pushAttemptResult.pushResult());
            }

            log.debug(
                "Push to branch '{}' was rejected because it moved, attempt {} of {}", remote.branch(), attempt,
                maxPushAttempts);

            backOff(attempt);
        }

        throw new ProjectGitSyncException(
            "Push was rejected because branch '" + remote.branch() + "' kept changing; try again later");
    }

    /**
     * Reads the files of the project directory from the branch tip.
     *
     * @param projectDirectory the normalized project directory ({@code ""} for the repository root)
     * @param fileFilter       accepts the relative paths to read; everything else is ignored without being loaded
     */
    public ProjectGitSyncSnapshot read(
        ProjectGitSyncRemote remote, String projectDirectory, Predicate<String> fileFilter) {

        return execute(remote, (repository, monitor) -> {
            ObjectId tipId = fetchBranch(repository, monitor, remote);

            if (tipId == null) {
                throw new ProjectGitSyncException(
                    "Branch '" + remote.branch() + "' does not exist in the repository");
            }

            return readTree(repository, tipId, projectDirectory, fileFilter);
        });
    }

    private PushAttemptResult pushAttempt(
        Repository repository, ProgressMonitor monitor, ProjectGitSyncRemote remote, String projectDirectory,
        Map<String, String> files, Predicate<String> managedFilePredicate, String message, PersonIdent author)
        throws IOException, GitAPIException {

        ObjectId tipId = fetchBranch(repository, monitor, remote);

        ObjectId commitId;

        try (ObjectInserter objectInserter = repository.newObjectInserter();
            ObjectReader objectReader = repository.newObjectReader();
            RevWalk revWalk = new RevWalk(objectReader)) {

            DirCache dirCache = DirCache.newInCore();

            ObjectId tipTreeId = null;

            if (tipId != null) {
                RevCommit tipCommit = revWalk.parseCommit(tipId);

                tipTreeId = tipCommit.getTree();

                DirCacheBuilder dirCacheBuilder = dirCache.builder();

                dirCacheBuilder.addTree(new byte[0], DirCacheEntry.STAGE_0, objectReader, tipTreeId);

                dirCacheBuilder.finish();
            }

            applyProjectFiles(dirCache, objectInserter, projectDirectory, files, managedFilePredicate);

            ObjectId treeId = dirCache.writeTree(objectInserter);

            if (tipId != null && treeId.equals(tipTreeId)) {
                return new PushAttemptResult(new ProjectGitSyncPushResult(tipId.name(), false), false);
            }

            CommitBuilder commitBuilder = new CommitBuilder();

            commitBuilder.setAuthor(author);
            commitBuilder.setCommitter(author);
            commitBuilder.setMessage(message);
            commitBuilder.setTreeId(treeId);

            if (tipId != null) {
                commitBuilder.setParentId(tipId);
            }

            commitId = objectInserter.insert(commitBuilder);

            objectInserter.flush();
        }

        String branchRef = Constants.R_HEADS + remote.branch();

        RefUpdate refUpdate = repository.updateRef(branchRef);

        refUpdate.setNewObjectId(commitId);
        refUpdate.setForceUpdate(true);

        RefUpdate.Result refUpdateResult = refUpdate.update();

        if (refUpdateResult != RefUpdate.Result.NEW && refUpdateResult != RefUpdate.Result.FORCED &&
            refUpdateResult != RefUpdate.Result.FAST_FORWARD) {

            throw new ProjectGitSyncException("Failed to prepare the local commit: " + refUpdateResult);
        }

        Iterable<PushResult> pushResults;

        try (Git git = Git.wrap(repository)) {
            pushResults = git.push()
                .setRemote(REMOTE_NAME)
                .setRefSpecs(new RefSpec(branchRef + ":" + branchRef))
                .setCredentialsProvider(getCredentialsProvider(remote))
                .setTimeout(getTimeoutSeconds())
                .setProgressMonitor(monitor)
                .call();
        }

        for (PushResult pushResult : pushResults) {
            RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdate(branchRef);

            if (remoteRefUpdate == null) {
                continue;
            }

            RemoteRefUpdate.Status status = remoteRefUpdate.getStatus();

            switch (status) {
                case OK, UP_TO_DATE -> {
                    return new PushAttemptResult(new ProjectGitSyncPushResult(commitId.name(), true), false);
                }
                case REJECTED_NONFASTFORWARD, REJECTED_REMOTE_CHANGED -> {
                    return new PushAttemptResult(null, true);
                }
                case REJECTED_OTHER_REASON -> {
                    String rejectionMessage = remoteRefUpdate.getMessage();

                    // a concurrent update holding the ref lock on the server is as transient as a non-fast-forward
                    if (rejectionMessage != null && rejectionMessage.contains("lock")) {
                        return new PushAttemptResult(null, true);
                    }

                    throw new ProjectGitSyncException(
                        "Push was rejected: " + ProjectGitSyncRedactor.redact(rejectionMessage, remote.accessToken()));
                }
                default -> throw new ProjectGitSyncException(
                    "Push was rejected (" + status + "): " +
                        ProjectGitSyncRedactor.redact(remoteRefUpdate.getMessage(), remote.accessToken()));
            }
        }

        throw new ProjectGitSyncException("Push returned no result for branch '" + remote.branch() + "'");
    }

    private static void applyProjectFiles(
        DirCache dirCache, ObjectInserter objectInserter, String projectDirectory, Map<String, String> files,
        Predicate<String> managedFilePredicate) throws IOException {

        String prefix = projectDirectory.isEmpty() ? "" : projectDirectory + "/";

        if (!projectDirectory.isEmpty()) {
            checkNoFileOnDirectoryPath(dirCache, projectDirectory);
        }

        DirCacheEditor dirCacheEditor = dirCache.editor();

        for (int index = 0; index < dirCache.getEntryCount(); index++) {
            DirCacheEntry dirCacheEntry = dirCache.getEntry(index);

            String path = dirCacheEntry.getPathString();

            if (!path.startsWith(prefix)) {
                continue;
            }

            String relativePath = path.substring(prefix.length());

            if (!files.containsKey(relativePath) && managedFilePredicate.test(relativePath)) {
                dirCacheEditor.add(new DeletePath(path));
            }
        }

        for (Map.Entry<String, String> fileEntry : new TreeMap<>(files).entrySet()) {
            String path = ProjectGitSyncPaths.join(projectDirectory, fileEntry.getKey());

            checkNoFileOnDirectoryPath(dirCache, path.substring(0, Math.max(0, path.lastIndexOf('/'))));

            DirCacheEntry existingDirCacheEntry = dirCache.getEntry(path);

            if (existingDirCacheEntry != null) {
                FileMode fileMode = existingDirCacheEntry.getFileMode();

                if (fileMode != FileMode.REGULAR_FILE && fileMode != FileMode.EXECUTABLE_FILE) {
                    throw new ProjectGitSyncException(
                        "Cannot write '" + path + "' because the repository contains a non-regular file there");
                }
            }

            if (dirCache.getEntriesWithin(path).length > 0) {

                throw new ProjectGitSyncException(
                    "Cannot write '" + path + "' because the repository contains a directory there");
            }

            String content = fileEntry.getValue();

            ObjectId blobId = objectInserter.insert(Constants.OBJ_BLOB, content.getBytes(StandardCharsets.UTF_8));

            dirCacheEditor.add(new PathEdit(path) {

                @Override
                public void apply(DirCacheEntry dirCacheEntry) {
                    dirCacheEntry.setFileMode(FileMode.REGULAR_FILE);
                    dirCacheEntry.setObjectId(blobId);
                }
            });
        }

        dirCacheEditor.finish();
    }

    /**
     * Fails when any ancestor of (or the) given directory path is a file, symlink or submodule entry, because writing
     * below it would make JGit silently replace that unrelated entry.
     */
    private static void checkNoFileOnDirectoryPath(DirCache dirCache, String directoryPath) {
        if (directoryPath.isEmpty()) {
            return;
        }

        String[] segments = directoryPath.split("/");
        StringBuilder currentPath = new StringBuilder();

        for (String segment : segments) {
            if (!currentPath.isEmpty()) {
                currentPath.append('/');
            }

            currentPath.append(segment);

            if (dirCache.findEntry(currentPath.toString()) >= 0) {
                throw new ProjectGitSyncException(
                    "Cannot write into '" + directoryPath + "' because '" + currentPath +
                        "' is a file in the repository");
            }
        }
    }

    private ProjectGitSyncSnapshot readTree(
        Repository repository, ObjectId tipId, String projectDirectory, Predicate<String> fileFilter)
        throws IOException {

        Map<String, String> files = new TreeMap<>();

        try (ObjectReader objectReader = repository.newObjectReader();
            RevWalk revWalk = new RevWalk(objectReader)) {

            RevCommit tipCommit = revWalk.parseCommit(tipId);

            ObjectId directoryTreeId = tipCommit.getTree();

            if (!projectDirectory.isEmpty()) {
                try (TreeWalk directoryTreeWalk = TreeWalk.forPath(
                    objectReader, projectDirectory, tipCommit.getTree())) {

                    if (directoryTreeWalk == null) {
                        return new ProjectGitSyncSnapshot(tipId.name(), false, files);
                    }

                    if (directoryTreeWalk.getFileMode(0) != FileMode.TREE) {
                        throw new ProjectGitSyncException(
                            "'" + projectDirectory + "' is not a directory in the repository");
                    }

                    directoryTreeId = directoryTreeWalk.getObjectId(0);
                }
            }

            long totalBytes = 0;

            try (TreeWalk treeWalk = new TreeWalk(objectReader)) {
                treeWalk.addTree(directoryTreeId);
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    String relativePath = treeWalk.getPathString();

                    if (!fileFilter.test(relativePath)) {
                        continue;
                    }

                    FileMode fileMode = treeWalk.getFileMode(0);

                    if (fileMode != FileMode.REGULAR_FILE && fileMode != FileMode.EXECUTABLE_FILE) {
                        log.debug("Ignoring non-regular entry '{}' in the project directory", relativePath);

                        continue;
                    }

                    if (files.size() >= limits.maxFiles()) {
                        throw new ProjectGitSyncException(
                            "The project directory contains more than " + limits.maxFiles() + " files");
                    }

                    ObjectLoader objectLoader = objectReader.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB);

                    long size = objectLoader.getSize();

                    if (size > limits.maxFileBytes()) {
                        throw new ProjectGitSyncException(
                            "File '" + relativePath + "' exceeds the maximum size of " + limits.maxFileBytes() +
                                " bytes");
                    }

                    totalBytes += size;

                    if (totalBytes > limits.maxTotalFileBytes()) {
                        throw new ProjectGitSyncException(
                            "The project files exceed the maximum total size of " + limits.maxTotalFileBytes() +
                                " bytes");
                    }

                    byte[] bytes;

                    try {
                        bytes = objectLoader.getCachedBytes(Math.toIntExact(limits.maxFileBytes()));
                    } catch (LargeObjectException largeObjectException) {
                        throw new ProjectGitSyncException(
                            "File '" + relativePath + "' exceeds the maximum size of " + limits.maxFileBytes() +
                                " bytes");
                    }

                    files.put(relativePath, new String(bytes, StandardCharsets.UTF_8));
                }
            }
        }

        return new ProjectGitSyncSnapshot(tipId.name(), true, files);
    }

    /**
     * @return the fetched tip of the branch, or {@code null} when the branch does not exist (or the repository is
     *         empty)
     */
    private @Nullable ObjectId fetchBranch(
        Repository repository, ProgressMonitor monitor, ProjectGitSyncRemote remote)
        throws IOException, GitAPIException {

        String branchRef = Constants.R_HEADS + remote.branch();

        boolean branchExists = false;

        for (Ref ref : listRemoteHeads(repository, remote)) {
            if (branchRef.equals(ref.getName())) {
                branchExists = true;

                break;
            }
        }

        if (!branchExists) {
            return null;
        }

        String trackingRef = Constants.R_REMOTES + REMOTE_NAME + "/" + remote.branch();

        try (Git git = Git.wrap(repository)) {
            git.fetch()
                .setRemote(REMOTE_NAME)
                .setRefSpecs(new RefSpec("+" + branchRef + ":" + trackingRef))
                .setDepth(1)
                .setTagOpt(TagOpt.NO_TAGS)
                .setCredentialsProvider(getCredentialsProvider(remote))
                .setTimeout(getTimeoutSeconds())
                .setProgressMonitor(monitor)
                .call();
        }

        checkRepositorySize(repository);

        ObjectId tipId = repository.resolve(trackingRef);

        if (tipId == null) {
            throw new ProjectGitSyncException("Branch '" + remote.branch() + "' could not be fetched");
        }

        return tipId;
    }

    private Collection<Ref> listRemoteHeads(Repository repository, ProjectGitSyncRemote remote)
        throws GitAPIException {

        try (Git git = Git.wrap(repository)) {
            return git.lsRemote()
                .setRemote(REMOTE_NAME)
                .setHeads(true)
                .setCredentialsProvider(getCredentialsProvider(remote))
                .setTimeout(getTimeoutSeconds())
                .call();
        }
    }

    private void checkRepositorySize(Repository repository) throws IOException {
        File directory = repository.getDirectory();

        long size;

        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            size = paths.filter(Files::isRegularFile)
                .mapToLong(ProjectGitSyncClient::getFileSize)
                .sum();
        }

        if (size > limits.maxRepositoryBytes()) {
            throw new ProjectGitSyncException(
                "The fetched branch exceeds the maximum size of " + limits.maxRepositoryBytes() + " bytes");
        }
    }

    @SuppressFBWarnings(value = "PREDICTABLE_RANDOM", justification = "Retry jitter only, not security relevant")
    private static void backOff(int attempt) {
        long delayMillis = ThreadLocalRandom.current()
            .nextLong(50, 250) * attempt;

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread()
                .interrupt();

            throw new ProjectGitSyncException("Interrupted while retrying the push");
        }
    }

    private static long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ioException) {
            return 0;
        }
    }

    private <T> T execute(ProjectGitSyncRemote remote, RepositoryOperation<T> repositoryOperation) {
        Path temporaryDirectory;

        try {
            temporaryDirectory = Files.createTempDirectory("bytechef-project-git-sync-");
        } catch (IOException ioException) {
            throw new ProjectGitSyncException("Failed to create a temporary directory");
        }

        try (Repository repository = createRepository(temporaryDirectory, remote.url())) {
            DeadlineProgressMonitor deadlineProgressMonitor = new DeadlineProgressMonitor(limits.operationTimeout());

            return repositoryOperation.execute(repository, deadlineProgressMonitor);
        } catch (ProjectGitSyncException projectGitSyncException) {
            throw projectGitSyncException;
        } catch (GitAPIException | IOException | JGitInternalException exception) {
            String description = ProjectGitSyncRedactor.describe(exception, remote.accessToken());

            log.debug("Git operation on branch '{}' failed: {}", remote.branch(), description);

            throw new ProjectGitSyncException("Git operation failed: " + description);
        } finally {
            deleteDirectory(temporaryDirectory);
        }
    }

    private static Repository createRepository(Path directory, String url) throws IOException {
        Repository repository = new FileRepositoryBuilder()
            .setGitDir(directory.toFile())
            .setBare()
            .build();

        repository.create(true);

        StoredConfig storedConfig = repository.getConfig();

        storedConfig.setString("remote", REMOTE_NAME, "url", url);
        storedConfig.setString("http", null, "followRedirects", "false");
        storedConfig.setBoolean("transfer", null, "fsckObjects", true);
        storedConfig.setBoolean("commit", null, "gpgSign", false);

        storedConfig.save();

        return repository;
    }

    private static void deleteDirectory(Path directory) {
        try {
            FileUtils.delete(
                directory.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY | FileUtils.SKIP_MISSING);
        } catch (IOException ioException) {
            log.warn("Failed to delete temporary Git directory {}", directory);
        }
    }

    private static @Nullable CredentialsProvider getCredentialsProvider(ProjectGitSyncRemote remote) {
        String accessToken = remote.accessToken();

        if (accessToken == null || accessToken.isEmpty()) {
            return null;
        }

        String username = remote.username();

        return new UsernamePasswordCredentialsProvider(
            username == null || username.isBlank() ? ProjectGitSyncRemote.DEFAULT_USERNAME : username, accessToken);
    }

    private int getTimeoutSeconds() {
        Duration operationTimeout = limits.operationTimeout();

        return (int) Math.max(1, operationTimeout.toSeconds());
    }

    @FunctionalInterface
    private interface RepositoryOperation<T> {

        T execute(Repository repository, ProgressMonitor monitor) throws IOException, GitAPIException;
    }

    private record PushAttemptResult(@Nullable ProjectGitSyncPushResult pushResult, boolean rejectedAsStale) {
    }

    /**
     * Cancels a running fetch or push once the wall-clock budget is spent; JGit polls {@link #isCancelled()}.
     */
    private static final class DeadlineProgressMonitor extends EmptyProgressMonitor {

        private final long deadlineNanos;

        private DeadlineProgressMonitor(Duration timeout) {
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        }

        @Override
        public boolean isCancelled() {
            return System.nanoTime() - deadlineNanos > 0;
        }
    }
}
