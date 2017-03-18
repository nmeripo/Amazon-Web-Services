aws s3 rb --force s3://cloudpi-requests
aws s3 mb s3://cloudpi-requests

instance_id=$(aws ec2 run-instances --image-id ami-3bd35a5b --count 1 --instance-type t2.micro --key-name aws --security-groups launch-wizard-1 --user-data "#!/bin/bash\nsudo service httpd restart" --output text --query 'Instances[*].InstanceId') 

x="not running"

while [ "$x" != "running" ]; do
             x=$(aws ec2 describe-instances --instance-ids $instance_id --filters Name=instance-state-code,Values=16 --region us-west-2 --output text --query Reservations[].Instances[].State.Name)
         done

aws ec2 associate-address --instance-id $instance_id --allocation-id eipalloc-48ea082e






