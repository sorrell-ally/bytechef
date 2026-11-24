import {useInviteUserDialogStore} from '@/pages/settings/platform/users/stores/useInviteUserDialogStore';
import {AUTHORITIES} from '@/shared/constants';
import useCeEdition from '@/shared/edition/useCeEdition';
import {useAuthoritiesQuery, useCreateUserInvitationMutation} from '@/shared/middleware/graphql';
import {useQueryClient} from '@tanstack/react-query';
import {useEffect, useMemo, useState} from 'react';

interface UseInviteUserDialogI {
    authorities: string[];
    handleClose: () => void;
    handleEmailChange: (email: string) => void;
    handleInvite: () => void;
    handleOpen: () => void;
    handleOpenChange: (open: boolean) => void;
    handleRoleChange: (role: string) => void;
    invitationResult: {
        acceptUrl?: string | null;
        deliveryError?: string | null;
        mailed: boolean;
    } | null;
    inviteDisabled: boolean;
    inviteEmail: string;
    invitePending: boolean;
    inviteRole: string | null;
    open: boolean;
    roleSelectVisible: boolean;
}

export default function useInviteUserDialog(): UseInviteUserDialogI {
    const {inviteEmail, inviteRole, open, reset, setInviteEmail, setInviteRole, setOpen} = useInviteUserDialogStore();

    const ceEdition = useCeEdition();

    const {data: authoritiesData} = useAuthoritiesQuery({}, {enabled: !ceEdition});

    const [invitationResult, setInvitationResult] = useState<{
        acceptUrl?: string | null;
        deliveryError?: string | null;
        mailed: boolean;
    } | null>(null);

    const queryClient = useQueryClient();

    const inviteUserMutation = useCreateUserInvitationMutation({
        onSuccess: (result) => {
            setInvitationResult(result.createUserInvitation);

            void queryClient.invalidateQueries({queryKey: ['pendingUserInvitations']});
            void queryClient.invalidateQueries({queryKey: ['users']});
        },
    });

    const authorities = useMemo(() => authoritiesData?.authorities ?? [], [authoritiesData]);
    const roleSelectVisible = !ceEdition;
    const invitePending = inviteUserMutation.isPending;
    const inviteDisabled = invitePending || !inviteEmail || (roleSelectVisible && !inviteRole);

    useEffect(() => {
        if (roleSelectVisible && open && !inviteRole && authorities.length > 0) {
            setInviteRole(authorities[0]);
        }
    }, [roleSelectVisible, open, inviteRole, authorities, setInviteRole]);

    const handleClose = () => {
        setInvitationResult(null);
        reset();
    };

    const handleOpen = () => {
        setOpen();
    };

    const handleEmailChange = (email: string) => {
        setInviteEmail(email);
    };

    const handleRoleChange = (role: string) => {
        setInviteRole(role);
    };

    const handleOpenChange = (open: boolean) => {
        if (!open) {
            handleClose();
        }
    };

    const handleInvite = () => {
        if (invitePending) {
            return;
        }

        const role = ceEdition ? AUTHORITIES.ADMIN : inviteRole;

        if (inviteEmail && role) {
            inviteUserMutation.mutate({
                email: inviteEmail,
                role,
            });
        }
    };

    return {
        authorities,
        handleClose,
        handleEmailChange,
        handleInvite,
        handleOpen,
        handleOpenChange,
        handleRoleChange,
        invitationResult,
        inviteDisabled,
        inviteEmail,
        invitePending,
        inviteRole,
        open,
        roleSelectVisible,
    };
}
