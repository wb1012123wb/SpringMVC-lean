package socket.cocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * ServerSocket用于服务端，通过accept()监听请求，监听到请求后返回Socket(Socket用于具体完成数据传输)
 * 客户端直接使用Socket发起请求并传输数据
 *
 * ServerSocket的使用分为3步：
 *
 * 1. 创建ServerSocket。ServerSocket的构造方法共有5个，用起来最方便的是ServerSocket(int port)。
 * 2. 调用创建出来的ServerSocket的accept方法进行监听。
 *      accept()是阻塞方法，调用accept()后程序会停下来等待连接请求，接收到请求之前程序不会往下走，接收到请求之后会返回一个Socket。
 * 3. 使用accept()方法返回的Socket与客户端进行通信。
 */
public class Server {
    public static void main(String[] args){
        try {
            // 创建一个ServerSocket监听8080端口
            ServerSocket server = new ServerSocket(8080);
            // 等待请求
            Socket socket = server.accept();
            //接收到请求后使用socket进行通信，创建BufferedReader用于读取数据
            BufferedReader is = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line = is.readLine();
            System.out.println("received from client: " + line);
            // 创建PrintWriter，用于发送数据
            PrintWriter pw = new PrintWriter(socket.getOutputStream());
            pw.println("received data: " + line);
            pw.flush();
            // 关闭资源
            pw.close();
            is.close();
            socket.close();
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
