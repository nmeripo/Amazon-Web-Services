#!/bin/bash

BUCKET=cloudpi-requests
suffix='.txt'
for key in `aws s3api list-objects --bucket $BUCKET --query 'Contents[].Key' --output text | column -t`

do
  echo -n "$key" | sed -e "s/$suffix$//"
  echo -n ", "
  aws s3 cp --quiet s3://$BUCKET/$key /dev/stdout
done


