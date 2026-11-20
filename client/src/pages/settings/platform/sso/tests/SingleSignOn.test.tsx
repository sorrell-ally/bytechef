import SingleSignOn from '@/pages/settings/platform/sso/SingleSignOn';
import {render, resetAll, screen} from '@/shared/util/test-utils';
import {afterEach, describe, expect, it, vi} from 'vitest';

const hoisted = vi.hoisted(() => ({
    query: {data: undefined, error: null, isLoading: false} as Record<string, unknown>,
}));

vi.mock('@/shared/middleware/graphql', async (importOriginal) => ({
    ...(await importOriginal<typeof import('@/shared/middleware/graphql')>()),
    useDeleteSsoProviderMutation: () => ({isPending: false, mutate: vi.fn()}),
    useSsoProvidersQuery: () => hoisted.query,
}));

describe('SingleSignOn', () => {
    afterEach(() => {
        resetAll();
    });

    it('explains the server property instead of failing when the feature is disabled', () => {
        hoisted.query = {
            data: undefined,
            error: new Error(
                "Validation error (FieldUndefined@[ssoProviders]) : Field 'ssoProviders' in type 'Query' is undefined"
            ),
            isLoading: false,
        };

        render(<SingleSignOn />);

        expect(screen.getByText('Single sign-on is not enabled')).toBeInTheDocument();
        expect(screen.getAllByText(/bytechef\.security\.sso\.enabled/).length).toBeGreaterThan(0);
        expect(screen.queryByText('Some error occurred.')).not.toBeInTheDocument();
    });

    it('shows an empty state with a create action when there are no providers', () => {
        hoisted.query = {data: {ssoProviders: []}, error: null, isLoading: false};

        render(<SingleSignOn />);

        expect(screen.getByText('No single sign-on providers yet')).toBeInTheDocument();
    });

    it('lists the providers', () => {
        hoisted.query = {
            data: {
                ssoProviders: [
                    {
                        domains: ['acme.com'],
                        enabled: true,
                        enforceSso: true,
                        id: '1',
                        name: 'Acme Entra',
                        type: 'OIDC',
                        vendor: 'MICROSOFT_ENTRA_ID',
                    },
                ],
            },
            error: null,
            isLoading: false,
        };

        render(<SingleSignOn />);

        expect(screen.getByText('Acme Entra')).toBeInTheDocument();
        expect(screen.getByText('Microsoft Entra ID')).toBeInTheDocument();
        expect(screen.getByText('SSO enforced')).toBeInTheDocument();
    });
});
