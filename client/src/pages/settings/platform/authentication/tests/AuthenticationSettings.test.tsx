import AuthenticationSettings from '@/pages/settings/platform/authentication/AuthenticationSettings';
import {render, resetAll, screen} from '@/shared/util/test-utils';
import {afterEach, describe, expect, it, vi} from 'vitest';

const hoisted = vi.hoisted(() => ({
    policy: {
        data: undefined as
            | {
                  authenticationPolicy: {
                      passwordLoginBreakGlass: boolean;
                      signUpEnabled: boolean;
                      signUpEnvironmentDefault: boolean;
                      signUpOverridden: boolean;
                      ssoOnlyEnabled: boolean;
                  };
              }
            | undefined,
        error: null as Error | null,
        isLoading: false,
    },
    updateSelfSignUp: vi.fn(),
    updateSsoOnly: vi.fn(),
}));

vi.mock('@/shared/middleware/graphql', async (importOriginal) => ({
    ...(await importOriginal<typeof import('@/shared/middleware/graphql')>()),
    useAuthenticationPolicyQuery: () => hoisted.policy,
    useUpdateSelfSignUpEnabledMutation: () => ({isPending: false, mutate: hoisted.updateSelfSignUp}),
    useUpdateSsoOnlyEnabledMutation: () => ({isPending: false, mutate: hoisted.updateSsoOnly}),
}));

describe('AuthenticationSettings', () => {
    afterEach(() => {
        resetAll();
        hoisted.updateSelfSignUp.mockReset();
        hoisted.updateSsoOnly.mockReset();
    });

    it('shows the environment default until an administrator saves a choice', async () => {
        hoisted.policy = {
            data: {
                authenticationPolicy: {
                    passwordLoginBreakGlass: false,
                    signUpEnabled: false,
                    signUpEnvironmentDefault: false,
                    signUpOverridden: false,
                    ssoOnlyEnabled: false,
                },
            },
            error: null,
            isLoading: false,
        };

        render(<AuthenticationSettings />);

        expect(await screen.findByRole('switch', {name: /allow self sign-up/i})).not.toBeChecked();
        expect(screen.getByText(/BYTECHEF_SIGN_UP_ENABLED=false/)).toBeInTheDocument();
        expect(screen.getByRole('switch', {name: /sso-only mode/i})).not.toBeChecked();
    });

    it('warns when the password-login break-glass is on', async () => {
        hoisted.policy = {
            data: {
                authenticationPolicy: {
                    passwordLoginBreakGlass: true,
                    signUpEnabled: true,
                    signUpEnvironmentDefault: true,
                    signUpOverridden: true,
                    ssoOnlyEnabled: true,
                },
            },
            error: null,
            isLoading: false,
        };

        render(<AuthenticationSettings />);

        expect(await screen.findByRole('switch', {name: /sso-only mode/i})).toBeChecked();
        expect(screen.getByText(/Password-login break-glass is on/)).toBeInTheDocument();
        expect(screen.getByText(/no longer the value in effect/)).toBeInTheDocument();
    });
});
