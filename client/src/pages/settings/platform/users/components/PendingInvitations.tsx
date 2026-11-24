import Button from '@/components/Button/Button';
import {
    usePendingUserInvitationsQuery,
    useResendUserInvitationMutation,
    useRevokeUserInvitationMutation,
} from '@/shared/middleware/graphql';
import {useQueryClient} from '@tanstack/react-query';

const PendingInvitations = () => {
    const queryClient = useQueryClient();

    const {data} = usePendingUserInvitationsQuery();

    const resendUserInvitation = useResendUserInvitationMutation({
        onSuccess: () => {
            void queryClient.invalidateQueries({queryKey: ['pendingUserInvitations']});
        },
    });

    const revokeUserInvitation = useRevokeUserInvitationMutation({
        onSuccess: () => {
            void queryClient.invalidateQueries({queryKey: ['pendingUserInvitations']});
        },
    });

    const invitations = data?.pendingUserInvitations ?? [];

    if (invitations.length === 0) {
        return null;
    }

    return (
        <div className="flex flex-col gap-2">
            <h2 className="text-sm font-medium text-content-neutral-primary">Pending invitations</h2>

            <ul className="flex flex-col gap-2">
                {invitations.map((invitation) => (
                    <li
                        className="flex items-center justify-between gap-3 rounded-md border border-stroke-neutral-secondary px-3 py-2"
                        key={invitation.id}
                    >
                        <span className="text-sm text-content-neutral-primary">{invitation.email}</span>

                        <span className="flex gap-2">
                            <Button
                                label="Resend"
                                onClick={() => resendUserInvitation.mutate({id: invitation.id})}
                                size="sm"
                                variant="outline"
                            />

                            <Button
                                label="Revoke"
                                onClick={() => revokeUserInvitation.mutate({id: invitation.id})}
                                size="sm"
                                variant="outline"
                            />
                        </span>
                    </li>
                ))}
            </ul>
        </div>
    );
};

export default PendingInvitations;
