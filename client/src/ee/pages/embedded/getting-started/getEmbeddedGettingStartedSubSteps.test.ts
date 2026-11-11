import {
    getIntegrationSubSteps,
    getTestUserSubSteps,
} from '@/ee/pages/embedded/getting-started/getEmbeddedGettingStartedSubSteps';
import {EmbeddedGettingStartedQuery} from '@/shared/middleware/graphql';
import {describe, expect, it} from 'vitest';

type StatusType = EmbeddedGettingStartedQuery['embeddedGettingStarted'];

const DEVELOPMENT_ENVIRONMENT_ID = 0;
const STAGING_ENVIRONMENT_ID = 1;

const baseStatus: StatusType = {
    apiBaseUrl: null,
    apiKeyCreated: false,
    completed: false,
    completionType: null,
    componentName: null,
    componentVersion: null,
    firstCallMade: false,
    integrationConfigurationCreated: false,
    integrationConfigurationEnabled: false,
    integrationCreated: false,
    integrationHasWorkflows: true,
    integrationId: null,
    integrationInstanceConfigurationId: null,
    integrationPublished: false,
    lastFailedCall: null,
    testUserAdded: false,
    testUserConnected: false,
    testUserExternalId: null,
    testUserId: null,
};

describe('getIntegrationSubSteps', () => {
    it('lists the four integration sub-steps in order, none done on an empty tenant', () => {
        const subSteps = getIntegrationSubSteps(baseStatus, DEVELOPMENT_ENVIRONMENT_ID, 'Development');

        expect(subSteps.map((subStep) => [subStep.id, subStep.label, subStep.done])).toEqual([
            ['create', 'Create an integration', false],
            ['publish', 'Publish a version', false],
            ['configure', 'Create a configuration in Development', false],
            ['enable', 'Enable the configuration', false],
        ]);
    });

    it('leaves out publishing for an integration without workflows', () => {
        const subSteps = getIntegrationSubSteps(
            {...baseStatus, integrationCreated: true, integrationHasWorkflows: false},
            DEVELOPMENT_ENVIRONMENT_ID,
            'Development'
        );

        expect(subSteps.map((subStep) => subStep.id)).toEqual(['create', 'configure', 'enable']);
    });

    it('marks each sub-step done from its own status field', () => {
        const subSteps = getIntegrationSubSteps(
            {
                ...baseStatus,
                integrationConfigurationCreated: true,
                integrationCreated: true,
                integrationPublished: true,
            },
            DEVELOPMENT_ENVIRONMENT_ID,
            'Development'
        );

        expect(subSteps.map((subStep) => subStep.done)).toEqual([true, true, true, false]);
    });

    it('points the configuration sub-steps at the integration list in Development', () => {
        const subSteps = getIntegrationSubSteps(baseStatus, DEVELOPMENT_ENVIRONMENT_ID, 'Development');

        expect(subSteps.map((subStep) => subStep.href)).toEqual([
            '/embedded/integrations',
            '/embedded/integrations',
            '/embedded/integrations',
            '/embedded/integrations',
        ]);
        expect(subSteps[2].hint).toBe('Integration menu (⋮) → New Configuration');
        expect(subSteps[3].hint).toBe('Expand the integration → Configurations tab → switch it on');
    });

    it('points the configuration sub-steps at the configurations page outside Development', () => {
        const subSteps = getIntegrationSubSteps(baseStatus, STAGING_ENVIRONMENT_ID, 'Staging');

        expect(subSteps[2].label).toBe('Create a configuration in Staging');
        expect(subSteps[2].href).toBe('/embedded/configurations');
        expect(subSteps[2].hint).toBe('Configurations → New Configuration');
        expect(subSteps[3].href).toBe('/embedded/configurations');
        expect(subSteps[3].hint).toBe('Configurations → switch it on');
    });
});

describe('getTestUserSubSteps', () => {
    it('lists adding the user and provisioning, none done on an empty tenant', () => {
        const subSteps = getTestUserSubSteps(baseStatus);

        expect(subSteps.map((subStep) => [subStep.id, subStep.label, subStep.done, subStep.href])).toEqual([
            ['add', 'Add a connected user', false, '/embedded/connected-users'],
            ['provision', 'Provision your integration and connect their account', false, '/embedded/connected-users'],
        ]);
    });

    it('marks adding done before provisioning', () => {
        const subSteps = getTestUserSubSteps({...baseStatus, testUserAdded: true});

        expect(subSteps.map((subStep) => subStep.done)).toEqual([true, false]);
    });

    it('marks both done once the test user is connected', () => {
        const subSteps = getTestUserSubSteps({...baseStatus, testUserAdded: true, testUserConnected: true});

        expect(subSteps.map((subStep) => subStep.done)).toEqual([true, true]);
    });
});
