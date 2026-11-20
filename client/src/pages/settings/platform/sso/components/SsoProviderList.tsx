import AlertDialog from '@/components/AlertDialog';
import Badge from '@/components/Badge/Badge';
import Button from '@/components/Button/Button';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import SsoProviderDialog from '@/pages/settings/platform/sso/components/SsoProviderDialog';
import {SsoProviderItemType} from '@/pages/settings/platform/sso/utils/ssoProviderFormUtils';
import {SsoProviderType, SsoProviderVendor, useDeleteSsoProviderMutation} from '@/shared/middleware/graphql';
import {useQueryClient} from '@tanstack/react-query';
import {PencilIcon, TrashIcon} from 'lucide-react';
import {useState} from 'react';
import {toast} from 'sonner';

interface SsoProviderListProps {
    ssoProviders: SsoProviderItemType[];
}

const SsoProviderList = ({ssoProviders}: SsoProviderListProps) => {
    const [deletingSsoProvider, setDeletingSsoProvider] = useState<SsoProviderItemType | undefined>(undefined);
    const [editingSsoProvider, setEditingSsoProvider] = useState<SsoProviderItemType | undefined>(undefined);

    const queryClient = useQueryClient();

    const deleteMutation = useDeleteSsoProviderMutation({
        onSuccess: () => {
            void queryClient.invalidateQueries({queryKey: ['ssoProviders']});

            toast('Single sign-on provider deleted.');

            setDeletingSsoProvider(undefined);
        },
    });

    return (
        <>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>Name</TableHead>

                        <TableHead>Type</TableHead>

                        <TableHead>Vendor</TableHead>

                        <TableHead>Domains</TableHead>

                        <TableHead>Status</TableHead>

                        <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                </TableHeader>

                <TableBody>
                    {ssoProviders.map((ssoProvider) => (
                        <TableRow key={ssoProvider.id}>
                            <TableCell className="font-medium">{ssoProvider.name}</TableCell>

                            <TableCell>{ssoProvider.type === SsoProviderType.Saml ? 'SAML' : 'OIDC'}</TableCell>

                            <TableCell>
                                {ssoProvider.type === SsoProviderType.Saml
                                    ? '-'
                                    : ssoProvider.vendor === SsoProviderVendor.MicrosoftEntraId
                                      ? 'Microsoft Entra ID'
                                      : 'Generic'}
                            </TableCell>

                            <TableCell>{ssoProvider.domains.length ? ssoProvider.domains.join(', ') : '-'}</TableCell>

                            <TableCell>
                                <div className="flex flex-wrap gap-1">
                                    <Badge
                                        label={ssoProvider.enabled ? 'Enabled' : 'Disabled'}
                                        styleType={ssoProvider.enabled ? 'success-outline' : 'secondary-outline'}
                                    />

                                    {ssoProvider.enforceSso && (
                                        <Badge label="SSO enforced" styleType="primary-outline" />
                                    )}
                                </div>
                            </TableCell>

                            <TableCell>
                                <div className="flex justify-end gap-1">
                                    <Button
                                        aria-label={`Edit ${ssoProvider.name}`}
                                        icon={<PencilIcon />}
                                        onClick={() => setEditingSsoProvider(ssoProvider)}
                                        size="icon"
                                        variant="ghost"
                                    />

                                    <Button
                                        aria-label={`Delete ${ssoProvider.name}`}
                                        icon={<TrashIcon />}
                                        onClick={() => setDeletingSsoProvider(ssoProvider)}
                                        size="icon"
                                        variant="ghost"
                                    />
                                </div>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>

            {editingSsoProvider && (
                <SsoProviderDialog
                    key={editingSsoProvider.id}
                    onClose={() => setEditingSsoProvider(undefined)}
                    ssoProvider={editingSsoProvider}
                />
            )}

            <AlertDialog
                confirmLabel="Delete"
                description={`Delete "${deletingSsoProvider?.name}"? Users who sign in through it will no longer be able to, and accounts it created keep working with a password reset.`}
                isPending={deleteMutation.isPending}
                onCancel={() => setDeletingSsoProvider(undefined)}
                onConfirm={() => deletingSsoProvider && deleteMutation.mutate({id: deletingSsoProvider.id})}
                open={!!deletingSsoProvider}
                title="Delete single sign-on provider"
            />
        </>
    );
};

export default SsoProviderList;
