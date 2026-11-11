import {EMBEDDED_GETTING_STARTED_PATH} from '@/pages/home/constants/platformModes';
import {EditionType} from '@/shared/stores/useApplicationInfoStore';

export const EMBEDDED_GETTING_STARTED_ROUTE_PATH = EMBEDDED_GETTING_STARTED_PATH.substring(
    EMBEDDED_GETTING_STARTED_PATH.lastIndexOf('/') + 1
);

export const EMBEDDED_DEFAULT_ROUTE_PATH = 'integrations';

interface ResolveEmbeddedIndexRedirectProps {
    edition?: EditionType;
    fetchCompleted: () => Promise<boolean>;
    isAdmin: boolean;
}

export default async function resolveEmbeddedIndexRedirect({
    edition,
    fetchCompleted,
    isAdmin,
}: ResolveEmbeddedIndexRedirectProps): Promise<string> {
    if (edition !== EditionType.EE || !isAdmin) {
        return EMBEDDED_DEFAULT_ROUTE_PATH;
    }

    try {
        const completed = await fetchCompleted();

        return completed ? EMBEDDED_DEFAULT_ROUTE_PATH : EMBEDDED_GETTING_STARTED_ROUTE_PATH;
    } catch {
        return EMBEDDED_DEFAULT_ROUTE_PATH;
    }
}
