import {PullProjectGitSyncMutation} from '@/shared/middleware/graphql';

interface ProjectGitSyncPullResultProps {
    pullResult: PullProjectGitSyncMutation['pullProjectGitSync'];
}

const ProjectGitSyncPullResult = ({pullResult}: ProjectGitSyncPullResultProps) => {
    const groups = [
        {items: pullResult.added, title: 'Added'},
        {items: pullResult.updated, title: 'Updated'},
        {items: pullResult.unchanged, title: 'Unchanged'},
        {items: pullResult.skipped, title: 'Skipped'},
        {items: pullResult.notInGit, title: 'Not in Git'},
    ];

    return (
        <section aria-label="Pull result" className="rounded-md border p-3 text-sm">
            <h3 className="font-medium">Pull result</h3>

            <p className="font-mono text-xs">Commit {pullResult.commitHash.substring(0, 12)}</p>

            <ul className="mt-1 space-y-1">
                {groups.map((group) => (
                    <li key={group.title}>
                        <span className="font-medium">
                            {group.title}: {group.items.length}
                        </span>

                        {group.items.length > 0 && (
                            <ul className="ml-4 list-disc">
                                {group.items.map((workflow) => (
                                    <li key={workflow.uuid}>
                                        {workflow.label || workflow.uuid}

                                        {workflow.reason && ` (${workflow.reason})`}
                                    </li>
                                ))}
                            </ul>
                        )}
                    </li>
                ))}
            </ul>
        </section>
    );
};

export default ProjectGitSyncPullResult;
