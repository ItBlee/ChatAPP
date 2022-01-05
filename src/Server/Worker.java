package Server;

import Security.AES_Encryptor;
import Services.DTO;
import Services.History;
import Services.Header;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;

public class Worker extends Thread implements Runnable{
    private User user;
    private User pairUser;
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final String IP;
    private final String fromIP;
    private boolean isPaired = false;

    private WorkerPair finder;

    //Danh sách worker đã từ chối ghép cặp
    private final Vector<String> denied = new Vector<>();
    //Danh sách lịch sử chat với mỗi element là danh sách chat với worker nào đó
    private final HashMap<User, ArrayList<History>> histories = new HashMap<>();

    /**
     * Tạo ra một thread mới để kết nối và xử lý yêu cầu từ phía Client
     * @param clientSocket truyền socket kết nối Client vào
     */
    public Worker(Socket clientSocket) throws IOException {
        socket = clientSocket;
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        IP = clientSocket.getInetAddress() //tách InetAddress từ socket
                .getHostAddress(); //in địa chỉ IP từ InetAddress của client kết nối tới
        fromIP = IP + ":" + clientSocket.getPort(); //in port của client kết nối tới từ socket
                                                    //vd: 127.0.0.1:5013

        if (Server.users.size() + 1 > Server.EXECUTOR_MAX) {
            DTO dto = new DTO(Header.SERVER_BUSY_HEADER);
            responseHandle(dto);
        }
    }

    public User getUser() {
        return user;
    }

    public User getPair() {
        return pairUser;
    }

    public Vector<String> getDenied() {
        return denied;
    }

    public HashMap<User, ArrayList<History>> getHistories() {
        return histories;
    }

    //Set đối tượng ghép cặp
    public void pairWith(User pair) {
        this.pairUser = pair;
    }

    //Khóa đối tượng muốn bắt cặp lại tránh xung đốt
    public void lockPair() {
        if (!isPaired)
            isPaired = true;
    }

    //Mở khóa đối tượng
    public void unlockPair() {
        if (isPaired)
            isPaired = false;
    }

    //Kiểm tra đối tượng có bị khóa không
    public boolean isPaired() {
        return isPaired;
    }

    //Loại bỏ ghép cặp khi có 1 bên thoát chat
    public void breakPair() throws IOException {
        getPair().getWorker().pairWith(null);
        getPair().getWorker().unlockPair();
        getPair().getWorker().responseHandle(new DTO(Header.PAIR_LEFT_HEADER));
        pairWith(null);
        unlockPair();
    }

    /**
     * Sau khi gọi hàm start() từ Thread sẽ tự động chày hàm run()
     */
    @Override
    public void run() {
        try {
            System.out.println("Client " + fromIP + " is connected.");

            //Xử lý xác minh uid gửi từ Client trước khi cho phép trao đổi data.
            verify();

            //Lặp liên tục để nhận request từ phía Client.
            while(true) {
                try {
                    //Chờ thông điệp từ Client rồi xử lý
                    String line = receive();
                    //Vòng lặp sẽ ngừng khi Client gửi lệnh "Break_Connect"
                    if (line.equals(Header.BREAK_CONNECT_HEADER))
                        break;

                    //Xử lý dữ liệu bằng class ServerHandler method responseHandle
                    DTO packet = requestHandle(line);
                    if (packet == null)
                        continue;
                    responseHandle(packet);
                } catch (Exception e) {
                    //Có exception thì break vòng lặp để close socket.
                    break;
                }
            }

            //Thực hiện đóng kết nối socket và đóng cổng đầu vào (in) + đầu ra (out).
            close();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            //Thực hiện đóng kết nối socket và đóng cổng đầu vào (in) + đầu ra (out).
            close();
        }
    }

    private void verify() throws IOException, NullPointerException {
        String verifyStatus = "Expired";
        do {
            try {
                user = new User(receive());
            } catch (IllegalArgumentException e) {
                send(verifyStatus);
                continue;
            }

            //check xem user có status như nào: Expired, Online, Banned.
            //check trong list ban.
            if (Server.banList.containsKey(IP)
                ||Server.banList.containsValue(user.getUID())) {
                send("Banned");
                close();
                throw new IOException();
            }

            //check trong list user.
            for (User u : Server.users) {
                if (u.equals(user)) {
                    if (u.getStatus().equalsIgnoreCase("online")) {
                        verifyStatus = "Duplicated";
                        break;
                    }

                    user = u;
                    user.setSocket(socket);
                    user.setWorker(Worker.this);
                    user.setStatus("online");
                    if (user.getSessionTime() != -1)
                        verifyStatus = "Verified";
                    break;
                }
            }

            send(verifyStatus);
        } while (!verifyStatus.equals("Verified"));
        System.out.println("Client " + fromIP
                + " - " + verifyStatus + " with ID: " + user.getUID()
                + " | Key: " + user.getSecretKey());

        recheckIfTargetAtManager(user);
    }

    //Hàm kiểm tra tên có tồn tại chưa
    public boolean checkName(String name) {
        for (User user : Server.users) {
            if (name.equalsIgnoreCase(user.getName()))
                return false;
        }
        return true;
    }

    private void reloadUser() {
        //check trong list user.
        for (User u : Server.users) {
            if (u.getUID().equals(user.getUID())) {
                user = u;
            }
        }
    }

    public static void recheckIfTargetAtManager(User user) {
        String managerCurrentTarget = Server.manager.textField.getText();
        String IP = user.getSocket()
                .getInetAddress().getHostAddress()
                + ":" + user.getSocket().getPort();
        //vd: 127.0.0.1:5013
        if (managerCurrentTarget.equals(user.getUID())
                || IP.contains(managerCurrentTarget)) {
            Server.manager._btnCheck.doClick();
        }
    }

