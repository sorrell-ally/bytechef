import {EditionType} from '@/shared/stores/useApplicationInfoStore';
import {describe, expect, it, vi} from 'vitest';

import resolveEmbeddedIndexRedirect from './resolveEmbeddedIndexRedirect';

describe('resolveEmbeddedIndexRedirect', () => {
    it('redirects admins to getting started until completed', async () => {
        const fetchCompleted = vi.fn(() => Promise.resolve(false));

        await expect(
            resolveEmbeddedIndexRedirect({edition: EditionType.EE, fetchCompleted, isAdmin: true})
        ).resolves.toBe('getting-started');
        expect(fetchCompleted).toHaveBeenCalledTimes(1);
    });

    it('redirects to integrations once completed', async () => {
        const fetchCompleted = vi.fn(() => Promise.resolve(true));

        await expect(
            resolveEmbeddedIndexRedirect({edition: EditionType.EE, fetchCompleted, isAdmin: true})
        ).resolves.toBe('integrations');
    });

    it('never redirects non-admins to getting started', async () => {
        const fetchCompleted = vi.fn(() => Promise.resolve(false));

        await expect(
            resolveEmbeddedIndexRedirect({edition: EditionType.EE, fetchCompleted, isAdmin: false})
        ).resolves.toBe('integrations');
        expect(fetchCompleted).not.toHaveBeenCalled();
    });

    it('does not query completion outside EE', async () => {
        const fetchCompleted = vi.fn(() => Promise.resolve(false));

        await expect(
            resolveEmbeddedIndexRedirect({edition: EditionType.CE, fetchCompleted, isAdmin: true})
        ).resolves.toBe('integrations');
        await expect(resolveEmbeddedIndexRedirect({edition: undefined, fetchCompleted, isAdmin: true})).resolves.toBe(
            'integrations'
        );
        expect(fetchCompleted).not.toHaveBeenCalled();
    });

    it('falls back to integrations when the completed query fails', async () => {
        const fetchCompleted = vi.fn(() => Promise.reject(new Error('Request failed')));

        await expect(
            resolveEmbeddedIndexRedirect({edition: EditionType.EE, fetchCompleted, isAdmin: true})
        ).resolves.toBe('integrations');
    });
});
