/*
 * The SSO GraphQL fields only exist when the server runs the Community Edition with
 * `bytechef.security.sso.enabled=true`. With the feature off, the settings page's query fails
 * schema validation ("Field 'ssoProviders' in type 'Query' is undefined"). The page renders an
 * explanatory empty state for that case, so the fetch interceptor skips its global toast.
 */
const SSO_FIELD_PATTERN = /\bssoProviders?\b/;

export function isSsoDisabledGraphQlError(error: {
    extensions?: {classification?: string; errorCode?: string};
    message?: string;
}): boolean {
    return (
        (error.extensions?.classification === 'ValidationError' || !error.extensions?.classification) &&
        SSO_FIELD_PATTERN.test(error.message ?? '')
    );
}

export function isSsoDisabledError(error: unknown): boolean {
    const errorMessage = error instanceof Error ? error.message : typeof error === 'string' ? error : '';

    return SSO_FIELD_PATTERN.test(errorMessage) && /undefined|FieldUndefined|ValidationError/.test(errorMessage);
}
