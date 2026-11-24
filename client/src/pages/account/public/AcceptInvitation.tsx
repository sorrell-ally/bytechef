import Button from '@/components/Button/Button';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import NewPasswordForm from '@/pages/account/public/components/NewPasswordForm';
import PublicLayoutContainer from '@/shared/layout/PublicLayoutContainer';
import {getXsrfToken} from '@/shared/util/cookie-utils';
import {CircleCheckBigIcon} from 'lucide-react';
import {useEffect, useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';

type AcceptInvitationStatusType = 'connectionError' | 'failure' | 'idle' | 'invalidPassword' | 'success';

const AcceptInvitation = () => {
    const [status, setStatus] = useState<AcceptInvitationStatusType>('idle');

    const [ssoOnly, setSsoOnly] = useState(false);

    const [searchParams] = useSearchParams();
    const key = searchParams.get('key');
    const token = searchParams.get('token');

    async function handleSubmit(newPassword: string) {
        const requestUrl = token ? '/api/account/accept-user-invitation' : '/api/account/accept-invitation';
        const body = token ? {password: newPassword, token} : {key, newPassword};

        try {
            const response = await fetch(requestUrl, {
                body: JSON.stringify(body),
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': await getXsrfToken(),
                },
                method: 'POST',
            });

            if (response.ok) {
                setStatus('success');
            } else if (response.status === 400) {
                setStatus('invalidPassword');
            } else {
                setStatus('failure');
            }
        } catch {
            setStatus('connectionError');
        }
    }

    async function handleSso() {
        if (!token) {
            return;
        }

        try {
            const response = await fetch('/api/user-invitations/sso', {
                body: JSON.stringify({token}),
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': await getXsrfToken(),
                },
                method: 'POST',
            });

            if (!response.ok) {
                setStatus('failure');

                return;
            }

            const body = await response.json();

            window.location.assign(body.redirectUrl);
        } catch {
            setStatus('connectionError');
        }
    }

    // Loads whether this invitation must be accepted with single sign-on.
    useEffect(() => {
        if (!token) {
            return;
        }

        const previewUrl = `/api/user-invitations/preview?token=${encodeURIComponent(token)}`;

        fetch(previewUrl)
            .then(async (response) => {
                if (!response.ok) {
                    setStatus('failure');

                    return;
                }

                const body = await response.json();

                setSsoOnly(body.ssoOnly === true);
            })
            .catch(() => {
                setStatus('connectionError');
            });
    }, [token]);

    if (status === 'success') {
        return (
            <PublicLayoutContainer>
                <Card className="mx-auto flex max-w-sm flex-col gap-6 rounded-xl p-6 text-center shadow-none">
                    <CircleCheckBigIcon className="mx-auto size-12 text-success" />

                    <CardHeader className="p-0">
                        <CardTitle className="self-center text-xl font-bold text-content-neutral-primary">
                            Your account is ready
                        </CardTitle>

                        <CardDescription className="self-center text-center text-content-neutral-secondary">
                            Your password is set and your account is active. <br /> Click below to log in.
                        </CardDescription>
                    </CardHeader>

                    <Link to="/login">
                        <Button className="w-fit" label="Continue to Log in" size="lg" />
                    </Link>
                </Card>
            </PublicLayoutContainer>
        );
    }

    return (
        <PublicLayoutContainer>
            <Card className="mx-auto max-w-sm rounded-xl p-6 text-start shadow-none">
                <CardHeader className="p-0 pb-8 text-center">
                    <CardTitle className="text-xl font-bold text-content-neutral-primary">Set your password</CardTitle>

                    <CardDescription className="text-content-neutral-secondary">
                        Choose a password to activate your account.
                    </CardDescription>
                </CardHeader>

                <CardContent className="flex flex-col gap-6 p-0">
                    {status === 'failure' && (
                        <p className="text-sm text-destructive" role="alert">
                            This invitation link is invalid or has expired. Ask your administrator to invite you again.
                        </p>
                    )}

                    {status === 'invalidPassword' && (
                        <p className="text-sm text-destructive" role="alert">
                            This password does not meet the requirements. Choose a different password.
                        </p>
                    )}

                    {status === 'connectionError' && (
                        <p className="text-sm text-destructive" role="alert">
                            Could not reach the server. Check your connection and try again.
                        </p>
                    )}

                    {ssoOnly && (
                        <Button className="w-full" label="Continue with single sign-on" onClick={handleSso} size="lg" />
                    )}

                    {!ssoOnly && (key || token) && (
                        <NewPasswordForm onSubmit={handleSubmit} submitLabel="Activate account" />
                    )}
                </CardContent>
            </Card>
        </PublicLayoutContainer>
    );
};

export default AcceptInvitation;
