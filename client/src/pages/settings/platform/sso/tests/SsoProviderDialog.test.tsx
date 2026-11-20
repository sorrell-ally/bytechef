import SsoProviderDialog from '@/pages/settings/platform/sso/components/SsoProviderDialog';
import {SsoProviderType, SsoProviderVendor} from '@/shared/middleware/graphql';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';

const hoisted = vi.hoisted(() => ({
    createMutate: vi.fn(),
    updateMutate: vi.fn(),
}));

vi.mock('@/shared/middleware/graphql', async (importOriginal) => ({
    ...(await importOriginal<typeof import('@/shared/middleware/graphql')>()),
    useCreateSsoProviderMutation: () => ({isPending: false, mutate: hoisted.createMutate}),
    useUpdateSsoProviderMutation: () => ({isPending: false, mutate: hoisted.updateMutate}),
}));

const TENANT_ID = '0f8fad5b-d9cb-469f-a165-70867728950e';

const storedOidcProvider = {
    autoProvision: false,
    callbackPath: '/login/oauth2/code/sso-0123',
    clientId: 'client-1',
    clientSecretSet: true,
    defaultAuthority: 'ROLE_USER',
    domains: ['acme.com'],
    enabled: true,
    enforceSso: false,
    id: '7',
    issuerUri: 'https://idp.example.com/realm',
    loginPath: '/oauth2/authorization/sso-0123',
    metadataUrl: null,
    metadataXml: null,
    name: 'Acme IdP',
    registrationId: 'sso-0123',
    scopes: ['openid', 'profile', 'email'],
    serviceProviderMetadataPath: null,
    type: SsoProviderType.Oidc,
    vendor: SsoProviderVendor.Generic,
};

const renderDialog = (ssoProvider?: typeof storedOidcProvider) =>
    render(
        <QueryClientProvider client={new QueryClient()}>
            <SsoProviderDialog onClose={vi.fn()} ssoProvider={ssoProvider} />
        </QueryClientProvider>
    );

