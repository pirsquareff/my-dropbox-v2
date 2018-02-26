package myDropbox_v2_5730329521;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.s3.transfer.model.UploadResult;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;

import javax.xml.bind.DatatypeConverter;

public class myDropbox_v2_5730329521 {
    static AmazonDynamoDB dynamoDBclient = AmazonDynamoDBClientBuilder
            .standard()
            .withCredentials(new ProfileCredentialsProvider())
            .withRegion("ap-southeast-1")
            .build();

    static AmazonS3 s3Client = AmazonS3ClientBuilder
            .standard()
            .withCredentials(new ProfileCredentialsProvider())
            .withRegion("ap-southeast-1")
            .build();

    static String bucketName = "mydropbox-storage";

    private static String currentUser = null;
    private static String currentUid = null;

    private static HashMap<String, String> cachedUidToUsername = new HashMap<>();

    public static void main(String[] args) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDBclient);

        // Print welcome message
        System.out.println("Welcome to myDropbox Application");
        System.out.print("======================================================\n" +
                "Please input command (newuser username password password, login\n" +
                "username password, put filename, get filename, view, or logout).\n" +
                "If you want to quit the program just type quit.\n" +
                "======================================================\n");

        Scanner sc = new Scanner(System.in);
        String command = sc.nextLine();
        String[] commandChunk = command.split(" ");

        while (commandChunk.length > 0 && commandChunk[0].compareTo("quit") != 0) {
            if (commandChunk[0].compareTo("newuser") == 0) {
                Integer exitCode = 1;
                try {
                    exitCode = createNewUser(mapper, commandChunk[1], commandChunk[2], commandChunk[3]);
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("newuser command requires at least 3 arguments. See command arguments below.");
                    System.err.println("newuser <email> <password> <confirm-password>");
                }
                if (exitCode != 0) {
                    System.err.println("Fail to create a new user.");
                } else {
                    System.out.println("OK");
                }
            } else if (commandChunk[0].compareTo("login") == 0) {
                Integer exitCode = 1;
                try {
                    exitCode = login(mapper, commandChunk[1], commandChunk[2]);
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("login command requires at least 2 arguments. See command arguments below.");
                    System.err.println("login <email> <password>");
                }
                if (exitCode != 0) {
                    System.err.println("Fail to login.");
                } else {
                    System.out.println("OK");
                }
            } else if (commandChunk[0].compareTo("put") == 0) {
                Integer exitCode = 1;
                try {
                    // Handle a file's name containing space characters.
                    String filePath = command.split(" ", 2)[1];
                    exitCode = put(mapper, filePath);
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("put command requires at least 1 argument. See command arguments below.");
                    System.err.println("put <file-path>");
                }
                if (exitCode != 0) {
                    System.err.println("Fail to put a file.");
                } else {
                    System.out.println("OK");
                }
            } else if (commandChunk[0].compareTo("get") == 0) {
                Integer exitCode = 1;
                try {
                    // Handle a file's name containing space characters.
                    String fileName;
                    String ownerUsername;
                    if (commandChunk.length > 2 && isEmailValid(commandChunk[commandChunk.length - 1])) {
                        ownerUsername = commandChunk[commandChunk.length - 1];
                        int firstSpaceIndex = command.indexOf(" ");
                        int lastSpaceIndex = command.lastIndexOf(" ");
                        fileName = command.substring(firstSpaceIndex + 1, lastSpaceIndex);
                    } else {
                        // If owner's username is not given, set to current user by default
                        ownerUsername = currentUser;
                        fileName = command.split(" ", 2)[1];
                    }
                    exitCode = get(mapper, fileName, ownerUsername);
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("get command requires at least 1 argument. See command arguments below.");
                    System.err.println("get <file-name>");
                }
                if (exitCode != 0) {
                    System.err.println("Fail to get a file.");
                } else {
                    System.out.println("OK");
                }
            } else if (commandChunk[0].compareTo("view") == 0) {
                Integer exitCode = 1;
                exitCode = view(mapper);
                if (exitCode != 0) {
                    System.err.println("Fail to view files.");
                } else {
                    System.out.println("OK");
                }
            } else if (commandChunk[0].compareTo("share") == 0) {
                Integer exitCode = 1;
                try {
                    // Handle a file's name containing space characters.
                    int firstSpaceIndex = command.indexOf(" ");
                    int lastSpaceIndex = command.lastIndexOf(" ");
                    String fileName = command.substring(firstSpaceIndex + 1, lastSpaceIndex);
                    String username = commandChunk[commandChunk.length - 1];
                    exitCode = share(mapper, fileName, username);
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("share command requires at least 2 arguments. See command arguments below.");
                    System.err.println("share <file-name> [username]");
                }
                if (exitCode != 0) {
                    System.err.println("Fail to share a file.");
                } else {
                    System.out.println("OK");
                }
            } else if (commandChunk[0].compareTo("logout") == 0) {
                Integer exitCode = 1;
                exitCode = logout();
                if (exitCode != 0) {
                    System.err.println("Fail to logout.");
                } else {
                    System.out.println("OK");
                }
            } else {
                System.err.println("Command not found: " + commandChunk[0]);
            }

            // Get a next command
            command = sc.nextLine();
            commandChunk = command.split(" ");
        }

        System.out.print("======================================================\n" +
                "Thank you for using myDropbox.\n" +
                "See you again!\n");
    }

    /**
     * Create a new user.
     *
     * @param {DynamoDBMapper} mapper - A DynamoDB mapper's object.
     * @param {String}         username - A username.
     * @param {String}         password - A password.
     * @param {String}         confirmPassword - A retyped password.
     * @return {Integer} An exit code.
     */
    private static Integer createNewUser(DynamoDBMapper mapper, String username, String password, String confirmPassword) {
        // Password validation
        if (password.compareTo(confirmPassword) != 0) {
            System.err.println("Passwords do not match.");
            return 1;
        }

        // Username validation
        if (!isEmailValid(username)) {
            System.err.println("Username '" + username + "' is invalid. It should be your email address.");
            return 1;
        }
        if (isUsernameExist(mapper, username)) {
            System.err.println("Username '" + username + "' is already existed. Please use a new one.");
            return 1;
        }

        // Generate UID by hashing username with MD5 algorithm
        MessageDigest md;
        String preHashedUsername = username;
        String uid = null;

        // There is still little chance that two different strings can have exact MD5 hash value.
        // To avoid collision, we re-generated new hash value from the previous one till it is unique across all users in the database.
        while (uid == null) {
            try {
                md = MessageDigest.getInstance("MD5");
                md.update(preHashedUsername.getBytes());
                byte[] digest = md.digest();
                uid = DatatypeConverter.printHexBinary(digest).toUpperCase();
                if (isUidExist(mapper, uid)) {
                    // Collide. Hash this collided value again
                    preHashedUsername = uid;
                    uid = null;
                }
            } catch (NoSuchAlgorithmException e) {
                System.err.println(e.getMessage());
                return 1;
            }
        }

        // Generate new salt and hash password
        PasswordManager pm = new PasswordManager();
        String saltedPassword;
        try {
            saltedPassword = pm.getSaltedHash(password);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 1;
        }

        // Add a new user to the database
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(saltedPassword);
        newUser.setUid(uid);
        mapper.save(newUser);
        return 0;
    }

    /**
     * Login.
     *
     * @param {DynamoDBMapper} mapper - A DynamoDB mapper's object.
     * @param {String}         username - A username.
     * @param {String}         password - A password.
     * @return {Integer} An exit code.
     */
    private static Integer login(DynamoDBMapper mapper, String username, String password) {
        // If there is another user currently logged in, skip this log in process
        if (currentUser != null) {
            System.err.println("You have already logged in. If you would like to log in with another account, please log out and then try again.");
            return 1;
        }

        // Retrieve a user by username
        User userRetrieved = null;
        try {
            userRetrieved = mapper.load(User.class, username);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 1;
        }

        // Cannot find a username in the database
        if (userRetrieved == null) {
            System.err.println("Username or password is incorrect.");
            return 1;
        }

        // Check password
        PasswordManager pm = new PasswordManager();
        Boolean isPasswordCorrect = false;
        try {
            isPasswordCorrect = pm.check(password, userRetrieved.getPassword());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 1;
        }
        if (!isPasswordCorrect) {
            System.err.println("Username or password is incorrect.");
            return 1;
        }

        // Set currentUser and currentUid
        currentUser = username;
        currentUid = userRetrieved.getUid();
        return 0;
    }

    /**
     * Put a file to myDropbox.
     *
     * @param {DynamoDBMapper} mapper - A DynamoDB mapper's object.
     * @param {String}         filePath - A relative or absolute file path.
     * @return {Integer} An exit code.
     */
    private static Integer put(DynamoDBMapper mapper, String filePath) {
        if (!isLoggedIn()) {
            System.err.println("You are not logged in. Please login and then try again.");
            return 1;
        }

        TransferManager tm = TransferManagerBuilder
                .standard()
                .withS3Client(s3Client)
                .build();

        File f = new File(filePath);
        String keyName = currentUid + '/' + f.getName();

        PutObjectRequest request = new PutObjectRequest(bucketName, keyName, new File(filePath));

        // Show progress
        // request.setGeneralProgressListener(
        //         (progressEvent) ->
        //                 System.out.println("Transferred bytes: " + progressEvent.getBytesTransferred())
        // );

        Upload upload = tm.upload(request);
        UploadResult uploadResult;

        // Use TransferManager to upload file to S3
        try {
            // Block and wait for the upload to finish
            uploadResult = upload.waitForUploadResult();
        } catch (AmazonClientException ace) {
            System.err.println(ace.getMessage());
            return 1;
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        // Get uploaded object's data
        String uploadedKeyName = uploadResult.getKey();
        String uploadedVersionId = uploadResult.getVersionId();
        Long fileSize;
        Long lastModifiedTimeMs;
        try {
            final ListObjectsV2Request req = new ListObjectsV2Request()
                    .withBucketName(bucketName)
                    .withPrefix(uploadedKeyName)
                    .withMaxKeys(1);
            ListObjectsV2Result result = s3Client.listObjectsV2(req);
            S3ObjectSummary objectSummary = result.getObjectSummaries().get(0);
            fileSize = objectSummary.getSize();
            Date lastModifiedTime = objectSummary.getLastModified();
            lastModifiedTimeMs = new Long(lastModifiedTime.getTime());
        } catch (AmazonServiceException ase) {
            // Roll back
            deleteObject(uploadedKeyName, uploadedVersionId);
            System.err.println(ase.getMessage());
            return 1;
        } catch (AmazonClientException ace) {
            // Roll back
            deleteObject(uploadedKeyName, uploadedVersionId);
            System.err.println(ace.getMessage());
            return 1;
        } catch (IndexOutOfBoundsException e) {
            // Roll back
            deleteObject(uploadedKeyName, uploadedVersionId);
            System.err.println(e.getMessage());
            return 1;
        }

        // Check whether it has already in the database
        FileRecord newFileRecord = null;
        try {
            newFileRecord = mapper.load(FileRecord.class, uploadedKeyName);
        } catch (Exception e) {
            // Roll back
            deleteObject(uploadedKeyName, uploadedVersionId);
            System.err.println(e.getMessage());
            return 1;
        }

        // Create new
        if (newFileRecord == null) {
            newFileRecord = new FileRecord();
            newFileRecord.setKeyName(uploadedKeyName);
        }

        newFileRecord.setVersionId(uploadedVersionId);
        newFileRecord.setOwner(currentUid);
        newFileRecord.setFileSize(fileSize);
        newFileRecord.setLastModifiedTime(lastModifiedTimeMs);

        // Add file metadata to the database
        mapper.save(newFileRecord);
        return 0;
    }

    /**
     * Share a file with another user.
     *
     * @param {DynamoDBMapper} mapper - A DynamoDB mapper's object.
     * @param {String}         fileName - A file's name.
     * @param {String}         username - A username to share a file with.
     * @return {Integer} An exit code.
     */
    private static Integer share(DynamoDBMapper mapper, String fileName, String username) {
        if (!isLoggedIn()) {
            System.err.println("You are not logged in. Please login and then try again.");
            return 1;
        }

        // Check username's existence
        if (!isUsernameExist(mapper, username)) {
            System.err.println("Username '" + username + "' does not exist.");
            return 1;
        }

        // Retrieve a file record
        String keyName = currentUid + "/" + fileName;
        FileRecord fileRecordRetrieved = mapper.load(FileRecord.class, keyName);

        // Prevent sharing a file which a current user does not own
        if (fileRecordRetrieved == null) {
            System.err.println("You are not the owner of this file.");
            return 1;
        }

        // Prevent sharing with the owner
        if (username.compareTo(currentUser) == 0) {
            System.err.println("You have already owned this file. It's unnecessary to share it with the owner.");
            return 1;
        }

        // Get UID
        String uid = getUidByUsername(mapper, username);
        if (uid.compareTo("UID Not Found") == 0) {
            System.err.println("Username '" + username + "' does not exist.");
            return 1;
        }

        // Add UID to sharedBy StringSet
        Set<String> sharedBy = fileRecordRetrieved.getSharedBy();
        if (sharedBy == null) {
            sharedBy = new HashSet<>();
        }
        sharedBy.add(uid);

        // Update the file record
        fileRecordRetrieved.setSharedBy(sharedBy);
        mapper.save(fileRecordRetrieved);
        return 0;
    }

    /**
     * View files owned by or shared with a current user.
     *
     * @param {DynamoDBMapper} mapper - A DynamoDB mapper's object.
     * @return {Integer} An exit code.
     */
    private static Integer view(DynamoDBMapper mapper) {
        if (!isLoggedIn()) {
            System.err.println("You are not logged in. Please login and then try again.");
            return 1;
        }

        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":val1", new AttributeValue().withS(currentUid));

        Map<String, String> ean = new HashMap<>();
        ean.put("#o", "owner");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("#o = :val1 or contains(shared_by, :val1)")
                .withExpressionAttributeNames(ean)
                .withExpressionAttributeValues(eav);

        List<FileRecord> scanResult = mapper.scan(FileRecord.class, scanExpression);

        for (FileRecord file : scanResult) {
            String fileName = file.getKeyName().split("/", 2)[1];
            Long fileSize = file.getFileSize();
            Date lastModifiedTime = new Date(file.getLastModifiedTime());
            String formattedLastModifiedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX").format(lastModifiedTime);
            String ownerUid = file.getOwner();

            // Get username from cache first
            String ownerUsername = cachedUidToUsername.get(ownerUid);
            // It does not exist in cache, get from the database instead
            if (ownerUsername == null) {
                ownerUsername = getUsernameByUid(mapper, ownerUid);
                if (ownerUsername.compareTo("UID Not Found") == 0) {
                    System.err.println("Oops, something went wrong!");
                    return 1;
                }
            }
            System.out.println(fileName + " " + fileSize + " " + formattedLastModifiedTime + " " + ownerUsername);
        }

        return 0;
    }

    /**
     * Get a file.
     *
     * @param {DynamoDBMapper} mapper - A DynamoDB mapper's object.
     * @param {String}         fileName - A file's name.
     * @param {String}         ownerUsername - An owner's username.
     * @return {Integer} An exit code.
     */
    private static Integer get(DynamoDBMapper mapper, String fileName, String ownerUsername) {
        if (!isLoggedIn()) {
            System.err.println("You are not logged in. Please login and then try again.");
            return 1;
        }

        String ownerUid = currentUid;
        if (ownerUsername.compareTo(currentUser) != 0) {
            ownerUid = getUidByUsername(mapper, ownerUsername);
        }

        Map<String, AttributeValue> eav = new HashMap<>();

        // 'owner' is a preserved word in Filter Expression
        Map<String, String> ean = new HashMap<>();
        ean.put("#o", "owner");

        DynamoDBScanExpression scanExpression;
        if (ownerUsername == null || ownerUsername.compareTo(currentUser) == 0) {
            eav.put(":val1", new AttributeValue().withS(ownerUid));
            eav.put(":val2", new AttributeValue().withS(fileName));
            scanExpression = new DynamoDBScanExpression()
                    .withFilterExpression("contains(key_name, :val2) and #o = :val1")
                    .withExpressionAttributeNames(ean)
                    .withExpressionAttributeValues(eav);
        } else {
            eav.put(":val1", new AttributeValue().withS(ownerUid));
            eav.put(":val2", new AttributeValue().withS(fileName));
            eav.put(":val3", new AttributeValue().withS(currentUid));
            scanExpression = new DynamoDBScanExpression()
                    .withFilterExpression("contains(key_name, :val2) and #o = :val1 and contains(shared_by, :val3)")
                    .withExpressionAttributeNames(ean)
                    .withExpressionAttributeValues(eav);
        }

        List<FileRecord> scanResult = mapper.scan(FileRecord.class, scanExpression);
        String keyName;
        String versionId;

        try {
            keyName = scanResult.get(0).getKeyName();
            versionId = scanResult.get(0).getVersionId();
        } catch (IndexOutOfBoundsException e) {
            System.err.println("A file '" + fileName + "' whose owner is '" + ownerUsername + "' does not exist.");
            return 1;
        }

        try {
            // S3Object o = s3Client.getObject(bucketName, keyName);
            S3Object o = s3Client.getObject(
                    new GetObjectRequest(
                            bucketName,
                            keyName,
                            versionId
                    )
            );
            S3ObjectInputStream s3is = o.getObjectContent();
            FileOutputStream fos = new FileOutputStream(new File(fileName));
            byte[] read_buf = new byte[1024];
            int read_len = 0;
            while ((read_len = s3is.read(read_buf)) > 0) {
                fos.write(read_buf, 0, read_len);
            }
            s3is.close();
            fos.close();
        } catch (AmazonServiceException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return 1;
        }
        return 0;
    }

    /**
     * Log out
     *
     * @return {Integer} An exit code.
     */
    private static Integer logout() {
        // If there is no user currently logged in, skip this log out process
        if (currentUser == null) {
            currentUid = null;
            System.err.println("You are not logged in.");
            return 1;
        }

        currentUser = null;
        currentUid = null;
        return 0;
    }

    /**
     * Delete an object with a specific key in S3 bucket
     *
     * @param {String} keyName - A key name of an object.
     * @param {String} versionId - A version ID of an object.
     * @return {Integer} An exit code.
     */
    public static Integer deleteObject(String keyName, String versionId) {
        try {
            s3Client.deleteVersion(
                    new DeleteVersionRequest(
                            bucketName,
                            keyName,
                            versionId)
            );
        } catch (AmazonServiceException ase) {
            System.out.println(ase.getMessage());
            return 1;
        } catch (AmazonClientException ace) {
            System.out.println(ace.getMessage());
            return 1;
        }
        return 0;
    }

    private static Boolean isUsernameExist(DynamoDBMapper mapper, String username) {
        User userRetrieved = null;
        try {
            userRetrieved = mapper.load(User.class, username);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (userRetrieved == null) {
            // Cannot find this username in the database.
            // So, it does not exist.
            return false;
        }
        return true;
    }

    private static Boolean isUidExist(DynamoDBMapper mapper, String uid) {
        HashMap<String, AttributeValue> eav = new HashMap<>();
        eav.put(":v1", new AttributeValue().withS(uid));

        DynamoDBQueryExpression<User> queryExpression = new DynamoDBQueryExpression<User>()
                .withIndexName("uid-index")
                .withConsistentRead(false)
                .withKeyConditionExpression("uid = :v1")
                .withExpressionAttributeValues(eav);
        List<User> user = mapper.query(User.class, queryExpression);
        if (user.isEmpty()) {
            return false;
        }
        return true;
    }

    private static String getUsernameByUid(DynamoDBMapper mapper, String uid) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":val1", new AttributeValue().withS(uid));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("uid = :val1")
                .withExpressionAttributeValues(eav);

        List<User> scanResult = mapper.scan(User.class, scanExpression);

        if (scanResult.isEmpty()) {
            return "UID Not Found";
        }

        return scanResult.get(0).getUsername();
    }

    private static String getUidByUsername(DynamoDBMapper mapper, String username) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":val1", new AttributeValue().withS(username));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("username = :val1")
                .withExpressionAttributeValues(eav);

        List<User> scanResult = mapper.scan(User.class, scanExpression);

        if (scanResult.isEmpty()) {
            return "Username Not Found";
        }

        return scanResult.get(0).getUid();
    }

    private static Boolean isLoggedIn() {
        if (currentUser == null) {
            return false;
        }
        return true;
    }

    private static Boolean isEmailValid(String email) {
        Pattern VALID_EMAIL_ADDRESS_REGEX =
                Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(email);
        if (!matcher.find()) {
            return false;
        }

        return true;
    }
}
    

