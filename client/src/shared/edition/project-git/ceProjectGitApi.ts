import {
    ProjectGitApiI,
    ProjectGitConfigurationI,
    registerCeProjectGitApi,
} from '@/shared/edition/project-git/projectGitApi';
import {
    ProjectGitSyncQuery,
    useProjectGitSyncQuery,
    usePullProjectGitSyncMutation,
    useSaveProjectGitSyncMutation,
    useWorkspaceProjectGitSyncsQuery,
} from '@/shared/middleware/graphql';
import {useQueryClient} from '@tanstack/react-query';

type ProjectGitSyncType = NonNullable<ProjectGitSyncQuery['projectGitSync']>;

function toProjectGitConfiguration(projectGitSync: ProjectGitSyncType): ProjectGitConfigurationI {
    return {
        branch: projectGitSync.branch,
        enabled: projectGitSync.enabled,
        projectId: Number(projectGitSync.projectId),
    };
}

export const ceProjectGitApi: ProjectGitApiI = {
    useProjectGitConfigurationQuery: (projectId) => {
        const {data} = useProjectGitSyncQuery({projectId: String(projectId)});

        return {data: data?.projectGitSync ? toProjectGitConfiguration(data.projectGitSync) : undefined};
    },

    usePullProjectFromGitMutation: ({onSuccess}) => {
        const queryClient = useQueryClient();

        const pullProjectGitSyncMutation = usePullProjectGitSyncMutation({
            onSuccess: () => {
                void queryClient.invalidateQueries({queryKey: ['projectGitSync']});
                void queryClient.invalidateQueries({queryKey: ['workspaceProjectGitSyncs']});

                onSuccess?.();
            },
        });

        return {
            isPending: pullProjectGitSyncMutation.isPending,
            mutate: ({id}) => pullProjectGitSyncMutation.mutate({projectId: String(id)}),
        };
    },

    useUpdateProjectGitConfigurationMutation: ({onSuccess}) => {
        const queryClient = useQueryClient();

        const saveProjectGitSyncMutation = useSaveProjectGitSyncMutation();

        return {
            mutate: ({id, projectGitConfiguration}, callbacks) =>
                saveProjectGitSyncMutation.mutate(
                    {
                        input: {branch: projectGitConfiguration.branch, enabled: projectGitConfiguration.enabled},
                        projectId: String(id),
                    },
                    {
                        onSuccess: () => {
                            void queryClient.invalidateQueries({queryKey: ['projectGitSync']});
                            void queryClient.invalidateQueries({queryKey: ['workspaceProjectGitSyncs']});

                            onSuccess?.();
                            callbacks?.onSuccess?.();
                        },
                    }
                ),
        };
    },

    useWorkspaceProjectGitConfigurationsQuery: (workspaceId, enabled) => {
        const {data, error, isLoading} = useWorkspaceProjectGitSyncsQuery(
            {workspaceId: String(workspaceId)},
            {enabled}
        );

        return {
            data: data?.workspaceProjectGitSyncs.map(toProjectGitConfiguration),
            error: error as Error | null,
            isLoading,
        };
    },
};

/**
 * Registers the Community Edition Git API. Only called by `registerCeEditionModules`, which the application
 * entry point loads when the edition is positively CE.
 */
export function registerCeProjectGit() {
    registerCeProjectGitApi(ceProjectGitApi);
}