    public void send(String data) throws IOException, NullPointerException {
        out.write(data);
        out.newLine();
        out.flush();
    }

    public void sendToPair(String data) throws IOException, NullPointerException {
        getPair().getWorker().out.write(data);
        getPair().getWorker().out.newLine();
        getPair().getWorker().out.flush();
    }

    public String receive() throws IOException, NullPointerException {
        return in.readLine();
    }

    public void close(){
        try {
            send(Header.BREAK_CONNECT_HEADER);
            if (isPaired())
                breakPair();
            stopFinder();
            in.close();
            out.close();
            if (user.getStatus().equals("online"))
                user.setStatus("offline");
            user.getSocket().close();

            recheckIfTargetAtManager(user);
            System.out.println("Server closed connection with " + fromIP + "- ID: " + user.getUID());
        } catch (Exception ignored) {}
    }

    public void clearInfo() {
        Server.queue.remove(user);
        System.out.println(Server.queue);
        user.setName(null);
        denied.clear();
        histories.clear();
    }

    public void stopFinder() {
        if (finder == null)
            return;
        finder.interrupt();
        finder = null;
        clearInfo();
    }

    /**
     * Hàm dùng để xử lý dữ liệu từ Client gửi tới
     * @param data dữ liệu từ client đã bị mã hóa
     * @return ClientDataPacket - Gói dữ liệu Client
     */
    private DTO requestHandle(String data) throws IOException {
        if (data == null || data.isEmpty() || data.isBlank() || data.equals("null"))
            return null;

        System.out.println("Server get: " + data
                + " from " + fromIP
                + " - ID: " + user.getUID());

        if (data.equalsIgnoreCase(Server.RENEW_USER_SESSION)) {
            reloadUser(); //đọc lại user từ list user.
            recheckIfTargetAtManager(user);//For server manager (bỏ qua)
            return null;
        }

        if (user.getSessionTime() == Server.SESSION_EXPIRED_TIME) {
            System.out.println("Secret Key of " + user.getUID() + " expired !");
            send("Expired");
            return null;
        }

        String decryptJson = AES_Encryptor.decrypt(data, getUser().getSecretKey()); //giả mã bằng secret key
        user.addRequestList(Services.JsonParser.unpack(decryptJson).toString());
        user.addDateList(LocalDateTime.now().toString());
        DTO dto = Services.JsonParser.unpack(decryptJson);
        switch (dto.getHeader()) {
            case Header.FIND_CHAT_HEADER:
                if (finder == null) {
                    finder = new WorkerPair(Worker.this);
                    finder.start();
                }
                return null;

            case Header.STOP_FIND_HEADER:
                if (finder != null)
                    stopFinder();
                return null;

            case Header.INVITE_CHAT_HEADER:
                if (finder != null)
                    finder.replyInvite(dto.getData());
                return null;

            case Header.CONFIRM_CHAT_HEADER:
                WorkerPair pair = null;
                for (User user:Server.users) {
                    if (dto.getSender().equals(user.getUID()))
                        pair = user.getWorker().finder;
                }
                if (pair != null)
                    pair.replyConfirm(dto.getData());
                return null;

            case Header.BREAK_PAIR_HEADER:
                if (isPaired())
                    breakPair();
                finder = null;
                return null;

            default:
                break;
        }
        return dto;
    }

    /**
     * Hàm dùng để xử lý dữ liệu trả về cho client
     * @param dataPacket Gói dự liệu client
     */
    public void responseHandle(DTO dataPacket) throws IOException, NullPointerException {
        boolean isForPair = false;
        String header = dataPacket.getHeader();
        String sender = dataPacket.getSender();
        if (sender == null)
            sender = getUser().getUID();
        String receiver;
        String data;

        switch (dataPacket.getHeader()) { //ĐỌc HEADER
            case Header.MESSAGE_HEADER:
                isForPair = true;
                receiver = getPair().getUID();
                data = dataPacket.getData();
                String sentDate = LocalDateTime.now().toString();
                getHistories().get(getPair()).add(new History(getUser().getName(), dataPacket.getData(), sentDate));
                getPair().getWorker().getHistories().get(getUser()).add(new History(getUser().getName(), dataPacket.getData(), sentDate));
                break;

            case Header.NAME_CHECK_HEADER:
                receiver = sender;
                boolean isValid = checkName(dataPacket.getData());
                if (isValid)
                    getUser().setName(dataPacket.getData());
                data = String.valueOf(isValid);
                break;

            default:
                receiver = sender;
                data = dataPacket.getData();
                if (data == null)
                    data = "";
                break;
        }

        DTO serverPacket = new DTO(header);
        serverPacket.setSender(sender);
        serverPacket.setReceiver(receiver);
        serverPacket.setData(data);
        serverPacket.setCreatedDate(LocalDateTime.now().toString());
        user.addResponseList(Services.JsonParser.pack(serverPacket));

        if (isForPair) {
            String output = AES_Encryptor.encrypt(Services.JsonParser.pack(serverPacket), getPair().getSecretKey()); //mã hóa bằng secret key trước khi gửi
            sendToPair(output);
            System.out.println("Server response: " + output
                    + " from " + fromIP
                    + " - ID: " + getUser().getUID() + " to " + getPair().getUID());
        } else {
            String output = AES_Encryptor.encrypt(Services.JsonParser.pack(serverPacket), getUser().getSecretKey()); //mã hóa bằng secret key trước khi gửi
            send(output);
            System.out.println("Server response: " + output
                    + " to " + fromIP
                    + " - ID: " + getUser().getUID());
        }
    }
}
