import Button from '@/components/Button/Button';
import {
    Dialog,
    DialogBody,
    DialogClose,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogMain,
} from '@/components/Dialog';
import {Input} from '@/components/Input/Input';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/Select/Select';
import {getRoleLabel} from '@/shared/util/role-utils';

import useInviteUserDialog from './hooks/useInviteUserDialog';

const InviteUserDialog = () => {
    const {
        authorities,
        handleClose,
        handleEmailChange,
        handleInvite,
        handleOpenChange,
        handleRoleChange,
        invitationResult,
        inviteDisabled,
        inviteEmail,
        invitePending,
        inviteRole,
        open,
        roleSelectVisible,
    } = useInviteUserDialog();

    return (
        <Dialog onOpenChange={handleOpenChange} open={open}>
            <DialogContent>
                <DialogMain>
                    <DialogHeader
                        description="The invitee gets a single-use link that expires. If mail is not configured, or the email cannot be sent, the link is shown here so you can copy it."
                        title="Invite User"
                    />

                    <DialogBody>
                        <div className="flex flex-col gap-4">
                            <div className="flex flex-col gap-2">
                                <label className="text-sm font-medium">Email</label>

                                <Input
                                    onChange={(event) => handleEmailChange(event.target.value)}
                                    placeholder="user@example.com"
                                    type="email"
                                    value={inviteEmail}
                                />
                            </div>

                            {roleSelectVisible && (
                                <div className="flex flex-col gap-2">
                                    <label className="text-sm font-medium">Role</label>

                                    <Select
                                        onValueChange={(value) => handleRoleChange(value)}
                                        value={inviteRole ?? undefined}
                                    >
                                        <SelectTrigger>
                                            <SelectValue placeholder="Select role" />
                                        </SelectTrigger>

                                        <SelectContent>
                                            {authorities.map((authority) => (
                                                <SelectItem key={authority} value={authority}>
                                                    {getRoleLabel(authority)}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                            )}

                            {invitationResult && (
                                <div className="flex flex-col gap-2">
                                    <p className="text-sm text-content-neutral-secondary" role="status">
                                        {invitationResult.mailed
                                            ? 'The invitation email was sent. The link is also here if you need to share it.'
                                            : invitationResult.deliveryError}
                                    </p>

                                    {invitationResult.acceptUrl && (
                                        <input
                                            aria-label="Invitation link"
                                            className="w-full rounded-md border border-stroke-neutral-secondary px-2 py-1 text-sm"
                                            readOnly
                                            value={invitationResult.acceptUrl}
                                        />
                                    )}
                                </div>
                            )}
                        </div>
                    </DialogBody>

                    <DialogFooter>
                        <DialogClose asChild>
                            <Button label="Cancel" onClick={handleClose} variant="outline" />
                        </DialogClose>

                        <Button
                            disabled={inviteDisabled}
                            label={invitePending ? 'Inviting...' : 'Invite'}
                            onClick={handleInvite}
                        />
                    </DialogFooter>
                </DialogMain>
            </DialogContent>
        </Dialog>
    );
};

export default InviteUserDialog;
