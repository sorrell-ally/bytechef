import {ProjectGitSyncQuery} from '@/shared/middleware/graphql';

interface ProjectGitSyncLastSyncProps {
    projectGitSync: NonNullable<ProjectGitSyncQuery['projectGitSync']>;
}

const STATUS_LABELS: Record<string, string> = {
    FAILED: 'Failed',
    NO_CHANGES: 'No changes',
    SKIPPED: 'Skipped',
    SUCCESS: 'Success',
};

const ProjectGitSyncLastSync = ({projectGitSync}: ProjectGitSyncLastSyncProps) => {
    const {lastSyncCommitHash, lastSyncDate, lastSyncDirection, lastSyncErrorMessage, lastSyncStatus} = projectGitSync;

    return (
        <section aria-label="Last sync" className="rounded-md border p-3 text-sm">
            <h3 className="font-medium">Last sync</h3>

            {lastSyncStatus ? (
                <dl className="mt-1 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                    <dt>Status</dt>

                    <dd>{STATUS_LABELS[lastSyncStatus] ?? lastSyncStatus}</dd>

                    {lastSyncDirection && (
                        <>
                            <dt>Direction</dt>

                            <dd>{lastSyncDirection === 'PUSH' ? 'Push' : 'Pull'}</dd>
                        </>
                    )}

                    {lastSyncDate && (
                        <>
                            <dt>Date</dt>

                            <dd>{new Date(lastSyncDate).toLocaleString()}</dd>
                        </>
                    )}

                    {lastSyncCommitHash && (
                        <>
                            <dt>Commit</dt>

                            <dd className="font-mono">{lastSyncCommitHash.substring(0, 12)}</dd>
                        </>
                    )}

                    {lastSyncErrorMessage && (
                        <>
                            <dt>Error</dt>

                            <dd className="text-content-destructive-primary">{lastSyncErrorMessage}</dd>
                        </>
                    )}
                </dl>
            ) : (
                <p className="text-content-neutral-secondary">Not synced yet.</p>
            )}
        </section>
    );
};

export default ProjectGitSyncLastSync;
