import Button from '@/components/Button/Button';
import EmptyState from '@/components/EmptyState/EmptyState';
import PageLoader from '@/components/PageLoader';
import SsoProviderDialog from '@/pages/settings/platform/sso/components/SsoProviderDialog';
import SsoProviderList from '@/pages/settings/platform/sso/components/SsoProviderList';
import Header from '@/shared/layout/Header';
import LayoutContainer from '@/shared/layout/LayoutContainer';
import {useSsoProvidersQuery} from '@/shared/middleware/graphql';
import {isSsoDisabledError} from '@/shared/util/ssoDisabledErrors';
import {KeyRoundIcon, PlusIcon} from 'lucide-react';
import {useState} from 'react';

const SingleSignOn = () => {
    const [createDialogOpen, setCreateDialogOpen] = useState(false);

    const {data, error, isLoading} = useSsoProvidersQuery(undefined, {retry: false});

    const featureDisabled = isSsoDisabledError(error);
    const ssoProviders = data?.ssoProviders;

    return (
        <PageLoader errors={featureDisabled ? [] : [error]} loading={isLoading}>
            <LayoutContainer
                header={
                    <Header
                        centerTitle
                        description="Let users sign in with your OpenID Connect or SAML 2.0 identity provider, such as Microsoft Entra ID."
                        position="main"
                        right={
                            ssoProviders && (
                                <Button
                                    icon={<PlusIcon />}
                                    label="New provider"
                                    onClick={() => setCreateDialogOpen(true)}
                                />
                            )
                        }
                        title="Single Sign-On"
                    />
                }
                leftSidebarOpen={false}
            >
                {featureDisabled && (
                    <EmptyState
                        description="Single sign-on is turned off on this server. Set the bytechef.security.sso.enabled property to true and restart the server to manage identity providers here."
                        icon={<KeyRoundIcon />}
                        note="The property is bytechef.security.sso.enabled. Single sign-on is available in the Community Edition only."
                        title="Single sign-on is not enabled"
                    />
                )}

                {ssoProviders && ssoProviders.length === 0 && (
                    <EmptyState
                        actions={<Button label="New provider" onClick={() => setCreateDialogOpen(true)} />}
                        description="Add an identity provider so your users can sign in with their company account."
                        icon={<KeyRoundIcon />}
                        title="No single sign-on providers yet"
                    />
                )}

                {ssoProviders && ssoProviders.length > 0 && (
                    <div className="w-full self-start p-4 pt-0 3xl:mx-auto 3xl:w-4/5">
                        <SsoProviderList ssoProviders={ssoProviders} />
                    </div>
                )}

                {createDialogOpen && <SsoProviderDialog onClose={() => setCreateDialogOpen(false)} />}
            </LayoutContainer>
        </PageLoader>
    );
};

export default SingleSignOn;
