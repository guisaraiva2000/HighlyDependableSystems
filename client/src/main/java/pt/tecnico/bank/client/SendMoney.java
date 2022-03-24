package pt.tecnico.bank.client;

//TODO
import com.google.protobuf.ByteString;

public class SendMoney {
    
    public ByteString originAccount;
    public ByteString destinationAccount;
    public float amount;
    public byte[] nonce;

    public SendMoney(ByteString originAccount, ByteString destinationAccount, float amount, byte[] nonce)
    {
        this.originAccount = originAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.nonce = nonce;
    }

    public ByteString getOriginAccount()
    {
        return originAccount;
    }

    public ByteString getDestinationAccount()
    {
        return destinationAccount;
    }

    public float getAmount()
    {
        return amount;
    }


}
