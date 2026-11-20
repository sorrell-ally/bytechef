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
import Switch from '@/components/Switch/Switch';
import {Checkbox} from '@/components/ui/checkbox';
import {Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage} from '@/components/ui/form';
import ProjectGitSyncLastSync from '@/pages/automation/project/components/git-sync/ProjectGitSyncLastSync';
import ProjectGitSyncPullResult from '@/pages/automation/project/components/git-sync/ProjectGitSyncPullResult';
import {
    ProjectGitSyncInput,
    useDeleteProjectGitSyncMutation,
    useProjectGitSyncQuery,
    usePullProjectGitSyncMutation,
    usePushProjectGitSyncMutation,
    useSaveProjectGitSyncMutation,
    useTestProjectGitSyncConnectionMutation,
} from '@/shared/middleware/graphql';
import {ProjectVersionKeys} from '@/shared/queries/automation/projectVersions.queries';
import {ProjectKeys} from '@/shared/queries/automation/projects.queries';
import {zodResolver} from '@hookform/resolvers/zod';
import {useQueryClient} from '@tanstack/react-query';
import {GitBranchIcon, GitPullRequestArrowIcon, UploadIcon} from 'lucide-react';
import {useState} from 'react';
import {useForm} from 'react-hook-form';
import {toast} from 'sonner';
import {z} from 'zod';

const formSchema = z.object({
    accessToken: z.string(),
    branch: z.string().trim().min(1, {message: 'Branch is required'}),
    clearAccessToken: z.boolean(),
    enabled: z.boolean(),
    repositoryUrl: z
        .string()
        .trim()
        .min(1, {message: 'Repository URL is required'})
        .refine((value) => value.toLowerCase().startsWith('https://'), {message: 'Only HTTPS URLs are supported'}),
    subDirectory: z.string(),
    username: z.string(),
});

type FormValuesType = z.infer<typeof formSchema>;

interface ProjectGitSyncDialogProps {
    onClose: () => void;
    projectId: number;
}

