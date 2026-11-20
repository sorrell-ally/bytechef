import Button from '@/components/Button/Button';
import {Input} from '@/components/Input/Input';
import {SsoProviderItemType, buildAbsoluteUrl} from '@/pages/settings/platform/sso/utils/ssoProviderFormUtils';
import {SsoProviderType} from '@/shared/middleware/graphql';
import {CheckIcon, CopyIcon} from 'lucide-react';
import {useRef, useState} from 'react';

interface CopyableUrlProps {
    description: string;
    label: string;
    value: string;
}

const CopyableUrl = ({description, label, value}: CopyableUrlProps) => {
    const [copied, setCopied] = useState(false);

    const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    const handleCopyClick = async () => {
        try {
            await navigator.clipboard.writeText(value);

            setCopied(true);

            clearTimeout(timeoutRef.current);

            timeoutRef.current = setTimeout(() => setCopied(false), 2000);
        } catch {
            setCopied(false);
        }
    };

    return (
        <div className="flex flex-col gap-1">
            <span className="text-sm font-medium">{label}</span>

            <div className="flex items-center gap-2">
                <Input aria-label={label} readOnly value={value} />

                <Button
                    aria-label={`Copy ${label}`}
                    icon={copied ? <CheckIcon /> : <CopyIcon />}
                    onClick={handleCopyClick}
                    size="icon"
                    type="button"
                    variant="outline"
                />
            </div>

            <span className="text-xs text-muted-foreground">{description}</span>
        </div>
    );
};

interface SsoProviderConnectionDetailsProps {
    ssoProvider: SsoProviderItemType;
}

const SsoProviderConnectionDetails = ({ssoProvider}: SsoProviderConnectionDetailsProps) => {
    const isSaml = ssoProvider.type === SsoProviderType.Saml;

    return (
        <div className="flex flex-col gap-3 rounded-md border p-3" data-testid="sso-connection-details">
            <p className="text-sm font-semibold">Register this in your identity provider</p>

            <CopyableUrl
                description={
                    isSaml
                        ? 'Assertion consumer service (ACS) URL.'
                        : 'Redirect URI: add it as a Web platform redirect URI of the application.'
                }
                label={isSaml ? 'ACS URL' : 'Redirect URI'}
                value={buildAbsoluteUrl(ssoProvider.callbackPath)}
            />

            {isSaml && ssoProvider.serviceProviderMetadataPath && (
                <CopyableUrl
                    description="Service provider metadata. Its address is also the entity ID (audience)."
                    label="Service provider metadata URL"
                    value={buildAbsoluteUrl(ssoProvider.serviceProviderMetadataPath)}
                />
            )}

            <CopyableUrl
                description="Users can start signing in directly through this link."
                label="Login URL"
                value={buildAbsoluteUrl(ssoProvider.loginPath)}
            />
        </div>
    );
};

export default SsoProviderConnectionDetails;
