package socket.nioSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * ServerSocketChannel，SocketChannel
 * 对应
 * ServerSocket       ，Socket
 *
 * 理解三个概念：Buffer、Channel、Selector
 * 1. 普通socket处理请求的方式：提供送货服务的大学宿舍送货模式。卖东西，接电话，送过去，收钱，返回来，周而复始。高并发会应付不过来
 * 2. NIOSocket处理请求的方式：快递模式。一次带走多个快递，在中转站有专门的分拣员负责分发给相应的送货员。
 *    对应关系：Buffer-要送的货物
 *             Channel-送货员
 *             Selector-中转站的分拣员
 *
 * ServerSocketChannel：可以使用自己的静态工厂方法open创建
 *
 * 阻塞模式：通过ServerSoxketChannel.configureBlocking(boolean)来设置是否采用阻塞模式
 * 阻塞模式不能使用Selector，非阻塞模式下可以调用register方法注册Selector使用
 *
 * Selector:可以通过其工厂方法open创建，创建后通过Channel的register方法注册到ServerSoketChannel或者SocketChannel上，注册完后Selector就可以通过select方法来等待请求，long类型的参数为最长等待时间
 *
 * SelectionKey：保存了处理当前请求的Channel和Selector，并且提供了不同的操作类型。
 * Channel在注册Selector的时候可以通过register的第二个参数选择特定的操作：
 *      SelectionKey.OP_ACCEPT       表示接受请求操作
 *      SelectionKey.OP_CONNECT      接受连接操作
 *      SelectionKey.OP_READ         接受读操作
 *      SelectionKey.OP_WRITE        接受写操作
 *
 * NioSocket中服务端的处理过程分为5步：
 * 1.创建ServerSocketChannel并设置相应参数
 * 2.创建Selector并注册到ServerSocketChannel上
 * 3.调用Selector的select方法请求等待
 * 4.Selector接收到请求后使用selectedKeys返回SelectionKey集合
 * 5.使用SelectionKey获取Channel、Selector和操作类型并进行具体操作
 *
 */
public class NIOServer {
    // main方法启动监听，监听到请求时根据SelectionKey的状态交给内部类Handler进行处理，Handler可以通过重载的构造方法设置编码格式和每次读取数据的最大值。
    public static void main(String[] args) throws Exception {
        // 创建ServerSocketChannel，监听8080端口
        // 使用自己的静态工厂方法open创建
        ServerSocketChannel ssc = ServerSocketChannel.open();
        // 每个ServerSocketChannel对应一个ServerSocket。使用获取到的ServerSocket来绑定端口
        ssc.socket().bind(new InetSocketAddress(8080));
        // 设置非阻塞模式
        ssc.configureBlocking(false);
        // 为ssc注册选择器selector
        Selector selector = Selector.open();    // 通过Selector的静态工厂方法的open()创建选择器
        ssc.register(selector, SelectionKey.OP_ACCEPT);
        // 创建处理器
        Handler handler = new Handler();
        while (true) {
            // 等待请求，每次等待阻塞3s，超过3s后线程持续向下运行，如果传入0或者不传参数将一直阻塞
            if (selector.select(3000) == 0) {
                System.out.printf("等待请求超时...");
                continue;
            }
            System.out.printf("处理请求...");
            // 获取待处理的SelectionKey
            Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();

            while (keyIter.hasNext()) {
                SelectionKey key = keyIter.next();
                // 接收到连接请求时
                if (key.isAcceptable()) {
                    handler.handleAccept(key);
                }
                // 读数据
                if (key.isReadable()) {
                    handler.handleRead(key);
                }
                // 处理完后，从待处理的SelectionKey迭代器中移除当前所使用的key
                keyIter.remove();
            }
        }
    }


    public static class Handler {
        private int bufferSize = 1024;
        private String localCharset = "UTF-8";

        public Handler(){}

        public Handler(int bufferSize) {
            this(bufferSize, null);
        }

        public Handler(String localCharset) {
            this(-1, localCharset);
        }

        public Handler(int bufferSize, String localCharset) {
            if (bufferSize > 0) {
                this.bufferSize = bufferSize;
            }
            if (localCharset != null) {
                this.localCharset = localCharset;
            }
        }

        public void handleAccept(SelectionKey key) throws IOException {
            SocketChannel sc = ((ServerSocketChannel)key.channel()).accept();
            sc.configureBlocking(false);
            sc.register(key.selector(), SelectionKey.OP_READ, ByteBuffer.allocate(bufferSize));
        }

        public void handleRead(SelectionKey key) throws IOException {
            // 获取channel
            SocketChannel sc = (SocketChannel) key.channel();
            // 获取buffer并重置
            ByteBuffer buffer = (ByteBuffer) key.attachment();
            buffer.clear(); // 作用：重新初始化limit、position和mark三个属性，让limit=capacity，position=0，mark=-1
            // 没有读到内容则关闭
            if (sc.read(buffer) == 1) {
                sc.close();
            } else {
                // 将buffer转换为读状态
                buffer.flip();  // 作用：在保存数据时保存一个数据position加1，保存完了后如果想读出来就需要将最后position的位置设置给limit，然后将position设置为0，这样就可以读取所保留的数据了
                // 将buffer中接收到的值按localCharset格式编码后保存到receivedString
                String receivedString = Charset.forName(localCharset).newDecoder().decode(buffer).toString();
                System.out.println("received from client: " + receivedString);

                // 返回数据给客户端
                String sendString = "received data: " + receivedString;
                buffer = ByteBuffer.wrap(sendString.getBytes(localCharset));
                sc.write(buffer);
                sc.close();
            }
        }
    }
/**
 * Handler处理过程中使用到了Buffer，Buffer是java.nio包中的一个类，专门用来存储数据，Buffer里有4个属性非常重要：
 *
 * capacity：容量，也就是Buffer最多可以保存元素的数量，在创建时设置，使用过程中不可以改变
 *
 * limit：可以使用的数量，开始创建时limit和capacity的值相同，如果给limit设置一个值之后，limit就成了最毒可以访问的值，其值不可以超过capacity。
 * 比如，一个Buffer的容量capacity为100，表示最多可以保存100个数据，但是现在只往里面写了20个数据然后要读取，在读取的时候limit就会设置为20
 *
 * position：当前所操作元素所在的索引位置，position从0开始，随着get和put方法自动更新
 *
 * mark：用来暂时保存position的值，position保存到mark后就可以修改并进行相关的操作，操作完后可以通过reset方法将mark的值恢复到position。
 * 比如，Buffer中一共保存了20个数据，position的位置是10，现在想读取15-20之间的数据，这时就可以调用Buffer#mark()将当前的position保存到mark中，然后调用Buffer#position(15)将position指向第15个元素，这时就可以读取了，读取完之后调用Buffer#reset就可以将position恢复到10。
 * mark默认值为-1，而且其值必须小于position的值，如果调用Buffer#position(int newPosition)时传入的newPosition比mark小则会将mark设为-1。
 *
 * 这4个属性的大小关系：mark <= position <= limit <= capacity
 */
}
