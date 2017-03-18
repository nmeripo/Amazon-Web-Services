<?php
require './vendor/autoload.php';

// Include the AWS SDK using the Composer autoloader.

useAwsS3S3Client;
useAwsS3ExceptionS3Exception;
useAwsSqsSqsClient;

// Instantiate the client.

$s3 = S3Client::factory(['credentials' => ['key' => '* * * * * * * * * * * * * * * *,
      'secret'=>' * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *',
      ],
'version' => 'latest',
'region' => 'us - west - 2',
]);



// Get Input

$x = '';
if (isset($_GET['input']))
{
     $x = $_GET['input'];
}

$bucket = "cloudpi-requests";
$key = '';
$key .= $x . ' . txt';


//  Check if corresponding output with the input file name exists in S3 bucket


$keyExists = $s3->doesObjectExist($bucket,$key);
if($keyExists) {

$result = $s3->getObject([
    'Bucket' => $bucket,
    'Key'    => $key
]);


echo $result['Body'];
}


else {


// Execute app-tier java code


$op = shell_exec("java -jar cloudpi-0.1-jar-with-dependencies.jar $x 2>&1");


$foo = True;


// Poll S3 bucket again


while ($foo) {
    $keyExists = $s3->doesObjectExist($bucket,$key);
    if($keyExists) {
	$foo = False;
        $result = $s3->getObject([
            'Bucket' => $bucket,
            'Key'    => $key
        ]);

         echo $result['Body];
}