const ProjectGitSyncDialog = ({onClose, projectId}: ProjectGitSyncDialogProps) => {
    const [confirmRemove, setConfirmRemove] = useState(false);

    const queryClient = useQueryClient();

    const projectGitSyncQuery = useProjectGitSyncQuery({projectId: String(projectId)});

    const projectGitSync = projectGitSyncQuery.data?.projectGitSync;

    const form = useForm<FormValuesType>({
        defaultValues: {
            accessToken: '',
            branch: 'main',
            clearAccessToken: false,
            enabled: true,
            repositoryUrl: '',
            subDirectory: '',
            username: '',
        },
        resolver: zodResolver(formSchema),
        values: projectGitSyncQuery.isSuccess
            ? {
                  accessToken: '',
                  branch: projectGitSync?.branch ?? 'main',
                  clearAccessToken: false,
                  enabled: projectGitSync?.enabled ?? true,
                  repositoryUrl: projectGitSync?.repositoryUrl ?? '',
                  subDirectory: projectGitSync?.subDirectory ?? '',
                  username: projectGitSync?.username ?? '',
              }
            : undefined,
    });

    const invalidateGitSync = () => {
        void queryClient.invalidateQueries({queryKey: ['projectGitSync']});
        void queryClient.invalidateQueries({queryKey: ['workspaceProjectGitSyncs']});
    };

    const saveMutation = useSaveProjectGitSyncMutation({
        onSuccess: () => {
            invalidateGitSync();

            toast('Git sync settings saved.');
        },
    });

    const testConnectionMutation = useTestProjectGitSyncConnectionMutation();

    const pushMutation = usePushProjectGitSyncMutation({
        onSuccess: () => {
            invalidateGitSync();

            toast('Push finished.');
        },
    });

    const pullMutation = usePullProjectGitSyncMutation({
        onSuccess: () => {
            invalidateGitSync();

            void queryClient.invalidateQueries({queryKey: ProjectKeys.project(projectId)});
            void queryClient.invalidateQueries({queryKey: ProjectKeys.projects});
            void queryClient.invalidateQueries({queryKey: ProjectVersionKeys.projectProjectVersions(projectId)});
        },
    });

    const deleteMutation = useDeleteProjectGitSyncMutation({
        onSuccess: () => {
            invalidateGitSync();

            toast('Git sync configuration removed.');

            onClose();
        },
    });

    const accessTokenSet = projectGitSync?.accessTokenSet ?? false;

    const buildInput = (values: FormValuesType): ProjectGitSyncInput => {
        const input: ProjectGitSyncInput = {
            branch: values.branch.trim(),
            enabled: values.enabled,
            repositoryUrl: values.repositoryUrl.trim(),
            subDirectory: values.subDirectory.trim(),
            username: values.username.trim(),
        };

        if (values.clearAccessToken) {
            input.accessToken = '';
        } else if (values.accessToken) {
            input.accessToken = values.accessToken;
        }

        return input;
    };

    const handleSubmit = (values: FormValuesType) => {
        const repositoryUrlChanged = !!projectGitSync && projectGitSync.repositoryUrl !== values.repositoryUrl.trim();

        if (repositoryUrlChanged && accessTokenSet && !values.accessToken && !values.clearAccessToken) {
            form.setError('accessToken', {
                message: 'Enter the access token again when the repository URL changes.',
                type: 'manual',
            });

            return;
        }

        saveMutation.mutate({input: buildInput(values), projectId: String(projectId)});
    };

    const handleTestConnectionClick = async () => {
        if (!(await form.trigger(['branch', 'repositoryUrl']))) {
            return;
        }

        testConnectionMutation.mutate({input: buildInput(form.getValues()), projectId: String(projectId)});
    };

    const handleRemoveClick = () => {
        if (!confirmRemove) {
            setConfirmRemove(true);

            return;
        }

        deleteMutation.mutate({projectId: String(projectId)});
    };

    const testResult = testConnectionMutation.data?.testProjectGitSyncConnection;
    const pullResult = pullMutation.data?.pullProjectGitSync;
    const busy =
        saveMutation.isPending ||
        pushMutation.isPending ||
        pullMutation.isPending ||
        deleteMutation.isPending ||
        testConnectionMutation.isPending;

    return (
        <Dialog onOpenChange={(open) => !open && onClose()} open>
            <DialogContent size="md">
                <DialogMain>
                    <DialogHeader
                        description="Push published project versions to a Git branch and pull them back into the draft."
                        title="Git Sync"
                    />

                    {projectGitSyncQuery.isLoading ? (
                        <div className="flex justify-center p-8">
                            <LoadingIcon />
                        </div>
                    ) : (
                        <Form {...form}>
                            <form className="flex min-h-0 flex-1 flex-col" onSubmit={form.handleSubmit(handleSubmit)}>
                                <DialogBody>
                                    <FormField
                                        control={form.control}
                                        name="repositoryUrl"
                                        render={({field}) => (
                                            <FormItem>
                                                <FormLabel>Repository URL</FormLabel>

                                                <FormControl>
                                                    <Input
                                                        placeholder="https://github.com/acme/projects.git"
                                                        {...field}
                                                    />
                                                </FormControl>

                                                <FormDescription>HTTPS URL without credentials.</FormDescription>

                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />

                                    <FormField
                                        control={form.control}
                                        name="branch"
                                        render={({field}) => (
                                            <FormItem>
                                                <FormLabel>Branch</FormLabel>

                                                <FormControl>
                                                    <Input placeholder="main" {...field} />
                                                </FormControl>

                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />

                                    <FormField
                                        control={form.control}
                                        name="subDirectory"
                                        render={({field}) => (
                                            <FormItem>
                                                <FormLabel>Sub-directory (optional)</FormLabel>

                                                <FormControl>
                                                    <Input placeholder="projects/sales" {...field} />
                                                </FormControl>

                                                <FormDescription>
                                                    Directory holding project.json and workflows/. Leave empty for the
                                                    repository root.
                                                </FormDescription>

                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />

                                    <FormField
                                        control={form.control}
                                        name="username"
                                        render={({field}) => (
                                            <FormItem>
                                                <FormLabel>Username</FormLabel>

                                                <FormControl>
                                                    <Input autoComplete="off" placeholder="Git username" {...field} />
                                                </FormControl>

                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />

                                    <FormField
                                        control={form.control}
                                        name="accessToken"
                                        render={({field}) => (
                                            <FormItem>
                                                <FormLabel>Access token</FormLabel>

                                                <FormControl>
                                                    <Input
                                                        autoComplete="new-password"
                                                        placeholder={accessTokenSet ? 'stored' : 'Access token'}
                                                        type="password"
                                                        {...field}
                                                    />
                                                </FormControl>

                                                <FormDescription>
                                                    Write-only. Leave empty to keep the stored token.
                                                </FormDescription>

                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />

                                    {accessTokenSet && (
                                        <FormField
                                            control={form.control}
                                            name="clearAccessToken"
                                            render={({field}) => (
                                                <FormItem>
                                                    <div className="flex items-center gap-2">
                                                        <FormControl>
                                                            <Checkbox
                                                                checked={field.value}
                                                                onCheckedChange={field.onChange}
                                                            />
                                                        </FormControl>

                                                        <FormLabel>Remove stored access token</FormLabel>
                                                    </div>
                                                </FormItem>
                                            )}
                                        />
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
                                                    Push automatically whenever a project version is published.
                                                </FormDescription>
                                            </FormItem>
                                        )}
                                    />

                                    <div className="flex items-center gap-2">
                                        <Button
                                            disabled={busy}
                                            icon={<GitBranchIcon />}
                                            label="Test connection"
                                            onClick={handleTestConnectionClick}
                                            type="button"
                                            variant="outline"
                                        />

                                        {testConnectionMutation.isPending && <LoadingIcon />}
                                    </div>

                                    {testResult && (
                                        <div aria-live="polite" className="rounded-md border p-3 text-sm" role="status">
                                            {testResult.success ? (
                                                <>
                                                    <p className="font-medium">Connection successful.</p>

                                                    <p>
                                                        {testResult.branchExists
                                                            ? 'The configured branch exists.'
                                                            : 'The configured branch does not exist yet.'}
                                                    </p>

                                                    {testResult.branches.length > 0 && (
                                                        <p>Branches: {testResult.branches.join(', ')}</p>
                                                    )}
                                                </>
                                            ) : (
                                                <p className="text-content-destructive-primary">
                                                    Connection failed: {testResult.errorMessage || 'Unknown error'}
                                                </p>
                                            )}
                                        </div>
                                    )}

                                    {projectGitSync && (
                                        <>
                                            <ProjectGitSyncLastSync projectGitSync={projectGitSync} />

                                            <div className="flex items-center gap-2">
                                                <Button
                                                    disabled={busy}
                                                    icon={<UploadIcon />}
                                                    label="Push now"
                                                    onClick={() => pushMutation.mutate({projectId: String(projectId)})}
                                                    type="button"
                                                    variant="outline"
                                                />

                                                <Button
                                                    disabled={busy}
                                                    icon={<GitPullRequestArrowIcon />}
                                                    label="Pull"
                                                    onClick={() => pullMutation.mutate({projectId: String(projectId)})}
                                                    type="button"
                                                    variant="outline"
                                                />

                                                {(pushMutation.isPending || pullMutation.isPending) && <LoadingIcon />}
                                            </div>

                                            {pullResult && <ProjectGitSyncPullResult pullResult={pullResult} />}
                                        </>
                                    )}
                                </DialogBody>

                                <DialogFooter>
                                    {projectGitSync && (
                                        <Button
                                            className="mr-auto"
                                            disabled={busy}
                                            label={confirmRemove ? 'Confirm removal' : 'Remove configuration'}
                                            onClick={handleRemoveClick}
                                            type="button"
                                            variant="destructiveGhost"
                                        />
                                    )}

                                    <DialogCancelButton />

                                    <Button disabled={busy} label="Save" type="submit" />
                                </DialogFooter>
                            </form>
                        </Form>
                    )}
                </DialogMain>
            </DialogContent>
        </Dialog>
    );
};

export default ProjectGitSyncDialog;
