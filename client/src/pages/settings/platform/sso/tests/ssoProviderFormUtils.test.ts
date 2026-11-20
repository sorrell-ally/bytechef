import {
    buildEntraIssuerUri,
    buildSsoProviderInput,
    getDefaultFormValues,
    isGuid,
    parseEntraIssuerUri,
    splitList,
    ssoProviderFormSchema,
} from '@/pages/settings/platform/sso/utils/ssoProviderFormUtils';
import {describe, expect, it} from 'vitest';

const TENANT_ID = '0f8fad5b-d9cb-469f-a165-70867728950e';

describe('ssoProviderFormUtils', () => {
    it('recognises tenant GUIDs and rejects multi-tenant aliases', () => {
        expect(isGuid(TENANT_ID)).toBe(true);
        expect(isGuid(` ${TENANT_ID.toUpperCase()} `)).toBe(true);
        expect(isGuid('common')).toBe(false);
        expect(isGuid('organizations')).toBe(false);
        expect(isGuid('contoso.onmicrosoft.com')).toBe(false);
    });

    it('builds and parses the Entra issuer URI, keeping a sovereign-cloud host', () => {
        expect(buildEntraIssuerUri(TENANT_ID)).toBe(`https://login.microsoftonline.com/${TENANT_ID}/v2.0`);
        expect(parseEntraIssuerUri(buildEntraIssuerUri(TENANT_ID, 'login.microsoftonline.us'))).toEqual({
            authorityHost: 'login.microsoftonline.us',
            tenantId: TENANT_ID,
        });
        expect(parseEntraIssuerUri('https://evil.example.com/' + TENANT_ID + '/v2.0')).toBeNull();
    });

    it('splits lists on commas, semicolons and whitespace', () => {
        expect(splitList('a.com, b.com;c.com\nd.com')).toEqual(['a.com', 'b.com', 'c.com', 'd.com']);
    });

    it('validates domains', () => {
        const result = ssoProviderFormSchema.safeParse({
            ...getDefaultFormValues(),
            clientId: 'client',
            domains: 'acme.com, not a domain!',
            issuerUri: 'https://idp.example.com',
            name: 'Acme',
        });

        expect(result.success).toBe(false);
    });

    it('requires https metadata URLs for SAML', () => {
        const result = ssoProviderFormSchema.safeParse({
            ...getDefaultFormValues(),
            metadataUrl: 'http://idp.example.com/metadata',
            name: 'Acme',
            type: 'SAML',
        });

        expect(result.success).toBe(false);
    });

    it('leaves OIDC fields out of a SAML input', () => {
        const input = buildSsoProviderInput({
            ...getDefaultFormValues(),
            clientId: 'ignored',
            metadataUrl: 'https://idp.example.com/metadata',
            name: 'Acme SAML',
            type: 'SAML',
        });

        expect(input).not.toHaveProperty('clientId');
        expect(input).not.toHaveProperty('issuerUri');
        expect(input.metadataUrl).toBe('https://idp.example.com/metadata');
    });
});
