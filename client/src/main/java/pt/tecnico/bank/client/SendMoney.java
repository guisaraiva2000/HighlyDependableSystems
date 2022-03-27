package pt.tecnico.bank.client;

public class SendMoney {
    
    public byte[] originAccount;
    public byte[] destinationAccount;
    public float amount;
    public String nonce;

    public SendMoney(byte[] originAccount, byte[] destinationAccount, float amount, String nonce)
    {
        this.originAccount = originAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.nonce = nonce;
    }

    public byte[] getOriginAccount()
    {
        return originAccount;
    }

    public byte[] getDestinationAccount()
    {
        return destinationAccount;
    }

    public float getAmount()
    {
        return amount;
    }


}
