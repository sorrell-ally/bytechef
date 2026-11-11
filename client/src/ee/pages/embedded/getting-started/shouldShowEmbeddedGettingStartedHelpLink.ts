import {EditionType} from '@/shared/stores/useApplicationInfoStore';

interface ShouldShowEmbeddedGettingStartedHelpLinkProps {
    edition?: EditionType;
    isAdmin: boolean;
    pathname: string;
}

export default function shouldShowEmbeddedGettingStartedHelpLink({
    edition,
    isAdmin,
    pathname,
}: ShouldShowEmbeddedGettingStartedHelpLinkProps): boolean {
    return edition === EditionType.EE && isAdmin && pathname.startsWith('/embedded/');
}
