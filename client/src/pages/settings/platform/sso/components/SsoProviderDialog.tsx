import Button from '@/components/Button/Button';
import {
    Dialog,
    DialogBody,
    DialogCancelButton,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogMain,
} from '@/components/Dialog';
import {Input} from '@/components/Input/Input';
import LoadingIcon from '@/components/LoadingIcon';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/Select/Select';
import Switch from '@/components/Switch/Switch';
import {Alert, AlertDescription} from '@/components/ui/alert';
import {Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage} from '@/components/ui/form';
import {RadioGroup, RadioGroupItem} from '@/components/ui/radio-group';
import {Textarea} from '@/components/ui/textarea';
import SsoProviderConnectionDetails from '@/pages/settings/platform/sso/components/SsoProviderConnectionDetails';
import {
    ENTRA_DEFAULT_SCOPES,
    SsoProviderFormValuesType,
    SsoProviderItemType,
    buildEntraIssuerUri,
    buildSsoProviderInput,
    getDefaultFormValues,
    isGuid,
    ssoProviderFormSchema,
} from '@/pages/settings/platform/sso/utils/ssoProviderFormUtils';
import {useCreateSsoProviderMutation, useUpdateSsoProviderMutation} from '@/shared/middleware/graphql';
import {zodResolver} from '@hookform/resolvers/zod';
import {useQueryClient} from '@tanstack/react-query';
import {InfoIcon, TriangleAlertIcon} from 'lucide-react';
import {useState} from 'react';
import {useForm, useWatch} from 'react-hook-form';
import {toast} from 'sonner';

interface SsoProviderDialogProps {
    onClose: () => void;
    ssoProvider?: SsoProviderItemType;
}

