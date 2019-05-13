import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.HashSet;

public class ClientWindow extends JFrame implements ActionListener{
    private JTextField tf;
    private JTextArea ta;
    private JScrollPane sp;
    private Container container;
    private JButton connect;
    private JButton quit;
    private JButton send;
    private Socket serverSocket;
    private BufferedInputStream buffIn;
    private BufferedOutputStream buffOut;
    private PrintWriter pw;
    private BufferedReader br;
    private HashSet<String> commandDict;
    /**
     * 窗体构建，绑定监听器发送消息和断开连接
     */
    private ClientWindow(){
        serverSocket=null;
        buffOut=null;
        buffIn=null;
        pw=null;
        br=null;
        commandDict=new HashSet<String>();
        connect=new JButton("启动连接");
        quit=new JButton("停止连接");
        send=new JButton();
        JPanel bt=new JPanel(new GridLayout(2,1));
        send.add(bt);
        bt.add(new JLabel("发送指令"));
        bt.add(new JLabel("(ENTER键)"));
        connect.setBorder(new BevelBorder(BevelBorder.RAISED));
        quit.setBorder(new BevelBorder(BevelBorder.RAISED));
        int screen_x=700;
        int screen_y=600;

        setTitle("Mud客户端");
        setBounds(300,100,screen_x,screen_y);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);
        container=getContentPane();
        JPanel texts=new JPanel(new BorderLayout());
        texts.setBorder(new BevelBorder(BevelBorder.RAISED));
        JPanel controls=new JPanel(new GridLayout(3,1));
        controls.setBorder(new BevelBorder(BevelBorder.RAISED));
        container.add(controls,BorderLayout.EAST);
        container.add(texts);
        controls.add(connect);
        controls.add(quit);
        controls.add(send);
        tf=new JTextField();
        ta=new JTextArea();
        ta.setFont(new Font("素晴字体",Font.PLAIN,15));
        ta.setEnabled(false);
        ta.setDisabledTextColor(Color.BLACK);
        tf.setFont(new Font("素晴字体",Font.PLAIN,20));
        sp=new JScrollPane();
        sp.setViewportView(ta);
        texts.add(sp,BorderLayout.CENTER);
        texts.add(tf,BorderLayout.SOUTH);
        texts.updateUI();
        //界面部分已完成
        connect.addActionListener(this);
        quit.addActionListener(this);
        send.addActionListener(this);
        tf.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {
                String s=tf.getText().trim();
                if(e.getKeyChar()==KeyEvent.VK_ENTER) {
                    if (doSendCommend(s)) {
                        doReceiveMessage();
                        if(s.equalsIgnoreCase("quit"))closeSockets();
                    }
                }
                else if(e.getKeyCode()==KeyEvent.VK_F10)doDisconnect();
            }
            public void keyPressed(KeyEvent e) {}
            public void keyReleased(KeyEvent e) {}
        });
        //按钮逻辑部分
    }
    public static void main(String[] args){
        ClientWindow mc=new ClientWindow();

    }

    /**
     * 启动连接的逻辑,先开启收发交替Socket，然后在新线程开启广播接收Socket，使其阻塞等待
     * @param host 主机地址，大概可以远程连接
     */
    private void doStartLink(String host){
        try {
            serverSocket=new Socket(host,8667);
            buffIn=new BufferedInputStream(serverSocket.getInputStream());
            buffOut=new BufferedOutputStream(serverSocket.getOutputStream());
            pw=new PrintWriter(buffOut);
            br=new BufferedReader(new InputStreamReader(buffIn));
        } catch (IOException e) {
            synchronized (ta) {
                ta.append("服务器连接失败\n");
                ta.setSelectionEnd(ta.getText().length());
            }
            sp.updateUI();
            e.printStackTrace();
            return;
        }
        new Thread(new BroadcastListener(host)).start();
        synchronized (ta) {
            ta.append("服务器连接成功\n请输入用户名进行登陆：\n");
            ta.setSelectionEnd(ta.getText().length());
        }
        sp.updateUI();
    }

    /**
     * 断开连接的逻辑，通过发送信息通知服务端
     * 实际上调用了发送quit的方法
     * 见 doSendCommend
     */
    private void doDisconnect(){
        doSendCommend("quit");//因为逻辑因素，必须拒绝用户该命令
        doReceiveMessage();
        closeSockets();
    }

    /**
     * 发送请求，仅限连接状态，否则提示
     * @param message 发送的指令
     */
    private boolean doSendCommend(String message){
        boolean flag=false;
        if(serverSocket==null||!serverSocket.isConnected()){
            synchronized (ta) {
                ta.append("服务器未连接，发送失败\n");
                ta.setSelectionEnd(ta.getText().length());
            }
        }
        else if(message.length()!=0){
                try {
                    buffOut.write(toByteArray(message.length()));//先写入长度
                    buffOut.flush();
                } catch (IOException e) {
                    System.out.println("data write error");
                    e.printStackTrace();
                }
                pw.print(message);
                pw.flush();
                tf.setText("");//清空
                flag = true;
        }
        tf.setText("");//清空
        return flag;
    }

    /**
     * 关闭流和Socket连接
     */
    private void closeSockets() {
        if(serverSocket!=null&&!serverSocket.isClosed()) {
            try {
                br.close();
                pw.close();
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            serverSocket=null;
            buffOut=null;
            buffIn=null;
            pw=null;
            br=null;
            synchronized (ta) {
                ta.append("服务器连接断开\n");
                ta.setSelectionEnd(ta.getText().length());
            }
            sp.updateUI();
        }
    }

    /**
     * 接收消息并显示在界面中
     */
    private void doReceiveMessage(){
        int len=0;
        char[] buff=new char[1024];
        byte[] num=new byte[4];
        String message;
        try {
            buffIn.read(num,0,4);
            len=toInt(num);
            br.read(buff,0,len);
            message= String.valueOf(buff).trim();
            synchronized (ta) {
                ta.append(message + "\n");
                ta.setSelectionEnd(ta.getText().length());
                //if(message.equals("华盛顿需要你，特工！"))doDisconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开启新线程进行广播监听，服务端发信息使线程停止，防止太多线程
     */
    class BroadcastListener implements Runnable{
        Socket broadcastSocket;
        BufferedInputStream buffIn;
        InputStreamReader br;
        BroadcastListener(String host){
            try {
                broadcastSocket=new Socket(host,8664);
                buffIn=new BufferedInputStream(broadcastSocket.getInputStream());
                br=new InputStreamReader(broadcastSocket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run() {
            String message = null;
            int len=0;
            char[] buff=new char[1024];
            byte[] num=new byte[4];
            while(true){
                try {
                    this.buffIn.read(num,0,4);
                    len=toInt(num);
                    br.read(buff,0,len);//此处一直存在数据超界异常，为len长度获取出错，原因未知
                    message=String.valueOf(buff).trim();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(message.equalsIgnoreCase("disconnect"))break;
                else {
                synchronized (ta) {//或许会争抢输出导致输出混乱，同步保护
                        ta.append(message + "\n");
                        ta.setSelectionEnd(ta.getText().length());
                    }
                }
                buff=new char[1024];
                num=new byte[4];
            }
            //处理关闭操作
            try {
                br.close();
                buffIn.close();
                broadcastSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 以下方法与服务端类似
     * @param num
     * @return
     */
    private int toInt(byte[] num){
        int result=0;
        result=num[0] & 0xff|(num[1]&0xff)<<8|(num[2]&0xff)<<16|(num[3]&0xff)<<24;
        return result;
    }

    private byte[] toByteArray(int num){
        byte[] n=new byte[4];
        for(int i=0;i<4;i++){
            n[i]=(byte)(num>>8*i&0xff);
        }
        return n;
    }

    /**
     * 继承的监听方法
     * @param e 用户事件
     */
    public void actionPerformed(ActionEvent e) {
        if (connect==e.getSource()) {
            doStartLink("127.0.0.1");
        }
        else if(quit==e.getSource()){
            doDisconnect();
        }
        else if(send==e.getSource()){
            String message=tf.getText().trim();
            if(doSendCommend(message))
                doReceiveMessage();
        }
    }
}