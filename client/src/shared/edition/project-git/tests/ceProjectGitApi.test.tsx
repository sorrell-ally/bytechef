import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {renderHook} from '@testing-library/react';
import {ReactNode} from 'react';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {ceProjectGitApi} from '../ceProjectGitApi';

const hoisted = vi.hoisted(() => ({
    pullMutate: vi.fn(),
    saveMutate: vi.fn(),
    workspaceQueryOptions: vi.fn(),
}));

vi.mock('@/shared/middleware/graphql', () => ({
    useProjectGitSyncQuery: (variables: {projectId: string}) => ({
        data: {
            projectGitSync: variables.projectId === '5' ? {branch: 'main', enabled: true, projectId: '5'} : null,
        },
    }),
    usePullProjectGitSyncMutation: () => ({isPending: false, mutate: hoisted.pullMutate}),
    useSaveProjectGitSyncMutation: () => ({mutate: hoisted.saveMutate}),
    useWorkspaceProjectGitSyncsQuery: (_variables: unknown, options: unknown) => {
        hoisted.workspaceQueryOptions(options);

        return {
            data: {workspaceProjectGitSyncs: [{branch: 'dev', enabled: false, projectId: '8'}]},
            error: null,
            isLoading: false,
        };
    },
}));

const wrapper = ({children}: {children: ReactNode}) => (
    <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>
);

describe('ceProjectGitApi', () => {
    beforeEach(() => {
        hoisted.pullMutate.mockReset();
        hoisted.saveMutate.mockReset();
        hoisted.workspaceQueryOptions.mockReset();
    });

    it('maps a project git sync to a git configuration', () => {
        const {result} = renderHook(() => ceProjectGitApi.useProjectGitConfigurationQuery(5), {wrapper});

        expect(result.current.data).toEqual({branch: 'main', enabled: true, projectId: 5});
    });

    it('returns undefined data when the project has no git sync', () => {
        const {result} = renderHook(() => ceProjectGitApi.useProjectGitConfigurationQuery(6), {wrapper});

        expect(result.current.data).toBeUndefined();
    });

    it('maps the workspace git syncs and forwards the enabled flag', () => {
        const {result} = renderHook(() => ceProjectGitApi.useWorkspaceProjectGitConfigurationsQuery(3, false), {
            wrapper,
        });

        expect(result.current.data).toEqual([{branch: 'dev', enabled: false, projectId: 8}]);
        expect(hoisted.workspaceQueryOptions).toHaveBeenCalledWith({enabled: false});
    });

    it('pulls with a string project id', () => {
        const {result} = renderHook(() => ceProjectGitApi.usePullProjectFromGitMutation({}), {wrapper});

        result.current.mutate({id: 5});

        expect(hoisted.pullMutate).toHaveBeenCalledWith({projectId: '5'});
    });

    it('saves only branch and enabled so the other stored settings are kept', () => {
        const {result} = renderHook(() => ceProjectGitApi.useUpdateProjectGitConfigurationMutation({}), {wrapper});

        result.current.mutate({id: 5, projectGitConfiguration: {branch: 'release', enabled: true}});

        expect(hoisted.saveMutate).toHaveBeenCalledWith(
            {input: {branch: 'release', enabled: true}, projectId: '5'},
            expect.any(Object)
        );
    });
});
