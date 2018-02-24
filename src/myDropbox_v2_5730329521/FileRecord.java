package myDropbox_v2_5730329521;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

import java.util.Set;

@DynamoDBTable(tableName = "myDropboxFiles")
public class FileRecord {
    private String keyName;
    private String owner;
    private Set<String> sharedBy;

    @DynamoDBHashKey(attributeName = "key_name")
    public String getKeyName() {
        return keyName;
    }
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    @DynamoDBAttribute(attributeName = "owner")
    public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }

    @DynamoDBAttribute(attributeName = "shared_by")
    public Set<String> getSharedBy() {
        return sharedBy;
    }
    public void setSharedBy(Set<String> sharedBy) {
        this.sharedBy = sharedBy;
    }
}
