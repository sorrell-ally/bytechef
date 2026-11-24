#!/bin/bash
# Create a change set for the existing bytechef-dev stack. Executes it only with --execute.
set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  deploy.sh --profile PROFILE --change-set-name NAME --use-previous [--set Key=Value ...] [--region REGION] [--execute] [--dry-run]
  deploy.sh --profile PROFILE --change-set-name NAME --parameters-file FILE [--region REGION] [--execute] [--dry-run]

Creates a CloudFormation change set of type UPDATE for the existing stack bytechef-dev
from cfn/bytechef-dev.yaml. The change set is executed only when --execute is set.
This script does not call create-stack.

--use-previous sends UsePreviousValue for every template parameter, so the encryption
key and the Neon password stay as they are. Add --set Key=Value for each parameter
that should change, for example --set ImageTag=202611241943-5a0d6ab7f000.
NeonPassword and EncryptionKey are rejected on the command line.

--parameters-file is a CloudFormation parameters JSON file. See params.example.json
for the shape. Do not apply the example file to the running stack: its placeholders
would replace the secrets, and DesiredCount 0 would stop the service.

--region defaults to us-east-2.
--dry-run prints the aws commands and does not call AWS.
EOF
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
template_file="${script_dir}/cfn/bytechef-dev.yaml"
stack_name=bytechef-dev

profile=""
region=us-east-2
change_set_name=""
parameters_file=""
use_previous=false
execute=false
dry_run=false

overrides_file=$(mktemp)
trap 'rm -f "$overrides_file"' EXIT

template_parameter_keys() {
    awk '
        /^Parameters:/ { in_params = 1; next }
        /^[A-Za-z]/ { in_params = 0 }
        in_params && /^  [A-Za-z0-9]+:$/ {
            name = $1
            sub(":$", "", name)
            print name
        }
    ' "$template_file"
}

parameter_known() {
    local candidate=$1
    local key

    while IFS= read -r key; do
        if [ "$key" = "$candidate" ]; then
            return 0
        fi
    done < <(template_parameter_keys)

    return 1
}

lookup_override() {
    local key=$1

    awk -F '=' -v key="$key" '
        $1 == key {
            value = substr($0, index($0, "=") + 1)
            found = 1
        }
        END {
            if (found) {
                print value
                exit 0
            }
            exit 1
        }
    ' "$overrides_file"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --profile)
            profile=$2
            shift 2
            ;;
        --region)
            region=$2
            shift 2
            ;;
        --change-set-name)
            change_set_name=$2
            shift 2
            ;;
        --parameters-file)
            parameters_file=$2
            shift 2
            ;;
        --use-previous)
            use_previous=true
            shift
            ;;
        --set)
            set_argument=$2
            case "$set_argument" in
                *=*) ;;
                *)
                    echo "--set expects Key=Value." >&2
                    exit 2
                    ;;
            esac
            set_key=${set_argument%%=*}
            set_value=${set_argument#*=}
            if [ -z "$set_key" ] || [ -z "$set_value" ]; then
                echo "--set expects a non-empty Key and Value." >&2
                exit 2
            fi
            case "$set_key" in
                NeonPassword|EncryptionKey)
                    echo "Refusing ${set_key} on the command line. Use a gitignored parameters file, or --use-previous so the stored value stays." >&2
                    exit 2
                    ;;
            esac
            case "$set_value" in
                *[[:space:],]*)
                    echo "--set values cannot contain whitespace or commas. Use --parameters-file." >&2
                    exit 2
                    ;;
            esac
            if ! parameter_known "$set_key"; then
                echo "Unknown template parameter: ${set_key}" >&2
                exit 2
            fi
            printf '%s=%s\n' "$set_key" "$set_value" >> "$overrides_file"
            shift 2
            ;;
        --execute)
            execute=true
            shift
            ;;
        --dry-run)
            dry_run=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [ -z "$profile" ] || [ -z "$change_set_name" ]; then
    usage >&2
    exit 2
fi

if [ ! -f "$template_file" ]; then
    echo "Template not found: ${template_file}" >&2
    exit 2
fi

if ! [[ "$change_set_name" =~ ^[A-Za-z][A-Za-z0-9-]{0,127}$ ]]; then
    echo "Change set name must start with a letter, contain only letters, numbers, and hyphens, and be at most 128 characters." >&2
    exit 2
fi

if [ -n "$parameters_file" ] && [ "$use_previous" = true ]; then
    echo "Pass either --parameters-file or --use-previous, not both." >&2
    exit 2
fi

if [ -n "$parameters_file" ] && [ -s "$overrides_file" ]; then
    echo "Pass either --parameters-file or --set, not both." >&2
    exit 2
fi

if [ -z "$parameters_file" ] && [ "$use_previous" != true ]; then
    echo "Pass --use-previous (with optional --set) or --parameters-file." >&2
    exit 2
fi

if [ -n "$parameters_file" ] && [ ! -f "$parameters_file" ]; then
    echo "Parameters file not found: ${parameters_file}" >&2
    exit 2
fi

if [ "$dry_run" != true ] && ! command -v aws >/dev/null 2>&1; then
    echo "aws is not on PATH." >&2
    exit 2
fi

run_aws() {
    if [ "$dry_run" = true ]; then
        printf 'dry-run:'
        local argument
        for argument in "$@"; do
            printf ' %s' "$argument"
        done
        printf '\n'
        return 0
    fi

    aws "$@"
}

aws_common=(
    --profile "$profile"
    --region "$region"
    --stack-name "$stack_name"
)

create_args=(
    cloudformation create-change-set
    "${aws_common[@]}"
    --change-set-name "$change_set_name"
    --change-set-type UPDATE
    --template-body "file://${template_file}"
    --capabilities CAPABILITY_IAM
)

if [ -n "$parameters_file" ]; then
    create_args+=(--parameters "file://${parameters_file}")
else
    parameter_arguments=()
    while IFS= read -r key; do
        if value=$(lookup_override "$key"); then
            parameter_arguments+=("ParameterKey=${key},ParameterValue=${value}")
        else
            parameter_arguments+=("ParameterKey=${key},UsePreviousValue=true")
        fi
    done < <(template_parameter_keys)

    create_args+=(--parameters "${parameter_arguments[@]}")
fi

run_aws "${create_args[@]}"

if [ "$dry_run" = true ]; then
    run_aws cloudformation wait change-set-create-complete "${aws_common[@]}" --change-set-name "$change_set_name"
    if [ "$execute" = true ]; then
        run_aws cloudformation execute-change-set "${aws_common[@]}" --change-set-name "$change_set_name"
    fi
    exit 0
fi

if ! aws cloudformation wait change-set-create-complete "${aws_common[@]}" --change-set-name "$change_set_name"; then
    echo "Change set ${change_set_name} did not become CREATE_COMPLETE." >&2
    echo "aws cloudformation describe-change-set --stack-name ${stack_name} --change-set-name ${change_set_name} --profile ${profile} --region ${region}" >&2
    exit 1
fi

if [ "$execute" = true ]; then
    aws cloudformation execute-change-set "${aws_common[@]}" --change-set-name "$change_set_name"

    echo "Submitted change set ${change_set_name}."
    exit 0
fi

echo "Change set ${change_set_name} is ready and has not been executed."
echo "Review it, then re-run this command with --execute."
echo "aws cloudformation describe-change-set --stack-name ${stack_name} --change-set-name ${change_set_name} --profile ${profile} --region ${region}"
