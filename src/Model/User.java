package Model;

import Server.Worker;

import java.net.Socket;
import java.util.*;


/**
 * Object cho người dùng khi kết nối vào Server
 */
public class User {
    public static final String STATUS_ONLINE = "online";
    public static final String STATUS_OFFLINE = "offline";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_VERIFIED = "verified";
    public static final String STATUS_DUPLICATED = "duplicated";

    private final String UID;
    private Socket socket;
    private Worker worker;
    private String secretKey;
    private long sessionTime;
    private String status;
    private String modifiedDate;
    private final ArrayList<String> requestList;
    private final ArrayList<String> responseList;
    private final ArrayList<String> dateList;

    public User(String UID) throws IllegalArgumentException{
        this.UID = UUID.fromString(UID).toString();
        requestList = new ArrayList<>();
        responseList = new ArrayList<>();
        dateList = new ArrayList<>();
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public Worker getWorker() {
        return worker;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public String getUID() {
        return UID;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public long getSessionTime() {
        return sessionTime;
    }

    public void setSessionTime(long sessionTime) {
        this.sessionTime = sessionTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(String modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public ArrayList<String> getRequestList() {
        return requestList;
    }

    public void addRequestList(String data) {
        this.requestList.add(data);
    }

    public ArrayList<String> getResponseList() {
        return responseList;
    }

    public void addResponseList(String data) {
        this.responseList.add(data);
    }

    public ArrayList<String> getDateList() {
        return dateList;
    }

    public void addDateList(String date) {
        this.dateList.add(date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return getUID().equals(user.getUID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUID());
    }
}
