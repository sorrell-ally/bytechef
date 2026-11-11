import useEmbeddedApiKeysProviderValue from '@/ee/pages/settings/embedded/api-keys/hooks/useEmbeddedApiKeysProviderValue';
import ApiKeysContent from '@/shared/components/api-keys/ApiKeysContent';
import {ApiKeysProvider} from '@/shared/components/api-keys/providers/apiKeysProvider';

const ApiKeys = () => {
    const apiKeysProviderValue = useEmbeddedApiKeysProviderValue();

    return (
        <ApiKeysProvider value={apiKeysProviderValue}>
            <ApiKeysContent
                description="Do not share your API key with others or expose it in the browser or other client-side code."
                title="API Keys"
            />
        </ApiKeysProvider>
    );
};

export default ApiKeys;
