import {EditionType} from '@/shared/stores/useApplicationInfoStore';
import {describe, expect, it} from 'vitest';

import shouldShowEmbeddedGettingStartedHelpLink from './shouldShowEmbeddedGettingStartedHelpLink';

describe('shouldShowEmbeddedGettingStartedHelpLink', () => {
    it('shows the help link for EE admins on embedded pages', () => {
        expect(
            shouldShowEmbeddedGettingStartedHelpLink({
                edition: EditionType.EE,
                isAdmin: true,
                pathname: '/embedded/integrations',
            })
        ).toBe(true);
    });

    it('hides the help link outside embedded pages', () => {
        expect(
            shouldShowEmbeddedGettingStartedHelpLink({
                edition: EditionType.EE,
                isAdmin: true,
                pathname: '/automation/projects',
            })
        ).toBe(false);
        expect(
            shouldShowEmbeddedGettingStartedHelpLink({
                edition: EditionType.EE,
                isAdmin: true,
                pathname: '/embedded-other',
            })
        ).toBe(false);
    });

    it('hides the help link for non-admins and outside EE', () => {
        expect(
            shouldShowEmbeddedGettingStartedHelpLink({
                edition: EditionType.EE,
                isAdmin: false,
                pathname: '/embedded/integrations',
            })
        ).toBe(false);
        expect(
            shouldShowEmbeddedGettingStartedHelpLink({
                edition: EditionType.CE,
                isAdmin: true,
                pathname: '/embedded/integrations',
            })
        ).toBe(false);
    });
});
