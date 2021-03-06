package Server;

import Model.DTO;
import Model.User;
import Services.StringUtils;
import Security.Security;
import Model.Header;
import com.google.gson.JsonParser;
import org.openeuler.com.sun.net.ssl.internal.ssl.Provider;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class Server {
    public static final int MAIN_PORT = 5000;
    public static final int VERIFY_PORT = 5005;
    private static ServerSocket serverSocket;
    private static SSLServerSocket sslServerSocket;

    public static ServerManagerGUI manager;
    protected static String keyStore_password;

    public static Thread accepter;
    public static Thread verifier;
    public static final Set<User> users = new LinkedHashSet<>();
    public static final Set<User> queue = new LinkedHashSet<>();
    public static final HashMap<String, String> banList = new HashMap<>();

    public static Thread timer;
    public static final float DEFAULT_TIMER_LOOP = 10f;         //đơn vị phút
    public static final float TIMER_SESSION = 60f;      //đơn vị phút

    public static ExecutorService sslExecutor;
    public static ExecutorService executor;
    public static final int EXECUTOR_CORE = 3;          //Số thread một lúc
    public static final int EXECUTOR_MAX = 5;           //số thread tối đa khi server quá tải
    public static final int EXECUTOR_ALIVE_TIME = 1;    //thời gian một thread được sống nếu không làm gì
    public static final int EXECUTOR_CAPACITY = 10;     //Số lượng hàng chờ có thể chứa của executor

    public static final String RENEW_USER_SESSION = "Renewed";
    public static final int SESSION_EXPIRED_TIME = -1;

    public static final String KEY_STORE_NAME = "myKeyStore.jks";  //store lưu private Key + public Key + chứng chỉ.
    public static final String SERVER_SIDE_PATH = "workspace/Server.Side/";
    public static final String KEY_STORE_ALIAS = "mykey";
    public static final String KEY_STORE_PASSWORD_HASH = "0b1957259ce60db4f9cb5c51cb76a000cefe7234f922a515f56b977951eb6f84";
    public static final String KEY_STORE_PASSWORD_SALT = "5ae877676f3efe25";
    public static final boolean SSL_DEBUG_ENABLE = false;

    /**
     * Khai báo bảo mật cho SSL Socket với Java Secure Socket Extension
     * Key Store: certificate và private ket, public key
     */
    private static void addProvider() {
        /*Adding the JSSE (Java Secure Socket Extension) provider which provides SSL and TLS protocols
        and includes functionality for data encryption, server authentication, message integrity,
        and optional client authentication.*/
        java.security.Security.addProvider(new Provider());
        //specifing the keystore file which contains the certificate/public key and the private key
        System.setProperty("javax.net.ssl.keyStore", SERVER_SIDE_PATH + KEY_STORE_NAME);
        //specifing the password of the keystore file
        System.setProperty("javax.net.ssl.keyStorePassword", keyStore_password);
        //This optional and it is just to show the dump of the details of the handshake process
        if (SSL_DEBUG_ENABLE)
            System.setProperty("javax.net.debug","all");
    }

    /**
     * Mở server
     */
    public static void open() throws IOException, NullPointerException {
        addProvider();
        getKey();
        //SSLSSocketFactory thiết lập the ssl context and tạo SSLSocket
        SSLServerSocketFactory sslServerSocketfactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        //Tạo SSLSocket bằng SSLServerFactory đã thiết lập ssl context và kết nối tới server
        sslServerSocket = (SSLServerSocket) sslServerSocketfactory.createServerSocket(VERIFY_PORT);
        serverSocket = new ServerSocket(MAIN_PORT);
    }

    /**
     * Chờ chứng chỉ từ Client
     */
    public static SSLSocket verifyClient() throws IOException, NullPointerException {
        return (SSLSocket) sslServerSocket.accept();
    }

    /**
     * Chờ Client kết nối tới
     */
    public static Socket acceptClient() throws IOException, NullPointerException {
        return serverSocket.accept();
    }

    /**
     * Gửi message của Server đến Client
     */
    public static String messageOfServerHandle(String message, User to) {
        DTO serverPacket = new DTO(Header.MESSAGE_SERVER_HEADER);
        serverPacket.setSender("Server");
        serverPacket.setReceiver(to.getUID());
        serverPacket.setData(message);
        serverPacket.setCreatedDate(LocalDateTime.now().toString());
        to.addRequestList(JsonParser.parseString("{ \"header\": " + Header.MESSAGE_SERVER_HEADER + " }").toString());
        to.addResponseList(Services.JsonParser.pack(serverPacket));
        to.addDateList(LocalDateTime.now().toString());
        return Security.encrypt(Services.JsonParser.pack(serverPacket), to.getSecretKey()); //mã hóa bằng secret key trước khi gửi
    }

    /**
     * Lấy chứng chỉ, public key, private key từ Key Store myKeyStore.jks
     */
    private static void getKey() {
        try {
            KeyStore ks = KeyStore.getInstance("jks");
            ks.load(new FileInputStream(SERVER_SIDE_PATH + KEY_STORE_NAME), keyStore_password.toCharArray());
            Key key = ks.getKey(KEY_STORE_ALIAS, keyStore_password.toCharArray());
            final Certificate cert = ks.getCertificate("mykey");
            System.out.println("--- Certificate START ---");
            System.out.println(cert);
            System.out.println("--- Certificate END ---\n");
            System.out.println("Public key: " + StringUtils.getStringFromKey(cert.getPublicKey()));
            System.out.println("Private key: " + StringUtils.getStringFromKey(key));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
