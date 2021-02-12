package werewolves.connect;

import java.net.Socket;
import java.util.Vector;

public class Model{
    private static Socket socket;
    private static Vector< String > players;
    private static String card;
    private static String nickname;

    public static synchronized Socket getSocket(){
        return socket;
    }

    public static synchronized void setSocket( Socket socket ){
        Model.socket = socket;
    }

    public static synchronized String getCard(){
        return card;
    }

    public static synchronized void setCard( String card ){
        Model.card = card;
    }

    public static synchronized Vector< String > getPlayers(){
        return players;
    }

    public static synchronized void setPlayers( Vector< String > players ){
        Model.players = players;
    }

    public static synchronized String getNickname(){
        return nickname;
    }

    public static synchronized void setNickname( String nickname ){
        Model.nickname = nickname;
    }
}
