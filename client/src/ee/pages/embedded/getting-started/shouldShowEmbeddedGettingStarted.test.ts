import {EditionType} from '@/shared/stores/useApplicationInfoStore';
import {describe, expect, it} from 'vitest';

import shouldShowEmbeddedGettingStarted from './shouldShowEmbeddedGettingStarted';

describe('shouldShowEmbeddedGettingStarted', () => {
    it('shows Getting Started for EE admins until completed', () => {
        expect(shouldShowEmbeddedGettingStarted({completed: false, edition: EditionType.EE, isAdmin: true})).toBe(true);
    });

    it('hides it for non-admins and once completed', () => {
        expect(shouldShowEmbeddedGettingStarted({completed: false, edition: EditionType.EE, isAdmin: false})).toBe(
            false
        );
        expect(shouldShowEmbeddedGettingStarted({completed: true, edition: EditionType.EE, isAdmin: true})).toBe(false);
    });

    it('hides it while the completion state is unknown', () => {
        expect(shouldShowEmbeddedGettingStarted({completed: undefined, edition: EditionType.EE, isAdmin: true})).toBe(
            false
        );
    });

    it('hides it outside EE', () => {
        expect(shouldShowEmbeddedGettingStarted({completed: false, edition: EditionType.CE, isAdmin: true})).toBe(
            false
        );
        expect(shouldShowEmbeddedGettingStarted({completed: false, edition: undefined, isAdmin: true})).toBe(false);
    });
});
