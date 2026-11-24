import {useApplicationInfoStore} from '@/shared/stores/useApplicationInfoStore';
import {Mock, vi} from 'vitest';

export const createMockApplicationInfoStore = (overrides = {}) => {
    const defaultMock = {
        getApplicationInfo: vi.fn(),
        signUp: {
            activationRequired: false,
            enabled: true,
            ssoOnly: false,
        },
    };

    return {
        ...defaultMock,
        ...overrides,
    };
};

export const mockApplicationInfoStore = (overrides = {}) => {
    const mockStore = createMockApplicationInfoStore(overrides);

    (useApplicationInfoStore as unknown as Mock).mockImplementation((selector) => {
        if (typeof selector === 'function') {
            return selector(mockStore);
        }

        return mockStore;
    });

    return mockStore;
};