const SsoProviderDialog = ({onClose, ssoProvider}: SsoProviderDialogProps) => {
    const [savedSsoProvider, setSavedSsoProvider] = useState<SsoProviderItemType | undefined>(undefined);

    const queryClient = useQueryClient();

    const form = useForm<SsoProviderFormValuesType>({
        defaultValues: getDefaultFormValues(ssoProvider),
        resolver: zodResolver(ssoProviderFormSchema),
    });

    const type = useWatch({control: form.control, name: 'type'});
    const vendor = useWatch({control: form.control, name: 'vendor'});
    const metadataSource = useWatch({control: form.control, name: 'metadataSource'});
    const enforceSso = useWatch({control: form.control, name: 'enforceSso'});
    const tenantId = useWatch({control: form.control, name: 'tenantId'});
    const authorityHost = useWatch({control: form.control, name: 'authorityHost'});

    const handleSaved = (saved: SsoProviderItemType) => {
        void queryClient.invalidateQueries({queryKey: ['ssoProviders']});

        toast(ssoProvider ? 'Single sign-on provider updated.' : 'Single sign-on provider created.');

        setSavedSsoProvider(saved);
    };

    const createMutation = useCreateSsoProviderMutation({
        onSuccess: (data) => handleSaved(data.createSsoProvider),
    });

    const updateMutation = useUpdateSsoProviderMutation({
        onSuccess: (data) => handleSaved(data.updateSsoProvider),
    });

    const isEdit = !!ssoProvider;
    const isPending = createMutation.isPending || updateMutation.isPending;
    const isEntra = type === 'OIDC' && vendor === 'MICROSOFT_ENTRA_ID';
    const entraIssuerPreview = isEntra && isGuid(tenantId) ? buildEntraIssuerUri(tenantId, authorityHost) : undefined;

    const handleVendorChange = (value: string) => {
        form.setValue('vendor', value as SsoProviderFormValuesType['vendor']);

        if (value === 'MICROSOFT_ENTRA_ID' && !form.getValues('scopes').trim()) {
            form.setValue('scopes', ENTRA_DEFAULT_SCOPES);
        }
    };

    const handleSubmit = (values: SsoProviderFormValuesType) => {
        const secretStored = ssoProvider?.clientSecretSet ?? false;

        if (values.type === 'OIDC' && !values.clientSecret && !(isEdit && secretStored)) {
            form.setError('clientSecret', {message: 'Client secret is required', type: 'manual'});

            return;
        }

        const input = buildSsoProviderInput(values);

        if (ssoProvider) {
            updateMutation.mutate({id: ssoProvider.id, input});
        } else {
            createMutation.mutate({input});
        }
    };

    if (savedSsoProvider) {
        return (
            <Dialog onOpenChange={(open) => !open && onClose()} open>
                <DialogContent size="md">
                    <DialogMain>
                        <DialogHeader
                            description={
                                savedSsoProvider.enabled
                                    ? 'The provider is enabled. Copy these values into your identity provider.'
                                    : 'The provider is saved but disabled. Register these values in your identity provider, then enable it.'
                            }
                            title={`${savedSsoProvider.name} saved`}
                        />

                        <DialogBody>
                            <SsoProviderConnectionDetails ssoProvider={savedSsoProvider} />
                        </DialogBody>

                        <DialogFooter>
                            <Button label="Done" onClick={onClose} />
                        </DialogFooter>
                    </DialogMain>
                </DialogContent>
            </Dialog>
        );
    }

    return (
        <Dialog onOpenChange={(open) => !open && !isPending && onClose()} open>
            <DialogContent size="md">
                <DialogMain>
                    <DialogHeader
                        description="Let users sign in with your OpenID Connect or SAML 2.0 identity provider."
                        title={isEdit ? 'Edit single sign-on provider' : 'New single sign-on provider'}
                    />

                    <Form {...form}>
                        <form className="flex min-h-0 flex-1 flex-col" onSubmit={form.handleSubmit(handleSubmit)}>
                            <DialogBody>
                                {ssoProvider && <SsoProviderConnectionDetails ssoProvider={ssoProvider} />}

                                <FormField
                                    control={form.control}
                                    name="name"
                                    render={({field}) => (
                                        <FormItem>
                                            <FormLabel>Name</FormLabel>

                                            <FormControl>
                                                <Input placeholder="Acme Entra ID" {...field} />
                                            </FormControl>

                                            <FormDescription>Shown on the sign-in button.</FormDescription>

                                            <FormMessage />
                                        </FormItem>
                                    )}
                                />

                                <FormField
                                    control={form.control}
                                    name="type"
                                    render={({field}) => (
                                        <FormItem>
                                            <FormLabel>Protocol</FormLabel>

                                            <FormControl>
                                                <RadioGroup
                                                    className="flex gap-6"
                                                    disabled={isEdit}
                                                    onValueChange={field.onChange}
                                                    value={field.value}
                                                >
                                                    <div className="flex items-center gap-2">
                                                        <RadioGroupItem id="sso-type-oidc" value="OIDC" />

                                                        <label htmlFor="sso-type-oidc">OpenID Connect (OIDC)</label>
                                                    </div>

                                                    <div className="flex items-center gap-2">
                                                        <RadioGroupItem id="sso-type-saml" value="SAML" />

                                                        <label htmlFor="sso-type-saml">SAML 2.0</label>
                                                    </div>
                                                </RadioGroup>
                                            </FormControl>

                                            {isEdit && (
                                                <FormDescription>
                                                    The protocol cannot be changed after the provider is created.
                                                </FormDescription>
                                            )}
                                        </FormItem>
                                    )}
                                />

                                {type === 'OIDC' && (
                                    <>
                                        <FormField
                                            control={form.control}
                                            name="vendor"
                                            render={({field}) => (
                                                <FormItem>
                                                    <FormLabel>Identity provider</FormLabel>

                                                    <FormControl>
                                                        <RadioGroup
                                                            className="flex gap-6"
                                                            onValueChange={handleVendorChange}
                                                            value={field.value}
                                                        >
                                                            <div className="flex items-center gap-2">
                                                                <RadioGroupItem
                                                                    id="sso-vendor-generic"
                                                                    value="GENERIC"
                                                                />

                                                                <label htmlFor="sso-vendor-generic">Generic OIDC</label>
                                                            </div>

                                                            <div className="flex items-center gap-2">
                                                                <RadioGroupItem
                                                                    id="sso-vendor-entra"
                                                                    value="MICROSOFT_ENTRA_ID"
                                                                />

                                                                <label htmlFor="sso-vendor-entra">
                                                                    Microsoft Entra ID
                                                                </label>
                                                            </div>
                                                        </RadioGroup>
                                                    </FormControl>
                                                </FormItem>
                                            )}
                                        />

                                        {isEntra ? (
                                            <>
                                                <Alert>
                                                    <InfoIcon />

                                                    <AlertDescription>
                                                        <p>
                                                            Register a single-tenant app in Entra ID (App
                                                            registrations), add the redirect URI shown after saving as a
                                                            Web redirect URI, create a client secret and copy its value.
                                                            Guest accounts of the tenant cannot sign in.
                                                        </p>
                                                    </AlertDescription>
                                                </Alert>

                                                <FormField
                                                    control={form.control}
                                                    name="tenantId"
                                                    render={({field}) => (
                                                        <FormItem>
                                                            <FormLabel>Directory (tenant) ID</FormLabel>

                                                            <FormControl>
                                                                <Input
                                                                    placeholder="00000000-0000-0000-0000-000000000000"
                                                                    {...field}
                                                                />
                                                            </FormControl>

                                                            <FormDescription>
                                                                {entraIssuerPreview
                                                                    ? `Issuer URI: ${entraIssuerPreview}`
                                                                    : 'The tenant GUID, not a domain name.'}
                                                            </FormDescription>

                                                            <FormMessage />
                                                        </FormItem>
                                                    )}
                                                />
                                            </>
                                        ) : (
                                            <FormField
                                                control={form.control}
                                                name="issuerUri"
                                                render={({field}) => (
                                                    <FormItem>
                                                        <FormLabel>Issuer URI</FormLabel>

                                                        <FormControl>
                                                            <Input
                                                                placeholder="https://idp.example.com/realms/acme"
                                                                {...field}
                                                            />
                                                        </FormControl>

                                                        <FormMessage />
                                                    </FormItem>
                                                )}
                                            />
                                        )}

                                        <FormField
                                            control={form.control}
                                            name="clientId"
                                            render={({field}) => (
                                                <FormItem>
                                                    <FormLabel>Client ID</FormLabel>

                                                    <FormControl>
                                                        <Input autoComplete="off" {...field} />
                                                    </FormControl>

                                                    <FormMessage />
                                                </FormItem>
                                            )}
                                        />

                                        <FormField
                                            control={form.control}
                                            name="clientSecret"
                                            render={({field}) => (
                                                <FormItem>
                                                    <FormLabel>Client secret</FormLabel>

                                                    <FormControl>
                                                        <Input
                                                            autoComplete="new-password"
                                                            placeholder={ssoProvider?.clientSecretSet ? 'stored' : ''}
                                                            type="password"
                                                            {...field}
                                                        />
                                                    </FormControl>

                                                    <FormDescription>
                                                        {isEdit && ssoProvider?.clientSecretSet
                                                            ? 'Write-only. Leave empty to keep the stored secret.'
                                                            : 'Write-only. It cannot be read back after saving.'}
                                                    </FormDescription>

                                                    <FormMessage />
                                                </FormItem>
                                            )}
                                        />

                                        <FormField
                                            control={form.control}
                                            name="scopes"
                                            render={({field}) => (
                                                <FormItem>
                                                    <FormLabel>Scopes</FormLabel>

                                                    <FormControl>
                                                        <Input placeholder={ENTRA_DEFAULT_SCOPES} {...field} />
                                                    </FormControl>

                                                    <FormDescription>
                                                        Separated by spaces. Leave empty for the default (
                                                        {ENTRA_DEFAULT_SCOPES}).
                                                    </FormDescription>

                                                    <FormMessage />
                                                </FormItem>
                                            )}
                                        />
                                    </>
                                )}

                                {type === 'SAML' && (
                                    <>
                                        <FormField
                                            control={form.control}
                                            name="metadataSource"
                                            render={({field}) => (
                                                <FormItem>
                                                    <FormLabel>Identity provider metadata</FormLabel>

                                                    <FormControl>
                                                        <RadioGroup
                                                            className="flex gap-6"
                                                            onValueChange={field.onChange}
                                                            value={field.value}
                                                        >
                                                            <div className="flex items-center gap-2">
                                                                <RadioGroupItem id="sso-metadata-url" value="URL" />

                                                                <label htmlFor="sso-metadata-url">Fetch from URL</label>
                                                            </div>

                                                            <div className="flex items-center gap-2">
                                                                <RadioGroupItem id="sso-metadata-xml" value="XML" />

                                                                <label htmlFor="sso-metadata-xml">Paste XML</label>
                                                            </div>
                                                        </RadioGroup>
                                                    </FormControl>
                                                </FormItem>
                                            )}
                                        />

                                        {metadataSource === 'URL' ? (
                                            <FormField
                                                control={form.control}
                                                name="metadataUrl"
                                                render={({field}) => (
                                                    <FormItem>
                                                        <FormLabel>Metadata URL</FormLabel>

                                                        <FormControl>
                                                            <Input
                                                                placeholder="https://idp.example.com/metadata.xml"
                                                                {...field}
                                                            />
                                                        </FormControl>

                                                        <FormMessage />
                                                    </FormItem>
                                                )}
                                            />
                                        ) : (
                                            <FormField
                                                control={form.control}
                                                name="metadataXml"
                                                render={({field}) => (
                                                    <FormItem>
                                                        <FormLabel>Metadata XML</FormLabel>

                                                        <FormControl>
                                                            <Textarea className="min-h-32 font-mono" {...field} />
                                                        </FormControl>

                                                        <FormMessage />
                                                    </FormItem>
                                                )}
                                            />
                                        )}
                                    </>
                                )}

                                <FormField
                                    control={form.control}
                                    name="domains"
                                    render={({field}) => (
                                        <FormItem>
                                            <FormLabel>Email domains</FormLabel>

                                            <FormControl>
                                                <Input placeholder="acme.com, acme.org" {...field} />
                                            </FormControl>

                                            <FormDescription>
                                                Separated by commas. Users with these email domains are offered this
                                                provider on the sign-in page.
                                            </FormDescription>

                                            <FormMessage />
                                        </FormItem>
                                    )}
                                />

                                <FormField
                                    control={form.control}
                                    name="defaultAuthority"
                                    render={({field}) => (
                                        <FormItem>
                                            <FormLabel>Default role for new users</FormLabel>

                                            <Select onValueChange={field.onChange} value={field.value}>
                                                <FormControl>
                                                    <SelectTrigger aria-label="Default role for new users">
                                                        <SelectValue />
                                                    </SelectTrigger>
                                                </FormControl>

                                                <SelectContent>
                                                    <SelectItem value="ROLE_USER">User</SelectItem>

                                                    <SelectItem value="ROLE_ADMIN">Admin</SelectItem>
                                                </SelectContent>
                                            </Select>

                                            <FormDescription>
                                                Applied to accounts created automatically on first sign-in.
                                            </FormDescription>
                                        </FormItem>
                                    )}
                                />

                                <FormField
                                    control={form.control}
                                    name="autoProvision"
                                    render={({field}) => (
                                        <FormItem>
                                            <div className="flex items-center gap-2">
                                                <FormControl>
                                                    <Switch
                                                        aria-label="Auto-provision users"
                                                        checked={field.value}
                                                        onCheckedChange={field.onChange}
                                                    />
                                                </FormControl>

                                                <FormLabel>Auto-provision users</FormLabel>
                                            </div>

                                            <FormDescription>
                                                Create a ByteChef account on first sign-in when none exists.
                                            </FormDescription>
                                        </FormItem>
                                    )}
                                />

                                <FormField
                                    control={form.control}
                                    name="enforceSso"
                                    render={({field}) => (
                                        <FormItem>
                                            <div className="flex items-center gap-2">
                                                <FormControl>
                                                    <Switch
                                                        aria-label="Enforce SSO"
                                                        checked={field.value}
                                                        onCheckedChange={field.onChange}
                                                    />
                                                </FormControl>

                                                <FormLabel>Enforce SSO</FormLabel>
                                            </div>

                                            <FormDescription>
                                                Refuse password sign-in for users with these email domains.
                                            </FormDescription>
                                        </FormItem>
                                    )}
                                />

                                {enforceSso && (
                                    <Alert variant="warning">
                                        <TriangleAlertIcon />

                                        <AlertDescription>
                                            <p>
                                                Local administrators keep password access as a break-glass option, so a
                                                misconfigured provider cannot lock everyone out. Make sure you can sign
                                                in with this provider before enabling enforcement.
                                            </p>
                                        </AlertDescription>
                                    </Alert>
                                )}

                                <FormField
                                    control={form.control}
                                    name="enabled"
                                    render={({field}) => (
                                        <FormItem>
                                            <div className="flex items-center gap-2">
                                                <FormControl>
                                                    <Switch
                                                        aria-label="Enabled"
                                                        checked={field.value}
                                                        onCheckedChange={field.onChange}
                                                    />
                                                </FormControl>

                                                <FormLabel>Enabled</FormLabel>
                                            </div>

                                            <FormDescription>
                                                You can save a disabled provider first to learn the redirect URI, then
                                                enable it.
                                            </FormDescription>
                                        </FormItem>
                                    )}
                                />
                            </DialogBody>

                            <DialogFooter>
                                <DialogCancelButton disabled={isPending} />

                                <Button
                                    disabled={isPending}
                                    icon={isPending ? <LoadingIcon /> : undefined}
                                    label="Save"
                                    type="submit"
                                />
                            </DialogFooter>
                        </form>
                    </Form>
                </DialogMain>
            </DialogContent>
        </Dialog>
    );
};

export default SsoProviderDialog;
