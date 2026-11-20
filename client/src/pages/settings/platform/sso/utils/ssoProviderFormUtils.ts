import {
    SsoProviderInput,
    SsoProviderType,
    SsoProviderVendor,
    type SsoProvidersQuery,
} from '@/shared/middleware/graphql';
import {z} from 'zod';

export type SsoProviderItemType = SsoProvidersQuery['ssoProviders'][number];

export const ENTRA_DEFAULT_AUTHORITY_HOST = 'login.microsoftonline.com';

export const ENTRA_DEFAULT_SCOPES = 'openid profile email';

const ENTRA_AUTHORITY_HOSTS = [
    ENTRA_DEFAULT_AUTHORITY_HOST,
    'login.microsoftonline.us',
    'login.partner.microsoftonline.cn',
];

const GUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const DOMAIN_PATTERN = /^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i;

export const isGuid = (value: string): boolean => GUID_PATTERN.test(value.trim());

export const buildEntraIssuerUri = (tenantId: string, authorityHost: string = ENTRA_DEFAULT_AUTHORITY_HOST): string =>
    `https://${authorityHost}/${tenantId.trim()}/v2.0`;

export const parseEntraIssuerUri = (issuerUri?: string | null): {authorityHost: string; tenantId: string} | null => {
    const match = /^https:\/\/([^/]+)\/([^/]+)\/v2\.0\/?$/i.exec((issuerUri ?? '').trim());

    if (!match || !ENTRA_AUTHORITY_HOSTS.includes(match[1].toLowerCase())) {
        return null;
    }

    return {authorityHost: match[1].toLowerCase(), tenantId: match[2]};
};

export const splitList = (value: string): string[] =>
    value
        .split(/[\s,;]+/)
        .map((item) => item.trim())
        .filter((item) => item.length > 0);

export const buildAbsoluteUrl = (path?: string | null): string => (path ? `${window.location.origin}${path}` : '');

export const ssoProviderFormSchema = z
    .object({
        authorityHost: z.string(),
        autoProvision: z.boolean(),
        clientId: z.string(),
        clientSecret: z.string(),
        defaultAuthority: z.enum(['ROLE_USER', 'ROLE_ADMIN']),
        domains: z.string(),
        enabled: z.boolean(),
        enforceSso: z.boolean(),
        issuerUri: z.string(),
        metadataSource: z.enum(['URL', 'XML']),
        metadataUrl: z.string(),
        metadataXml: z.string(),
        name: z.string().trim().min(1, {message: 'Name is required'}).max(255),
        scopes: z.string(),
        tenantId: z.string(),
        type: z.enum(['OIDC', 'SAML']),
        vendor: z.enum(['GENERIC', 'MICROSOFT_ENTRA_ID']),
    })
    .superRefine((values, context) => {
        const addIssue = (path: string, message: string) => context.addIssue({code: 'custom', message, path: [path]});

        splitList(values.domains).forEach((domain) => {
            if (!DOMAIN_PATTERN.test(domain)) {
                addIssue('domains', `"${domain}" is not a valid domain`);
            }
        });

        if (values.type === 'OIDC') {
            if (values.vendor === 'MICROSOFT_ENTRA_ID') {
                if (!values.tenantId.trim()) {
                    addIssue('tenantId', 'Tenant ID is required');
                } else if (!isGuid(values.tenantId)) {
                    addIssue(
                        'tenantId',
                        'Enter the Directory (tenant) ID as a GUID, not a domain name or "common", "organizations" or "consumers"'
                    );
                }
            } else if (!values.issuerUri.trim()) {
                addIssue('issuerUri', 'Issuer URI is required');
            } else if (!values.issuerUri.trim().toLowerCase().startsWith('https://')) {
                addIssue('issuerUri', 'Issuer URI must start with https://');
            }

            if (!values.clientId.trim()) {
                addIssue('clientId', 'Client ID is required');
            }
        } else if (values.metadataSource === 'URL') {
            if (!values.metadataUrl.trim()) {
                addIssue('metadataUrl', 'Metadata URL is required');
            } else if (!values.metadataUrl.trim().toLowerCase().startsWith('https://')) {
                addIssue('metadataUrl', 'Metadata URL must start with https://');
            }
        } else if (!values.metadataXml.trim()) {
            addIssue('metadataXml', 'Metadata XML is required');
        }
    });