describe('SsoProviderDialog', () => {
    beforeEach(() => {
        hoisted.createMutate.mockReset();
        hoisted.updateMutate.mockReset();
    });

    describe('fields by protocol', () => {
        it('shows the OIDC fields by default', () => {
            renderDialog();

            expect(screen.getByLabelText('Issuer URI')).toBeInTheDocument();
            expect(screen.getByLabelText('Client ID')).toBeInTheDocument();
            expect(screen.getByLabelText('Client secret')).toBeInTheDocument();
            expect(screen.queryByLabelText('Metadata URL')).not.toBeInTheDocument();
        });

        it('switches to the SAML fields and back', async () => {
            const user = userEvent.setup();

            renderDialog();

            await user.click(screen.getByRole('radio', {name: 'SAML 2.0'}));

            expect(screen.getByLabelText('Metadata URL')).toBeInTheDocument();
            expect(screen.queryByLabelText('Issuer URI')).not.toBeInTheDocument();
            expect(screen.queryByLabelText('Client secret')).not.toBeInTheDocument();

            await user.click(screen.getByRole('radio', {name: 'Paste XML'}));

            expect(screen.getByLabelText('Metadata XML')).toBeInTheDocument();

            await user.click(screen.getByRole('radio', {name: 'OpenID Connect (OIDC)'}));

            expect(screen.getByLabelText('Issuer URI')).toBeInTheDocument();
        });

        it('locks the protocol when editing', () => {
            renderDialog(storedOidcProvider);

            expect(screen.getByRole('radio', {name: 'SAML 2.0'})).toBeDisabled();
        });
    });

    describe('Microsoft Entra ID', () => {
        const selectEntra = async (user: ReturnType<typeof userEvent.setup>) => {
            await user.click(screen.getByRole('radio', {name: 'Microsoft Entra ID'}));
        };

        it('asks for the tenant ID instead of the issuer URI and pre-fills the scopes', async () => {
            const user = userEvent.setup();

            renderDialog();

            await selectEntra(user);

            expect(screen.getByLabelText('Directory (tenant) ID')).toBeInTheDocument();
            expect(screen.queryByLabelText('Issuer URI')).not.toBeInTheDocument();
            expect(screen.getByLabelText('Scopes')).toHaveValue('openid profile email');
        });

        it('rejects a tenant value that is not a GUID', async () => {
            const user = userEvent.setup();

            renderDialog();

            await user.type(screen.getByLabelText('Name'), 'Entra');
            await selectEntra(user);
            await user.type(screen.getByLabelText('Directory (tenant) ID'), 'common');
            await user.type(screen.getByLabelText('Client ID'), 'client-1');
            await user.type(screen.getByLabelText('Client secret'), 'secret');
            await user.click(screen.getByRole('button', {name: 'Save'}));

            expect(await screen.findByText(/Enter the Directory \(tenant\) ID as a GUID/)).toBeInTheDocument();
            expect(hoisted.createMutate).not.toHaveBeenCalled();
        });

        it('builds the issuer URI from the tenant ID on save', async () => {
            const user = userEvent.setup();

            renderDialog();

            await user.type(screen.getByLabelText('Name'), 'Entra');
            await selectEntra(user);
            await user.type(screen.getByLabelText('Directory (tenant) ID'), TENANT_ID);
            await user.type(screen.getByLabelText('Client ID'), 'client-1');
            await user.type(screen.getByLabelText('Client secret'), 'secret');
            await user.type(screen.getByLabelText('Email domains'), 'Acme.com');
            await user.click(screen.getByRole('button', {name: 'Save'}));

            await waitFor(() => expect(hoisted.createMutate).toHaveBeenCalledTimes(1));

            expect(hoisted.createMutate.mock.calls[0][0].input).toMatchObject({
                clientId: 'client-1',
                clientSecret: 'secret',
                domains: ['acme.com'],
                issuerUri: `https://login.microsoftonline.com/${TENANT_ID}/v2.0`,
                scopes: ['openid', 'profile', 'email'],
                type: SsoProviderType.Oidc,
                vendor: SsoProviderVendor.MicrosoftEntraId,
            });
        });
    });

    describe('client secret', () => {
        it('requires a secret when creating an OIDC provider', async () => {
            const user = userEvent.setup();

            renderDialog();

            await user.type(screen.getByLabelText('Name'), 'Acme');
            await user.type(screen.getByLabelText('Issuer URI'), 'https://idp.example.com/realm');
            await user.type(screen.getByLabelText('Client ID'), 'client-1');
            await user.click(screen.getByRole('button', {name: 'Save'}));

            expect(await screen.findByText('Client secret is required')).toBeInTheDocument();
            expect(hoisted.createMutate).not.toHaveBeenCalled();
        });

        it('keeps the stored secret by leaving it out when editing without typing one', async () => {
            const user = userEvent.setup();

            renderDialog(storedOidcProvider);

            expect(screen.getByLabelText('Client secret')).toHaveAttribute('placeholder', 'stored');

            await user.click(screen.getByRole('button', {name: 'Save'}));

            await waitFor(() => expect(hoisted.updateMutate).toHaveBeenCalledTimes(1));

            const {id, input} = hoisted.updateMutate.mock.calls[0][0];

            expect(id).toBe('7');
            expect(input).not.toHaveProperty('clientSecret');
        });

        it('sends a new secret when one is typed while editing', async () => {
            const user = userEvent.setup();

            renderDialog(storedOidcProvider);

            await user.type(screen.getByLabelText('Client secret'), 'rotated');
            await user.click(screen.getByRole('button', {name: 'Save'}));

            await waitFor(() => expect(hoisted.updateMutate).toHaveBeenCalledTimes(1));

            expect(hoisted.updateMutate.mock.calls[0][0].input.clientSecret).toBe('rotated');
        });
    });

    describe('enforce SSO', () => {
        it('warns that local admins keep password access when enforcement is turned on', async () => {
            const user = userEvent.setup();

            renderDialog();

            expect(screen.queryByText(/break-glass/)).not.toBeInTheDocument();

            await user.click(screen.getByRole('switch', {name: 'Enforce SSO'}));

            expect(
                screen.getByText(/Local administrators keep password access as a break-glass option/)
            ).toBeInTheDocument();
        });
    });

    describe('connection details', () => {
        it('shows the absolute redirect URI when editing', () => {
            renderDialog(storedOidcProvider);

            expect(screen.getByLabelText('Redirect URI')).toHaveValue(
                `${window.location.origin}/login/oauth2/code/sso-0123`
            );
        });
    });
});
