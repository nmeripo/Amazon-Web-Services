#!/bin/bash

aws ec2 describe-instances --query 'Reservations[*].Instances[*].[InstanceId]' --output text | column -t  | while read x; 


do 
echo -n $x
echo -n ", "
aws cloudwatch get-metric-statistics --metric-name CPUUtilization --start-time $(date '+%Y-%m-%dT00:00:00') --end-time $(date "+%Y-%m-%dT%H:%M:%S") --period 3600 --namespace AWS/EC2 --statistics Maximum --dimensions Name=InstanceId,Value=$x --output text --query 'Datapoints[0].Maximum';
echo ""
done




