package werewolves.connect;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Model{
    private static Socket socket;
    private static String nickname;
    private static BufferedReader input;
    private static PrintWriter output;

    public static synchronized Socket getSocket(){
        return socket;
    }

    public static synchronized void setSocket( Socket socket ){
        Model.socket = socket;
    }

    public static synchronized String getNickname(){
        return nickname;
    }

    public static synchronized void setNickname( String nickname ){
        Model.nickname = nickname;
    }

    public static BufferedReader getInput(){
        return input;
    }

    public static void setInput( BufferedReader input ){
        Model.input = input;
    }

    public static PrintWriter getOutput(){
        return output;
    }

    public static void setOutput( PrintWriter output ){
        Model.output = output;
    }
}