export type SsoProviderFormValuesType = z.infer<typeof ssoProviderFormSchema>;

export const getDefaultFormValues = (ssoProvider?: SsoProviderItemType): SsoProviderFormValuesType => {
    if (!ssoProvider) {
        return {
            authorityHost: ENTRA_DEFAULT_AUTHORITY_HOST,
            autoProvision: false,
            clientId: '',
            clientSecret: '',
            defaultAuthority: 'ROLE_USER',
            domains: '',
            enabled: false,
            enforceSso: false,
            issuerUri: '',
            metadataSource: 'URL',
            metadataUrl: '',
            metadataXml: '',
            name: '',
            scopes: '',
            tenantId: '',
            type: 'OIDC',
            vendor: 'GENERIC',
        };
    }

    const entra =
        ssoProvider.vendor === SsoProviderVendor.MicrosoftEntraId ? parseEntraIssuerUri(ssoProvider.issuerUri) : null;

    return {
        authorityHost: entra?.authorityHost ?? ENTRA_DEFAULT_AUTHORITY_HOST,
        autoProvision: ssoProvider.autoProvision,
        clientId: ssoProvider.clientId ?? '',
        clientSecret: '',
        defaultAuthority: ssoProvider.defaultAuthority === 'ROLE_ADMIN' ? 'ROLE_ADMIN' : 'ROLE_USER',
        domains: ssoProvider.domains.join(', '),
        enabled: ssoProvider.enabled,
        enforceSso: ssoProvider.enforceSso,
        issuerUri: ssoProvider.issuerUri ?? '',
        metadataSource: !ssoProvider.metadataUrl && ssoProvider.metadataXml ? 'XML' : 'URL',
        metadataUrl: ssoProvider.metadataUrl ?? '',
        metadataXml: ssoProvider.metadataXml ?? '',
        name: ssoProvider.name,
        scopes: ssoProvider.scopes.join(' '),
        tenantId: entra?.tenantId ?? '',
        type: ssoProvider.type === SsoProviderType.Saml ? 'SAML' : 'OIDC',
        vendor: ssoProvider.vendor === SsoProviderVendor.MicrosoftEntraId ? 'MICROSOFT_ENTRA_ID' : 'GENERIC',
    };
};

/**
 * Builds the GraphQL input. The client secret is write-only: it is sent only when the admin typed one,
 * so an edit that leaves the field empty keeps the stored secret.
 */
export const buildSsoProviderInput = (values: SsoProviderFormValuesType): SsoProviderInput => {
    const input: SsoProviderInput = {
        autoProvision: values.autoProvision,
        defaultAuthority: values.defaultAuthority,
        domains: splitList(values.domains).map((domain) => domain.toLowerCase()),
        enabled: values.enabled,
        enforceSso: values.enforceSso,
        name: values.name.trim(),
        type: values.type as SsoProviderType,
    };

    if (values.type === 'SAML') {
        input.vendor = SsoProviderVendor.Generic;

        if (values.metadataSource === 'URL') {
            input.metadataUrl = values.metadataUrl.trim();
        } else {
            input.metadataXml = values.metadataXml.trim();
        }

        return input;
    }

    input.clientId = values.clientId.trim();
    input.scopes = splitList(values.scopes);

    if (values.vendor === 'MICROSOFT_ENTRA_ID') {
        input.vendor = SsoProviderVendor.MicrosoftEntraId;
        input.issuerUri = buildEntraIssuerUri(values.tenantId, values.authorityHost);
    } else {
        input.vendor = SsoProviderVendor.Generic;
        input.issuerUri = values.issuerUri.trim();
    }

    if (values.clientSecret) {
        input.clientSecret = values.clientSecret;
    }

    return input;
};
