import {EditionType} from '@/shared/stores/useApplicationInfoStore';

interface ShouldShowEmbeddedGettingStartedProps {
    completed?: boolean;
    edition?: EditionType;
    isAdmin: boolean;
}

export default function shouldShowEmbeddedGettingStarted({
    completed,
    edition,
    isAdmin,
}: ShouldShowEmbeddedGettingStartedProps): boolean {
    return edition === EditionType.EE && isAdmin && completed === false;
}
