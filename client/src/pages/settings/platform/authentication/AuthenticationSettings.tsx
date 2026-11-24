import PageLoader from '@/components/PageLoader';
import Switch from '@/components/Switch/Switch';
import Header from '@/shared/layout/Header';
import LayoutContainer from '@/shared/layout/LayoutContainer';
import {
    useAuthenticationPolicyQuery,
    useUpdateSelfSignUpEnabledMutation,
    useUpdateSsoOnlyEnabledMutation,
} from '@/shared/middleware/graphql';
import {useQueryClient} from '@tanstack/react-query';
import {useEffect, useState} from 'react';

const AuthenticationSettings = () => {
    const [signUpEnabled, setSignUpEnabled] = useState(false);
    const [ssoOnlyEnabled, setSsoOnlyEnabled] = useState(false);

    const queryClient = useQueryClient();

    const {data, error, isLoading} = useAuthenticationPolicyQuery();

    const updateSelfSignUpEnabled = useUpdateSelfSignUpEnabledMutation({
        onSuccess: () => {
            void queryClient.invalidateQueries({queryKey: ['authenticationPolicy']});
        },
    });

    const updateSsoOnlyEnabled = useUpdateSsoOnlyEnabledMutation({
        onSuccess: () => {
            void queryClient.invalidateQueries({queryKey: ['authenticationPolicy']});
        },
    });

    const authenticationPolicy = data?.authenticationPolicy;

    const handleSignUpChange = (enabled: boolean) => {
        const previousValue = signUpEnabled;

        setSignUpEnabled(enabled);

        updateSelfSignUpEnabled.mutate(
            {enabled},
            {
                onError: () => {
                    setSignUpEnabled(previousValue);
                },
            }
        );
    };

    const handleSsoOnlyChange = (enabled: boolean) => {
        const previousValue = ssoOnlyEnabled;

        setSsoOnlyEnabled(enabled);

        updateSsoOnlyEnabled.mutate(
            {enabled},
            {
                onError: () => {
                    setSsoOnlyEnabled(previousValue);
                },
            }
        );
    };

    const environmentDefault = authenticationPolicy?.signUpEnvironmentDefault ? 'true' : 'false';
    const signUpDescription = authenticationPolicy?.signUpOverridden
        ? 'Saved for this organization. The environment variable BYTECHEF_SIGN_UP_ENABLED is no longer the value in effect.'
        : `Following the environment default (BYTECHEF_SIGN_UP_ENABLED=${environmentDefault}) until you save a choice.`;

    // Keeps the switches aligned with the policy returned by the server.
    useEffect(() => {
        if (!authenticationPolicy) {
            return;
        }

        setSignUpEnabled(authenticationPolicy.signUpEnabled);
        setSsoOnlyEnabled(authenticationPolicy.ssoOnlyEnabled);
    }, [authenticationPolicy]);

    return (
        <PageLoader errors={[error]} loading={isLoading}>
            <LayoutContainer
                header={
                    <Header
                        centerTitle
                        description="Control who can create an account and whether password sign-in is allowed."
                        position="main"
                        title="Authentication"
                    />
                }
                leftSidebarOpen={false}
            >
                {authenticationPolicy && (
                    <div className="flex w-full flex-col gap-4 self-start p-4 pt-0 3xl:mx-auto 3xl:w-4/5">
                        <Switch
                            checked={signUpEnabled}
                            description={signUpDescription}
                            disabled={updateSelfSignUpEnabled.isPending}
                            label="Allow self sign-up"
                            onCheckedChange={handleSignUpChange}
                            variant="box"
                        />

                        <Switch
                            checked={ssoOnlyEnabled}
                            description="Password login and password registration are refused. People sign in with an enabled single sign-on provider. You can turn this on only after you have signed in through one."
                            disabled={updateSsoOnlyEnabled.isPending}
                            label="SSO-only mode"
                            onCheckedChange={handleSsoOnlyChange}
                            variant="box"
                        />

                        <div className="flex flex-col gap-2 text-sm text-content-neutral-secondary">
                            <p>
                                If single sign-on becomes unavailable, set BYTECHEF_SECURITY_PASSWORD_LOGIN_BREAK_GLASS
                                to true and restart. Activated administrators can then sign in with a password. Turn the
                                variable off again after SSO-only mode is off.
                            </p>

                            {authenticationPolicy.passwordLoginBreakGlass && (
                                <p role="status">
                                    Password-login break-glass is on. Activated administrators can sign in with a
                                    password until the variable is turned off and the server is restarted.
                                </p>
                            )}
                        </div>
                    </div>
                )}
            </LayoutContainer>
        </PageLoader>
    );
};

export default AuthenticationSettings;
