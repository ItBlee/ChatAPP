package Services;

import java.util.Objects;

/**
 * DATA TRANSFER OBJECT
 */
public class DTO {
    private final String header;
    private String sender;
    private String receiver;
    private String data;
    private String createdDate;

    public DTO(String header) {
        this.header = header;
    }

    public String getHeader() {
        return header;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public String toString() {
        return JsonParser.pack(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DTO)) return false;
        DTO dto = (DTO) o;
        return Objects.equals(getHeader(), dto.getHeader()) && Objects.equals(getData(), dto.getData());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHeader(), getData());
    }
}
