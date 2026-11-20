import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import ProjectGitSyncDialog from '../ProjectGitSyncDialog';

const hoisted = vi.hoisted(() => ({
    deleteMutate: vi.fn(),
    projectGitSync: null as Record<string, unknown> | null,
    pullData: undefined as unknown,
    pullMutate: vi.fn(),
    pushMutate: vi.fn(),
    saveMutate: vi.fn(),
    testData: undefined as unknown,
    testMutate: vi.fn(),
}));

vi.mock('@/shared/middleware/graphql', async (importOriginal) => ({
    ...(await importOriginal<typeof import('@/shared/middleware/graphql')>()),
    useDeleteProjectGitSyncMutation: () => ({isPending: false, mutate: hoisted.deleteMutate}),
    useProjectGitSyncQuery: () => ({
        data: {projectGitSync: hoisted.projectGitSync},
        isLoading: false,
        isSuccess: true,
    }),
    usePullProjectGitSyncMutation: () => ({data: hoisted.pullData, isPending: false, mutate: hoisted.pullMutate}),
    usePushProjectGitSyncMutation: () => ({isPending: false, mutate: hoisted.pushMutate}),
    useSaveProjectGitSyncMutation: () => ({isPending: false, mutate: hoisted.saveMutate}),
    useTestProjectGitSyncConnectionMutation: () => ({
        data: hoisted.testData,
        isPending: false,
        mutate: hoisted.testMutate,
    }),
}));

const storedProjectGitSync = {
    accessTokenSet: true,
    branch: 'main',
    enabled: true,
    lastSyncCommitHash: 'abcdef1234567890',
    lastSyncDate: '2026-01-01T10:00:00Z',
    lastSyncDirection: 'PUSH',
    lastSyncErrorMessage: null,
    lastSyncStatus: 'SUCCESS',
    projectId: '5',
    repositoryUrl: 'https://github.com/acme/projects.git',
    subDirectory: 'sales',
    username: 'octocat',
};

const renderDialog = (onClose = vi.fn()) =>
    render(
        <QueryClientProvider client={new QueryClient()}>
            <ProjectGitSyncDialog onClose={onClose} projectId={5} />
        </QueryClientProvider>
    );

describe('ProjectGitSyncDialog', () => {
    beforeEach(() => {
        hoisted.deleteMutate.mockReset();
        hoisted.projectGitSync = null;
        hoisted.pullData = undefined;
        hoisted.pullMutate.mockReset();
        hoisted.pushMutate.mockReset();
        hoisted.saveMutate.mockReset();
        hoisted.testData = undefined;
        hoisted.testMutate.mockReset();
    });

    it('rejects a non-HTTPS repository URL without saving', async () => {
        const user = userEvent.setup();

        renderDialog();

        await user.type(screen.getByLabelText('Repository URL'), 'http://example.com/repo.git');
        await user.click(screen.getByRole('button', {name: 'Save'}));

        expect(await screen.findByText('Only HTTPS URLs are supported')).toBeInTheDocument();
        expect(hoisted.saveMutate).not.toHaveBeenCalled();
    });

    it('saves a new configuration and leaves the token out when it is empty', async () => {
        const user = userEvent.setup();

        renderDialog();

        await user.type(screen.getByLabelText('Repository URL'), 'https://github.com/acme/projects.git');
        await user.click(screen.getByRole('button', {name: 'Save'}));

        await waitFor(() => expect(hoisted.saveMutate).toHaveBeenCalledTimes(1));

        expect(hoisted.saveMutate.mock.calls[0][0]).toEqual({
            input: {
                branch: 'main',
                enabled: true,
                repositoryUrl: 'https://github.com/acme/projects.git',
                subDirectory: '',
                username: '',
            },
            projectId: '5',
        });
    });

    it('shows a stored placeholder and requires the token again when the URL changes', async () => {
        const user = userEvent.setup();

        hoisted.projectGitSync = storedProjectGitSync;

        renderDialog();

        expect(screen.getByLabelText('Access token')).toHaveAttribute('placeholder', 'stored');

        await user.clear(screen.getByLabelText('Repository URL'));
        await user.type(screen.getByLabelText('Repository URL'), 'https://github.com/acme/other.git');
        await user.click(screen.getByRole('button', {name: 'Save'}));

        expect(await screen.findByText('Enter the access token again when the repository URL changes.')).toBeVisible();
        expect(hoisted.saveMutate).not.toHaveBeenCalled();
    });

    it('sends an empty token to clear the stored one', async () => {
        const user = userEvent.setup();

        hoisted.projectGitSync = storedProjectGitSync;

        renderDialog();

        await user.click(screen.getByRole('checkbox', {name: 'Remove stored access token'}));
        await user.click(screen.getByRole('button', {name: 'Save'}));

        await waitFor(() => expect(hoisted.saveMutate).toHaveBeenCalledTimes(1));

        expect(hoisted.saveMutate.mock.calls[0][0].input.accessToken).toBe('');
    });

    it('tests the connection with the entered values and shows the branches', async () => {
        const user = userEvent.setup();

        hoisted.projectGitSync = storedProjectGitSync;
        hoisted.testData = {
            testProjectGitSyncConnection: {
                branchExists: true,
                branches: ['main', 'dev'],
                errorMessage: null,
                success: true,
            },
        };

        renderDialog();

        await user.click(screen.getByRole('button', {name: 'Test connection'}));

        await waitFor(() => expect(hoisted.testMutate).toHaveBeenCalledTimes(1));

        expect(hoisted.testMutate.mock.calls[0][0].input.repositoryUrl).toBe('https://github.com/acme/projects.git');
        expect(screen.getByText('Connection successful.')).toBeInTheDocument();
        expect(screen.getByText('Branches: main, dev')).toBeInTheDocument();
    });

    it('shows the error of a failed connection test', () => {
        hoisted.projectGitSync = storedProjectGitSync;
        hoisted.testData = {
            testProjectGitSyncConnection: {
                branchExists: false,
                branches: [],
                errorMessage: 'Auth failed',
                success: false,
            },
        };

        renderDialog();

        expect(screen.getByText('Connection failed: Auth failed')).toBeInTheDocument();
    });

    it('shows the last sync state and triggers push and pull', async () => {
        const user = userEvent.setup();

        hoisted.projectGitSync = storedProjectGitSync;

        renderDialog();

        expect(screen.getByText('Success')).toBeInTheDocument();
        expect(screen.getByText('abcdef123456')).toBeInTheDocument();

        await user.click(screen.getByRole('button', {name: 'Push now'}));
        await user.click(screen.getByRole('button', {name: 'Pull'}));

        expect(hoisted.pushMutate).toHaveBeenCalledWith({projectId: '5'});
        expect(hoisted.pullMutate).toHaveBeenCalledWith({projectId: '5'});
    });

    it('summarises the pull result', () => {
        hoisted.projectGitSync = storedProjectGitSync;
        hoisted.pullData = {
            pullProjectGitSync: {
                added: [{label: 'New flow', reason: null, uuid: 'u1'}],
                commitHash: '1234567890abcdef',
                notInGit: [],
                skipped: [{label: null, reason: 'invalid', uuid: 'u2'}],
                unchanged: [],
                updated: [],
            },
        };

        renderDialog();

        expect(screen.getByText('Added: 1')).toBeInTheDocument();
        expect(screen.getByText('New flow')).toBeInTheDocument();
        expect(screen.getByText('Skipped: 1')).toBeInTheDocument();
        expect(screen.getByText('u2 (invalid)')).toBeInTheDocument();
    });

    it('removes the configuration only after a second confirming click', async () => {
        const user = userEvent.setup();

        hoisted.projectGitSync = storedProjectGitSync;

        renderDialog();

        await user.click(screen.getByRole('button', {name: 'Remove configuration'}));

        expect(hoisted.deleteMutate).not.toHaveBeenCalled();

        await user.click(screen.getByRole('button', {name: 'Confirm removal'}));

        expect(hoisted.deleteMutate).toHaveBeenCalledWith({projectId: '5'});
    });

    it('hides push, pull and remove when nothing is configured', () => {
        renderDialog();

        expect(screen.queryByRole('button', {name: 'Push now'})).not.toBeInTheDocument();
        expect(screen.queryByRole('button', {name: 'Remove configuration'})).not.toBeInTheDocument();
    });
});
